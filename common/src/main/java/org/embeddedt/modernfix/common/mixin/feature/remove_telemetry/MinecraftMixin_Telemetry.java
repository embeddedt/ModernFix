package org.embeddedt.modernfix.common.mixin.feature.remove_telemetry;

import net.minecraft.client.Minecraft;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Minecraft.class, priority = 1100)
@ClientOnlyMixin
public class MinecraftMixin_Telemetry {
    @Inject(method = "allowsTelemetry", at = @At("HEAD"), cancellable = true)
    private void markTelemetryNotAllowed(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
