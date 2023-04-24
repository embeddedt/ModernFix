package org.embeddedt.modernfix.mixin.perf.dedicated_reload_executor;

import net.minecraft.client.Minecraft;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Redirect(method = { "<init>", "reloadResourcePacks(Z)Ljava/util/concurrent/CompletableFuture;" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;backgroundExecutor()Ljava/util/concurrent/ExecutorService;", ordinal = 0))
    private ExecutorService getResourceReloadExecutor() {
        return ModernFix.resourceReloadExecutor();
    }
}
