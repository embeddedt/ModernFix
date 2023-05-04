package org.embeddedt.modernfix.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class DynamicMap<K, V> implements Map<K, V> {
    protected final Function<K, V> function;

    public DynamicMap(Function<K, V> function) {
        this.function = function;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(Object o) {
        return true;
    }

    @Override
    public boolean containsValue(Object o) {
        return true;
    }

    @Override
    public V get(Object o) {
        return function.apply((K)o);
    }

    @Nullable
    @Override
    public V put(K k, V v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Set<K> keySet() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }
}
