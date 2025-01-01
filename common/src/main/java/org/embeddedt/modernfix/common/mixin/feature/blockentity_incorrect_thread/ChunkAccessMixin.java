package org.embeddedt.modernfix.common.mixin.feature.blockentity_incorrect_thread;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.embeddedt.modernfix.util.ConcurrencySanitizingMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ChunkAccess.class)
public class ChunkAccessMixin {
    @Shadow @Final @Mutable protected Map<BlockPos, BlockEntity> blockEntities;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void wrapInConcurrencyDetector(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, Registry biomeRegistry, long inhabitedTime, LevelChunkSection[] sections, BlendingData blendingData, CallbackInfo ci) {
        if (levelHeightAccessor instanceof Level level) {
            this.blockEntities = new ConcurrencySanitizingMap<>(this.blockEntities, ((LevelThreadAccessor)level).getThread());
        }
    }
}
