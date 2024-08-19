package org.embeddedt.modernfix.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

public class DynamicOverridableMap<K, V> extends DynamicMap<K, V> {
    private final Map<K, V> overrideMap;

    public DynamicOverridableMap(Class<K> keyClass, Function<K, V> function) {
        super(keyClass, function);
        overrideMap = new Object2ObjectOpenHashMap<>();
    }

    @Override
    public @Nullable V put(K k, V v) {
        if(v == null)
            throw new IllegalArgumentException();
        overrideMap.put(k, v);
        return null;
    }

    @Override
    public V get(Object o) {
        V val = overrideMap.get(o);
        if(val != null)
            return val;
        return super.get(o);
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> map) {
        for(V val : map.values()) {
            if(val == null)
                throw new IllegalArgumentException();
        }
        overrideMap.putAll(map);
    }
}
