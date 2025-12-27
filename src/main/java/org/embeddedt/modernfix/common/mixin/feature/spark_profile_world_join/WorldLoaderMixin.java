package org.embeddedt.modernfix.common.mixin.feature.spark_profile_world_join;

import net.minecraft.server.WorldLoader;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.spark.SparkLaunchProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldLoader.class)
@ClientOnlyMixin
public class WorldLoaderMixin {
    @Inject(method = "load", at = @At("HEAD"))
    private static void startProfiling(CallbackInfoReturnable<?> cir) {
        SparkLaunchProfiler.start("world_join");
    }
}
