package org.embeddedt.modernfix.common.mixin.perf.encoder_cache_leak;

import net.minecraft.client.Minecraft;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
@ClientOnlyMixin
public class MinecraftMixin {
    /**
     * @author embeddedt
     * @reason Make sure the encoder cache is cleared when the client disconnects, as it retains strong references
     * to registries.
     */
    @Inject(method = {
            "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
            "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V"
    }, at = @At("RETURN"))
    private void clearEncoderCache(CallbackInfo ci) {
        ((EncoderCacheAccessor)DataComponentsAccessor.mfix$getCache()).mfix$getCache().invalidateAll();
    }
}
