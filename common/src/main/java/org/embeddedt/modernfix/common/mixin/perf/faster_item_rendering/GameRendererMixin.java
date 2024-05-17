package org.embeddedt.modernfix.common.mixin.perf.faster_item_rendering;

import net.minecraft.client.renderer.GameRenderer;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.render.RenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
@ClientOnlyMixin
public class GameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.BEFORE))
    private void markRenderingLevel(CallbackInfo ci) {
        RenderState.IS_RENDERING_LEVEL = true;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void markNotRenderingLevel(CallbackInfo ci) {
        RenderState.IS_RENDERING_LEVEL = false;
    }
}
