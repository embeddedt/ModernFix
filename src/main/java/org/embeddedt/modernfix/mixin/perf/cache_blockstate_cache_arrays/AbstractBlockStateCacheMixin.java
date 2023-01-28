package org.embeddedt.modernfix.mixin.perf.cache_blockstate_cache_arrays;

import net.minecraft.util.BlockVoxelShape;
import net.minecraft.util.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = { "net/minecraft/block/AbstractBlock$AbstractBlockState$Cache" })
public class AbstractBlockStateCacheMixin {
    private static final BlockVoxelShape[] MF_BLOCK_VOXEL_SHAPES = BlockVoxelShape.values();
    private static final Direction.Axis[] DIRECTION_AXIS_VALUES = Direction.Axis.values();

    @Redirect(method = "<init>(Lnet/minecraft/block/BlockState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/BlockVoxelShape;values()[Lnet/minecraft/util/BlockVoxelShape;"))
    private BlockVoxelShape[] getVoxelShapeValues() {
        return MF_BLOCK_VOXEL_SHAPES;
    }

    @Redirect(method = "<init>(Lnet/minecraft/block/BlockState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Direction$Axis;values()[Lnet/minecraft/util/Direction$Axis;"))
    private Direction.Axis[] getDirectionAxisValues() {
        return DIRECTION_AXIS_VALUES;
    }
}
