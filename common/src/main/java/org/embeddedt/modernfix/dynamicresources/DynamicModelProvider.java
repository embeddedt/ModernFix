package org.embeddedt.modernfix.dynamicresources;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Handles loading models dynamically, rather than at startup time.
 */
public class DynamicModelProvider {
    private final Map<ResourceLocation, UnbakedModel> internalModels;
    private final Cache<ResourceLocation, Optional<UnbakedModel>> loadedModels =
            CacheBuilder.newBuilder()
                    .expireAfterAccess(3, TimeUnit.MINUTES)
                    .maximumSize(1000)
                    .concurrencyLevel(8)
                    .softValues()
                    .build();

    public DynamicModelProvider(Map<ResourceLocation, UnbakedModel> initialModels) {
        this.internalModels = initialModels;
    }

    public UnbakedModel getModel(ResourceLocation location) {
        try {
            return loadedModels.get(location, () -> Optional.ofNullable(loadModel(location))).orElse(null);
        } catch(ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private UnbakedModel loadModel(ResourceLocation location) {
        return null; /* TODO :) */
    }
}
