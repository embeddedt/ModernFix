package org.embeddedt.modernfix.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

public class StrongholdLocationCache extends SavedData {
    private List<ChunkPos> chunkPosList;
    public StrongholdLocationCache() {
        super();
        chunkPosList = new ArrayList<>();
    }

    private StrongholdLocationCache(List<ChunkPos> list) {
        this.chunkPosList = new ArrayList<>(list);
    }

    public static final Codec<StrongholdLocationCache> CODEC = RecordCodecBuilder.create(instance ->
       instance.group(ChunkPos.CODEC.listOf().optionalFieldOf("stronghold_positions", List.of()).forGetter(StrongholdLocationCache::getChunkPosList))
               .apply(instance, StrongholdLocationCache::new)
    );

    public static final SavedDataType<StrongholdLocationCache> TYPE = new SavedDataType<>(
            "modernfix_stronghold_cache",
            StrongholdLocationCache::new,
            CODEC,
            DataFixTypes.SAVED_DATA_FORCED_CHUNKS
    );

    public List<ChunkPos> getChunkPosList() {
        return new ArrayList<>(chunkPosList);
    }

    public void setChunkPosList(List<ChunkPos> positions) {
        this.chunkPosList = new ArrayList<>(positions);
        this.setDirty();
    }
}
