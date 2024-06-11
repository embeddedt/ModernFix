package org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(value = NoiseChunk.class, priority = 100)
public abstract class NoiseChunkMixin {
    @Shadow @Final @Mutable
    private Map<DensityFunction, DensityFunction> wrapped = new Object2ObjectOpenHashMap<>();

    @Shadow protected abstract DensityFunction wrapNew(DensityFunction densityFunction);

    /**
     * @author embeddedt
     * @reason Avoid lambda allocation
     */
    @Overwrite
    protected DensityFunction wrap(DensityFunction unwrapped) {
        DensityFunction func = this.wrapped.get(unwrapped);
        if (func == null) {
            func = this.wrapNew(unwrapped);
            this.wrapped.put(unwrapped, func);
        }
        return func;
    }
}
