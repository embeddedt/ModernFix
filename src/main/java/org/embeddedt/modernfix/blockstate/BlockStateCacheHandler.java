package org.embeddedt.modernfix.blockstate;

import com.google.common.base.Stopwatch;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.config.ModernFixConfig;
import org.embeddedt.modernfix.duck.IBlockState;
import org.embeddedt.modernfix.util.BakeReason;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BlockStateCacheHandler {
    private static boolean needToBake() {
        BakeReason reason = BakeReason.getCurrentBakeReason();
        return !(reason == BakeReason.FREEZE /* startup */
                || reason == BakeReason.REVERT /* crash, in which case cache likely doesn't matter, or exiting world */
                || reason == BakeReason.REMOTE_SNAPSHOT_INJECT /* will be handled when tags are reloaded */
                || (reason == BakeReason.LOCAL_SNAPSHOT_INJECT && FMLLoader.getDist() == Dist.CLIENT /* will be handled when tags are reloaded */));
    }

    public static void rebuildParallel(boolean force) {
        synchronized (BlockBehaviour.BlockStateBase.Cache.class) {
            for (BlockState blockState : Block.BLOCK_STATE_REGISTRY) {
                ((IBlockState)blockState).clearCache();
            }
        }
    }
}
