package org.embeddedt.modernfix.mixin.perf.remove_spawn_chunks;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Redirect(method = "setDefaultSpawnPos", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;removeRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"))
    private void removeTicket(ServerChunkCache cache, TicketType<?> type, ChunkPos pos, int distance, Object o) {
        DistanceManager manager = ((ServerChunkCacheAccessor)cache).getDistanceManager();
        /* make one chunk load */
        manager.removeTicket(TicketType.START, pos, 44, Unit.INSTANCE);
    }

    @Redirect(method = "setDefaultSpawnPos", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"))
    private void addTicket(ServerChunkCache cache, TicketType<?> type, ChunkPos pos, int distance, Object o) {
        DistanceManager manager = ((ServerChunkCacheAccessor)cache).getDistanceManager();
        /* make one chunk load */
        manager.addTicket(TicketType.START, pos, 44, Unit.INSTANCE);
    }
}
