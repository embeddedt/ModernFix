package org.embeddedt.modernfix.duck;

import net.minecraft.core.RegistryAccess;

import java.nio.file.Path;

public interface IChunkGenerator {
    void mfix$setStrongholdCachePath(Path cachePath, RegistryAccess.Frozen registryAccess);
}
