package org.embeddedt.modernfix.common.mixin.feature.spark_profile_world_join;

import net.minecraft.client.Minecraft;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.spark.SparkLaunchProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
@ClientOnlyMixin
public class MinecraftMixin {
    @Inject(method = "prepareForMultiplayer", at = @At("HEAD"))
    private void startProfiling(CallbackInfo ci) {
        SparkLaunchProfiler.start("world_join");
    }
}
