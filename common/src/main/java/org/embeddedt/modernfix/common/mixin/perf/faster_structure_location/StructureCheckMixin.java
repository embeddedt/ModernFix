package org.embeddedt.modernfix.common.mixin.perf.faster_structure_location;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StructureCheck.class)
public class StructureCheckMixin {
    @Shadow @Final private ChunkGenerator chunkGenerator;
    @Shadow @Final private long seed;
    @Shadow @Final private Registry<Structure> structureConfigs;
    @Shadow @Final private RandomState randomState;

    /**
     * @author embeddedt (inspired by 24w04a and Bytzo's comment on https://bugs.mojang.com/browse/MC-249136)
     * @reason Avoid running the canCreateStructure method (which can be expensive) if the structure placement already
     * forbids placing the structure in this chunk.
     */
    @ModifyExpressionValue(method = "checkStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/StructureCheck;tryLoadFromStorage(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/levelgen/structure/Structure;ZJ)Lnet/minecraft/world/level/levelgen/structure/StructureCheckResult;"))
    private StructureCheckResult mfix$checkForValidPosition(StructureCheckResult storageResult, ChunkPos chunkPos, Structure structure, boolean skipKnownStructures) {
        if (storageResult != null) {
            return storageResult;
        } else {
            // Check if any of the placements allow for this structure to be in this chunk
            var structureHolder = this.structureConfigs.getHolder(this.structureConfigs.getId(structure)).orElseThrow();
            for (var placement : ((ChunkGeneratorAccessor)this.chunkGenerator).invokeGetPlacementsForStructure(structureHolder, this.randomState)) {
                if (placement.isStructureChunk(this.chunkGenerator, this.randomState, this.seed, chunkPos.x, chunkPos.z)) {
                    // Allowed - return null so regular check runs
                    return null;
                }
            }
            // Not allowed - early exit by returning a non-null value
            return StructureCheckResult.START_NOT_PRESENT;
        }
    }
}
