package org.embeddedt.modernfix.common.mixin.perf.fix_loop_spin_waiting;

import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.TimeUnit;

// This should fix https://bugs.mojang.com/browse/MC-183518
@Mixin(BlockableEventLoop.class)
public class BlockableEventLoopMixin {
    /**
     * @author embeddedt
     * @reason yielding the thread is pretty pointless if we're about to park anyway
     */
    @Redirect(method = "waitForTasks", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;yield()V"), require = 0)
    private void doNotYield() {}

    private static final long MFIX$TICK_WAIT_TIME = TimeUnit.MILLISECONDS.toNanos(2);

    /**
     * @author embeddedt
     * @reason park for more than 0.1ms at a time. Task submission will call unpark(), so the thread will become
     * runnable again if a task is submitted.
     */
    @ModifyArg(method = "waitForTasks", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/LockSupport;parkNanos(Ljava/lang/Object;J)V"), index = 1, require = 0)
    private long changeParkDuration(long originalDuration) {
        return MFIX$TICK_WAIT_TIME;
    }
}
