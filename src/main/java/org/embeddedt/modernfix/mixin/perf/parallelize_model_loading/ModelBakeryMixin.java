package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.core.Registry;
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

import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {
    @Shadow @Final protected ResourceManager resourceManager;

    private Map<ResourceLocation, List<Pair<String, BlockModelDefinition>>> deserializedBlockstateCache = null;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", ordinal = 0))
    private void preloadJsonModels(ProfilerFiller profilerIn, String str) {
        StartupMessageManager.mcLoaderConsumer().ifPresent(c -> c.accept("Loading models"));
        profilerIn.popPush("loadblockstates");
        AsyncStopwatch.measureAndLogSerialRunningTime("Parallel blockstate loading", () -> {
            ThreadLocal<BlockModelDefinition.Context> containerHolder = ThreadLocal.withInitial(BlockModelDefinition.Context::new);
            this.deserializedBlockstateCache = new ConcurrentHashMap<>();
            ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
            for(Block block : Registry.BLOCK) {
                ResourceLocation blockLocation = Registry.BLOCK.getKey(block);
                futures.add(CompletableFuture.runAsync(() -> {
                    ResourceLocation blockStateJSON = new ResourceLocation(blockLocation.getNamespace(), "blockstates/" + blockLocation.getPath() + ".json");
                    List<Resource> blockStates;
                    /* Some mods' custom resource pack implementations don't seem to like concurrency here */
                    synchronized(this.resourceManager) {
                        blockStates = this.resourceManager.getResourceStack(blockStateJSON);
                    }
                    List<Pair<String, BlockModelDefinition>> definitions = new ArrayList<>();
                    StateDefinition<Block, BlockState> stateContainer = block.getStateDefinition();
                    BlockModelDefinition.Context context = containerHolder.get();
                    context.setDefinition(stateContainer);
                    for(Resource resource : blockStates) {
                        try (InputStream inputstream = resource.open()) {
                            BlockModelDefinition definition = BlockModelDefinition.fromStream(context, new InputStreamReader(inputstream, StandardCharsets.UTF_8));
                            definitions.add(Pair.of(resource.sourcePackId(), definition));
                        } catch (Exception exception1) {
                            ModernFix.LOGGER.warn(String.format("Exception loading blockstate definition: '%s' in resourcepack: '%s': %s", blockStateJSON, resource.sourcePackId(), exception1.getMessage()));
                            return;
                        }
                    }
                    this.deserializedBlockstateCache.put(blockLocation, definitions);
                }, Util.backgroundExecutor()));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        });
        profilerIn.popPush(str);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void clearModelCache(ResourceManager arg, BlockColors arg2, ProfilerFiller arg3, int i, CallbackInfo ci) {
        deserializedBlockstateCache.clear();
    }

    private List<?> replacementList = null;

    @Redirect(method = "loadModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/ResourceManager;getResourceStack(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;", ordinal = 0))
    private List<?> getResourceList(ResourceManager instance, ResourceLocation jsonLocation, ResourceLocation originalBlockLocation) throws IOException {
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
        return instance.getResourceStack(jsonLocation);
    }

    @Redirect(method = "loadModel", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;", ordinal = 0))
    private Stream<?> fakeResourceList(Stream<?> instance, Function function) {
        return replacementList != null ? instance : instance.map(function);
    }
}
