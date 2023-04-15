package org.embeddedt.modernfix.mixin.perf.cache_strongholds;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import org.embeddedt.modernfix.duck.IChunkGenerator;
import org.embeddedt.modernfix.duck.IServerLevel;
import org.embeddedt.modernfix.world.StrongholdLocationCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements IServerLevel {
    protected ServerLevelMixin(WritableLevelData arg, ResourceKey<Level> arg2, RegistryAccess arg3, Holder<DimensionType> arg4, Supplier<ProfilerFiller> supplier, boolean bl, boolean bl2, long l, int i) {
        super(arg, arg2, arg3, arg4, supplier, bl, bl2, l, i);
    }

    @Shadow public abstract DimensionDataStorage getDataStorage();

    @Shadow @Final private ServerChunkCache chunkSource;
    private StrongholdLocationCache mfix$strongholdCache;

    /**
     * Initialize the stronghold cache but don't force any structure generation yet.
     */
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;ensureStructuresGenerated()V"))
    private void hookStrongholdCache(ChunkGeneratorStructureState generator) {
        ((IChunkGenerator)generator).mfix$setAssociatedServerLevel((ServerLevel)(Object)this);
    }

    /**
     * Now start the stronghold generation process.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void ensureGeneration(MinecraftServer minecraftServer, Executor executor, LevelStorageSource.LevelStorageAccess arg, ServerLevelData arg2, ResourceKey<Level> arg3, LevelStem arg4, ChunkProgressListener arg5, boolean bl, long l, List<CustomSpawner> list, boolean bl2, CallbackInfo ci) {
        mfix$strongholdCache = this.getDataStorage().computeIfAbsent(StrongholdLocationCache::load,
                StrongholdLocationCache::new,
                StrongholdLocationCache.getFileId(this.dimensionTypeRegistration()));
        this.chunkSource.getGeneratorState().ensureStructuresGenerated();
    }

    @Override
    public StrongholdLocationCache mfix$getStrongholdCache() {
        return mfix$strongholdCache;
    }
}
