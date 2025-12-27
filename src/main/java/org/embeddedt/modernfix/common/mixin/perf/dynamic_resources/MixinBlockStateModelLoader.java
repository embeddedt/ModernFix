package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynresources.DynamicModelSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Mixin(BlockStateModelLoader.class)
@ClientOnlyMixin
public abstract class MixinBlockStateModelLoader {
    @Shadow
    protected static BlockStateModelLoader.LoadedModels lambda$loadBlockStates$1(Map.Entry<Identifier, List<Resource>> entry, Function<Identifier, StateDefinition<Block, BlockState>> locationToBlockStateMapper) {
        throw new AssertionError();
    }

    /**
     * @author embeddedt
     * @reason Load blockstate model definitions dynamically instead of all at once
     */
    @ModifyArg(method = "loadBlockStates", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenCompose(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;"))
    private static Function<Map<Identifier, List<Resource>>, ? extends CompletionStage<BlockStateModelLoader.LoadedModels>> skipAOTBlockStateLoad(Function<Map<Identifier, List<Resource>>, ? extends CompletionStage<Map<Identifier, BlockStateModelLoader.LoadedModels>>> original, @Local(ordinal = 0) Function<Identifier, StateDefinition<Block, BlockState>> mapper) {
        return resourceMap -> CompletableFuture.completedFuture(DynamicModelSystem.createDynamicBlockStateLoadedModels(resourceMap, (id, resources) -> {
            return lambda$loadBlockStates$1(Map.entry(id, resources), mapper);
        }));
    }
}
