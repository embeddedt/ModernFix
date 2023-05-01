package org.embeddedt.modernfix.common.mixin.perf.dedicated_reload_executor;

import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

@Mixin(WorldOpenFlows.class)
public class WorldOpenFlowsMixin {
    @ModifyArg(method = "loadWorldStem(Lnet/minecraft/server/WorldLoader$PackConfig;Lnet/minecraft/server/WorldLoader$WorldDataSupplier;)Lnet/minecraft/server/WorldStem;", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/WorldStem;load(Lnet/minecraft/server/WorldLoader$InitConfig;Lnet/minecraft/server/WorldLoader$WorldDataSupplier;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 2)
    private Executor getResourceReloadExecutor(Executor service) {
        return ModernFix.resourceReloadExecutor();
    }
}
