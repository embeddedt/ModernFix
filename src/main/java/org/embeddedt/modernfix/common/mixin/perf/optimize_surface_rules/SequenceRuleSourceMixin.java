package org.embeddedt.modernfix.common.mixin.perf.optimize_surface_rules;

import net.minecraft.world.level.levelgen.SurfaceRules;
import org.embeddedt.modernfix.world.gen.SurfaceRuleOptimizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"net/minecraft/world/level/levelgen/SurfaceRules$SequenceRuleSource"})
public class SequenceRuleSourceMixin {
    @Inject(method = "apply(Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;)Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;", at = @At("HEAD"), cancellable = true)
    private void optimizeApply(SurfaceRules.Context context, CallbackInfoReturnable<SurfaceRules.SurfaceRule> cir) {
        var optimized = SurfaceRuleOptimizer.optimizeSequenceRule((SurfaceRules.SequenceRuleSource)(Object) this, context);
        if (optimized != null) {
            cir.setReturnValue(optimized);
        }
    }
}
