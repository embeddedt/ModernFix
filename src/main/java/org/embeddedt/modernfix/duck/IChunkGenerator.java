package org.embeddedt.modernfix.duck;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

public interface IChunkGenerator {
    void mfix$setStrongholdCachePath(Path cachePath, MinecraftServer server);
}
