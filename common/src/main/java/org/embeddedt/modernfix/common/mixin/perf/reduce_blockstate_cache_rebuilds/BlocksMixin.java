package org.embeddedt.modernfix.common.mixin.perf.reduce_blockstate_cache_rebuilds;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.modernfix.blockstate.BlockStateCacheHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(value = Blocks.class, priority = 1100)
public class BlocksMixin {
    @ModifyArg(method = "rebuildCache", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/IdMapper;forEach(Ljava/util/function/Consumer;)V"), index = 0)
    private static Consumer getEmptyConsumer(Consumer original) {
        BlockStateCacheHandler.rebuildParallel(true);
        return o -> {};
    }

    // require = 0 due to Forge removing the BLOCK_STATE_REGISTRY init here
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;initCache()V"), require = 0)
    private static void skipCacheInit(BlockState state) {
        /* no-op, our dynamic logic handles everything properly (including the 1.19.4+ fluidState, etc. caching) */
    }
}
