package org.embeddedt.modernfix.testmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import org.embeddedt.modernfix.testmod.TestMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugLevelSource.class)
public class DebugLevelSourceMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void showColorCube(WorldGenLevel level, ChunkAccess chunk, StructureFeatureManager structureFeatureManager, CallbackInfo ci) {
        ci.cancel();
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
