package org.embeddedt.modernfix.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;

import java.util.Map;
import java.util.Set;

public class LRUMap<K, V> extends Object2ObjectLinkedOpenHashMap<K, V> {
    private Set<K> permanentEntries = Set.of();
    public LRUMap(Map<K, V> map) {
        super(map);
    }

    @Override
    public V put(K k, V v) {
        return putAndMoveToLast(k, v);
    }

    @Override
    public V get(Object k) {
        return getAndMoveToLast((K)k);
    }

    public void setPermanentEntries(Set<K> permanentEntries) {
        this.permanentEntries = permanentEntries;
    }

    public void dropEntriesToMeetSize(int size) {
        // Increase allowed size quota to include permanent entries
        size += permanentEntries.size();
        int prevSize = size();
        if(size() > size) {
            var iterator = entrySet().iterator();
            while(size() > size && iterator.hasNext()) {
                var entry = iterator.next();
                if(!this.permanentEntries.contains(entry.getKey())) {
                    iterator.remove();
                }
            }
            if(ModernFixPlatformHooks.INSTANCE.isDevEnv()) {
                ModernFix.LOGGER.warn("Trimmed map from {} to {} entries", prevSize, size);
            }
        }
    }
}
