package org.embeddedt.modernfix.common.mixin.perf.release_protochunks;

import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ThreadedLevelLightEngine.class)
public interface ThreadedLevelLightEngineAccessor {
    @Invoker("updateChunkStatus")
    void mfix$invokeUpdateChunkStatus(ChunkPos pos);
}
