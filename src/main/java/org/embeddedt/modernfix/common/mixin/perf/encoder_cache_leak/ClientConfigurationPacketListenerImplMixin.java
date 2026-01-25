package org.embeddedt.modernfix.common.mixin.perf.encoder_cache_leak;

import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConfigurationPacketListenerImpl.class)
@ClientOnlyMixin
public class ClientConfigurationPacketListenerImplMixin {
    /**
     * @author embeddedt
     * @reason Reset the encoder cache after configuration finishes as the registries are now changing.
     */
    @Inject(method = "handleConfigurationFinished", at = @At("RETURN"))
    private void resetEncoderCache(CallbackInfo ci) {
        ((EncoderCacheAccessor)DataComponentsAccessor.mfix$getCache()).mfix$getCache().invalidateAll();
    }
}
