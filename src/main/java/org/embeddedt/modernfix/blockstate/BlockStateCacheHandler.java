package org.embeddedt.modernfix.blockstate;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.world.EmptyBlockReader;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.IBlockState;
import org.embeddedt.modernfix.util.BakeReason;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class BlockStateCacheHandler {
    private static final Set<String> PRECACHED_COLLISION_SHAPES = ImmutableSet.<String>builder()
            .add("refinedstorage")
            .add("cabletiers")
            .add("extrastorage")
            .build();
    public static void handleStateCache(BlockState state) {
        BakeReason reason = BakeReason.getCurrentBakeReason();
        if(reason == BakeReason.FREEZE
                || reason == BakeReason.REMOTE_SNAPSHOT_INJECT
                || (reason == BakeReason.LOCAL_SNAPSHOT_INJECT && ModernFix.runningFirstInjection)) {
            //((IBlockState)state).clearCache();
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

    @SuppressWarnings("deprecation")
    public static void rebuildParallel(boolean force) {
        /* Run some special sauce for Refined Storage since it has very slow collision shapes */
        Stopwatch realtimeStopwatch = Stopwatch.createStarted();
        List<BlockState> specialStates = StreamSupport.stream(Block.BLOCK_STATE_REGISTRY.spliterator(), false)
                .filter(state -> PRECACHED_COLLISION_SHAPES.contains(state.getBlock().getRegistryName().getNamespace())).collect(Collectors.toList());
        CompletableFuture.runAsync(() -> {
            specialStates.parallelStream()
                    .forEach(state -> {
                        /* Force these blocks to compute their shapes ahead of time on worker threads */
                        state.getBlock().getCollisionShape(state, EmptyBlockReader.INSTANCE, BlockPos.ZERO, ISelectionContext.empty());
                        state.getBlock().getOcclusionShape(state, EmptyBlockReader.INSTANCE, BlockPos.ZERO);
                    });
        }, Util.backgroundExecutor()).join();
        Block.BLOCK_STATE_REGISTRY.forEach(state -> handleStateCacheParallel(state, force));
        realtimeStopwatch.stop();
        ModernFix.LOGGER.info("Real time spent rebuilding blockstate cache: " + realtimeStopwatch.elapsed(TimeUnit.MILLISECONDS)/1000f + " seconds");
    }
}
