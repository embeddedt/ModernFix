package org.embeddedt.modernfix.forge.mixin.perf.reduce_blockstate_cache_rebuilds;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.embeddedt.modernfix.blockstate.BlockStateCacheHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = { "net/neoforged/neoforge/registries/NeoForgeRegistryCallbacks$BlockCallbacks" })
public class BlockCallbacksMixin {
    @Redirect(method = "onBake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;"))
    private ImmutableList<BlockState> skipCache(StateDefinition<Block, BlockState> definition) {
        // prevent initCache from being called on these blockstates
        return ImmutableList.of();
    }

    @Inject(method = "onBake", at = @At(value = "TAIL"), remap = false)
    private void computeCaches(Registry<Block> registry, CallbackInfo ci) {
        BlockStateCacheHandler.rebuildParallel(false);
    }
}
