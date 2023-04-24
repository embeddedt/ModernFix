package org.embeddedt.modernfix.mixin.perf.dedicated_reload_executor;

import net.minecraft.server.MinecraftServer;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @ModifyArg(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ReloadableServerResources;loadResources(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/RegistryAccess$Frozen;Lnet/minecraft/commands/Commands$CommandSelection;ILjava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 4)
    private Executor getReloadExecutor(Executor asyncExecutor) {
        return ModernFix.resourceReloadExecutor();
    }
}
