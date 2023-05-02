package org.embeddedt.modernfix.fabric.mixin.perf.dynamic_resources;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.LambdaMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Mixin(ModelManager.class)
public class ModelManagerMixin {
    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelManager;loadBlockModels(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Map<ResourceLocation, BlockModel>> deferBlockModelLoad(ResourceManager manager, Executor executor) {
        return CompletableFuture.completedFuture(new LambdaMap<>(location -> loadSingleBlockModel(manager, location)));
    }

    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelManager;loadBlockStates(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Map<ResourceLocation, List<ModelBakery.LoadedJson>>> deferBlockStateLoad(ResourceManager manager, Executor executor) {
        return CompletableFuture.completedFuture(new LambdaMap<>(location -> loadSingleBlockState(manager, location)));
    }

    @Redirect(method = "loadModels", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;"))
    private ImmutableList<BlockState> skipCollection(StateDefinition<Block, BlockState> definition) {
        return ImmutableList.of();
    }

    private BlockModel loadSingleBlockModel(ResourceManager manager, ResourceLocation location) {
        return manager.getResource(location).map(resource -> {
            try {
                return BlockModel.fromStream(resource.openAsReader());
            } catch(IOException e) {
                ModernFix.LOGGER.error("Couldn't load model", e);
                return null;
            }
        }).orElse(null);
    }

    private List<ModelBakery.LoadedJson> loadSingleBlockState(ResourceManager manager, ResourceLocation location) {
        return manager.getResourceStack(location).stream().map(resource -> {
            try {
                return new ModelBakery.LoadedJson(resource.sourcePackId(), GsonHelper.parse(resource.openAsReader()));
            } catch(IOException e) {
                ModernFix.LOGGER.error("Couldn't load blockstate", e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
