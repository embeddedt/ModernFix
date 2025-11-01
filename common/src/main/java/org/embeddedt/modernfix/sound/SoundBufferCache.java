package org.embeddedt.modernfix.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SoundBufferCache extends LinkedHashMap<ResourceLocation, CompletableFuture<SoundBuffer>> {
    @Override
    protected boolean removeEldestEntry(Map.Entry<ResourceLocation, CompletableFuture<SoundBuffer>> eldest) {
        return super.removeEldestEntry(eldest);
    }
}
