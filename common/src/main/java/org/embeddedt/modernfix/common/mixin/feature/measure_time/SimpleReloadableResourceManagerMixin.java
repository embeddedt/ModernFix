package org.embeddedt.modernfix.common.mixin.feature.measure_time;

import net.minecraft.server.packs.resources.SimpleReloadableResourceManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SimpleReloadableResourceManager.class)
public class SimpleReloadableResourceManagerMixin {
    // TODO maybe expose as a mixin config
    private static final boolean ENABLE_DEBUG_RELOADER = Boolean.getBoolean("modernfix.debugReloader");
    /**
     * @author embeddedt
     * @reason add ability to use this feature in modpacks
     */
    @Redirect(method = "createReload", at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;isDebugEnabled()Z", remap = false))
    private boolean enableDebugReloader(Logger logger) {
        return logger.isDebugEnabled() || ENABLE_DEBUG_RELOADER;
    }
}
