package org.embeddedt.modernfix.common.mixin.perf.cache_strongholds;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.DimensionDataStorage;
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

import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements IServerLevel {
    protected ServerLevelMixin(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, RegistryAccess registryAccess, Holder<DimensionType> holder, boolean bl, boolean bl2, long l, int i) {
        super(writableLevelData, resourceKey, registryAccess, holder, bl, bl2, l, i);
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
    private void ensureGeneration(CallbackInfo ci) {
        mfix$strongholdCache = this.getDataStorage().computeIfAbsent(
                StrongholdLocationCache.factory((ServerLevel)(Object)this),
                StrongholdLocationCache.getFileId(this.dimensionTypeRegistration()));
        this.chunkSource.getGeneratorState().ensureStructuresGenerated();
    }

    @Override
    public StrongholdLocationCache mfix$getStrongholdCache() {
        return mfix$strongholdCache;
    }
}
