package org.embeddedt.modernfix.registry;

import com.google.common.collect.BiMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DirectStorageBiMap<K, V> implements BiMap<K, V> {
    private final Function<V, K> keyGetter;
    private final BiConsumer<V, K> keySetter;
    private final Map<K, V> forwardMap;

    public DirectStorageBiMap(Function<V, K> keyGetter, BiConsumer<V, K> keySetter) {
        Objects.requireNonNull(keyGetter);
        Objects.requireNonNull(keySetter);
        this.keyGetter = keyGetter;
        this.keySetter = keySetter;
        this.forwardMap = new Object2ObjectOpenHashMap<>();
    }

    @Override
    public int size() {
        return this.forwardMap.size();
    }

    @Override
    public boolean isEmpty() {
        return this.forwardMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return this.forwardMap.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return o != null && keyGetter.apply((V)o) != null;
    }

    @Override
    public V get(Object o) {
        return this.forwardMap.get(o);
    }

    @Override
    public V put(K key, V value) {
        if(this.forwardMap.containsKey(key) || (value != null && keyGetter.apply(value) != null))
            throw new IllegalArgumentException("Already have mapping for " + key);
        return forcePut(key, value);
    }

    @Override
    public V remove(Object o) {
        return put((K)o, null);
    }

    @Override
    public V forcePut(K key, V value) {
        V previousValue = this.forwardMap.put(key, value);
        if(previousValue != null)
            keySetter.accept(previousValue, null);
        if(value != null)
            keySetter.accept(value, key);
        return previousValue;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        map.forEach(this::put);
    }

    @Override
    public void clear() {
        for(V value : this.forwardMap.values()) {
            if(value != null)
                keySetter.accept(value, null);
        }
        this.forwardMap.clear();
    }

    @NotNull
    @Override
    public Set<K> keySet() {
        return this.forwardMap.keySet();
    }

    @Override
    public Set<V> values() {
        return new HashSet<>(this.forwardMap.values());
    }

    @NotNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        return this.forwardMap.entrySet();
    }

    @Override
    public BiMap<V, K> inverse() {
        return new Reverse();
    }

    class Reverse implements BiMap<V, K> {
        @Override
        public int size() {
            return DirectStorageBiMap.this.size();
        }

        @Override
        public boolean isEmpty() {
            return DirectStorageBiMap.this.isEmpty();
        }

        @Override
        public boolean containsKey(Object o) {
            return DirectStorageBiMap.this.containsValue(o);
        }

        @Override
        public boolean containsValue(Object o) {
            return DirectStorageBiMap.this.containsKey(o);
        }

        @Override
        public K get(Object o) {
            return o == null ? null : keyGetter.apply((V)o);
        }

        @Override
        public K put(V key, K value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public K remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public K forcePut(V key, K value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(Map<? extends V, ? extends K> map) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public Set<V> keySet() {
            return DirectStorageBiMap.this.values();
        }

        @Override
        public Set<K> values() {
            return DirectStorageBiMap.this.keySet();
        }

        @NotNull
        @Override
        public Set<Entry<V, K>> entrySet() {
            return DirectStorageBiMap.this.entrySet().stream()
                    .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getValue(), entry.getKey()))
                    .collect(Collectors.toSet());
        }

        @Override
        public BiMap<K, V> inverse() {
            return DirectStorageBiMap.this;
        }
    }
}
