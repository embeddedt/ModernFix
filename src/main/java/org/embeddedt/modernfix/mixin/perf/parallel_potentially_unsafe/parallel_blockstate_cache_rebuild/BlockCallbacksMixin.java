package org.embeddedt.modernfix.mixin.perf.parallel_potentially_unsafe.parallel_blockstate_cache_rebuild;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.ObjectIntIdentityMap;
import net.minecraft.world.gen.DebugChunkGenerator;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.IForgeRegistryInternal;
import net.minecraftforge.registries.RegistryManager;
import org.embeddedt.modernfix.blockstate.BlockStateCacheHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = { "net/minecraftforge/registries/GameData$BlockCallbacks" })
public class BlockCallbacksMixin {
    @Inject(method = "onBake", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;hasNext()Z"), cancellable = true, remap = false)
    private void computeCacheParallel(IForgeRegistryInternal<Block> owner, RegistryManager stage, CallbackInfo ci) {
        ci.cancel();
        ObjectIntIdentityMap<BlockState> blockstateMap = GameData.getBlockStateIDMap();
        for (Block block : owner) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                blockstateMap.add(state);
            }
        }
        BlockStateCacheHandler.rebuildParallel(false);
        DebugChunkGenerator.initValidStates();
    }
}
