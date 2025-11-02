package org.embeddedt.modernfix.common.mixin.perf.dynamic_sounds;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamicresources.DynamicSoundHelpers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.Map;

@Mixin(SoundBufferLibrary.class)
@ClientOnlyMixin
public abstract class SoundBufferLibraryMixin {
    @Shadow @Final @Mutable
    private Map<ResourceLocation, CompletableFuture<SoundBuffer>> cache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceCache(CallbackInfo ci) {
        this.cache = new DynamicSoundHelpers.Cache(cache);
    }
}
