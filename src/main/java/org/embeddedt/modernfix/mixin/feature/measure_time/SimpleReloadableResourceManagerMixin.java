package org.embeddedt.modernfix.mixin.feature.measure_time;

import net.minecraft.server.packs.resources.SimpleReloadableResourceManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.core.config.ModernFixConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SimpleReloadableResourceManager.class)
public class SimpleReloadableResourceManagerMixin {

    /**
     * @author embeddedt
     * @reason add ability to use this feature in modpacks
     */
    @Redirect(method = "createReload", at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;isDebugEnabled()Z", remap = false))
    private boolean enableDebugReloader(Logger logger) {
        return logger.isDebugEnabled() || ModernFixConfig.ENABLE_DEBUG_RELOADER.get();
    }
}
