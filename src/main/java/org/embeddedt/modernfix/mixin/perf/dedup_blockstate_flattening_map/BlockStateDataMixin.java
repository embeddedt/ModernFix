package org.embeddedt.modernfix.mixin.perf.dedup_blockstate_flattening_map;

import net.minecraft.util.datafix.fixes.BlockStateData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockStateData.class)
public class BlockStateDataMixin {
    @Inject(method = {"register", "finalizeMaps"}, at = @At("HEAD"), cancellable = true)
    private static void noFlattening(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = {"upgradeBlockStateTag", "upgradeBlock(I)Ljava/lang/String;", "upgradeBlock(Ljava/lang/String;)Ljava/lang/String;", "getTag"}, at = @At("HEAD"), require = 4)
    private static void preventCorruption(CallbackInfoReturnable<?> cir) {
        throw new UnsupportedOperationException("Performing the Flattening is currently disabled in the ModernFix config.");
    }
}
