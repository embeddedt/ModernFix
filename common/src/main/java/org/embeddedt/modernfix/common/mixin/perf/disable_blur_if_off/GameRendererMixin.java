package org.embeddedt.modernfix.common.mixin.perf.disable_blur_if_off;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
@ClientOnlyMixin
public class GameRendererMixin {
    @Shadow @Final private Minecraft minecraft;

    /**
     * @author embeddedt
     * @reason Do not run the blur shader if blur is completely disabled.
     */
    @Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
    private void skipBlurIfDisabled(float f, CallbackInfo ci) {
        if(this.minecraft.options.getMenuBackgroundBlurriness() <= 0) {
            ci.cancel();
        }
    }
}
