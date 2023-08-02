package org.embeddedt.modernfix.common.mixin.feature.measure_time;

import net.minecraft.client.gui.screens.ConnectScreen;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
@ClientOnlyMixin
public class ConnectScreenMixin {
    @Inject(method = "connect", at = @At("HEAD"))
    private void recordConnectStartTime(CallbackInfo ci) {
        ModernFixClient.worldLoadStartTime = System.nanoTime();
    }
}
