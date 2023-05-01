package org.embeddedt.modernfix.common.mixin.perf.compress_blockstate;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockBehaviour.class)
public class BlockBehaviourMixin {
    @Overwrite(remap = false)
    protected boolean isAir(BlockState state) {
        return state.getBlock().properties.isAir;
    }
}
