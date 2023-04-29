package org.embeddedt.modernfix.mixin.perf.remove_spawn_chunks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MinecraftServer.class, priority = 1100)
public class MinecraftServerMixin {
    @Redirect(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"))
    private void addSpawnChunkTicket(ServerChunkCache cache, TicketType<?> type, ChunkPos pos, int distance, Object o) {
        DistanceManager manager = ((ServerChunkCacheAccessor)cache).getDistanceManager();
        /* make one chunk load */
        manager.addTicket(TicketType.START, pos, 44, Unit.INSTANCE);
    }

    @Redirect(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;getTickingGenerated()I"), require = 0)
    private int getGenerated(ServerChunkCache cache) {
        return 441;
    }
}
