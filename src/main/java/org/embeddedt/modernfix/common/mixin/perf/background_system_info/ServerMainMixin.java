package org.embeddedt.modernfix.common.mixin.perf.background_system_info;

import net.minecraft.server.Main;
import org.embeddedt.modernfix.util.BackgroundCrashReportPreload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public class ServerMainMixin {
    @Redirect(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/CrashReport;preload()V"))
    private static void startPreloadInBackground() {
        BackgroundCrashReportPreload.start();
    }

    @Inject(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/Bootstrap;validate()V"))
    private static void finishPreload(CallbackInfo ci) {
        BackgroundCrashReportPreload.join();
    }
}
