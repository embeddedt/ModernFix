package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamicresources.DynamicSoundHelpers;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map;

@Mixin(SoundBufferLibrary.class)
@ClientOnlyMixin
public abstract class SoundBufferLibraryMixin {

    private static final boolean debugDynamicSoundLoading = Boolean.getBoolean("modernfix.debugDynamicSoundLoading");

    @Shadow @Final @Mutable
    private Map<ResourceLocation, CompletableFuture<SoundBuffer>> cache =
    CacheBuilder.newBuilder()
    .maximumSize(DynamicSoundHelpers.MAX_SOUND_COUNT)
    .expireAfterAccess(DynamicSoundHelpers.MAX_SOUND_LIFETIME_SECS, TimeUnit.SECONDS)
    // Excessive use of type hinting due to it assuming Object as the broadest correct type
    .<ResourceLocation, CompletableFuture<SoundBuffer>>removalListener(this::onSoundRemoval)
    .<ResourceLocation, CompletableFuture<SoundBuffer>>build()
    .asMap();

    private <K extends ResourceLocation, V extends CompletableFuture<SoundBuffer>> void onSoundRemoval(RemovalNotification<K, V> notification) {
        notification.getValue().thenAccept(SoundBuffer::discardAlBuffer);
        if(debugDynamicSoundLoading) {
            K k = notification.getKey();
            if(k == null)
                return;
            ModernFix.LOGGER.warn("Evicted sound {}", k);
        }
    }
}