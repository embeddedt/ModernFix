package org.embeddedt.modernfix.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A map wrapper that throws if the map is ever accessed on the wrong thread.
 */
public class ConcurrencySanitizingMap<K, V> implements Map<K, V> {
    private final Map<K, V> map;
    private final Thread owner;

    public ConcurrencySanitizingMap(Map<K, V> map, Thread owner) {
        this.map = map;
        this.owner = owner;
    }

    private void checkThread() {
        var current = Thread.currentThread();
        if (current != owner) {
            throw new IllegalStateException("Map is being accessed on thread " + current + " while owned by " + owner);
        }
    }

    @Override
    public int size() {
        checkThread();
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        checkThread();
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        checkThread();
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        checkThread();
        return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        checkThread();
        return map.get(key);
    }

    @Override
    public @Nullable V put(K key, V value) {
        checkThread();
        return map.put(key, value);
    }

    @Override
    public V remove(Object key) {
        checkThread();
        return map.remove(key);
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> m) {
        checkThread();
        map.putAll(m);
    }

    @Override
    public void clear() {
        checkThread();
        map.clear();
    }

    @Override
    public @NotNull Set<K> keySet() {
        checkThread();
        return map.keySet();
    }

    @Override
    public @NotNull Collection<V> values() {
        checkThread();
        return map.values();
    }

    @Override
    public @NotNull Set<Entry<K, V>> entrySet() {
        checkThread();
        return map.entrySet();
    }
}
