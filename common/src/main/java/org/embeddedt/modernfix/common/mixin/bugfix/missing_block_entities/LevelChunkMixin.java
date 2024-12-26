package org.embeddedt.modernfix.common.mixin.bugfix.missing_block_entities;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hypixel (and possibly other buggy servers) send chunks to the client that are missing some block entity data, which
 * causes these entities to be invisible. We "fix" this by recreating the block entity on the client with default data,
 * which is hopefully what the legacy server also expects.
 */
@Mixin(LevelChunk.class)
@ClientOnlyMixin
public abstract class LevelChunkMixin extends ChunkAccess {
    @Shadow @Final private Level level;

    @Shadow @Nullable public abstract BlockEntity getBlockEntity(BlockPos pos, LevelChunk.EntityCreationType creationType);

    public LevelChunkMixin(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, Registry<Biome> biomeRegistry, long inhabitedTime, @Nullable LevelChunkSection[] sections, @Nullable BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, biomeRegistry, inhabitedTime, sections, blendingData);
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void validateBlockEntitiesInChunk(CallbackInfo ci) {
        // No reason to check in singleplayer or on the integrated server
        if (this.level.isClientSide && !Minecraft.getInstance().isLocalServer()) {
            for (int i = 0; i < this.sections.length; i++) {
                var section = this.sections[i];
                try {
                    if (!section.hasOnlyAir() && section.maybeHas(BlockBehaviour.BlockStateBase::hasBlockEntity)) {
                        scanSectionForBlockEntities(section, i);
                    }
                } catch(Exception e) {
                    ModernFix.LOGGER.error("Exception validating data in chunk", e);
                    return;
                }
            }
        }
    }

    @Unique
    private void scanSectionForBlockEntities(LevelChunkSection section, int i) {
        int chunkX = this.chunkPos.x;
        int chunkZ = this.chunkPos.z;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int sectionY = this.getSectionYFromSectionIndex(i);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    var state = section.getBlockState(x, y, z);
                    if (state.hasBlockEntity()) {
                        cursor.set(chunkX * 16 + x, sectionY * 16 + y, chunkZ * 16 + z);
                        makeBlockEntityIfNotExists(state, cursor);
                    }
                }
            }
        }
    }

    @Unique
    private void makeBlockEntityIfNotExists(BlockState state, BlockPos.MutableBlockPos pos) {
        if (this.blockEntities.containsKey(pos) || this.pendingBlockEntities.containsKey(pos)) {
            return;
        }

        BlockEntity blockEntity = this.getBlockEntity(pos.immutable(), LevelChunk.EntityCreationType.IMMEDIATE);
        String blockName = state.getBlock().toString();
        if (blockEntity != null) {
            ModernFix.LOGGER.warn("Created missing block entity for {} at {}", blockName, pos.toShortString());
        } else {
            ModernFix.LOGGER.error("Block entity is missing for {} at {}, but could not be created", blockName, pos.toShortString());
        }
    }
}

