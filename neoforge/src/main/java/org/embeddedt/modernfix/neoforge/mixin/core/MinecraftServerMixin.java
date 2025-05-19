package org.embeddedt.modernfix.neoforge.mixin.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.MinecraftServer;
import org.embeddedt.modernfix.neoforge.load.MinecraftServerReloadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
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
}
