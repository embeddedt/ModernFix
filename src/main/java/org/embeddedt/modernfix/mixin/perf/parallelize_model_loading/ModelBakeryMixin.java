package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.model.*;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.state.StateContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.registry.Registry;
import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.AsyncStopwatch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {
    @Shadow @Final protected IResourceManager resourceManager;

    private Map<ResourceLocation, List<Pair<String, BlockModelDefinition>>> deserializedBlockstateCache = null;

    @Inject(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/IProfiler;popPush(Ljava/lang/String;)V", ordinal = 0))
    private void preloadJsonModels(IProfiler profilerIn, int maxMipmapLevel, CallbackInfo ci) {
        StartupMessageManager.mcLoaderConsumer().ifPresent(c -> c.accept("Loading models"));
        profilerIn.popPush("loadblockstates");
        AsyncStopwatch.measureAndLogSerialRunningTime("Parallel blockstate loading", () -> {
            ThreadLocal<BlockModelDefinition.ContainerHolder> containerHolder = ThreadLocal.withInitial(BlockModelDefinition.ContainerHolder::new);
            this.deserializedBlockstateCache = new ConcurrentHashMap<>();
            ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
            for(Block block : Registry.BLOCK) {
                ResourceLocation blockLocation = Registry.BLOCK.getKey(block);
                futures.add(CompletableFuture.runAsync(() -> {
                    ResourceLocation blockStateJSON = new ResourceLocation(blockLocation.getNamespace(), "blockstates/" + blockLocation.getPath() + ".json");
                    List<IResource> blockStates;
                    try {
                        blockStates = this.resourceManager.getResources(blockStateJSON);
                    } catch(IOException e) {
                        ModernFix.LOGGER.warn("Exception loading blockstate definition: {}: {}", blockLocation, e);
                        return;
                    }
                    List<Pair<String, BlockModelDefinition>> definitions = new ArrayList<>();
                    StateContainer<Block, BlockState> stateContainer = block.getStateDefinition();
                    BlockModelDefinition.ContainerHolder context = containerHolder.get();
                    context.setDefinition(stateContainer);
                    for(IResource resource : blockStates) {
                        try (InputStream inputstream = resource.getInputStream()) {
                            BlockModelDefinition definition = BlockModelDefinition.fromStream(context, new InputStreamReader(inputstream, StandardCharsets.UTF_8));
                            definitions.add(Pair.of(resource.getSourceName(), definition));
                        } catch (Exception exception1) {
                            ModernFix.LOGGER.warn(String.format("Exception loading blockstate definition: '%s' in resourcepack: '%s': %s", resource.getLocation(), resource.getSourceName(), exception1.getMessage()));
                            return;
                        }
                    }
                    this.deserializedBlockstateCache.put(blockLocation, definitions);
                }, Util.backgroundExecutor()));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        });
    }

    @Inject(method = "processLoading", at = @At("RETURN"), remap = false)
    private void clearModelCache(IProfiler profilerIn, int maxMipmapLevel, CallbackInfo ci) {
        deserializedBlockstateCache.clear();
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
