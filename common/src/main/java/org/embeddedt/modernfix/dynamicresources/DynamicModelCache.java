package org.embeddedt.modernfix.dynamicresources;

import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import net.minecraft.client.resources.model.BakedModel;

import java.util.concurrent.locks.StampedLock;

/**
 * The Mojang Triple-based baked cache system is too slow to be hitting on every model retrieval, so
 * we need a fast, concurrency-safe wrapper on top.
 */
public class DynamicModelCache<K> {
    private final Reference2ReferenceLinkedOpenHashMap<K, BakedModel> cache = new Reference2ReferenceLinkedOpenHashMap<>();
    private final StampedLock lock = new StampedLock();
    private final Function<K, BakedModel> modelRetriever;
    private final boolean allowNulls;

    public DynamicModelCache(Function<K, BakedModel> modelRetriever, boolean allowNulls) {
        this.modelRetriever = modelRetriever;
        this.allowNulls = allowNulls;
    }

    public void clear() {
        long stamp = lock.writeLock();
        try {
            cache.clear();
        } finally {
            lock.unlock(stamp);
        }
    }

    private boolean needToPopulate(K state) {
        long stamp = lock.readLock();
        try {
            return !cache.containsKey(state);
        } finally {
            lock.unlock(stamp);
        }
    }

    private BakedModel getModelFromCache(K state) {
        long stamp = lock.readLock();
        try {
            return cache.get(state);
        } finally {
            lock.unlock(stamp);
        }
    }

    private BakedModel cacheModel(K state) {
        BakedModel model = modelRetriever.apply(state);

        // Lock and modify our local, faster cache
        long stamp = lock.writeLock();

        try {
            cache.putAndMoveToFirst(state, model);
            // TODO: choose less arbitrary number
            if(cache.size() >= 1000) {
                cache.removeLast();
            }
        } finally {
            lock.unlock(stamp);
        }

        return model;
    }

    public BakedModel get(K key) {
        BakedModel model = getModelFromCache(key);

        if(model == null && (!allowNulls || needToPopulate(key))) {
            model = cacheModel(key);
        }

        return model;
    }
}
