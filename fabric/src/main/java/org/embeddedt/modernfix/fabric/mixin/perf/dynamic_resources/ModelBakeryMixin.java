package org.embeddedt.modernfix.fabric.mixin.perf.dynamic_resources;

import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableList;
import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.fabricmc.fabric.impl.client.model.ModelLoadingRegistryImpl;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.FolderPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.apache.commons.lang3.tuple.Triple;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
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
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    @Shadow @Final private static String MISSING_MODEL_LOCATION_STRING;

    @Shadow protected abstract void cacheAndQueueDependencies(ResourceLocation location, UnbakedModel model);

    @Shadow @Final private BlockModelDefinition.Context context;
    @Shadow @Final private static Map<ResourceLocation, StateDefinition<Block, BlockState>> STATIC_DEFINITIONS;
    private Cache<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> loadedBakedModels;
    private Cache<ResourceLocation, UnbakedModel> loadedModels;

    private HashMap<ResourceLocation, UnbakedModel> smallLoadingCache = new HashMap<>();

    private boolean inTextureGatheringPass;

    private Set<ResourceLocation> injectedModels;

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
        this.injectedModels = new ObjectOpenHashSet<>();
        ModelLoadingRegistryImpl.LoaderInstance instance = ModelLoadingRegistryImpl.begin((ModelBakery)(Object)this, this.resourceManager);
        instance.onModelPopulation(this.injectedModels::add);
        instance.finish();
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

    private Set<ResourceLocation> blockStateFiles = new ObjectOpenHashSet<>();
    private Set<ResourceLocation> modelFiles = new ObjectOpenHashSet<>();

    private boolean forceLoadModel = false;

    @Inject(method = "loadModel", at = @At(value = "HEAD"), cancellable = true)
    private void ignoreNonFabricModel(ResourceLocation modelLocation, CallbackInfo ci) throws Exception {
        if(this.inTextureGatheringPass && this.blockStateFiles != null && !this.forceLoadModel && !this.injectedModels.contains(modelLocation)) {
            // Custom model processor, try to avoid loading unwrapped models
            // First add this to the list of models to scan for textures
            ResourceLocation blockStateLocation = null;
            if(modelLocation instanceof ModelResourceLocation) {
                ModelResourceLocation location = (ModelResourceLocation)modelLocation;
                if(Objects.equals(location.getVariant(), "inventory")) {
                    modelFiles.add(new ResourceLocation(location.getNamespace(), "item/" + location.getPath()));
                } else {
                    blockStateLocation = new ResourceLocation(location.getNamespace(), location.getPath());
                    blockStateFiles.add(blockStateLocation);
                }
            } else
                modelFiles.add(modelLocation);
            // Now check if it's a wrapped model
            boolean isWrappedModel = false;
            // Set the correct blockstate context
            StateDefinition<Block, BlockState> statecontainer;
            if(blockStateLocation != null) {
                statecontainer = STATIC_DEFINITIONS.get(blockStateLocation);
                if(statecontainer == null)
                    statecontainer = Registry.BLOCK.get(blockStateLocation).getStateDefinition();
            } else
                statecontainer = Blocks.AIR.getStateDefinition();
            this.context.setDefinition(statecontainer);
            // Pretend to be caching the model by caching the missing model, if the actually cached model isn't
            // the exact same instance we know a mixin tampered with it
            this.forceLoadModel = true;
            this.cacheAndQueueDependencies(modelLocation, this.missingModel);
            this.forceLoadModel = false;
            if(this.smallLoadingCache.get(modelLocation) != this.missingModel) {
                // probably a wrapped model, allow it to load normally
                isWrappedModel = true;
            }
            this.smallLoadingCache.clear();
            this.unbakedCache.remove(modelLocation);
            // Load the model through the normal code path
            if(isWrappedModel) {
                ModernFix.LOGGER.warn("Model {} appears to be replaced by another mod and will load at startup", modelLocation);
                this.forceLoadModel = true;
                this.loadModel(modelLocation);
                this.forceLoadModel = false;
            }
            ci.cancel();
        }
    }

    private boolean trustedResourcePack(PackResources pack) {
        return pack instanceof VanillaPackResources ||
                pack instanceof ClientPackSource ||
                pack instanceof FolderPackResources ||
                pack instanceof FilePackResources;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;", ordinal = 0))
    private Object collectExtraTextures(Stream<Material> instance, Collector<?, ?, ?> arCollector) {
        Set<Material> materialsSet = new ObjectOpenHashSet<>(instance.collect(Collectors.toSet()));
        ModelBakeryHelpers.gatherModelMaterials(this.resourceManager, this::trustedResourcePack, materialsSet,
                blockStateFiles, modelFiles, this.missingModel, json -> BlockModel.GSON.fromJson(json, BlockModel.class),
                this::getModel);
        /* take every texture from these folders (1.19.3+ emulation) */
        String[] extraFolders = new String[] {
                "attachment",
                "bettergrass",
                "block",
                "blocks",
                "cape",
                "entity/bed",
                "entity/chest",
                "item",
                "items",
                "model",
                "models",
                "part",
                "pipe",
                "ropebridge",
                "solid_block",
                "spell_effect",
                "spell_projectile"
        };
        for(String folder : extraFolders) {
            Collection<ResourceLocation> textureLocations = this.resourceManager.listResources("textures/" + folder, p -> p.getPath().endsWith(".png")).keySet();
            for(ResourceLocation rl : textureLocations) {
                if(rl.getNamespace().equals("assets")) {
                    /* buggy pack, correct path */
                    int slashIndex = rl.getPath().indexOf('/');
                    String actualNamespace = rl.getPath().substring(0, slashIndex);
                    String actualPath = rl.getPath().substring(slashIndex + 1);
                    rl = new ResourceLocation(actualNamespace, actualPath);
                }
                ResourceLocation texLoc = new ResourceLocation(rl.getNamespace(), rl.getPath().substring(9, rl.getPath().length() - 4));
                materialsSet.add(new Material(TextureAtlas.LOCATION_BLOCKS, texLoc));
            }
        }
        blockStateFiles = null;
        modelFiles = null;
        return materialsSet;
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=textures"))
    private void clearDummyModels(CallbackInfo ci) {
        // discard unwrapped models
        Predicate<Map.Entry<ResourceLocation, UnbakedModel>> isVanillaModel = entry -> entry.getValue() instanceof BlockModel || entry.getValue() instanceof MultiVariant || entry.getValue() instanceof MultiPart;
        this.unbakedCache.entrySet().removeIf(isVanillaModel);
        this.topLevelModels.entrySet().removeIf(isVanillaModel);
    }

    @Redirect(method = "uploadTextures", at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V", ordinal = 0))
    private void skipBake(Set instance, Consumer consumer, TextureManager resourceManager, ProfilerFiller profiler) {
        this.inTextureGatheringPass = false;
        this.injectedModels = null;
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
        // bake indigo models
        Stopwatch watch = Stopwatch.createStarted();
        new ArrayList<>(this.topLevelModels.keySet()).forEach(key -> {
            try {
                this.bake(key, BlockModelRotation.X0_Y0);
            } catch(RuntimeException e) {
                ModernFix.LOGGER.error("Model {} failed to bake", key, e);
            }
        });
        watch.stop();
        ModernFix.LOGGER.info("Early model bake took {}", watch);
        ModernFix.LOGGER.info("{} unbaked models, {} baked models loaded permanently", this.unbakedCache.size(), this.bakedCache.size());
        this.unbakedCache = new LayeredForwardingMap<>(new Map[] { this.unbakedCache, mutableBackingMap });
        this.bakedTopLevelModels = new DynamicBakedModelProvider((ModelBakery)(Object)this, bakedCache);
        if(this.bakedMissingModel != null)
            ((DynamicBakedModelProvider)this.bakedTopLevelModels).setMissingModel(this.bakedMissingModel);

        // ensure missing model is a permanent override
        this.bakedTopLevelModels.put(MISSING_MODEL_LOCATION, this.bake(MISSING_MODEL_LOCATION, BlockModelRotation.X0_Y0));
        this.loadedModels.invalidateAll();
        this.loadedModels.put(MISSING_MODEL_LOCATION, this.missingModel);
        this.topLevelModels.clear();
        this.topLevelModels.put(MISSING_MODEL_LOCATION, this.missingModel);
        this.smallLoadingCache.clear();
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
        if(this.inTextureGatheringPass)
            return model;
        for(ModernFixClientIntegration integration : ModernFixClient.CLIENT_INTEGRATIONS) {
            try {
                model = integration.onUnbakedModelLoad(location, model, (ModelBakery)(Object)this);
            } catch(RuntimeException e) {
                ModernFix.LOGGER.error("Exception firing model load event for {}", location, e);
            }
        }
        return model;
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
                    try {
                        // required as some mods (e.g. EBE) call bake directly on the returned model, without resolving parents themselves
                        result.getMaterials(this::getModel, new HashSet<>());
                    } catch(RuntimeException ignored) {}
                    // We are done with loading, so clear this cache to allow GC of any unneeded models
                    if(mfix$nestedLoads == 0)
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

    private Set<ResourceLocation> bakeStack = new LinkedHashSet<>();

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
                Collection<Material> theMaterials = iunbakedmodel.getMaterials(this::getModel, new HashSet<>());
                /* check if sprites are actually present */
                TextureAtlasSprite missingSprite = this.atlasSet.getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(MissingTextureAtlasSprite.getLocation());
                for(Material m : theMaterials) {
                    if(m.atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)) {
                        TextureAtlasSprite sprite = this.atlasSet.getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(m.texture());
                        if(sprite == missingSprite && !m.texture().equals(MissingTextureAtlasSprite.getLocation()))
                            ModernFix.LOGGER.warn("Texture {} is not present in blocks atlas", m.texture());
                    }
                }

                if(iunbakedmodel == missingModel && debugDynamicModelLoading)
                    LOGGER.warn("Model {} not present", arg);

                if(iunbakedmodel != missingModel) {
                    for(ModernFixClientIntegration integration : ModernFixClient.CLIENT_INTEGRATIONS) {
                        try {
                            iunbakedmodel = integration.onUnbakedModelPreBake(arg, iunbakedmodel, (ModelBakery)(Object)this);
                        } catch(RuntimeException e) {
                            ModernFix.LOGGER.error("Exception firing model pre-bake event for {}", arg, e);
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
                        }
                        if(this.bakedTopLevelModels instanceof DynamicBakedModelProvider)
                            ((DynamicBakedModelProvider)this.bakedTopLevelModels).setMissingModel(bakedMissingModel);
                        ibakedmodel = bakedMissingModel;
                    } else
                        ibakedmodel = iunbakedmodel.bake((ModelBakery) (Object) this, textureGetter, arg2, arg);
                }
                if(ibakedmodel == null) {
                    ModernFix.LOGGER.error("Model {} returned null baked model", arg);
                    ibakedmodel = bakedMissingModel;
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
