package org.embeddedt.modernfix.mixin.perf.reduce_blockstate_cache_rebuilds;

import net.minecraft.block.BlockState;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.blockstate.BlockStateCacheHandler;
import org.embeddedt.modernfix.duck.IBlockState;
import org.embeddedt.modernfix.util.BakeReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = { "net/minecraftforge/registries/GameData$BlockCallbacks" })
public class BlockCallbacksMixin {
    @Redirect(method = "onBake", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;initCache()V"))
    private void skipCacheIfAllowed(BlockState state) {
        BlockStateCacheHandler.handleStateCache(state);
    }
}
