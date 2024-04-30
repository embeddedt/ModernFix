package org.embeddedt.modernfix.common.mixin.perf.faster_structure_location;

import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccessor {
    @Invoker("getPlacementsForStructure")
    List<StructurePlacement> invokeGetPlacementsForStructure(Holder<Structure> structure, RandomState random);
}
