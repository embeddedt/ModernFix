package org.embeddedt.modernfix.util;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * Replacement backing map for CompoundTags. Uses an array map for tags with 4 or less entries,
 * and a hash map for larger tags.
 */
public class CanonizingStringMap<T> implements Map<String, T> {
    private Object2ObjectMap<String, T> backingMap;

    private static final int GROWTH_THRESHOLD = 4;
    private static final Interner<String> KEY_INTERNER = Interners.newStrongInterner();

    public CanonizingStringMap() {
        this(new Object2ObjectArrayMap<>());
    }

    protected CanonizingStringMap(Object2ObjectMap<String, T> newMap) {
        this.backingMap = newMap;
    }

    @Override
    public int size() {
        return backingMap.size();
    }

    @Override
    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return backingMap.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return backingMap.containsValue(o);
    }

    @Override
    public T get(Object o) {
        return backingMap.get(o);
    }

    @Nullable
    @Override
    public T put(String s, T t) {
        if(backingMap.size() >= GROWTH_THRESHOLD && !(backingMap instanceof Object2ObjectOpenHashMap) && !backingMap.containsKey(s)) {
            // map will grow to GROWTH_THRESHOLD + 1 entries, change to hashmap
            backingMap = new Object2ObjectOpenHashMap<>(backingMap);
        }
        s = KEY_INTERNER.intern(s);
        return backingMap.put(s, t);
    }

    @Override
    public T remove(Object o) {
        T value = backingMap.remove(o);
        // need to shrink to be consistent with new maps
        if(backingMap.size() <= GROWTH_THRESHOLD && backingMap instanceof Object2ObjectOpenHashMap) {
            backingMap = new Object2ObjectArrayMap<>(backingMap);
        }
        return value;
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ? extends T> map) {
        if(map.size() == 0)
            return;
        // grow early if we know there are enough non-overlapping keys
        if((map.size() - backingMap.size()) > GROWTH_THRESHOLD && !(backingMap instanceof Object2ObjectOpenHashMap)) {
            backingMap = new Object2ObjectOpenHashMap<>(backingMap);
        }
        map.forEach((String key, T val) -> {
            key = KEY_INTERNER.intern(key);
            backingMap.put(key, val);
        });
        // if it's still an array, and now too big, grow it
        if(backingMap.size() > GROWTH_THRESHOLD && !(backingMap instanceof Object2ObjectOpenHashMap)) {
            backingMap = new Object2ObjectOpenHashMap<>(backingMap);
        }
    }

    @Override
    public void clear() {
        if(!(this.backingMap instanceof Object2ObjectArrayMap))
            this.backingMap = new Object2ObjectArrayMap<>();
        else
            this.backingMap.clear();
    }

    @NotNull
    @Override
    public Set<String> keySet() {
        // has to be modifiable because mods (cough, Tinkers) use it to clear the tag
        return this.backingMap.keySet();
    }

    @NotNull
    @Override
    public Collection<T> values() {
        return Collections.unmodifiableCollection(this.backingMap.values());
    }

    @NotNull
    @Override
    public Set<Entry<String, T>> entrySet() {
        return Collections.unmodifiableSet(this.backingMap.entrySet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CanonizingStringMap<?> that = (CanonizingStringMap<?>)o;
        if(that.backingMap.size() != backingMap.size())
            return false;
        return backingMap.object2ObjectEntrySet().containsAll(that.backingMap.object2ObjectEntrySet());
    }

    /**
     * We deliberately use a hashcode that will be consistent regardless of underlying map type.
     */
    @Override
    public int hashCode() {
        final ObjectIterator<Object2ObjectMap.Entry<String, T>> i = Object2ObjectMaps.fastIterator(backingMap);
        int h = 0, n = backingMap.size();
        while (n-- != 0)
            h += i.next().hashCode();
        return h;
    }

    public static <T> CanonizingStringMap<T> deepCopy(CanonizingStringMap<T> inputMap, Function<T, T> deepCopier) {
        Objects.requireNonNull(deepCopier);
        Object2ObjectMap<String, T> copiedBackingMap;
        int size = inputMap.backingMap.size();
        if(size > GROWTH_THRESHOLD) {
            copiedBackingMap = new Object2ObjectOpenHashMap<>(size);
        } else
            copiedBackingMap = new Object2ObjectArrayMap<>(size);
        inputMap.backingMap.object2ObjectEntrySet().forEach(entry -> {
            if(entry.getKey() != null && entry.getValue() != null)
                copiedBackingMap.put(entry.getKey(), deepCopier.apply(entry.getValue()));
        });
        return new CanonizingStringMap<>(copiedBackingMap);
    }
}
