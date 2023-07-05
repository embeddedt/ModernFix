package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.mojang.math.Transformation;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.FolderPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.packs.DelegatingResourcePack;
import net.minecraftforge.fml.packs.ModFileResourcePack;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider;
import org.embeddedt.modernfix.dynamicresources.ModelBakeryHelpers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

/* high priority so that our injectors are added before other mods' */
@Mixin(value = ModelBakery.class, priority = 600)
@ClientOnlyMixin
public abstract class ModelBakeryMixin implements IExtendedModelBakery {

    private static final boolean debugDynamicModelLoading = Boolean.getBoolean("modernfix.debugDynamicModelLoading");

    @Shadow @Final @Mutable public Map<ResourceLocation, UnbakedModel> unbakedCache;

    @Shadow @Final public static ModelResourceLocation MISSING_MODEL_LOCATION;

    @Shadow protected abstract BlockModel loadBlockModel(ResourceLocation location) throws IOException;

    @Shadow @Final protected ResourceManager resourceManager;
    @Shadow @Nullable private AtlasSet atlasSet;
    @Shadow @Final private Set<ResourceLocation> loadingStack;

    @Shadow protected abstract void loadModel(ResourceLocation blockstateLocation) throws Exception;

    @Shadow @Final private static Logger LOGGER;

    @Shadow @Final @Mutable
    private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Shadow @Final @Mutable private Map<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> bakedCache;

    @Shadow @Final public static BlockModel GENERATION_MARKER;

    @Shadow @Final private static ItemModelGenerator ITEM_MODEL_GENERATOR;

    @Shadow public abstract UnbakedModel getModel(ResourceLocation modelLocation);

    @Shadow @Nullable public abstract BakedModel getBakedModel(ResourceLocation arg, ModelState arg2, Function<Material, TextureAtlasSprite> textureGetter);

    @Shadow @org.jetbrains.annotations.Nullable public abstract BakedModel bake(ResourceLocation location, ModelState transform);

    private Cache<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> loadedBakedModels;
    private Cache<ResourceLocation, UnbakedModel> loadedModels;

    private HashMap<ResourceLocation, UnbakedModel> smallLoadingCache = new HashMap<>();


    @Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/client/color/block/BlockColors;Z)V", at = @At("RETURN"))
    private void replaceTopLevelBakedModels(ResourceManager manager, BlockColors colors, boolean vanillaBakery, CallbackInfo ci) {
        this.loadedBakedModels = CacheBuilder.newBuilder()
                .expireAfterAccess(ModelBakeryHelpers.MAX_MODEL_LIFETIME_SECS, TimeUnit.SECONDS)
                .maximumSize(ModelBakeryHelpers.MAX_BAKED_MODEL_COUNT)
                .concurrencyLevel(8)
                .removalListener(this::onModelRemoved)
                .softValues()
                .build();
        this.loadedModels = CacheBuilder.newBuilder()
                .expireAfterAccess(ModelBakeryHelpers.MAX_MODEL_LIFETIME_SECS, TimeUnit.SECONDS)
                .maximumSize(ModelBakeryHelpers.MAX_UNBAKED_MODEL_COUNT)
                .concurrencyLevel(8)
                .removalListener(this::onModelRemoved)
                .softValues()
                .build();
        this.bakedCache = loadedBakedModels.asMap();
        this.unbakedCache = loadedModels.asMap();
        this.bakedTopLevelModels = new DynamicBakedModelProvider((ModelBakery)(Object)this, bakedCache);
    }

    private <K, V> void onModelRemoved(RemovalNotification<K, V> notification) {
        if(!debugDynamicModelLoading)
            return;
        Object k = notification.getKey();
        if(k == null)
            return;
        ResourceLocation rl;
        boolean baked = false;
        if(k instanceof ResourceLocation) {
            rl = (ResourceLocation)k;
        } else {
            rl = ((Triple<ResourceLocation, Transformation, Boolean>)k).getLeft();
            baked = true;
        }
        ModernFix.LOGGER.warn("Evicted {} model {}", baked ? "baked" : "unbaked", rl);
    }

    private UnbakedModel missingModel;

    private Set<ResourceLocation> blockStateFiles;
    private Set<ResourceLocation> modelFiles;

    @Redirect(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelBakery;loadBlockModel(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/block/model/BlockModel;", ordinal = 0))
    private BlockModel captureMissingModel(ModelBakery bakery, ResourceLocation location) throws IOException {
        this.missingModel = this.loadBlockModel(location);
        this.blockStateFiles = new HashSet<>();
        this.modelFiles = new HashSet<>();
        return (BlockModel)this.missingModel;
    }

    /**
     * @author embeddedt
     * @reason don't actually load the model. instead, keep track of if we need to load a blockstate or a model,
     * and save the info into the two lists
     */
    @Inject(method = "loadTopLevel", at = @At("HEAD"), cancellable = true)
    private void addTopLevelFile(ModelResourceLocation location, CallbackInfo ci) {
        ci.cancel();
        if(Objects.equals(location.getVariant(), "inventory")) {
            modelFiles.add(new ResourceLocation(location.getNamespace(), "item/" + location.getPath()));
        } else {
            blockStateFiles.add(new ResourceLocation(location.getNamespace(), location.getPath()));
        }
    }

    @Redirect(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;gatherFluidTextures(Ljava/util/Set;)V", remap = false))
    private void gatherModelTextures(Set<Material> materialSet) {
        ForgeHooksClient.gatherFluidTextures(materialSet);
        gatherModelMaterials(materialSet);
    }

    @Redirect(method = "processLoading", at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V", ordinal = 0))
    private void fetchStaticDefinitions(Map<ResourceLocation, StateDefinition<Block, BlockState>> map, BiConsumer<ResourceLocation, StateDefinition<Block, BlockState>> func) {
        map.forEach((loc, def) -> blockStateFiles.add(loc));
    }

    @Redirect(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;", ordinal = 0))
    private ImmutableList<BlockState> fetchBlocks(StateDefinition<Block, BlockState> def) {
        blockStateFiles.add(def.any().getBlock().getRegistryName());
        return ImmutableList.of();
    }

    private boolean trustedResourcePack(PackResources pack) {
        return pack instanceof VanillaPackResources ||
                pack instanceof ModFileResourcePack ||
                pack instanceof ClientPackSource ||
                pack instanceof DelegatingResourcePack ||
                pack instanceof FolderPackResources ||
                pack instanceof FilePackResources;
    }

    /**
     * Load all blockstate JSONs and model files, collect textures.
     */
    private void gatherModelMaterials(Set<Material> materialSet) {
        Function<JsonElement, BlockModel> modelDeserializer = model -> ModelLoaderRegistry.ExpandedBlockModelDeserializer.INSTANCE.fromJson(model, BlockModel.class);
        ModelBakeryHelpers.gatherModelMaterials(this.resourceManager, this::trustedResourcePack, materialSet, blockStateFiles,
                modelFiles, missingModel, modelDeserializer, this::getModel);
        loadedModels.invalidateAll();
        loadedModels.put(MISSING_MODEL_LOCATION, missingModel);
    }

    @Inject(method = "uploadTextures", at = @At(value = "FIELD", target = "Lnet/minecraft/client/resources/model/ModelBakery;topLevelModels:Ljava/util/Map;", ordinal = 0), cancellable = true)
    private void skipBake(TextureManager resourceManager, ProfilerFiller profiler, CallbackInfoReturnable<AtlasSet> cir) {
        profiler.pop();
        // ensure missing model is a permanent override
        this.bakedTopLevelModels.put(MISSING_MODEL_LOCATION, this.getBakedModel(MISSING_MODEL_LOCATION, BlockModelRotation.X0_Y0, this.atlasSet::getSprite));
        cir.setReturnValue(atlasSet);
    }

    /**
     * Use the already loaded missing model instead of the cache entry (which will probably get evicted).
     */
    @Redirect(method = "loadModel", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 1))
    private Object getMissingModel(Map map, Object rl) {
        if(rl == MISSING_MODEL_LOCATION && map == unbakedCache)
            return missingModel;
        return unbakedCache.get(rl);
    }

    @ModifyVariable(method = "cacheAndQueueDependencies", at = @At("HEAD"), argsOnly = true)
    private UnbakedModel fireUnbakedEvent(UnbakedModel model, ResourceLocation location) {
        for(ModernFixClientIntegration integration : ModernFixClient.CLIENT_INTEGRATIONS) {
            try {
                model = integration.onUnbakedModelLoad(location, model, (ModelBakery)(Object)this);
            } catch(RuntimeException e) {
                ModernFix.LOGGER.error("Exception firing model load event for {}", location, e);
            }
        }
        return model;
    }

    @Inject(method = "cacheAndQueueDependencies", at = @At("RETURN"))
    private void addToSmallLoadingCache(ResourceLocation location, UnbakedModel model, CallbackInfo ci) {
        smallLoadingCache.put(location, model);
    }

    private int mfix$nestedLoads = 0;

    /**
     * @author embeddedt
     * @reason synchronize
     */
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    public void getOrLoadModelDynamic(ResourceLocation modelLocation, CallbackInfoReturnable<UnbakedModel> cir) {
        if(modelLocation.equals(MISSING_MODEL_LOCATION)) {
            cir.setReturnValue(missingModel);
            return;
        }
        UnbakedModel existing = this.unbakedCache.get(modelLocation);
        if (existing != null) {
            cir.setReturnValue(existing);
        } else {
            synchronized(this) {
                if (this.loadingStack.contains(modelLocation)) {
                    throw new IllegalStateException("Circular reference while loading " + modelLocation);
                } else {
                    this.loadingStack.add(modelLocation);
                    UnbakedModel iunbakedmodel = missingModel;

                    while(!this.loadingStack.isEmpty()) {
                        ResourceLocation resourcelocation = this.loadingStack.iterator().next();

                        mfix$nestedLoads++;
                        try {
                            existing = this.unbakedCache.get(resourcelocation);
                            if (existing == null) {
                                if(debugDynamicModelLoading)
                                    LOGGER.info("Loading {}", resourcelocation);
                                this.loadModel(resourcelocation);
                            } else
                                smallLoadingCache.put(resourcelocation, existing);
                        } catch (ModelBakery.BlockStateDefinitionException var9) {
                            LOGGER.warn(var9.getMessage());
                            this.unbakedCache.put(resourcelocation, iunbakedmodel);
                            smallLoadingCache.put(resourcelocation, iunbakedmodel);
                        } catch (Exception var10) {
                            LOGGER.warn("Unable to load model: '{}' referenced from: {}: {}", resourcelocation, modelLocation, var10);
                            this.unbakedCache.put(resourcelocation, iunbakedmodel);
                            smallLoadingCache.put(resourcelocation, iunbakedmodel);
                        } finally {
                            mfix$nestedLoads--;
                            this.loadingStack.remove(resourcelocation);
                        }
                    }

                    // We have to get the result from the temporary cache used for a model load
                    // As in pathological cases (e.g. Pedestals on 1.19) unbakedCache can lose
                    // the model immediately
                    UnbakedModel result = smallLoadingCache.getOrDefault(modelLocation, iunbakedmodel);
                    // We are done with loading, so clear this cache to allow GC of any unneeded models
                    if(mfix$nestedLoads == 0)
                        smallLoadingCache.clear();
                    cir.setReturnValue(result);
                }
            }
        }
    }

    private <T extends Comparable<T>, V extends T> BlockState setPropertyGeneric(BlockState state, Property<T> prop, Object o) {
        return state.setValue(prop, (V)o);
    }
    @Redirect(method = "loadModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;"))
    private ImmutableList<BlockState> loadOnlyRelevantBlockState(StateDefinition<Block, BlockState> stateDefinition, ResourceLocation location) {
       return ModelBakeryHelpers.getBlockStatesForMRL(stateDefinition, (ModelResourceLocation)location);
    }

    @Override
    public ImmutableList<BlockState> getBlockStatesForMRL(StateDefinition<Block, BlockState> stateDefinition, ModelResourceLocation location) {
        return loadOnlyRelevantBlockState(stateDefinition, location);
    }

    private BakedModel bakedMissingModel = null;

    @Inject(method = "getBakedModel", at = @At("HEAD"), cancellable = true)
    public void getOrLoadBakedModelDynamic(ResourceLocation arg, ModelState arg2, Function<Material, TextureAtlasSprite> textureGetter, CallbackInfoReturnable<BakedModel> cir) {
        Triple<ResourceLocation, Transformation, Boolean> triple = Triple.of(arg, arg2.getRotation(), arg2.isUvLocked());
        BakedModel existing = this.bakedCache.get(triple);
        if (existing != null) {
            cir.setReturnValue(existing);
        } else if (this.atlasSet == null) {
            throw new IllegalStateException("bake called too early");
        } else {
            synchronized (this) {
                if(debugDynamicModelLoading)
                    LOGGER.info("Baking {}", arg);
                UnbakedModel iunbakedmodel = this.getModel(arg);
                iunbakedmodel.getMaterials(this::getModel, new HashSet<>());
                if(iunbakedmodel == missingModel && debugDynamicModelLoading)
                    LOGGER.warn("Model {} not present", arg);

                if(iunbakedmodel != missingModel) {
                    for(ModernFixClientIntegration integration : ModernFixClient.CLIENT_INTEGRATIONS) {
                        try {
                            iunbakedmodel = integration.onUnbakedModelPreBake(arg, iunbakedmodel, (ModelBakery)(Object)this);
                        } catch(RuntimeException e) {
                            ModernFix.LOGGER.error("Exception encountered firing bake event for {}", arg, e);
                        }
                    }
                }

                BakedModel ibakedmodel = null;
                if (iunbakedmodel instanceof BlockModel) {
                    BlockModel blockmodel = (BlockModel)iunbakedmodel;
                    if (blockmodel.getRootModel() == GENERATION_MARKER) {
                        ibakedmodel = ITEM_MODEL_GENERATOR.generateBlockModel(textureGetter, blockmodel).bake((ModelBakery)(Object)this, blockmodel, textureGetter, arg2, arg, false);
                    }
                }
                if(ibakedmodel == null) {
                    if(iunbakedmodel == missingModel) {
                        // use a shared baked missing model
                        if(bakedMissingModel == null) {
                            bakedMissingModel = iunbakedmodel.bake((ModelBakery) (Object) this, textureGetter, arg2, arg);
                            ((DynamicBakedModelProvider)this.bakedTopLevelModels).setMissingModel(bakedMissingModel);
                        }
                        ibakedmodel = bakedMissingModel;
                    } else
                        ibakedmodel = iunbakedmodel.bake((ModelBakery) (Object) this, textureGetter, arg2, arg);
                }
                for(ModernFixClientIntegration integration : ModernFixClient.CLIENT_INTEGRATIONS) {
                    try {
                        ibakedmodel = integration.onBakedModelLoad(arg, iunbakedmodel, ibakedmodel, arg2, (ModelBakery)(Object)this);
                    } catch(RuntimeException e) {
                        ModernFix.LOGGER.error("Exception encountered firing bake event for {}", arg, e);
                    }
                }
                this.bakedCache.put(triple, ibakedmodel);
                cir.setReturnValue(ibakedmodel);
            }
        }
    }
}
