package org.embeddedt.modernfix.common.mixin.perf.resourcepacks;

import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.SimpleReloadableResourceManager;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.resources.ICachingResourcePack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(SimpleReloadableResourceManager.class)
public class ReloadableResourceManagerMixin {
    @Shadow @Final private List<PackResources> packs;

    @Inject(method = "createReload", at = @At("HEAD"))
    private void invalidateResourceCaches(CallbackInfoReturnable<?> cir) {
        ModernFix.LOGGER.info("Invalidating pack caches");
        for(PackResources pack : this.packs) {
            if(pack instanceof ICachingResourcePack)
                ((ICachingResourcePack)pack).invalidateCache();
        }
    }
}
