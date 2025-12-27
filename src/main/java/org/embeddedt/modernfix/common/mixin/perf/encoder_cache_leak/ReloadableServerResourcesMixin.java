package org.embeddedt.modernfix.common.mixin.perf.encoder_cache_leak;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@Mixin(ReloadableServerResources.class)
public class ReloadableServerResourcesMixin {
    /**
     * @author embeddedt
     * @reason Some mods (e.g. KubeJS) may provide a custom DynamicOps instance during resource reload. This instance
     * can end up being strongly retained by an EncoderCache.Key entry even after the reload finishes. The simplest
     * fix is to invalidate all entries of the encoder cache after a server-side resource reload, which should not break
     * mods, as the cache is not guaranteed to persist entries for any length of time due to using both a maximum size
     * & soft values.
     */
    @ModifyReturnValue(method = "loadResources", at = @At("RETURN"))
    private static CompletableFuture<ReloadableServerResources> resetEncoderCache(CompletableFuture<ReloadableServerResources> future) {
        return future.whenComplete((r, t) -> {
            ((EncoderCacheAccessor)DataComponentsAccessor.mfix$getCache()).mfix$getCache().invalidateAll();
        });
    }
}
