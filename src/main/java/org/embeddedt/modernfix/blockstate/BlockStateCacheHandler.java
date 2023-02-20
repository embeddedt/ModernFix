package org.embeddedt.modernfix.blockstate;

import com.google.common.base.Stopwatch;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.config.ModernFixConfig;
import org.embeddedt.modernfix.util.BakeReason;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BlockStateCacheHandler {
    private static RebuildThread currentRebuildThread = null;

    private static boolean needToBake() {
        BakeReason reason = BakeReason.getCurrentBakeReason();
        return !(reason == BakeReason.FREEZE /* startup */
                || reason == BakeReason.REVERT /* crash, in which case cache likely doesn't matter, or exiting world */
                || reason == BakeReason.REMOTE_SNAPSHOT_INJECT /* will be handled when tags are reloaded */
                || (reason == BakeReason.LOCAL_SNAPSHOT_INJECT && FMLLoader.getDist() == Dist.CLIENT /* will be handled when tags are reloaded */));
    }

    public static void rebuildParallel(boolean force) {
        if(currentRebuildThread != null) {
            if(currentRebuildThread.isAlive())
                ModernFix.LOGGER.warn("Interrupting previous blockstate cache rebuild");
            currentRebuildThread.stopRebuild();
            try {
                currentRebuildThread.join(10000);
                if(currentRebuildThread.isAlive())
                    throw new IllegalStateException("Blockstate cache rebuild thread has hung");
            } catch(InterruptedException e) {
                throw new RuntimeException("Don't interrupt Minecraft threads", e);
            }
            ModernFix.LOGGER.debug("Rebuild thread exited");
            currentRebuildThread = null;
        }
        if(force || needToBake()) {
            ArrayList<BlockState> stateList = new ArrayList<>(Block.BLOCK_STATE_REGISTRY.size());
            for (BlockState blockState : Block.BLOCK_STATE_REGISTRY) {
                stateList.add(blockState);
            }
            currentRebuildThread = new RebuildThread(stateList);
            if(ModernFixConfig.REBUILD_BLOCKSTATES_ASYNC.get())
                currentRebuildThread.start();
            else {
                currentRebuildThread.run();
                currentRebuildThread = null;
            }
        } else {
            ModernFix.LOGGER.warn("Deferred blockstate cache rebuild");
        }
    }

    private static class RebuildThread extends Thread {
        private boolean stopRebuild = false;
        private final List<BlockState> blockStateList;

        public RebuildThread(List<BlockState> statesToInit) {
            this.setName("ModernFix blockstate cache rebuild thread");
            this.setPriority(Thread.MIN_PRIORITY + 1);
            this.blockStateList = statesToInit;
        }

        public void stopRebuild() {
            this.stopRebuild = true;
        }

        private void rebuildCache() {
            Iterator<BlockState> stateIterator = blockStateList.iterator();
            while(!stopRebuild && stateIterator.hasNext()) {
                stateIterator.next().initCache();
            }
        }

        @Override
        public void run() {
            Stopwatch realtimeStopwatch = Stopwatch.createStarted();
            rebuildCache();
            realtimeStopwatch.stop();
            if(!stopRebuild)
                ModernFix.LOGGER.info("Blockstate cache rebuilt in " + realtimeStopwatch.elapsed(TimeUnit.MILLISECONDS)/1000f + " seconds");
        }
    }
}
