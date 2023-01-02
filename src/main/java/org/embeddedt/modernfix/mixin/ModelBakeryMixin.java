package org.embeddedt.modernfix.mixin;

import com.google.common.base.Stopwatch;
import com.google.common.cache.LoadingCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.texture.SpriteMap;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.vector.TransformationMatrix;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {
    @Shadow protected abstract BlockModel loadModel(ResourceLocation location) throws IOException;

    @Shadow @Final private Map<ResourceLocation, IUnbakedModel> unbakedModels;
    @Shadow @Final public static ModelResourceLocation MODEL_MISSING;

    @Shadow public abstract IUnbakedModel getUnbakedModel(ResourceLocation modelLocation);

    @Shadow @Final public static BlockModel MODEL_GENERATED;
    @Shadow @Final private static ItemModelGenerator ITEM_MODEL_GENERATOR;
    @Shadow @Nullable private SpriteMap spriteMap;
    @Shadow @Final private Map<Triple<ResourceLocation, TransformationMatrix, Boolean>, IBakedModel> bakedModels;
    @Shadow @Final private Map<ResourceLocation, IBakedModel> topBakedModels;
    private Map<ResourceLocation, BlockModel> deserializedModelCache = null;
    private boolean useModelCache = false;

    @Inject(method = "loadModel", at = @At("HEAD"), cancellable = true)
    private void useCachedModel(ResourceLocation location, CallbackInfoReturnable<BlockModel> cir) {
        if(useModelCache && deserializedModelCache != null) {
            BlockModel model = deserializedModelCache.get(location);
            if(model != null)
                cir.setReturnValue(model);
        }
    }

    private BlockModel loadModelSafely(ResourceLocation location) {
        try {
            return this.loadModel(location);
        } catch(Throwable e) {
            ModernFix.LOGGER.warn("Model " + location + " will not be preloaded", e);
            return null;
        }
    }

    @Inject(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/IProfiler;endStartSection(Ljava/lang/String;)V", ordinal = 1))
    private void preloadJsonModels(IProfiler profilerIn, int maxMipmapLevel, CallbackInfo ci) {
        profilerIn.endStartSection("loadjsons");
        ModernFix.LOGGER.warn("Preloading JSONs in parallel...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        useModelCache = false;
        deserializedModelCache = Minecraft.getInstance().getResourceManager().getAllResourceLocations("models", p -> p.endsWith(".json"))
                .parallelStream()
                .map(location -> new ResourceLocation(location.getNamespace(), location.getPath().substring(7, location.getPath().length() - 5)))
                .map(location -> Pair.of(location, this.loadModelSafely(location)))
                .filter(pair -> pair.getValue() != null)
                .collect(Collectors.toConcurrentMap(Pair::getKey, Pair::getValue));
        useModelCache = true;
        ModernFix.LOGGER.warn("Preloading JSONs took " + stopwatch.elapsed(TimeUnit.SECONDS) + " seconds");
        stopwatch.stop();
    }

    @Inject(method = "processLoading", at = @At("RETURN"), remap = false)
    private void clearModelCache(IProfiler profilerIn, int maxMipmapLevel, CallbackInfo ci) {
        deserializedModelCache.clear();
        useModelCache = false;
    }

    @Redirect(method = "uploadTextures", at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V", ordinal = 0))
    private void parallelBake(Set<ResourceLocation> locationSet, Consumer consumer) {
        final IModelTransform transform = ModelRotation.X0_Y0;
        if(this.spriteMap == null)
            throw new IllegalStateException("no sprite map");
        ModernFix.LOGGER.warn("Baking models in parallel...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        locationSet.forEach(this::getUnbakedModel); /* make sure every unbaked model is loaded, should be fast */
        List<Pair<ResourceLocation, IBakedModel>> models = CompletableFuture.supplyAsync(() -> {
            return locationSet.parallelStream().map(location -> {
                IUnbakedModel iunbakedmodel = this.unbakedModels.get(location);
                if (iunbakedmodel instanceof BlockModel) {
                    BlockModel blockmodel = (BlockModel)iunbakedmodel;
                    if (blockmodel.getRootModel() == MODEL_GENERATED) {
                        return Pair.of(location, ITEM_MODEL_GENERATOR.makeItemModel(this.spriteMap::getSprite, blockmodel).bakeModel((ModelBakery)(Object)this, blockmodel, this.spriteMap::getSprite, transform, location, false));
                    }
                }

                IBakedModel ibakedmodel = iunbakedmodel.bakeModel((ModelBakery)(Object)this, this.spriteMap::getSprite, transform, location);
                return Pair.of(location, ibakedmodel);
            }).collect(Collectors.toList());
        }, Util.getServerExecutor()).join();
        models.forEach(pair -> {
            Triple<ResourceLocation, TransformationMatrix, Boolean> triple = Triple.of(pair.getKey(), transform.getRotation(), transform.isUvLock());
            this.bakedModels.put(triple, pair.getValue());
            if(pair.getValue() != null)
                this.topBakedModels.put(pair.getKey(), pair.getValue());
        });
        ModernFix.LOGGER.warn("Baking in parallel took " + stopwatch.elapsed(TimeUnit.SECONDS) + " seconds");
        stopwatch.stop();
    }
}
