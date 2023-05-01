package org.embeddedt.modernfix.blockstate;

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
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsValue(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public S get(Object o) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public S put(Map<Property<?>, Comparable<?>> propertyComparableMap, S s) {
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
        for(int i = 0; i < this.keys.length; i++) {
            this.keys[i] = null;
            this.values[i] = null;
        }
        this.usedSlots = 0;
    }

    @NotNull
    @Override
    public Set<Map<Property<?>, Comparable<?>>> keySet() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Collection<S> values() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Set<Entry<Map<Property<?>, Comparable<?>>, S>> entrySet() {
        return new Set<Entry<Map<Property<?>, Comparable<?>>, S>>() {
            @Override
            public int size() {
                return usedSlots;
            }

            @Override
            public boolean isEmpty() {
                return FakeStateMap.this.isEmpty();
            }

            @Override
            public boolean contains(Object o) {
                throw new UnsupportedOperationException();
            }

            @NotNull
            @Override
            public Iterator<Entry<Map<Property<?>, Comparable<?>>, S>> iterator() {
                return new Iterator<Entry<Map<Property<?>, Comparable<?>>, S>>() {
                    int currentIdx = 0;
                    @Override
                    public boolean hasNext() {
                        return currentIdx < usedSlots;
                    }

                    @Override
                    public Entry<Map<Property<?>, Comparable<?>>, S> next() {
                        if(currentIdx >= usedSlots)
                            throw new IndexOutOfBoundsException();
                        Entry<Map<Property<?>, Comparable<?>>, S> entry = new AbstractMap.SimpleImmutableEntry<>(keys[currentIdx], (S)values[currentIdx]);
                        currentIdx++;
                        return entry;
                    }
                };
            }

            @NotNull
            @Override
            public Object[] toArray() {
                throw new UnsupportedOperationException();
            }

            @NotNull
            @Override
            public <T> T[] toArray(@NotNull T[] ts) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean add(Entry<Map<Property<?>, Comparable<?>>, S> mapSEntry) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean remove(Object o) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean containsAll(@NotNull Collection<?> collection) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean addAll(@NotNull Collection<? extends Entry<Map<Property<?>, Comparable<?>>, S>> collection) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean retainAll(@NotNull Collection<?> collection) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean removeAll(@NotNull Collection<?> collection) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
