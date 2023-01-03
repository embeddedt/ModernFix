package org.embeddedt.modernfix.mixin;

import net.minecraft.block.BlockState;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.IBlockState;
import org.embeddedt.modernfix.util.BakeReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = { "net/minecraftforge/registries/GameData$BlockCallbacks" })
public class BlockCallbacksMixin {
    @Redirect(method = "onBake", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;cacheState()V"))
    private void skipCacheIfAllowed(BlockState state) {
        if(BakeReason.currentBakeReason == BakeReason.FREEZE
                || BakeReason.currentBakeReason == BakeReason.REMOTE_SNAPSHOT_INJECT
                || (BakeReason.currentBakeReason == BakeReason.LOCAL_SNAPSHOT_INJECT && ModernFix.runningFirstInjection)) {
            ((IBlockState)state).clearCache();
        } else {
            state.cacheState();
        }
    }
}
