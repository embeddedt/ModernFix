package org.embeddedt.modernfix.fabric.mixin.perf.dynamic_resources;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
import org.embeddedt.modernfix.duck.IExtendedModelBaker;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider;
import org.embeddedt.modernfix.dynamicresources.ModelBakeryHelpers;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/* low priority so that our injectors are added after other mods' */
@Mixin(value = ModelBakery.class, priority = 1100)
@ClientOnlyMixin
public abstract class ModelBakeryMixin implements IExtendedModelBakery {

    private static final boolean debugDynamicModelLoading = Boolean.getBoolean("modernfix.debugDynamicModelLoading");

    @Shadow @Final @Mutable public Map<ResourceLocation, UnbakedModel> unbakedCache;

    @Shadow @Final public static ModelResourceLocation MISSING_MODEL_LOCATION;

    @Shadow @Final private Set<ResourceLocation> loadingStack;

    @Shadow protected abstract void loadModel(ResourceLocation blockstateLocation) throws Exception;

    @Shadow @Final @Mutable
    private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Shadow @Final @Mutable private Map<ModelBakery.BakedCacheKey, BakedModel> bakedCache;

    @Shadow @Final @Mutable private BlockColors blockColors;
    @Shadow @Final private static Logger LOGGER;

    @Shadow
    public abstract void loadTopLevel(ModelResourceLocation modelResourceLocation);

    @Shadow public abstract UnbakedModel getModel(ResourceLocation resourceLocation);

    private Cache<ModelBakery.BakedCacheKey, BakedModel> loadedBakedModels;

    private Cache<ResourceLocation, UnbakedModel> loadedModels;

    private HashMap<ResourceLocation, UnbakedModel> smallLoadingCache = new HashMap<>();

    private boolean ignoreModelLoad;

    // disable fabric recursion
    @SuppressWarnings("unused")
    private boolean fabric_enableGetOrLoadModelGuard;


    @Redirect(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/resources/model/ModelBakery;blockColors:Lnet/minecraft/client/color/block/BlockColors;"))
    private void replaceTopLevelBakedModels(ModelBakery bakery, BlockColors val) {
        // we can handle recursion in getModel without issues
        fabric_enableGetOrLoadModelGuard = false;
        this.blockColors = val;
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
                //.softValues()
                .build();
        this.bakedCache = loadedBakedModels.asMap();
        ConcurrentMap<ResourceLocation, UnbakedModel> unbakedCacheBackingMap = loadedModels.asMap();
        this.unbakedCache = new ForwardingMap<ResourceLocation, UnbakedModel>() {
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
        this.bakedTopLevelModels = new DynamicBakedModelProvider((ModelBakery)(Object)this, bakedCache);
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", ordinal = 0))
    private void ignoreFutureModelLoads(CallbackInfo ci) {
        this.ignoreModelLoad = true;
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
            rl = ((ModelBakery.BakedCacheKey)k).id();
            baked = true;
        }
        /* can fire when a model is replaced */
        if(!baked && this.loadedModels.getIfPresent(rl) != null)
            return;
        ModernFix.LOGGER.warn("Evicted {} model {}", baked ? "baked" : "unbaked", rl);
    }

    private UnbakedModel missingModel;

    private Set<ResourceLocation> blockStateFiles;
    private Set<ResourceLocation> modelFiles;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0), index = 1)
    private Object captureMissingModel(Object model) {
        this.missingModel = (UnbakedModel)model;
        this.blockStateFiles = new HashSet<>();
        this.modelFiles = new HashSet<>();
        return this.missingModel;
    }

    /**
     * @author embeddedt
     * @reason don't actually load most models
     */
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelBakery;loadTopLevel(Lnet/minecraft/client/resources/model/ModelResourceLocation;)V"))
    private void addTopLevelFile(ModelBakery bakery, ModelResourceLocation location) {
        if(location == MISSING_MODEL_LOCATION || !this.ignoreModelLoad) {
            loadTopLevel(location);
        }
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V", ordinal = 0))
    private void fetchStaticDefinitions(Map<ResourceLocation, StateDefinition<Block, BlockState>> map, BiConsumer<ResourceLocation, StateDefinition<Block, BlockState>> func) {
        /* no-op */
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;", ordinal = 0))
    private ImmutableList<BlockState> fetchBlocks(StateDefinition<Block, BlockState> def) {
        /* no-op */
        return ImmutableList.of();
    }

    /**
     * Make a copy of the top-level model list to avoid CME if more models get loaded here.
     */
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;", ordinal = 0))
    private Collection<?> copyTopLevelModelList(Map<?, ?> map) {
        return new ArrayList<>(map.values());
    }

    private BiFunction<ResourceLocation, Material, TextureAtlasSprite> textureGetter;

    @Inject(method = "bakeModels", at = @At("HEAD"))
    private void captureGetter(BiFunction<ResourceLocation, Material, TextureAtlasSprite> getter, CallbackInfo ci) {
        this.ignoreModelLoad = false;
        textureGetter = getter;
        DynamicBakedModelProvider.currentInstance = (DynamicBakedModelProvider)this.bakedTopLevelModels;
    }

    @Redirect(method = "bakeModels", at = @At(value = "INVOKE", target = "Ljava/util/Map;keySet()Ljava/util/Set;"))
    private Set<ResourceLocation> skipBake(Map<ResourceLocation, UnbakedModel> instance) {
        Set<ResourceLocation> modelSet = new HashSet<>(instance.keySet());
        if(modelSet.size() > 0)
            ModernFix.LOGGER.info("Early baking {} models", modelSet.size());
        return modelSet;
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
                        result.resolveParents(this::getModel);
                    } catch(RuntimeException ignored) {}
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
        if(!(location instanceof ModelResourceLocation) || Minecraft.getInstance().getOverlay() != null || Minecraft.getInstance().level == null)
            return stateDefinition.getPossibleStates();
        return ModelBakeryHelpers.getBlockStatesForMRL(stateDefinition, (ModelResourceLocation)location);
    }

    @Override
    public BakedModel bakeDefault(ResourceLocation modelLocation, ModelState state) {
        ModelBakery.BakedCacheKey key = new ModelBakery.BakedCacheKey(modelLocation, BlockModelRotation.X0_Y0.getRotation(), BlockModelRotation.X0_Y0.isUvLocked());
        BakedModel m = loadedBakedModels.getIfPresent(key);
        if(m != null)
            return m;
        ModelBakery self = (ModelBakery) (Object) this;
        ModelBaker theBaker = self.new ModelBakerImpl(textureGetter, modelLocation);
        ((IExtendedModelBaker)theBaker).throwOnMissingModel(true);
        synchronized(this) { m = theBaker.bake(modelLocation, state); }
        if(m != null)
            loadedBakedModels.put(key, m);
        return m;
    }

    @Override
    public ImmutableList<BlockState> getBlockStatesForMRL(StateDefinition<Block, BlockState> stateDefinition, ModelResourceLocation location) {
        return loadOnlyRelevantBlockState(stateDefinition, location);
    }

    public UnbakedModel mfix$getUnbakedMissingModel() {
        return missingModel;
    }
}
