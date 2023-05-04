package org.embeddedt.modernfix.fabric.mixin.perf.dynamic_resources;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableList;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.apache.commons.lang3.tuple.Triple;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider;
import org.embeddedt.modernfix.dynamicresources.ModelBakeryHelpers;
import org.embeddedt.modernfix.util.LayeredForwardingMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

/* high priority so that our injectors are added before other mods' */
@Mixin(value = ModelBakery.class, priority = 600)
@ClientOnlyMixin
public abstract class ModelBakeryMixin implements IExtendedModelBakery {

    private static final boolean debugDynamicModelLoading = Boolean.getBoolean("modernfix.debugDynamicModelLoading");

    @Shadow @Final @Mutable public Map<ResourceLocation, UnbakedModel> unbakedCache;

    @Shadow @Final public static ModelResourceLocation MISSING_MODEL_LOCATION;

    @Shadow @Final protected ResourceManager resourceManager;
    @Shadow private AtlasSet atlasSet;
    @Shadow @Final private Set<ResourceLocation> loadingStack;

    @Shadow protected abstract void loadModel(ResourceLocation blockstateLocation) throws Exception;

    @Shadow @Final private static Logger LOGGER;

    @Shadow @Final @Mutable
    private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Shadow @Final @Mutable private Map<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> bakedCache;

    @Shadow @Final public static BlockModel GENERATION_MARKER;

    @Shadow @Final private static ItemModelGenerator ITEM_MODEL_GENERATOR;

    @Shadow public abstract UnbakedModel getModel(ResourceLocation modelLocation);

    @Shadow @Nullable public abstract BakedModel bake(ResourceLocation location, ModelState transform);

    @Shadow @Final private Map<ResourceLocation, UnbakedModel> topLevelModels;
    private Cache<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> loadedBakedModels;
    private Cache<ResourceLocation, UnbakedModel> loadedModels;

    private HashMap<ResourceLocation, UnbakedModel> smallLoadingCache = new HashMap<>();

    private boolean inTextureGatheringPass;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V", ordinal = 0))
    private void replaceTopLevelBakedModels(ProfilerFiller filler, String s) {
        this.inTextureGatheringPass = true;
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
        // temporarily replace this map to capture models into the small loading cache
        Map<ResourceLocation, UnbakedModel> oldMap = this.unbakedCache;
        this.unbakedCache = new ForwardingMap<ResourceLocation, UnbakedModel>() {
            @Override
            protected Map<ResourceLocation, UnbakedModel> delegate() {
                return oldMap;
            }

            @Override
            public UnbakedModel put(ResourceLocation key, UnbakedModel value) {
                smallLoadingCache.put(key, value);
                return super.put(key, value);
            }
        };
        filler.push(s);
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

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0), index = 1)
    private Object captureMissingModel(Object model) {
        this.missingModel = (UnbakedModel)model;
        return this.missingModel;
    }

    @Inject(method = "uploadTextures", at = @At(value = "FIELD", target = "Lnet/minecraft/client/resources/model/ModelBakery;topLevelModels:Ljava/util/Map;", ordinal = 0), cancellable = true)
    private void skipBake(TextureManager resourceManager, ProfilerFiller profiler, CallbackInfoReturnable<AtlasSet> cir) {
        profiler.pop();
        this.inTextureGatheringPass = false;
        // hand off to the dynamic model system
        this.loadedModels.put(MISSING_MODEL_LOCATION, this.missingModel);
        this.bakedCache = loadedBakedModels.asMap();
        ConcurrentMap<ResourceLocation, UnbakedModel> unbakedCacheBackingMap = loadedModels.asMap();
        Map<ResourceLocation, UnbakedModel> mutableBackingMap = new ForwardingMap<ResourceLocation, UnbakedModel>() {
            @Override
            protected Map<ResourceLocation, UnbakedModel> delegate() {
                return unbakedCacheBackingMap;
            }

            @Override
            public UnbakedModel put(ResourceLocation key, UnbakedModel value) {
                smallLoadingCache.put(key, value);
                return super.put(key, value);
            }
        };
        // discard unwrapped models
        int oldSize = this.unbakedCache.size();
        Predicate<Map.Entry<ResourceLocation, UnbakedModel>> isVanillaModel = entry -> entry.getValue() instanceof BlockModel || entry.getValue() instanceof MultiVariant || entry.getValue() instanceof MultiPart;
        // bake indigo models
        this.topLevelModels.entrySet().forEach((entry) -> {
            if(!isVanillaModel.test(entry))
                this.bake(entry.getKey(), BlockModelRotation.X0_Y0);
        });
        this.unbakedCache.entrySet().removeIf(isVanillaModel);
        ModernFix.LOGGER.info("{} models evicted, {} custom models loaded permanently", oldSize - this.unbakedCache.size(), this.unbakedCache.size());
        this.unbakedCache = new LayeredForwardingMap<>(new Map[] { this.unbakedCache, mutableBackingMap });
        this.bakedTopLevelModels = new DynamicBakedModelProvider((ModelBakery)(Object)this, bakedCache);

        // ensure missing model is a permanent override
        this.bakedTopLevelModels.put(MISSING_MODEL_LOCATION, this.bake(MISSING_MODEL_LOCATION, BlockModelRotation.X0_Y0));
        this.loadedModels.invalidateAll();
        this.loadedModels.put(MISSING_MODEL_LOCATION, this.missingModel);
        this.topLevelModels.clear();
        this.topLevelModels.put(MISSING_MODEL_LOCATION, this.missingModel);
        this.smallLoadingCache.clear();
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
                            this.loadingStack.remove(resourcelocation);
                        }
                    }

                    // We have to get the result from the temporary cache used for a model load
                    // As in pathological cases (e.g. Pedestals on 1.19) unbakedCache can lose
                    // the model immediately
                    UnbakedModel result = smallLoadingCache.getOrDefault(modelLocation, iunbakedmodel);
                    // We are done with loading, so clear this cache to allow GC of any unneeded models
                    smallLoadingCache.clear();
                    cir.setReturnValue(result);
                }
            }
        }
    }

    @Redirect(method = "loadModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;"))
    private ImmutableList<BlockState> loadOnlyRelevantBlockState(StateDefinition<Block, BlockState> stateDefinition, ResourceLocation location) {
        if(this.inTextureGatheringPass)
            return stateDefinition.getPossibleStates();
        else
            return ModelBakeryHelpers.getBlockStatesForMRL(stateDefinition, (ModelResourceLocation)location);
    }

    @Override
    public ImmutableList<BlockState> getBlockStatesForMRL(StateDefinition<Block, BlockState> stateDefinition, ModelResourceLocation location) {
        return loadOnlyRelevantBlockState(stateDefinition, location);
    }

    private BakedModel bakedMissingModel = null;

    @Inject(method = "bake", at = @At("HEAD"), cancellable = true)
    public void getOrLoadBakedModelDynamic(ResourceLocation arg, ModelState arg2, CallbackInfoReturnable<BakedModel> cir) {
        Function<Material, TextureAtlasSprite> textureGetter = mat -> this.atlasSet.getSprite(mat);
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
                BakedModel ibakedmodel = null;
                if (iunbakedmodel instanceof BlockModel) {
                    BlockModel blockmodel = (BlockModel)iunbakedmodel;
                    if (blockmodel.getRootModel() == GENERATION_MARKER) {
                        ibakedmodel = ITEM_MODEL_GENERATOR.generateBlockModel(textureGetter, blockmodel).bake((ModelBakery)(Object)this, blockmodel, this.atlasSet::getSprite, arg2, arg, false);
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
                if(ibakedmodel == null) {
                    ModernFix.LOGGER.error("Model {} returned null baked model", arg);
                    ibakedmodel = bakedMissingModel;
                }
                // TODO event
                this.bakedCache.put(triple, ibakedmodel);
                cir.setReturnValue(ibakedmodel);
            }
        }
    }
}
