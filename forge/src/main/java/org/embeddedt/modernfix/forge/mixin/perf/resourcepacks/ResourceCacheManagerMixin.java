package org.embeddedt.modernfix.forge.mixin.perf.resourcepacks;

import net.minecraftforge.resource.ResourceCacheManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ResourceCacheManager.class, remap = false)
public class ResourceCacheManagerMixin {
    @Inject(method = "shouldUseCache", at = @At("HEAD"), cancellable = true)
    private static void disableCache(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
