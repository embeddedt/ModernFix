package org.embeddedt.modernfix.common.mixin.perf.resourcepacks;

import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.resources.ICachingResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableResourceManager.class)
public class ReloadableResourceManagerMixin {
    @Inject(method = "createReload", at = @At("HEAD"))
    private void invalidateResourceCaches(Executor backgroundExecutor, Executor gameExecutor, CompletableFuture<Unit> waitingFor, List<PackResources> resourcePacks, CallbackInfoReturnable<?> cir) {
        ModernFix.LOGGER.info("Invalidating pack caches");
        for(PackResources pack : resourcePacks) {
            if(pack instanceof ICachingResourcePack)
                ((ICachingResourcePack)pack).invalidateCache();
        }
    }
}
