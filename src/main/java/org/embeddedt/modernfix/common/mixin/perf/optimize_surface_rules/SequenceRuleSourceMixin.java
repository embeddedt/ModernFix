package org.embeddedt.modernfix.common.mixin.perf.optimize_surface_rules;

import net.minecraft.world.level.levelgen.SurfaceRules;
import org.embeddedt.modernfix.world.gen.SurfaceRuleOptimizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"net/minecraft/world/level/levelgen/SurfaceRules$SequenceRuleSource"})
public class SequenceRuleSourceMixin {
    @Unique
    private transient SurfaceRules.RuleSource mfix$optimizedSource;

    @Inject(method = "apply(Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;)Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;", at = @At("HEAD"), cancellable = true)
    private void optimizeApply(SurfaceRules.Context context, CallbackInfoReturnable<SurfaceRules.SurfaceRule> cir) {
        var optimizedSource = mfix$optimizedSource;
        if (optimizedSource == null) {
            mfix$optimizedSource = optimizedSource = SurfaceRuleOptimizer.optimizeSequenceRuleSource((SurfaceRules.SequenceRuleSource)(Object) this);
        }
        // Must check for it not being ourselves, to avoid infinite reentrance
        if (optimizedSource != (Object)this) {
            cir.setReturnValue(optimizedSource.apply(context));
        }
    }
}
