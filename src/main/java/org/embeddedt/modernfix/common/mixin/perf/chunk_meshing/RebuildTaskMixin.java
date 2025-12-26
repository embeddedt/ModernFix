package org.embeddedt.modernfix.common.mixin.perf.chunk_meshing;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.util.blockpos.SectionBlockPosIterator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = { "net/minecraft/client/renderer/chunk/ChunkRenderDispatcher$RenderChunk$RebuildTask"}, priority = 2000)
@ClientOnlyMixin
@RequiresMod("!fluidlogged")
public class RebuildTaskMixin {
    /**
     * @author embeddedt
     * @reason Use a much faster iterator implementation than vanilla's Guava-based one.
     */
    @Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"), require = 0)
    private Iterable<BlockPos> fastBetweenClosed(BlockPos firstPos, BlockPos secondPos) {
        return () -> new SectionBlockPosIterator(firstPos);
    }

    /**
     * @author embeddedt
     * @reason RenderChunkRegion.getBlockState is expensive, avoid calling it multiple times for the same position
     */
    @Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 1), require = 0)
    private BlockState useExistingBlockState(RenderChunkRegion instance, BlockPos pos, @Local(ordinal = 0) BlockState state) {
        return state;
    }
}
