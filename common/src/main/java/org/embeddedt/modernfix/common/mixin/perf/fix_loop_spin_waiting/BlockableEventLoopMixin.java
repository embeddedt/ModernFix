package org.embeddedt.modernfix.common.mixin.perf.fix_loop_spin_waiting;

import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

// This should fix https://bugs.mojang.com/browse/MC-183518
@Mixin(value = BlockableEventLoop.class, priority = 500)
public class BlockableEventLoopMixin {
    private static final long MFIX$TICK_WAIT_TIME = TimeUnit.MILLISECONDS.toNanos(2);

    /**
     * @author embeddedt
     * @reason yielding the thread is pretty pointless if we're about to park anyway
     */
    @Overwrite
    public void waitForTasks() {
        LockSupport.parkNanos("waiting for tasks", MFIX$TICK_WAIT_TIME);
    }
}
