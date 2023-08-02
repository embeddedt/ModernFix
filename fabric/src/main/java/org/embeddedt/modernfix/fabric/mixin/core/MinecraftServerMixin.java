package org.embeddedt.modernfix.fabric.mixin.core;

import net.minecraft.server.MinecraftServer;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixFabric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(method = "runServer", at = @At("HEAD"))
    private void changeServerReference(CallbackInfo ci) {
        ModernFixFabric.theServer = new WeakReference<>((MinecraftServer)(Object)this);
    }

    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J", ordinal = 0))
    private void hookServerStarted(CallbackInfo ci) {
        ModernFix.INSTANCE.onServerStarted();
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void hookServerShutdown(CallbackInfo ci) {
        ModernFix.INSTANCE.onServerDead((MinecraftServer)(Object)this);
    }
}
