package org.embeddedt.modernfix.mixin.perf.sync_executor_sleep;

import net.minecraftforge.fml.ModWorkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@Mixin(targets = "net/minecraftforge/fml/ModWorkManager$SyncExecutor")
public abstract class SyncExecutorMixin {
    @Shadow(remap = false) public abstract boolean driveOne();

    private static final long PARK_TIME = TimeUnit.MILLISECONDS.toNanos(50);

    /**
     * Currently FML spins in driveOne while waiting for the modloading workers. We can improve this
     * by sleeping for 50ms at a time.
     *
     * Also, render FML splash screen regardless of task availability.
     * @author embeddedt
     * @reason improve CPU efficiency
     */
    public void drive(Runnable ticker) {
        int executions = 0;
        do {
            executions++;
            ticker.run();
        } while(driveOne());
        if(executions < 2)
            LockSupport.parkNanos(PARK_TIME);
    }
}
