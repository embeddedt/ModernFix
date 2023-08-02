package org.embeddedt.modernfix.fabric.mixin.core;

import net.minecraft.client.Minecraft;
import org.embeddedt.modernfix.ModernFixClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MCMixin_Fabric {
    @Inject(method = "tick", at = @At("RETURN"))
    private void onRenderTickEnd(CallbackInfo ci) {
        ModernFixClient.INSTANCE.onRenderTickEnd();
    }
}
