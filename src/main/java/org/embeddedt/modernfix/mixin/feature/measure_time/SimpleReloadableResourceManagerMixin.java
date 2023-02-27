package org.embeddedt.modernfix.mixin.feature.measure_time;

import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.embeddedt.modernfix.core.config.ModernFixConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ReloadableResourceManager.class)
public class SimpleReloadableResourceManagerMixin {

    /**
     * @author embeddedt
     * @reason add ability to use this feature in modpacks
     */
    @ModifyArg(method = "createReload", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;create(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Z)Lnet/minecraft/server/packs/resources/ReloadInstance;"), index = 5)
    private boolean enableDebugReloader(boolean bl) {
        return bl || (ModernFixConfig.isLoaded() && ModernFixConfig.ENABLE_DEBUG_RELOADER.get());
    }
}
