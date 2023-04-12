package org.embeddedt.modernfix.mixin.bugfix.chunk_deadlock.valhesia;

import com.stal111.valhelsia_structures.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockBehaviour.BlockStateBase.class, priority = 900)
public abstract class BlockStateBaseMixin {
    @Shadow public abstract Block getBlock();

    /**
     * Do not call getBlockState here; this can cause deadlocks during world gen/lighting.
     */
    @Inject(method = "getOffset", at = @At("HEAD"), cancellable = true)
    private void useThisBlock(BlockGetter getter, BlockPos pos, CallbackInfoReturnable<Vec3> cir) {
        if(this.getBlock() == ModBlocks.BONE_PILE.get())
            cir.setReturnValue(new Vec3(0.0, -0.46875, 0.0));
    }
}
