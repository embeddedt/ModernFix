package org.embeddedt.modernfix.duck;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.tuple.Pair;

public interface ISpawnTrackingMinecraftServer {
    Pair<ResourceKey<Level>, ChunkPos> mfix$getInitialStartTicketLocation();
}
