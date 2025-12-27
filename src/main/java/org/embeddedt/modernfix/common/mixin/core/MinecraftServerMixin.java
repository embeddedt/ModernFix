package org.embeddedt.modernfix.common.mixin.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.util.Util;
import net.minecraft.server.MinecraftServer;
import org.embeddedt.modernfix.duck.ITimeTrackingServer;
import org.embeddedt.modernfix.neoforge.load.MinecraftServerReloadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin implements ITimeTrackingServer {
    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void startReloadTrack(Collection<String> selectedIds, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        MinecraftServerReloadTracker.ACTIVE_RELOADS++;
    }

    @ModifyExpressionValue(method = "reloadResources", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", ordinal = 0))
    private CompletableFuture<Void> mfix$endReloadTrack(CompletableFuture<Void> original) {
        return original.thenAcceptAsync(val -> {
            MinecraftServerReloadTracker.ACTIVE_RELOADS--;
        }, (Executor)this);
    }

    private long mfix$lastTickStartTime = -1L;

    @Override
    public long mfix$getLastTickStartTime() {
        return mfix$lastTickStartTime;
    }

    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;processPacketsAndTick(Z)V"))
    private void trackTickTime(CallbackInfo ci) {
        mfix$lastTickStartTime = Util.getMillis();
    }
}
