package org.embeddedt.modernfix.duck.release_protochunks;

import java.util.concurrent.atomic.AtomicInteger;

public interface IClearableChunkHolder {
    void mfix$resetProtoChunkFutures();

    AtomicInteger mfix$getGenerationRefCount();
}
