package org.embeddedt.modernfix.testmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.StructureSettings;
import org.embeddedt.modernfix.testmod.TestMod;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FlatLevelSource.class)
public abstract class DebugLevelSourceMixin extends ChunkGenerator {
    public DebugLevelSourceMixin(BiomeSource biomeSource, StructureSettings structureSettings) {
        super(biomeSource, structureSettings);
    }

    @Override
    public void applyBiomeDecoration(WorldGenRegion region, StructureFeatureManager structureManager) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int i = region.getCenterX();
        int j = region.getCenterZ();

        for(int k = 0; k < 16; ++k) {
            for(int l = 0; l < 16; ++l) {
                int m = (i << 4) + k;
                int n = (j << 4) + l;
                for(int y = 0; y < 255; y++) {
                    BlockState blockState = TestMod.getColorCubeStateFor(m, y, n);
                    if (blockState != null) {
                        region.setBlock(mutableBlockPos.set(m, y, n), blockState, 2);
                    }
                }
            }
        }
    }
}
