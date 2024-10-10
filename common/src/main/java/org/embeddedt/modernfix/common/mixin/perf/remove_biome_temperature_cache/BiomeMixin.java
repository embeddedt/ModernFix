package org.embeddedt.modernfix.common.mixin.perf.remove_biome_temperature_cache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/* Idea from Lithium for 1.19.3 */
@Mixin(Biome.class)
public abstract class BiomeMixin {
    @Shadow protected abstract float getHeightAdjustedTemperature(BlockPos pos, int i);

    /**
     * @author 2No2Name
     * @reason Remove caching, it's not effective
     * @param pos
     * @return
     */
    @Overwrite
    private float getTemperature(BlockPos pos, int i) {
        return this.getHeightAdjustedTemperature(pos, i);
    }
}
