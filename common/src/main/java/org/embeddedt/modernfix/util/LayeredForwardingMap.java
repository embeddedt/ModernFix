package org.embeddedt.modernfix.util;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Simple forwarding map implementation that allows layering multiple maps together, with the last layer being
 * mutable.
 */
public class LayeredForwardingMap<K, V> implements Map<K, V> {
    private final Map<K, V>[] layers;

    public LayeredForwardingMap(Map<K, V>[] layers) {
        if(layers.length < 1)
            throw new IllegalArgumentException();
        for(Map<K, V> layer : layers) {
            if(layer == null)
                throw new IllegalArgumentException();
        }
        this.layers = layers;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        for(Map<K, V> map : layers) {
            if(!map.isEmpty())
                return false;
        }
        return true;
    }

    @Override
    public boolean containsKey(Object key) {
        for(Map<K, V> map : layers) {
            if(map.containsKey(key))
                return true;
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        for(Map<K, V> map : layers) {
            if(map.containsValue(value))
                return true;
        }
        return false;
    }

    @Override
    public V get(Object key) {
        for(Map<K, V> map : layers) {
            V value = map.get(key);
            if(value != null)
                return value;
        }
        return null;
    }

    @Nullable
    @Override
    public V put(K key, V value) {
        if(value == null)
            throw new IllegalArgumentException();
        return layers[layers.length - 1].put(key, value);
    }

    @Override
    public V remove(Object key) {
        for(Map<K, V> map : layers) {
            map.remove(key);
        }
        return null;
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> m) {
        for(V value : m.values()) {
            if(value == null)
                throw new IllegalArgumentException();
        }
        layers[layers.length - 1].putAll(m);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Set<K> keySet() {
        Set<K> keys = new ObjectOpenHashSet<>();
        for(Map<K, V> map : layers) {
            keys.addAll(map.keySet());
        }
        return Collections.unmodifiableSet(keys);
    }

    @NotNull
    @Override
    public Collection<V> values() {
        Set<K> keys = keySet();
        List<V> vals = new ArrayList<>();
        for(K key : keys) {
            vals.add(get(key));
        }
        return vals;
    }

    @NotNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }
}
