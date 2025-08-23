package org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation;

import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = {
        "net/minecraft/world/level/levelgen/SurfaceRules$BiomeConditionSource$1BiomeCondition",
        "net/minecraft/world/level/levelgen/SurfaceRules$StoneDepthCheck$1StoneDepthCondition",
        "net/minecraft/world/level/levelgen/SurfaceRules$VerticalGradientConditionSource$1VerticalGradientCondition",
        "net/minecraft/world/level/levelgen/SurfaceRules$WaterConditionSource$1WaterCondition",
        "net/minecraft/world/level/levelgen/SurfaceRules$YConditionSource$1YCondition",
})
public abstract class SurfaceRulesMixin extends SurfaceRules.LazyCondition {
    protected SurfaceRulesMixin(SurfaceRules.Context context) {
        super(context);
    }

    /**
     * @author VoidsongDragonfly
     * @reason Replacing Vanilla's use of {@link SurfaceRules.LazyYCondition LazyYCondition} that causes performance
     * detriments due to unused caching behavior. The `lastUpdateY` field is updated every time the block position
     * changes (making the cache useful only within a single block), and the targeted condition objects are not interned
     * (meaning there is no caching happening anyway, as each instance uses its own cache).
     *
     */
    @Override
    public boolean test() {
        return compute();
    }
}
