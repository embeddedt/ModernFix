package org.embeddedt.modernfix.common.mixin.perf.remove_spawn_chunks;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WorldData;
import org.apache.commons.lang3.tuple.Pair;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.ISpawnTrackingMinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

@Mixin(value = MinecraftServer.class, priority = 1100)
public abstract class MinecraftServerMixin implements ISpawnTrackingMinecraftServer {
    @Shadow
    public abstract boolean isDedicatedServer();

    @Shadow
    public abstract WorldData getWorldData();

    @Shadow
    @Nullable
    public abstract ServerLevel getLevel(ResourceKey<Level> dimension);

    private Pair<ResourceKey<Level>, ChunkPos> mfix$initialSpawnLocation;

    private @Nullable Pair<ResourceKey<Level>, ChunkPos> loadPlayerSpawnLocation() {
        CompoundTag player = this.getWorldData().getLoadedPlayerTag();

        if (player == null) {
            return null;
        }

        ListTag pos = player.getList("Pos", CompoundTag.TAG_DOUBLE);
        double x = pos.getDouble(0);
        double z = pos.getDouble(2);

        // Dimension
        ResourceKey<Level> dimension = DimensionType.parseLegacy(
                new Dynamic<>(NbtOps.INSTANCE, player.get("Dimension"))
        ).resultOrPartial(ModernFix.LOGGER::error).orElse(Level.OVERWORLD);

        return Pair.of(dimension, new ChunkPos(SectionPos.blockToSectionCoord(Mth.floor(x)), SectionPos.blockToSectionCoord(Mth.floor(z))));
    }

    @Redirect(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"))
    private void addSpawnChunkTicket(ServerChunkCache cache, TicketType<?> type, ChunkPos pos, int distance, Object o, @Local(ordinal = 0, argsOnly = true) ChunkProgressListener listener) {
        if (!this.isDedicatedServer()) {
            // Temporarily create a START ticket around the player to load the world in parallel with client join
            // We remove it once the player has joined the world
            var pair = this.mfix$initialSpawnLocation = loadPlayerSpawnLocation();
            if (pair != null) {
                var level = this.getLevel(pair.getLeft());
                if (level != null) {
                    cache = level.getChunkSource();
                    pos = pair.getRight();
                }
            }

            listener.updateSpawnPos(pos);
            cache.addRegionTicket(TicketType.START, pos, 0, Unit.INSTANCE);
        } else {
            // just trigger sync load of initial spawn once
            // TODO: figure out if this magic is still needed
            cache.getChunk(pos.x, pos.z, true);
        }
    }

    @Redirect(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;getTickingGenerated()I"), require = 0)
    private int getGenerated(ServerChunkCache cache) {
        return 441;
    }

    @Override
    public Pair<ResourceKey<Level>, ChunkPos> mfix$getInitialStartTicketLocation() {
        var pair = this.mfix$initialSpawnLocation;
        this.mfix$initialSpawnLocation = null;
        return pair;
    }
}
