package org.fury_phoenix.mixinAp.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static java.util.Map.Entry;

/**
 * Type-safe heterogenous map of accessors
 * @author Fury_Phoenix
 * @reason Type-safety since K, V of Map are non-identical
 * @param <SuperType> The supertype of desired types.
 * This is useful in cases such as <A extends Annotation>.
 */
public class TypedAccessorMap<SuperType> {
    private final Map<Class<? extends SuperType>, Function<Object, ?>> typedAccessors = new HashMap<>();

    public <T extends SuperType> void put(Class<T> key, Function<? super T, ?> func) {
        Objects.requireNonNull(func);
        typedAccessors.put(Objects.requireNonNull(key), o -> func.apply(key.cast(o)));
    }

    public <T extends SuperType> void put(Entry<Class<T>, Function<? super T, ?>> entry) {
        put(entry.getKey(), entry.getValue());
    }

    public <T extends SuperType> Function<Object, ?> get(Class<T> key) {
        return typedAccessors.get(key);
    }

    public Set<Entry<Class<? extends SuperType>, Function<Object, ?>>> entrySet() {
        return typedAccessors.entrySet();
    }
}
