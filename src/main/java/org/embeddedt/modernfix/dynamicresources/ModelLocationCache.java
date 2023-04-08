package org.embeddedt.modernfix.dynamicresources;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ModelLocationCache {
    private static final Map<BlockState, ModelResourceLocation> locationCache = new Object2ObjectOpenHashMap<>();
    public static void rebuildLocationCache() {
        locationCache.clear();
        ArrayList<CompletableFuture<Pair<BlockState, ModelResourceLocation>>> futures = new ArrayList<>();
        for(Block block : Registry.BLOCK) {
            block.getStateDefinition().getPossibleStates().forEach((state) -> {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    return Pair.of(state, BlockModelShaper.stateToModelLocation(state));
                }, Util.backgroundExecutor()));
            });
        }
        for(CompletableFuture<Pair<BlockState, ModelResourceLocation>> future : futures) {
            Pair<BlockState, ModelResourceLocation> pair = future.join();
            locationCache.put(pair.getFirst(), pair.getSecond());
        }
    }

    public static ModelResourceLocation get(BlockState state) {
        return locationCache.get(state);
    }
}
