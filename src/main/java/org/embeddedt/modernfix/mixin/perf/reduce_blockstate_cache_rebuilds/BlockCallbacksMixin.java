package org.embeddedt.modernfix.mixin.perf.reduce_blockstate_cache_rebuilds;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.IForgeRegistryInternal;
import net.minecraftforge.registries.RegistryManager;
import org.embeddedt.modernfix.blockstate.BlockStateCacheHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = { "net/minecraftforge/registries/GameData$BlockCallbacks" })
public class BlockCallbacksMixin {
    @Redirect(method = "onBake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;initCache()V"))
    private void skipCache(BlockState instance) {

    }

    @Inject(method = "onBake", at = @At(value = "TAIL"), remap = false)
    private void computeCaches(IForgeRegistryInternal<Block> owner, RegistryManager stage, CallbackInfo ci) {
        BlockStateCacheHandler.rebuildParallel(false);
    }
}
