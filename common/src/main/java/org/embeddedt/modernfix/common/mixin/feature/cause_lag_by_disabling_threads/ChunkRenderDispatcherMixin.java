package org.embeddedt.modernfix.common.mixin.feature.cause_lag_by_disabling_threads;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Mixin(ChunkRenderDispatcher.class)
@ClientOnlyMixin
public class ChunkRenderDispatcherMixin {
    private static final Executor MFIX_CHUNK_BUILD_EXECUTOR = new ThreadPoolExecutor(1, computeNumThreads(), 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    private static int computeNumThreads() {
       return Math.max(Math.min(Runtime.getRuntime().availableProcessors() / 4, 10), 1);
    }

    @ModifyVariable(method = "<init>*", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static Executor replaceExecutor(Executor old) {
        return MFIX_CHUNK_BUILD_EXECUTOR;
    }
}
