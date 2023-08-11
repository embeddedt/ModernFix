package org.embeddedt.modernfix.testmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.embeddedt.modernfix.testmod.TestMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public class ChunkMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void redirectDebugWorld(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState overrideState = TestMod.getColorCubeStateFor(pos.getX(), pos.getY(), pos.getZ());
        if(overrideState != null) {
            cir.setReturnValue(overrideState);
        }
    }
}
