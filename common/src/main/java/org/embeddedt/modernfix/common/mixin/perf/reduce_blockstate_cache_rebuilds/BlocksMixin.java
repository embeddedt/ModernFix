package org.embeddedt.modernfix.common.mixin.perf.reduce_blockstate_cache_rebuilds;

import net.minecraft.world.level.block.Blocks;
import org.embeddedt.modernfix.blockstate.BlockStateCacheHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Blocks.class)
public class BlocksMixin {
    @Inject(method = "rebuildCache", at = @At("HEAD"), cancellable = true)
    private static void rebuildParallel(CallbackInfo ci) {
        ci.cancel();
        BlockStateCacheHandler.rebuildParallel(true);
    }
}
