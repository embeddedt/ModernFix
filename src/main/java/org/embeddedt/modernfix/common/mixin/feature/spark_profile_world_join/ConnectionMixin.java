package org.embeddedt.modernfix.common.mixin.feature.spark_profile_world_join;

import net.minecraft.network.Connection;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.spark.SparkLaunchProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
@ClientOnlyMixin
public class ConnectionMixin {
    /**
     * @author embeddedt
     * @reason stop the profiler if joining the world fails (this is a no-op if profiler is not running)
     */
    @Inject(method = "handleDisconnection", at = @At("HEAD"))
    private void stopProfilingOnDisconnect(CallbackInfo ci) {
        SparkLaunchProfiler.stop("world_join");
    }
}
