package org.embeddedt.modernfix.common.mixin.feature.cause_lag_by_disabling_threads;

import net.minecraft.TracingExecutor;
import net.minecraft.util.Util;
import org.embeddedt.modernfix.util.SingleThreadedWorkerService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Util.class)
public class UtilMixin {
    @Shadow @Final @Mutable
    private static final TracingExecutor BACKGROUND_EXECUTOR = new TracingExecutor(new SingleThreadedWorkerService());
}
