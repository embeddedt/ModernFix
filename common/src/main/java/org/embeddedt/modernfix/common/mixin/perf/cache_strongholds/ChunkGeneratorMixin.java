package org.embeddedt.modernfix.common.mixin.perf.cache_strongholds;

import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.IChunkGenerator;
import org.embeddedt.modernfix.duck.IServerLevel;
import org.embeddedt.modernfix.world.StrongholdLocationCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin implements IChunkGenerator {
    private WeakReference<ServerLevel> mfix$serverLevel;

    @Override
    public void mfix$setAssociatedServerLevel(ServerLevel level) {
        mfix$serverLevel = new WeakReference<>(level);
    }

    @Inject(method = "generateRingPositions", at = @At("HEAD"), cancellable = true)
    private void useCachedDataIfAvailable(Holder<StructureSet> structureSet, RandomState random, ConcentricRingsStructurePlacement placement, CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> cir) {
        if(placement.count() == 0)
            return;
        ServerLevel level = searchLevel();
        if(level == null)
            return;
        StrongholdLocationCache cache = ((IServerLevel)level).mfix$getStrongholdCache();
        List<ChunkPos> positions = cache.getChunkPosList();
        if(positions.isEmpty())
            return;
        ModernFix.LOGGER.debug("Loaded stronghold cache for dimension {} with {} positions", level.dimension().location(), positions.size());
        cir.setReturnValue(CompletableFuture.completedFuture(positions));
    }

    private ServerLevel searchLevel() {
        if(mfix$serverLevel != null)
            return mfix$serverLevel.get();
        else
            return null;
    }

    @Inject(method = "generateRingPositions", at = @At("RETURN"), cancellable = true)
    private void saveCachedData(Holder<StructureSet> structureSet, RandomState random, ConcentricRingsStructurePlacement placement, CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> cir) {
        cir.setReturnValue(cir.getReturnValue().thenApplyAsync(list -> {
            if(list.size() == 0)
                return list;
            ServerLevel level = searchLevel();
            if(level != null) {
                StrongholdLocationCache cache = ((IServerLevel)level).mfix$getStrongholdCache();
                cache.setChunkPosList(list);
                ModernFix.LOGGER.debug("Saved stronghold cache for dimension {}", level.dimension().location());
            }
            return list;
        }, Util.backgroundExecutor()));
    }
}
