package org.embeddedt.modernfix.common.mixin.perf.chunk_meshing;

import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.util.blockpos.SectionBlockPosIterator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SectionCompiler.class, priority = 2000)
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
}
