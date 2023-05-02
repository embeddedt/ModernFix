package org.embeddedt.modernfix.blockstate;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.modernfix.duck.IBlockState;

public class BlockStateCacheHandler {
    public static void rebuildParallel(boolean force) {
        synchronized (BlockBehaviour.BlockStateBase.class) {
            for (BlockState blockState : Block.BLOCK_STATE_REGISTRY) {
                ((IBlockState)blockState).clearCache();
            }
        }
    }
}
