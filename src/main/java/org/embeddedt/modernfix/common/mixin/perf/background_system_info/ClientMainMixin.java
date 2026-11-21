package org.embeddedt.modernfix.common.mixin.perf.background_system_info;

import net.minecraft.client.main.Main;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.util.BackgroundCrashReportPreload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
@ClientOnlyMixin
public class ClientMainMixin {
    @Redirect(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/CrashReport;preload()V"))
    private static void startPreloadInBackground() {
        BackgroundCrashReportPreload.start();
    }

    @Inject(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/Bootstrap;validate()V"))
    private static void finishPreload(CallbackInfo ci) {
        BackgroundCrashReportPreload.join();
    }
}
