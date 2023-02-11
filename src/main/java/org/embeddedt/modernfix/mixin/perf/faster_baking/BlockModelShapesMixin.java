package org.embeddedt.modernfix.mixin.perf.faster_baking;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ModelManager;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mixin(BlockModelShapes.class)
public abstract class BlockModelShapesMixin {
    @Shadow @Final private ModelManager modelManager;

    @Shadow @Final private Map<BlockState, IBakedModel> modelByStateCache;

    /**
     * @author embeddedt
     * @reason parallelize cache rebuild
     */
    @Overwrite
    public void rebuildCache() {
        this.modelByStateCache.clear();
        ArrayList<CompletableFuture<Pair<BlockState, IBakedModel>>> futures = new ArrayList<>();
        for(Block block : Registry.BLOCK) {
            block.getStateDefinition().getPossibleStates().forEach((state) -> {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    return Pair.of(state, this.modelManager.getModel(BlockModelShapes.stateToModelLocation(state)));
                }, Util.backgroundExecutor()));
            });
        }
        for(CompletableFuture<Pair<BlockState, IBakedModel>> future : futures) {
            Pair<BlockState, IBakedModel> pair = future.join();
            this.modelByStateCache.put(pair.getFirst(), pair.getSecond());
        }
    }
}
