package org.embeddedt.modernfix.neoforge.mixin.core;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.WorldLoader;
import org.embeddedt.modernfix.neoforge.load.MinecraftServerReloadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(WorldLoader.class)
public class WorldLoaderMixin {
    @Inject(method = "load", at = @At("HEAD"))
    private static void trackStartReload(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        MinecraftServerReloadTracker.ACTIVE_RELOADS++;
    }

    @ModifyReturnValue(method = "load", at = @At("RETURN"))
    private static <V> CompletableFuture<V> trackEndReload(CompletableFuture<V> original, @Local(ordinal = 1, argsOnly = true) Executor syncExecutor) {
        return original.thenApplyAsync(val -> {
            MinecraftServerReloadTracker.ACTIVE_RELOADS--;
            return val;
        }, syncExecutor);
    }
}
