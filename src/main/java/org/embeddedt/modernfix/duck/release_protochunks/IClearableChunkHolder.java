package org.embeddedt.modernfix.duck.release_protochunks;

import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;

import java.util.concurrent.atomic.AtomicInteger;

public interface IClearableChunkHolder {
    /**
     * We don't want to drop FULL chunks, or chunks immediately surrouding FULL. So + 2 is the minimum we can drop.
     */
    int LOWEST_DROPPABLE_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.FULL) + 2;

    void mfix$resetProtoChunkFutures();

    AtomicInteger mfix$getGenerationRefCount();
}
