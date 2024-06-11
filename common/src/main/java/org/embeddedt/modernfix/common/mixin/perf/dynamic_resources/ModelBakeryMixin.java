package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IBlockStateModelLoader;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.util.DynamicOverridableMap;
import org.embeddedt.modernfix.util.LRUMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Mixin(ModelBakery.class)
@ClientOnlyMixin
public abstract class ModelBakeryMixin implements IExtendedModelBakery {
    @Unique
    private BlockStateModelLoader dynamicLoader;

    @Unique
    private final ReentrantLock modelBakeryLock = new ReentrantLock();

    @Unique
    private ModelBakery.TextureGetter textureGetter;

    @Unique
    private BakedModel bakedMissingModel;

    @Shadow abstract UnbakedModel getModel(ResourceLocation resourceLocation);

    @Shadow @Final private UnbakedModel missingModel;

    @Unique
    private static final boolean DEBUG_MODEL_LOADS = Boolean.getBoolean("modernfix.debugDynamicModelLoading");

    /**
     * Bake a model using the provided texture getter and location. The model is stored in {@link ModelBakeryMixin#bakedTopLevelModels}.
     */
    @Shadow protected abstract void method_61072(ModelBakery.TextureGetter getter, ModelResourceLocation location, UnbakedModel model);

    @Shadow @Mutable @Final private Map<ModelResourceLocation, BakedModel> bakedTopLevelModels;
    @Shadow @Mutable @Final public Map<ModelResourceLocation, UnbakedModel> topLevelModels;
    @Shadow @Mutable @Final private Map<ResourceLocation, UnbakedModel> unbakedCache;
    @Shadow @Mutable @Final public Map<ModelBakery.BakedCacheKey, BakedModel> bakedCache;

    @Shadow protected abstract void loadItemModelAndDependencies(ResourceLocation resourceLocation);


    @Shadow @Final public static ModelResourceLocation MISSING_MODEL_VARIANT;

    private final Map<ModelResourceLocation, BakedModel> mfix$emulatedBakedRegistry = new DynamicOverridableMap<>(this::loadBakedModelDynamic);

    @Unique
    private UnbakedModel loadUnbakedModelDynamic(ModelResourceLocation location) {
        if(location.equals(MISSING_MODEL_VARIANT)) {
            return missingModel;
        }
        if(DEBUG_MODEL_LOADS) {
            ModernFix.LOGGER.info("Loading model {}", location);
        }
        if(location.variant().equals("inventory")) {
            this.loadItemModelAndDependencies(location.id());
        } else {
            ((IBlockStateModelLoader)dynamicLoader).loadSpecificBlock(location);
        }
        return this.topLevelModels.getOrDefault(location, this.missingModel);
    }

    @Unique
    private BakedModel loadBakedModelDynamic(ModelResourceLocation location) {
        if(location.equals(MISSING_MODEL_VARIANT)) {
            return bakedMissingModel;
        }
        BakedModel model;
        modelBakeryLock.lock();
        try {
            model = bakedTopLevelModels.get(location);
            if(model == null) {
                UnbakedModel prototype = loadUnbakedModelDynamic(location);
                prototype.resolveParents(this::getModel);
                if(DEBUG_MODEL_LOADS) {
                    ModernFix.LOGGER.info("Baking model {}", location);
                }
                this.method_61072(this.textureGetter, location, prototype);
                model = bakedTopLevelModels.remove(location);
                if(model == null) {
                    ModernFix.LOGGER.error("Failed to load model " + location);
                    model = bakedMissingModel;
                }
            }
        } finally {
            modelBakeryLock.unlock();
        }
        return model;
    }

    @ModifyExpressionValue(method = "<init>", at = @At(value = "CONSTANT", args = "stringValue=missing_model"))
    private String replaceBackingMaps(String original) {
        this.unbakedCache = new LRUMap<>(this.unbakedCache);
        this.bakedCache = new LRUMap<>(this.bakedCache);
        this.topLevelModels = new LRUMap<>(this.topLevelModels);
        this.bakedTopLevelModels = new LRUMap<>(this.bakedTopLevelModels);
        return original;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/BlockStateModelLoader;loadAllBlockStates()V"))
    private void noInitialBlockStateLoad(BlockStateModelLoader instance) {
        dynamicLoader = instance;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/DefaultedRegistry;keySet()Ljava/util/Set;"))
    private Set<?> skipLoadingItems(DefaultedRegistry instance) {
        return Collections.emptySet();
    }


    @Inject(method = "bakeModels", at = @At("HEAD"))
    private void storeTextureGetterAndBakeMissing(ModelBakery.TextureGetter textureGetter, CallbackInfo ci) {
        this.textureGetter = textureGetter;
        this.method_61072(textureGetter, MISSING_MODEL_VARIANT, Objects.requireNonNull(this.topLevelModels.get(MISSING_MODEL_VARIANT)));
        this.bakedMissingModel = this.bakedTopLevelModels.get(MISSING_MODEL_VARIANT);
    }

    private boolean inInitialLoad = true;

    @Inject(method = "bakeModels", at = @At("RETURN"))
    private void onInitialBakeFinish(ModelBakery.TextureGetter textureGetter, CallbackInfo ci) {
        inInitialLoad = false;
        var permanentMRLs = new ObjectOpenHashSet<>(this.bakedTopLevelModels.keySet());
        ((LRUMap<ModelResourceLocation, BakedModel>)this.bakedTopLevelModels).setPermanentEntries(permanentMRLs);
        ModernFix.LOGGER.info("Dynamic model bakery initial baking finished, with {} permanent top level baked models", this.bakedTopLevelModels.size());
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInitialLoadFinish(BlockColors blockColors, ProfilerFiller profilerFiller, Map map, Map map2, CallbackInfo ci) {
        var permanentMRLs = new ObjectOpenHashSet<>(this.topLevelModels.keySet());
        ((LRUMap<ModelResourceLocation, UnbakedModel>)this.topLevelModels).setPermanentEntries(permanentMRLs);
        ModernFix.LOGGER.info("Dynamic model bakery loading finished, with {} permanent top level models", this.topLevelModels.size());
    }

    @Unique
    private int tickCount;

    @Unique
    private static final int MAXIMUM_CACHE_SIZE = 1000;

    private void runCleanup() {
        ((LRUMap<?, ?>)this.unbakedCache).dropEntriesToMeetSize(MAXIMUM_CACHE_SIZE);
        ((LRUMap<?, ?>)this.bakedCache).dropEntriesToMeetSize(MAXIMUM_CACHE_SIZE);
        ((LRUMap<?, ?>)this.topLevelModels).dropEntriesToMeetSize(MAXIMUM_CACHE_SIZE);
        ((LRUMap<?, ?>)this.bakedTopLevelModels).dropEntriesToMeetSize(MAXIMUM_CACHE_SIZE);
    }

    @Override
    public void mfix$tick() {
        if(inInitialLoad) {
            return;
        }
        tickCount++;
        if((tickCount % 200) == 0) {
            if(modelBakeryLock.tryLock()) {
                try {
                    runCleanup();
                } finally {
                    modelBakeryLock.unlock();
                }
            }
        }
    }

    /**
     * @author embeddedt
     * @reason We provide a fake baked registry to the rest of Minecraft, that dynamically loads models.
     */
    @Overwrite
    public Map<ModelResourceLocation, BakedModel> getBakedTopLevelModels() {
        return this.mfix$emulatedBakedRegistry;
    }
}
