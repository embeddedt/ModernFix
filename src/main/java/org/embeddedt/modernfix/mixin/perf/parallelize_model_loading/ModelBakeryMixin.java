package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.texture.SpriteMap;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.state.StateContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.vector.TransformationMatrix;
import net.minecraft.util.registry.Registry;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.AsyncStopwatch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    @Shadow @Final private Map<ResourceLocation, IUnbakedModel> topLevelModels;
    @Shadow @Final protected IResourceManager resourceManager;
    private Map<ResourceLocation, BlockModel> deserializedModelCache = null;
    private Map<ResourceLocation, List<Pair<String, BlockModelDefinition>>> deserializedBlockstateCache = null;
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
            ModernFix.LOGGER.warn("Model " + location + " will not be preloaded");
            return null;
        }
    }

    @Inject(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/IProfiler;popPush(Ljava/lang/String;)V", ordinal = 0))
    private void preloadJsonModels(IProfiler profilerIn, int maxMipmapLevel, CallbackInfo ci) {
        profilerIn.popPush("loadjsons");
        AsyncStopwatch.measureAndLogSerialRunningTime("Parallel JSON loading", () -> {
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
                    .filter(pair -> pair.getSecond() != null)
                    .collect(Collectors.toConcurrentMap(Pair::getFirst, Pair::getSecond));
            useModelCache = true;
        });
        AsyncStopwatch.measureAndLogSerialRunningTime("Parallel blockstate loading", () -> {
            ThreadLocal<BlockModelDefinition.ContainerHolder> containerHolder = ThreadLocal.withInitial(BlockModelDefinition.ContainerHolder::new);
            this.deserializedBlockstateCache = Registry.BLOCK.keySet().parallelStream()
                    .flatMap(block -> {
                        ResourceLocation blockStateJSON = new ResourceLocation(block.getNamespace(), "blockstates/" + block.getPath() + ".json");
                        List<IResource> blockStates;
                        try {
                            blockStates = this.resourceManager.getResources(blockStateJSON);
                        } catch(IOException e) {
                            ModernFix.LOGGER.warn("Exception loading blockstate definition: {}: {}", block, e);
                            blockStates = Collections.emptyList();
                        }
                        return blockStates.stream().map(resource -> Pair.of(block, resource));
                    })
                    .map((pair) -> {
                        ResourceLocation block = pair.getFirst();
                        IResource resource = pair.getSecond();
                        try (InputStream inputstream = resource.getInputStream()) {
                            BlockModelDefinition.ContainerHolder context = containerHolder.get();
                            context.setDefinition(Registry.BLOCK.get(block).getStateDefinition());
                            return Pair.of(block, Pair.of(resource.getSourceName(), BlockModelDefinition.fromStream(context, new InputStreamReader(inputstream, StandardCharsets.UTF_8))));
                        } catch (Exception exception1) {
                            ModernFix.LOGGER.warn(String.format("Exception loading blockstate definition: '%s' in resourcepack: '%s': %s", resource.getLocation(), resource.getSourceName(), exception1.getMessage()));
                            return Pair.of(block, Pair.of((String)null, (BlockModelDefinition)null));
                        }
                    })
                    .filter(pair -> pair.getSecond().getSecond() != null)
                    .collect(Collectors.groupingBy(Pair::getFirst, Collectors.mapping(Pair::getSecond, Collectors.toList())));
        });
        AsyncStopwatch.measureAndLogSerialRunningTime("Predicate generation", () -> {
            /* Pregenerate predicates */
            this.deserializedBlockstateCache.entrySet().parallelStream()
                    .flatMap(entry -> entry.getValue().stream()
                            .map(Pair::getSecond)
                            .map(def -> Pair.of(Registry.BLOCK.get(entry.getKey()).getStateDefinition(), def)))
                    .filter(pair -> pair.getSecond().isMultiPart())
                    .flatMap(pair -> pair.getSecond().getMultiPart().getSelectors().stream().map(selector -> Pair.of(pair.getFirst(), selector)))
                    .forEach(pair -> {
                        pair.getSecond().getPredicate(pair.getFirst());
                    });
        });
    }

    @Inject(method = "processLoading", at = @At("RETURN"), remap = false)
    private void clearModelCache(IProfiler profilerIn, int maxMipmapLevel, CallbackInfo ci) {
        deserializedModelCache.clear();
        deserializedBlockstateCache.clear();
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
        Set<Pair<String, String>> set = Collections.synchronizedSet(Sets.newLinkedHashSet());
        String modelMissingString = MISSING_MODEL_LOCATION.toString();
        Set<RenderMaterial> materials = this.topLevelModels.values().parallelStream().flatMap((unbaked) -> {
            return unbaked.getMaterials(safeUnbakedGetter, set).stream();
        }).collect(Collectors.toSet());
        set.stream().filter((stringPair) -> {
            return !stringPair.getSecond().equals(modelMissingString);
        }).forEach((textureReferenceErrors) -> {
            ModernFix.LOGGER.warn("Unable to resolve texture reference: {} in {}", textureReferenceErrors.getFirst(), textureReferenceErrors.getSecond());
        });
        ModernFix.LOGGER.warn("Collecting textures took " + stopwatch.elapsed(TimeUnit.MILLISECONDS)/1000f + " seconds");
        stopwatch.stop();
        return materials;
    }

    private List<?> replacementList = null;

    @Redirect(method = "loadModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/IResourceManager;getResources(Lnet/minecraft/util/ResourceLocation;)Ljava/util/List;", ordinal = 0))
    private List<?> getResourceList(IResourceManager instance, ResourceLocation jsonLocation, ResourceLocation originalBlockLocation) throws IOException {
        replacementList = null;
        if(this.deserializedBlockstateCache != null) {
            if(!(originalBlockLocation instanceof ModelResourceLocation)) {
                throw new AssertionError("Injector in unexpected spot?");
            }
            ModelResourceLocation mrl = (ModelResourceLocation)originalBlockLocation;
            ResourceLocation location = new ResourceLocation(mrl.getNamespace(), mrl.getPath());
            List<?> theList = this.deserializedBlockstateCache.get(location);
            if(theList != null && theList.size() > 0) {
                replacementList = theList;
                return theList;
            }
        }
        return instance.getResources(jsonLocation);
    }

    @Redirect(method = "loadModel", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;", ordinal = 0))
    private Stream<?> fakeResourceList(Stream<?> instance, Function function) {
        return replacementList != null ? instance : instance.map(function);
    }
}
