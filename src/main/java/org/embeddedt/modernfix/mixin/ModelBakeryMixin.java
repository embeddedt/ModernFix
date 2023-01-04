package org.embeddedt.modernfix.mixin;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import net.minecraft.client.Minecraft;
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
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {
    @Shadow protected abstract BlockModel loadBlockModel(ResourceLocation location) throws IOException;

    @Shadow @Final private Map<ResourceLocation, IUnbakedModel> unbakedCache;
    @Shadow @Final public static ModelResourceLocation MISSING_MODEL_LOCATION;

    @Shadow public abstract IUnbakedModel getModel(ResourceLocation modelLocation);

    @Shadow @Final public static BlockModel GENERATION_MARKER;
    @Shadow @Final private static ItemModelGenerator ITEM_MODEL_GENERATOR;
    @Shadow @Nullable private SpriteMap atlasSet;
    @Shadow @Final private Map<Triple<ResourceLocation, TransformationMatrix, Boolean>, IBakedModel> bakedCache;
    @Shadow @Final private Map<ResourceLocation, IBakedModel> bakedTopLevelModels;
    @Shadow @Final private Map<ResourceLocation, IUnbakedModel> topLevelModels;
    private Map<ResourceLocation, BlockModel> deserializedModelCache = null;
    private boolean useModelCache = false;

    @Inject(method = "loadBlockModel", at = @At("HEAD"), cancellable = true)
    private void useCachedModel(ResourceLocation location, CallbackInfoReturnable<BlockModel> cir) {
        if(useModelCache && deserializedModelCache != null) {
            BlockModel model = deserializedModelCache.get(location);
            if(model != null)
                cir.setReturnValue(model);
        }
    }

    private BlockModel loadModelSafely(ResourceLocation location) {
        try {
            return this.loadBlockModel(location);
        } catch(Throwable e) {
            ModernFix.LOGGER.warn("Model " + location + " will not be preloaded", e);
            return null;
        }
    }

    @Inject(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/IProfiler;popPush(Ljava/lang/String;)V", ordinal = 1))
    private void preloadJsonModels(IProfiler profilerIn, int maxMipmapLevel, CallbackInfo ci) {
        profilerIn.popPush("loadjsons");
        ModernFix.LOGGER.warn("Preloading JSONs in parallel...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        useModelCache = false;
        deserializedModelCache = Minecraft.getInstance().getResourceManager().listResources("models", p -> {
            if(!p.endsWith(".json"))
                return false;
            for(int i = 0; i < p.length(); i++) {
                if(!ResourceLocation.validPathChar(p.charAt(i)))
                    return false;
            }
            return true;
        })
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

    @Redirect(method = "processLoading", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;", ordinal = 0), remap = false)
    private Object collectTexturesParallel(Stream instance, Collector arCollector) {
        ModernFix.LOGGER.warn("Collecting textures in parallel...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        ConcurrentHashMap<ResourceLocation, IUnbakedModel> threadedunbakedCache = new ConcurrentHashMap<>(this.unbakedCache);
        Function<ResourceLocation, IUnbakedModel> safeUnbakedGetter = (location) -> {
            IUnbakedModel candidate = threadedunbakedCache.get(location);
            if(candidate == null) {
                synchronized (this.unbakedCache) {
                    candidate = this.getModel(location);
                    threadedunbakedCache.put(location, candidate);
                }
            }
            return candidate;
        };
        Set<com.mojang.datafixers.util.Pair<String, String>> set = Collections.synchronizedSet(Sets.newLinkedHashSet());
        String modelMissingString = MISSING_MODEL_LOCATION.toString();
        Set<RenderMaterial> materials = this.topLevelModels.values().parallelStream().flatMap((unbaked) -> {
            return unbaked.getMaterials(safeUnbakedGetter, set).stream();
        }).collect(Collectors.toSet());
        set.stream().filter((stringPair) -> {
            return !stringPair.getSecond().equals(modelMissingString);
        }).forEach((textureReferenceErrors) -> {
            ModernFix.LOGGER.warn("Unable to resolve texture reference: {} in {}", textureReferenceErrors.getFirst(), textureReferenceErrors.getSecond());
        });
        ModernFix.LOGGER.warn("Collecting textures took " + stopwatch.elapsed(TimeUnit.SECONDS) + " seconds");
        stopwatch.stop();
        return materials;
    }
}
