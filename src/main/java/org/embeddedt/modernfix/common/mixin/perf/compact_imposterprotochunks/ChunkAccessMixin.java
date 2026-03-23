package org.embeddedt.modernfix.common.mixin.perf.compact_imposterprotochunks;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(ChunkAccess.class)
public class ChunkAccessMixin {
    @Shadow
    @Final
    @Mutable
    protected LevelChunkSection[] sections;

    @Shadow
    protected ChunkSkyLightSources skyLightSources;
}
