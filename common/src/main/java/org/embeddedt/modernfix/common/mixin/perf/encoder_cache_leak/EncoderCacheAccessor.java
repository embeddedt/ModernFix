package org.embeddedt.modernfix.common.mixin.perf.encoder_cache_leak;

import com.google.common.cache.LoadingCache;
import net.minecraft.util.EncoderCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EncoderCache.class)
public interface EncoderCacheAccessor {
    @Accessor("cache")
    LoadingCache<?, ?> mfix$getCache();
}
