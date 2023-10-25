package org.embeddedt.modernfix.fabric.mixin.core;

import net.minecraft.server.MinecraftServer;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
@ClientOnlyMixin
public class ClientMinecraftServerMixin {
    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getNanos()J", ordinal = 0))
    private void markServerStarted(CallbackInfo ci) {
        ModernFixClient.INSTANCE.onServerStarted((MinecraftServer)(Object)this);
    }
}
