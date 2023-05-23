package org.embeddedt.modernfix.common.mixin.safety;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.embeddedt.modernfix.ModernFix;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DynamicTexture.class)
public class DynamicTextureMixin {
    @Shadow @Nullable private NativeImage pixels;

    @Redirect(method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;pixels:Lcom/mojang/blaze3d/platform/NativeImage;", ordinal = 0))
    private void putNewPixel(DynamicTexture texture, NativeImage pixels) {
        if(pixels == null) {
            ModernFix.LOGGER.error("Null image provided to DynamicTexture", new Exception());
            pixels = new NativeImage(4, 4, false);
        }
        this.pixels = pixels;
    }
}
