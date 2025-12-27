package org.embeddedt.modernfix.common.mixin.feature.remove_telemetry;

import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.client.telemetry.TelemetryEventSender;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientTelemetryManager.class, priority = 1500)
@ClientOnlyMixin
public class ClientTelemetryManagerMixin {
    /**
     * @author embeddedt
     * @reason telemetry is useless noise for modded instances anyway, and introduces privacy concerns
     */
    @Inject(method = "createEventSender", at = @At("HEAD"), cancellable = true)
    private void disableTelemetrySender(CallbackInfoReturnable<TelemetryEventSender> cir) {
        cir.setReturnValue(TelemetryEventSender.DISABLED);
    }
}
