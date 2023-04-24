package org.embeddedt.modernfix.mixin.core;

import net.minecraft.server.Bootstrap;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.load.ModWorkManagerQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bootstrap.class)
public class BootstrapMixin {
    @Shadow private static boolean isBootstrapped;

    @Shadow @Final private static Logger LOGGER;

    @Inject(method = "bootStrap", at = @At("HEAD"))
    private static void doModernFixBootstrap(CallbackInfo ci) {
        if(!isBootstrapped) {
            LOGGER.info("ModernFix bootstrap");
            ModWorkManagerQueue.replace();
        }
    }
}
