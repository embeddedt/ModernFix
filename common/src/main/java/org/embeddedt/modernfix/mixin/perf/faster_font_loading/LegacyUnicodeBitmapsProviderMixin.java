package org.embeddedt.modernfix.mixin.perf.faster_font_loading;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.font.providers.LegacyUnicodeBitmapsProvider;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Objective: avoid recomputing locations many times, as well as loading all the font sheets in the constructor
 * only to do it again later.
 */
@Mixin(LegacyUnicodeBitmapsProvider.class)
@ClientOnlyMixin
public abstract class LegacyUnicodeBitmapsProviderMixin {
    @Shadow protected abstract ResourceLocation getSheetLocation(int i);

    @Shadow @Final private Map<ResourceLocation, NativeImage> textures;
    private final ResourceLocation[] glyphLocations = new ResourceLocation[256];

    private ResourceLocation currentCharIdx;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/font/providers/LegacyUnicodeBitmapsProvider;getSheetLocation(I)Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation storeCurrentCharIdx(LegacyUnicodeBitmapsProvider provider, int i) {
        ResourceLocation location = getSheetLocation(i);
        currentCharIdx = location;
        return location;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;read(Lcom/mojang/blaze3d/platform/NativeImage$Format;Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;"))
    private NativeImage storeLoadedFontSheet(NativeImage.Format format, InputStream stream) throws IOException {
        NativeImage image = NativeImage.read(format, stream);
        textures.put(currentCharIdx, image);
        return image;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;close()V"))
    private void skipCloseNativeImage(NativeImage image) {
        /* we can't close here, as the image has been stored for use later */
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void clearLocation(CallbackInfo ci) {
        currentCharIdx = null;
    }

    @Inject(method = "getSheetLocation", at = @At("HEAD"), cancellable = true)
    private void useCachedLocation(int idx, CallbackInfoReturnable<ResourceLocation> cir) {
        int cachedIdx = idx / 256;
        if(cachedIdx >= 0 && cachedIdx < glyphLocations.length && glyphLocations[cachedIdx] != null)
            cir.setReturnValue(glyphLocations[cachedIdx]);
    }

    @Inject(method = "getSheetLocation", at = @At("RETURN"))
    private void saveCachedLocation(int idx, CallbackInfoReturnable<ResourceLocation> cir) {
        int cachedIdx = idx / 256;
        if(cachedIdx >= 0 && cachedIdx < glyphLocations.length)
            glyphLocations[cachedIdx] = cir.getReturnValue();
    }
}
