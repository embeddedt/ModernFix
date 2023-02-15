package org.embeddedt.modernfix.mixin.perf.thread_priorities;

import net.minecraft.Util;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.config.ModernFixConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

@Mixin(Util.class)
public class UtilMixin {
    @ModifyArg(method = "makeExecutor", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ForkJoinPool;<init>(ILjava/util/concurrent/ForkJoinPool$ForkJoinWorkerThreadFactory;Ljava/lang/Thread$UncaughtExceptionHandler;Z)V"), index = 1)
    private static ForkJoinPool.ForkJoinWorkerThreadFactory adjustPriorityOfThreadFactory(ForkJoinPool.ForkJoinWorkerThreadFactory factory) {
        return pool -> {
            ForkJoinWorkerThread thread = factory.newThread(pool);
            int pri = ModernFixConfig.BACKGROUND_WORKER_PRIORITY.get();
            thread.setPriority(pri);
            return thread;
        };
    }
}
