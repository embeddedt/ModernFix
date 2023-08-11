package org.embeddedt.modernfix.testmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import org.embeddedt.modernfix.testmod.TestMod;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FlatLevelSource.class)
public abstract class DebugLevelSourceMixin extends ChunkGenerator {
    public DebugLevelSourceMixin(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureFeatureManager) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        ChunkPos chunkPos = chunk.getPos();
        int i = chunkPos.x;
        int j = chunkPos.z;

        for(int k = 0; k < 16; ++k) {
            for(int l = 0; l < 16; ++l) {
                int m = SectionPos.sectionToBlockCoord(i, k);
                int n = SectionPos.sectionToBlockCoord(j, l);
                for(int y = 0; y < 255; y++) {
                    BlockState blockState = TestMod.getColorCubeStateFor(m, y, n);
                    if (blockState != null) {
                        level.setBlock(mutableBlockPos.set(m, y, n), blockState, 2);
                    }
                }
            }
        }
    }
}
