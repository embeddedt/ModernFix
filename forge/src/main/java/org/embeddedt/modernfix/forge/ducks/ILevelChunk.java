package org.embeddedt.modernfix.forge.ducks;

import org.jetbrains.annotations.Nullable;

public interface ILevelChunk {
    void setEntityLoadHook(@Nullable Runnable loadHook);
    void runEntityLoadHook();
    boolean getEntitiesWereLoaded();
}
