package org.embeddedt.modernfix.common.mixin.perf.faster_structure_location;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import org.embeddedt.modernfix.duck.IStructureCheck;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StructureCheck.class)
public class StructureCheckMixin implements IStructureCheck {
    @Shadow @Final private Registry<Structure> structureConfigs;

    private ChunkGeneratorStructureState mfix$structureState;

    @Override
    public void mfix$setStructureState(ChunkGeneratorStructureState state) {
        mfix$structureState = state;
    }

    /**
     * @author embeddedt (inspired by 24w04a and Bytzo's comment on https://bugs.mojang.com/browse/MC-249136)
     * @reason Avoid running the canCreateStructure method (which can be expensive) if the structure placement already
     * forbids placing the structure in this chunk.
     */
    @ModifyExpressionValue(method = "checkStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/StructureCheck;tryLoadFromStorage(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/levelgen/structure/Structure;ZJ)Lnet/minecraft/world/level/levelgen/structure/StructureCheckResult;"))
    private StructureCheckResult mfix$checkForValidPosition(StructureCheckResult storageResult, ChunkPos chunkPos, Structure structure, boolean skipKnownStructures) {
        if (storageResult != null) {
            return storageResult;
        } else if(mfix$structureState != null) {
            // Check if any of the placements allow for this structure to be in this chunk
            var structureHolder = this.structureConfigs.wrapAsHolder(structure);
            for (var placement : mfix$structureState.getPlacementsForStructure(structureHolder)) {
                if (placement.isStructureChunk(mfix$structureState, chunkPos.x, chunkPos.z)) {
                    // Allowed - return null so regular check runs
                    return null;
                }
            }
            // Not allowed - early exit by returning a non-null value
            return StructureCheckResult.START_NOT_PRESENT;
        } else {
            return null;
        }
    }
}
