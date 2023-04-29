package org.embeddedt.modernfix.mixin.perf.dynamic_resources;

import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableList;
import com.mojang.math.Transformation;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.apache.commons.lang3.tuple.Triple;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/* high priority so that our injectors are added before other mods' */
@Mixin(value = ModelBakery.class, priority = 600)
public abstract class ModelBakeryMixin implements IExtendedModelBakery {

    private static final boolean debugDynamicModelLoading = Boolean.getBoolean("modernfix.debugDynamicModelLoading");

    @Shadow @Final @Mutable public Map<ResourceLocation, UnbakedModel> unbakedCache;

    @Shadow @Final public static ModelResourceLocation MISSING_MODEL_LOCATION;

    @Shadow protected abstract BlockModel loadBlockModel(ResourceLocation location) throws IOException;

    @Shadow @Final private Set<ResourceLocation> loadingStack;

    @Shadow protected abstract void loadModel(ResourceLocation blockstateLocation) throws Exception;

    @Shadow @Final private static Logger LOGGER;

    @Shadow @Final private static Splitter COMMA_SPLITTER;
    @Shadow @Final private static Splitter EQUAL_SPLITTER;
    @Shadow @Nullable static <T extends Comparable<T>> T getValueHelper(Property<T> property, String value) {
        throw new AssertionError();
    }

    @Shadow @Final @Mutable
    private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Shadow @Final @Mutable private Map<ModelBakery.BakedCacheKey, BakedModel> bakedCache;

    @Shadow @Final @Mutable private BlockColors blockColors;
    private Cache<ModelBakery.BakedCacheKey, BakedModel> loadedBakedModels;

    private Cache<ResourceLocation, UnbakedModel> loadedModels;

    private HashMap<ResourceLocation, UnbakedModel> smallLoadingCache = new HashMap<>();


    @Redirect(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/resources/model/ModelBakery;blockColors:Lnet/minecraft/client/color/block/BlockColors;"))
    private void replaceTopLevelBakedModels(ModelBakery bakery, BlockColors val) {
        this.blockColors = val;
        this.loadedBakedModels = CacheBuilder.newBuilder()
                .expireAfterAccess(3, TimeUnit.MINUTES)
                .maximumSize(1000)
                .concurrencyLevel(8)
                .removalListener(this::onModelRemoved)
                .softValues()
                .build();
        this.loadedModels = CacheBuilder.newBuilder()
                .expireAfterAccess(3, TimeUnit.MINUTES)
                .maximumSize(1000)
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
            rl = ((ModelBakery.BakedCacheKey)k).id();
            baked = true;
        }
        ModernFix.LOGGER.warn("Evicted {} model {}", baked ? "baked" : "unbaked", rl);
    }

    private UnbakedModel missingModel;

    private Set<ResourceLocation> blockStateFiles;
    private Set<ResourceLocation> modelFiles;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelBakery;loadBlockModel(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/block/model/BlockModel;", ordinal = 0))
    private BlockModel captureMissingModel(ModelBakery bakery, ResourceLocation location) throws IOException {
        this.missingModel = this.loadBlockModel(location);
        this.blockStateFiles = new HashSet<>();
        this.modelFiles = new HashSet<>();
        return (BlockModel)this.missingModel;
    }

    /**
     * @author embeddedt
     * @reason don't actually load the model.
     */
    @Inject(method = "loadTopLevel", at = @At("HEAD"), cancellable = true)
    private void addTopLevelFile(ModelResourceLocation location, CallbackInfo ci) {
        ci.cancel();
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

    private BiFunction<ResourceLocation, Material, TextureAtlasSprite> textureGetter;

    @Inject(method = "bakeModels", at = @At("HEAD"), cancellable = true)
    private void skipBake(BiFunction<ResourceLocation, Material, TextureAtlasSprite> getter, CallbackInfo ci) {
        textureGetter = getter;
        ci.cancel();
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

    @Inject(method = "cacheAndQueueDependencies", at = @At("RETURN"))
    private void addToSmallLoadingCache(ResourceLocation location, UnbakedModel model, CallbackInfo ci) {
        smallLoadingCache.put(location, model);
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

    private <T extends Comparable<T>, V extends T> BlockState setPropertyGeneric(BlockState state, Property<T> prop, Object o) {
        return state.setValue(prop, (V)o);
    }
    @Redirect(method = "loadModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;"))
    private ImmutableList<BlockState> loadOnlyRelevantBlockState(StateDefinition<Block, BlockState> stateDefinition, ResourceLocation location) {
        ModelResourceLocation mrl = (ModelResourceLocation)location;
        if(Objects.equals(mrl.getVariant(), "inventory"))
            return ImmutableList.of();
        Set<Property<?>> fixedProperties = new HashSet<>();
        BlockState fixedState = stateDefinition.any();
        for(String s : COMMA_SPLITTER.split(mrl.getVariant())) {
            Iterator<String> iterator = EQUAL_SPLITTER.split(s).iterator();
            if (iterator.hasNext()) {
                String s1 = iterator.next();
                Property<?> property = stateDefinition.getProperty(s1);
                if (property != null && iterator.hasNext()) {
                    String s2 = iterator.next();
                    Object value = getValueHelper(property, s2);
                    if (value == null) {
                        throw new RuntimeException("Unknown value: '" + s2 + "' for blockstate property: '" + s1 + "' " + property.getPossibleValues());
                    }
                    fixedState = setPropertyGeneric(fixedState, property, value);
                    fixedProperties.add(property);
                } else if (!s1.isEmpty()) {
                    throw new RuntimeException("Unknown blockstate property: '" + s1 + "'");
                }
            }
        }
        // generate all possible blockstates from the remaining properties
        ArrayList<Property<?>> anyProperties = new ArrayList<>(stateDefinition.getProperties());
        anyProperties.removeAll(fixedProperties);
        ArrayList<BlockState> finalList = new ArrayList<>();
        finalList.add(fixedState);
        for(Property<?> property : anyProperties) {
            ArrayList<BlockState> newPermutations = new ArrayList<>();
            for(BlockState state : finalList) {
                for(Comparable<?> value : property.getPossibleValues()) {
                    newPermutations.add(setPropertyGeneric(state, property, value));
                }
            }
            finalList = newPermutations;
        }
        return ImmutableList.copyOf(finalList);
    }

    @Override
    public BakedModel bakeDefault(ResourceLocation modelLocation) {
        ModelBakery self = (ModelBakery) (Object) this;
        ModelBaker theBaker = self.new ModelBakerImpl(textureGetter, modelLocation);
        return theBaker.bake(modelLocation, BlockModelRotation.X0_Y0);
    }

    @Override
    public ImmutableList<BlockState> getBlockStatesForMRL(StateDefinition<Block, BlockState> stateDefinition, ModelResourceLocation location) {
        return loadOnlyRelevantBlockState(stateDefinition, location);
    }

    private BakedModel bakedMissingModel = null;

    public void setBakedMissingModel(BakedModel m) {
        bakedMissingModel = m;
    }

    public BakedModel getBakedMissingModel() {
        return bakedMissingModel;
    }

    public UnbakedModel mfix$getUnbakedMissingModel() {
        return missingModel;
    }
}
