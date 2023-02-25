package org.embeddedt.modernfix.mixin.feature.measure_time;

import net.minecraft.server.ReloadableServerResources;
import org.embeddedt.modernfix.core.config.ModernFixConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ReloadableServerResources.class)
public class ReloadableServerResourcesMixin {
    /**
     * @author embeddedt
     * @reason add ability to use this feature in modpacks
     */
    @ModifyArg(method = "loadResources", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;create(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Z)Lnet/minecraft/server/packs/resources/ReloadInstance;"), index = 5)
    private static boolean enableDebugReloader(boolean bl) {
        return bl || ModernFixConfig.ENABLE_DEBUG_RELOADER.get();
    }
}
