package org.embeddedt.modernfix.blockstate;

import com.google.common.base.Stopwatch;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.Util;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.IBlockState;
import org.embeddedt.modernfix.util.AsyncStopwatch;
import org.embeddedt.modernfix.util.BakeReason;
import org.embeddedt.modernfix.util.OrderedParallelModDispatcher;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BlockStateCacheHandler {
    public static void handleStateCache(BlockState state) {
        if(BakeReason.currentBakeReason == BakeReason.FREEZE
                || BakeReason.currentBakeReason == BakeReason.REMOTE_SNAPSHOT_INJECT
                || (BakeReason.currentBakeReason == BakeReason.LOCAL_SNAPSHOT_INJECT && ModernFix.runningFirstInjection)) {
            ((IBlockState)state).clearCache();
        } else {
            state.initCache();
        }
    }
    private static void handleStateCacheParallel(BlockState state, boolean force) {
        if(force)
            state.initCache();
        else
            handleStateCache(state);
    }
    public static void rebuildParallel(boolean force) {
        Map<String, ArrayList<BlockState>> statesByModId = StreamSupport.stream(Block.BLOCK_STATE_REGISTRY.spliterator(), false)
                .collect(Collectors.groupingBy(state -> state.getBlock().getRegistryName().getNamespace(), Collectors.toCollection(ArrayList::new)));
        Stopwatch realtimeStopwatch = Stopwatch.createStarted();
        AsyncStopwatch cpuStopwatch = new AsyncStopwatch();
        /* For safety, do built-in blocks first */
        cpuStopwatch.startMeasuringAsync();
        ArrayList<BlockState> initialStates = statesByModId.remove("minecraft");
        for(BlockState state : initialStates) {
            handleStateCacheParallel(state, force);
        }
        cpuStopwatch.stopMeasuringAsync();
        OrderedParallelModDispatcher.dispatchBlocking(Util.backgroundExecutor(), modId -> {
            ArrayList<BlockState> states = statesByModId.get(modId);
            if(states == null)
                return;
            cpuStopwatch.startMeasuringAsync();
            states.removeIf(state -> {
                try {
                    handleStateCacheParallel(state, force);
                    return true;
                } catch(RuntimeException e) {
                    ModernFix.LOGGER.error("Error computing state cache for " + state + ": ", e);
                    return false;
                }
            });
            cpuStopwatch.stopMeasuringAsync();
        });
        cpuStopwatch.startMeasuringAsync();
        for(ArrayList<BlockState> remainingStates : statesByModId.values()) {
            for(BlockState state : remainingStates) {
                handleStateCacheParallel(state, force);
            }
        }
        cpuStopwatch.stopMeasuringAsync();
        realtimeStopwatch.stop();
        ModernFix.LOGGER.info("CPU time spent rebuilding blockstate cache: " + cpuStopwatch.getCpuTime()/1000f + " seconds");
        ModernFix.LOGGER.info("Real time spent rebuilding blockstate cache: " + realtimeStopwatch.elapsed(TimeUnit.MILLISECONDS)/1000f + " seconds");
    }
}
