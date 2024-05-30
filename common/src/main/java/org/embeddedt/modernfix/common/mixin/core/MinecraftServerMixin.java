package org.embeddedt.modernfix.common.mixin.core;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import org.embeddedt.modernfix.duck.ITimeTrackingServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin implements ITimeTrackingServer {
    private long mfix$lastTickStartTime = -1L;

    @Override
    public long mfix$getLastTickStartTime() {
        return mfix$lastTickStartTime;
    }

    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V"))
    private void trackTickTime(CallbackInfo ci) {
        mfix$lastTickStartTime = Util.getMillis();
    }
}
