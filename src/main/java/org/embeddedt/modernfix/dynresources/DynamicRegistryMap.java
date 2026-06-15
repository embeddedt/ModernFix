package org.embeddedt.modernfix.dynresources;

import com.google.common.collect.Collections2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A map that behaves like Guava's Maps.asMap but allows for additional entries to be written that override the backing
 * map's entries.
 */
public final class DynamicRegistryMap<K, V> implements Map<K, V> {
    private static final Object NULL_OVERRIDE = new Object();
    private final Set<K> originalKeys;
    private final Function<K, V> fallbackGetter;
    private final ConcurrentHashMap<K, Object> overrides;
    private final EntrySet entrySet;

    public DynamicRegistryMap(Set<K> originalKeys, Function<K, V> fallbackGetter) {
        this.originalKeys = originalKeys;
        this.fallbackGetter = fallbackGetter;
        this.overrides = new ConcurrentHashMap<>();
        this.entrySet = new EntrySet();
    }

    @Override
    public int size() {
        return originalKeys.size();
    }

    @Override
    public boolean isEmpty() {
        return originalKeys.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        if (o == null) {
            return false;
        }
        var override = overrides.get(o);
        if (override == NULL_OVERRIDE) {
            return false;
        }
        return override != null || originalKeys.contains(o);
    }

    @Override
    public boolean containsValue(Object o) {
        if (o == null || o == NULL_OVERRIDE) {
            return false;
        }
        return overrides.containsValue(o);
    }

    @Override
    public V get(Object o) {
        Object value = overrides.get(o);
        if (value == NULL_OVERRIDE) {
            return null;
        } else if (value != null) {
            return (V) value;
        } else {
            return fallbackGetter.apply((K)o);
        }
    }

    @Override
    public V getOrDefault(Object o, V defaultValue) {
        Object value = overrides.get(o);
        if (value == NULL_OVERRIDE) {
            return null;
        } else if (value != null) {
            return (V) value;
        } else {
            var fallback = fallbackGetter.apply((K)o);
            return fallback != null ? fallback : defaultValue;
        }
    }

    @Override
    public @Nullable V put(K k, V v) {
        if (v == null) {
            return remove(k);
        }
        overrides.put(k, v);
        return null;
    }

    @Override
    public V remove(Object o) {
        overrides.put((K)o, NULL_OVERRIDE);
        return null;
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> map) {
        map.forEach(this::put);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Set<K> keySet() {
        return Collections.unmodifiableSet(originalKeys);
    }

    @Override
    public @NotNull Collection<V> values() {
        return Collections2.transform(originalKeys, this::get);
    }

    @Override
    public @NotNull Set<Entry<K, V>> entrySet() {
        return this.entrySet;
    }

    private class ModelEntry implements Map.Entry<K, V> {
        private final K key;

        private ModelEntry(K key) {
            this.key = key;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return get(key);
        }

        @Override
        public V setValue(V value) {
            return put(key, value);
        }
    }

    private class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        @Override
        public Iterator<Entry<K, V>> iterator() {
            var iterator = originalKeys.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Entry<K, V> next() {
                    return new ModelEntry(iterator.next());
                }
            };
        }

        @Override
        public int size() {
            return DynamicRegistryMap.this.size();
        }
    }
}
