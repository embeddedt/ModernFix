package org.embeddedt.modernfix.common.mixin.bugfix.chunk_deadlock;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {
    @Accessor("mainThreadProcessor")
    ServerChunkCache.MainThreadExecutor mfix$getMainThreadProcessor();
}
