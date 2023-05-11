package org.embeddedt.modernfix.common.mixin.perf.resourcepacks;

import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.resources.PackResourcesCacheEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
public class ReloadableResourceManagerMixin {
    @Inject(method = "createReload", at = @At("HEAD"))
    private void invalidateResourceCaches(CallbackInfoReturnable<?> cir) {
        ModernFix.LOGGER.info("Invalidating pack caches");
        PackResourcesCacheEngine.invalidate();
    }
}
