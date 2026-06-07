package org.embeddedt.modernfix.common.mixin.bugfix.buffer_builder_leak;

import com.mojang.blaze3d.vertex.BufferBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderBuffers.class)
@ClientOnlyMixin
public class RenderBuffersMixin {
    /**
     * @author embeddedt
     * @reason put() may be called for multiple instances of the same render type (e.g. signSheet and hangingSignSheet
     * in 1.20.1). This leaks the previous BufferBuilder if one is already in the map.
     */
    @Inject(method = "put", at = @At("HEAD"), cancellable = true)
    private static void mfix$preventBufferLeak(Object2ObjectLinkedOpenHashMap<RenderType, BufferBuilder> mapBuilders, RenderType renderType, CallbackInfo ci) {
        if (mapBuilders.containsKey(renderType)) {
            ci.cancel();
        }
    }
}
