package org.embeddedt.modernfix.mixin.bugfix.tf_cme_on_load;

import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import twilightforest.TwilightForestMod;
import twilightforest.worldgen.biomes.BiomeKeys;

@Mixin(TwilightForestMod.class)
@RequiresMod("twilightforest")
public class TwilightForestModMixin {
    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Ltwilightforest/worldgen/biomes/BiomeKeys;addBiomeTypes()V"), remap = false)
    private static void avoidBiomeTypes() {

    }

    @Inject(method = "lambda$init$1", at = @At(value = "INVOKE", target = "Ltwilightforest/block/TFBlocks;tfCompostables()V", ordinal = 0), remap = false)
    private static void doBiomeTypes(CallbackInfo ci) {
        BiomeKeys.addBiomeTypes();
    }
}
