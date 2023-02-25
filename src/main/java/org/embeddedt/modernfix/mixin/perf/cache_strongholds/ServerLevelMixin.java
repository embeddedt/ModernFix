package org.embeddedt.modernfix.mixin.perf.cache_strongholds;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import org.embeddedt.modernfix.duck.IServerLevel;
import org.embeddedt.modernfix.world.StrongholdLocationCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements IServerLevel {
    protected ServerLevelMixin(WritableLevelData arg, ResourceKey<Level> arg2, DimensionType arg3, Supplier<ProfilerFiller> supplier, boolean bl, boolean bl2, long l) {
        super(arg, arg2, arg3, supplier, bl, bl2, l);
    }

    @Shadow public abstract DimensionDataStorage getDataStorage();

    private StrongholdLocationCache mfix$strongholdCache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addStrongholdCache(MinecraftServer minecraftServer, Executor executor, LevelStorageSource.LevelStorageAccess arg,
                                ServerLevelData arg2, ResourceKey<Level> arg3, DimensionType arg4, ChunkProgressListener arg5,
                                ChunkGenerator arg6, boolean bl, long l, List<CustomSpawner> list, boolean bl2, CallbackInfo ci) {
        mfix$strongholdCache = this.getDataStorage().computeIfAbsent(() -> new StrongholdLocationCache((ServerLevel)(Object)this), StrongholdLocationCache.getFileId(this.dimensionType()));
    }

    @Override
    public StrongholdLocationCache mfix$getStrongholdCache() {
        return mfix$strongholdCache;
    }
}
