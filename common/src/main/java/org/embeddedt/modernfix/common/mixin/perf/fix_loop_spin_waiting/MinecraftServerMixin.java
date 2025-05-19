package org.embeddedt.modernfix.common.mixin.perf.fix_loop_spin_waiting;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

@Mixin(value = MinecraftServer.class, priority = 500)
public abstract class MinecraftServerMixin extends BlockableEventLoop<Runnable> {
    @Shadow private long nextTickTimeNanos;

    protected MinecraftServerMixin(String name) {
        super(name);
    }

    @Unique
    private boolean mfix$isWaitingForNextTick = false;

    @WrapOperation(
            method = "waitUntilNextTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V")
    )
    private void managedBlock(MinecraftServer instance, BooleanSupplier isDone, Operation<Void> original) {
        try {
            this.mfix$isWaitingForNextTick = true;
            original.call(instance, isDone);
        } finally {
            this.mfix$isWaitingForNextTick = false;
        }
    }

    @Override
    public void waitForTasks() {
        if (this.mfix$isWaitingForNextTick) {
            LockSupport.parkNanos("waiting for tasks", this.nextTickTimeNanos - Util.getNanos());
        } else {
            super.waitForTasks();
        }
    }
}
