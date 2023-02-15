package org.embeddedt.modernfix.mixin.perf.faster_baking;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mixin(BlockModelShaper.class)
public abstract class BlockModelShapesMixin {
    @Shadow @Final private ModelManager modelManager;

    @Shadow @Final private Map<BlockState, BakedModel> modelByStateCache;

    /**
     * @author embeddedt
     * @reason parallelize cache rebuild
     */
    @Overwrite
    public void rebuildCache() {
        this.modelByStateCache.clear();
        ArrayList<CompletableFuture<Pair<BlockState, BakedModel>>> futures = new ArrayList<>();
        for(Block block : Registry.BLOCK) {
            block.getStateDefinition().getPossibleStates().forEach((state) -> {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    return Pair.of(state, this.modelManager.getModel(BlockModelShaper.stateToModelLocation(state)));
                }, Util.backgroundExecutor()));
            });
        }
        for(CompletableFuture<Pair<BlockState, BakedModel>> future : futures) {
            Pair<BlockState, BakedModel> pair = future.join();
            this.modelByStateCache.put(pair.getFirst(), pair.getSecond());
        }
    }
}
