package org.embeddedt.modernfix.blockstate;

import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Fake "map" implementation used to hold the states.
 *
 * Intentionally throws on methods that would be inefficient so that we know
 * if an incompatible mod is present.
 */
public class FakeStateMap<S> implements Map<Map<Property<?>, Comparable<?>>, S> {
    private final Map<Property<?>, Comparable<?>>[] keys;
    private Map<Map<Property<?>, Comparable<?>>, S> fastLookup;
    private final Object[] values;
    private int usedSlots;
    public FakeStateMap(int numStates) {
        this.keys = new Map[numStates];
        this.values = new Object[numStates];
        this.usedSlots = 0;
    }

    @Override
    public int size() {
        return usedSlots;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object o) {
        return getFastLookup().containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return getFastLookup().containsValue(o);
    }

    @SuppressWarnings("unchecked")
    private Map<Map<Property<?>, Comparable<?>>, S> getFastLookup() {
        if(fastLookup == null) {
            var map = new Object2ObjectOpenHashMap<Map<Property<?>, Comparable<?>>, S>(usedSlots);
            Map<Property<?>, Comparable<?>>[] keys = this.keys;
            Object[] values = this.values;
            for(int i = 0; i < usedSlots; i++) {
                map.put(keys[i], (S)values[i]);
            }
            fastLookup = map;
        }
        return fastLookup;
    }

    @Override
    public S get(Object o) {
        return getFastLookup().get(o);
    }

    @Nullable
    @Override
    public S put(Map<Property<?>, Comparable<?>> propertyComparableMap, S s) {
        if(fastLookup != null) {
            throw new IllegalStateException("Cannot populate map after fast lookup is built");
        }
        keys[usedSlots] = propertyComparableMap;
        values[usedSlots] = s;
        usedSlots++;
        return null;
    }

    @Override
    public S remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(@NotNull Map<? extends Map<Property<?>, Comparable<?>>, ? extends S> map) {
        for(Entry<? extends Map<Property<?>, Comparable<?>>, ? extends S> entry : map.entrySet()) {
            this.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        for(int i = 0; i < usedSlots; i++) {
            this.keys[i] = null;
            this.values[i] = null;
        }
        this.usedSlots = 0;
    }

    private <T> List<T> asList(T... array) {
        var list = Arrays.asList(array);
        if(usedSlots < array.length) {
            list = list.subList(0, usedSlots);
        }
        return list;
    }

    @NotNull
    @Override
    public Set<Map<Property<?>, Comparable<?>>> keySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Map<Property<?>, Comparable<?>>> iterator() {
                return keys.length == usedSlots ? Iterators.forArray(keys) : asList(keys).iterator();
            }

            @Override
            public int size() {
                return usedSlots;
            }
        };
    }

    @NotNull
    @Override
    public Collection<S> values() {
        return (Collection<S>)asList(values);
    }

    @NotNull
    @Override
    public Set<Entry<Map<Property<?>, Comparable<?>>, S>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return usedSlots;
            }

            @NotNull
            @Override
            public Iterator<Entry<Map<Property<?>, Comparable<?>>, S>> iterator() {
                return new Iterator<>() {
                    int currentIdx = 0;

                    @Override
                    public boolean hasNext() {
                        return currentIdx < usedSlots;
                    }

                    @Override
                    public Entry<Map<Property<?>, Comparable<?>>, S> next() {
                        if (currentIdx >= usedSlots)
                            throw new IndexOutOfBoundsException();
                        Entry<Map<Property<?>, Comparable<?>>, S> entry = new AbstractMap.SimpleImmutableEntry<>(keys[currentIdx], (S) values[currentIdx]);
                        currentIdx++;
                        return entry;
                    }
                };
            }
        };
    }
}
