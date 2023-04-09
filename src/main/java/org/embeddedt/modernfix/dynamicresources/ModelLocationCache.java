package org.embeddedt.modernfix.dynamicresources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ModelLocationCache {
    private static final LoadingCache<BlockState, ModelResourceLocation> locationCache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .build(new CacheLoader<BlockState, ModelResourceLocation>() {
                @Override
                public ModelResourceLocation load(BlockState key) throws Exception {
                    return BlockModelShaper.stateToModelLocation(key);
                }
            });

    public static ModelResourceLocation get(BlockState state) {
        try {
            return locationCache.get(state);
        } catch(ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
