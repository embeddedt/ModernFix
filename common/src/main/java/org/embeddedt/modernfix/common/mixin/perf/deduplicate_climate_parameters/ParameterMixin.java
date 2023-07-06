package org.embeddedt.modernfix.common.mixin.perf.deduplicate_climate_parameters;

import net.minecraft.world.level.biome.Climate;
import org.embeddedt.modernfix.dedup.ClimateCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({ Climate.Parameter.class, Climate.ParameterPoint.class })
public class ParameterMixin {
    @Redirect(method = "*", at = @At(value = "NEW", target = "net/minecraft/world/level/biome/Climate$Parameter"), require = 0)
    private static Climate.Parameter internParameterStatic(long min, long max) {
        return ClimateCache.MFIX_INTERNER.intern(new Climate.Parameter(min, max));
    }
}
