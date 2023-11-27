package org.embeddedt.modernfix.fabric.mixin.safety;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicTexture.class)
@ClientOnlyMixin
public class DynamicTextureMixin {
    @Shadow @Nullable private NativeImage pixels;

    private Exception closeTrace;

    @Inject(method = "method_22793", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;pixels:Lcom/mojang/blaze3d/platform/NativeImage;", ordinal = 0))
    private void checkNullPixels(CallbackInfo ci) {
        if(pixels == null) {
            ModernFix.LOGGER.error("Attempted to upload null texture! This is not allowed, closed here", closeTrace);
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void storeCloseTrace(CallbackInfo ci) {
        closeTrace = new Exception();
    }
}
