package org.embeddedt.modernfix.common.mixin.perf.encoder_cache_leak;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.EncoderCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DataComponents.class)
public interface DataComponentsAccessor {
    @Accessor("ENCODER_CACHE")
    static EncoderCache mfix$getCache() {
        throw new AssertionError();
    }
}
