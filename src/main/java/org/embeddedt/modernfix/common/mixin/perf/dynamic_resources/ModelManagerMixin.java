package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.util.CacheUtil;
import org.embeddedt.modernfix.util.LambdaMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mixin(ModelManager.class)
@ClientOnlyMixin
public class ModelManagerMixin {
    @Shadow private Map<ResourceLocation, BakedModel> bakedRegistry;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void injectDummyBakedRegistry(CallbackInfo ci) {
        if(this.bakedRegistry == null) {
            this.bakedRegistry = new HashMap<>();
        }
    }

    @ModifyArg(method = "loadBlockModels", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenCompose(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;", ordinal = 0), index = 0)
    private static Function<Map<ResourceLocation, Resource>, ? extends CompletionStage<Map<ResourceLocation, BlockModel>>> deferBlockModelLoad(Function<Map<ResourceLocation, Resource>, ? extends CompletionStage<Map<ResourceLocation, BlockModel>>> fn) {
        return resourceMap -> {
            var resourceMapSnapshot = Map.copyOf(resourceMap);
            var fallbackModel = BlockModel.fromString(ModelBakery.MISSING_MODEL_MESH);
            var cache = CacheUtil.<ResourceLocation, BlockModel>simpleCacheForLambda(location -> loadSingleBlockModel(resourceMapSnapshot, location, fallbackModel), 100L);
            return CompletableFuture.completedFuture(Maps.asMap(resourceMapSnapshot.keySet(), location -> cache.getUnchecked(location)));
        };
    }

    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelManager;loadBlockStates(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Map<ResourceLocation, List<ModelBakery.LoadedJson>>> deferBlockStateLoad(ResourceManager manager, Executor executor) {
        var cache = CacheUtil.<ResourceLocation, List<ModelBakery.LoadedJson>>simpleCacheForLambda(location -> loadSingleBlockState(manager, location), 100L);
        return CompletableFuture.completedFuture(new LambdaMap<>(location -> cache.getUnchecked(location)));
    }

    @Redirect(method = "loadModels", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;"))
    private ImmutableList<BlockState> skipCollection(StateDefinition<Block, BlockState> definition) {
        return ImmutableList.of();
    }

    private static BlockModel loadSingleBlockModel(Map<ResourceLocation, Resource> resourceMap, ResourceLocation location, BlockModel fallbackModel) {
        Resource resource = resourceMap.get(location);
        if(resource == null) {
            return null;
        }
        try (BufferedReader reader = resource.openAsReader()) {
            return BlockModel.fromStream(reader);
        } catch (Exception e) {
            ModernFix.LOGGER.error("Couldn't load model {}, substituting missing", location, e);
            return fallbackModel;
        }
    }

    private List<ModelBakery.LoadedJson> loadSingleBlockState(ResourceManager manager, ResourceLocation location) {
        return manager.getResourceStack(location).stream().map(resource -> {
            try (BufferedReader reader = resource.openAsReader()) {
                return new ModelBakery.LoadedJson(resource.sourcePackId(), GsonHelper.parse(reader));
            } catch(IOException e) {
                ModernFix.LOGGER.error("Couldn't load blockstate", e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
