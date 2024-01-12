package org.embeddedt.modernfix.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Map;
import java.util.function.Function;

public class DynamicInt2ObjectMap<V> extends DynamicMap<Integer, V> implements Int2ObjectMap<V> {
    public DynamicInt2ObjectMap(Function<Integer, V> function) {
        super(function);
    }

    @Override
    public IntSet keySet() {
        return IntSets.EMPTY_SET;
    }

    @Override
    public ObjectCollection<V> values() {
        return ObjectLists.emptyList();
    }

    @Override
    public ObjectSet<Map.Entry<Integer, V>> entrySet() {
        return ObjectSets.emptySet();
    }

    @Override
    public void defaultReturnValue(V rv) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V defaultReturnValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectSet<Int2ObjectMap.Entry<V>> int2ObjectEntrySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public V getOrDefault(int key, V defaultValue) {
        V value = get(key);
        return value == null ? defaultValue :  value;
    }

    @Override
    public V get(int key) {
        return function.apply(key);
    }

    @Override
    public boolean containsKey(int key) {
        return true;
    }
}
