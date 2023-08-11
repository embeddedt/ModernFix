package org.embeddedt.modernfix.registry;

import com.google.common.collect.BiMap;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterators;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class TransformingBiMap<KFrom, VFrom, KTo, VTo> implements BiMap<KTo, VTo> {
    private final BiMap<KFrom, VFrom> delegate;
    private final Function<KFrom, KTo> keyFwd;
    private final Function<KTo, KFrom> keyBack;
    private final Function<VFrom, VTo> valueFwd;
    private final Function<VTo, VFrom> valueBack;

    public TransformingBiMap(BiMap<KFrom, VFrom> map, Function<KFrom, KTo> keyFwd, Function<KTo, KFrom> keyBack, Function<VFrom, VTo> valueFwd, Function<VTo, VFrom> valueBack) {
        this.delegate = map;
        this.keyFwd = keyFwd;
        this.keyBack = keyBack;
        this.valueFwd = valueFwd;
        this.valueBack = valueBack;
    }

    private KFrom keyBack(KTo key) {
        return key == null ? null : this.keyBack.apply(key);
    }

    private KTo keyFwd(KFrom key) {
        return key == null ? null : this.keyFwd.apply(key);
    }

    private VFrom valueBack(VTo value) {
        return value == null ? null : this.valueBack.apply(value);
    }

    private VTo valueFwd(VFrom value) {
        return value == null ? null : this.valueFwd.apply(value);
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return this.delegate.containsKey(keyBack((KTo)o));
    }

    @Override
    public boolean containsValue(Object o) {
        return false;
    }

    @Override
    public VTo get(Object o) {
        return valueFwd(this.delegate.get(keyBack((KTo)o)));
    }

    @Override
    public VTo put(KTo key, VTo value) {
        return valueFwd(this.delegate.put(keyBack(key), valueBack(value)));
    }

    @Override
    public VTo remove(Object o) {
        return valueFwd(this.delegate.remove(keyBack((KTo)o)));
    }

    @Override
    public VTo forcePut(KTo key, VTo value) {
        return valueFwd(this.delegate.forcePut(keyBack(key), valueBack(value)));
    }

    @Override
    public void putAll(Map<? extends KTo, ? extends VTo> map) {
        map.forEach((key, value) -> {
            this.delegate.put(keyBack(key), valueBack(value));
        });
    }

    @Override
    public void clear() {
        this.delegate.clear();
    }

    @NotNull
    @Override
    public Set<KTo> keySet() {
        return new TransformingSet<>(this.delegate.keySet(), this.keyFwd, this.keyBack);
    }

    @Override
    public Set<VTo> values() {
        return new TransformingSet<>(this.delegate.values(), this.valueFwd, this.valueBack);
    }

    @NotNull
    @Override
    public Set<Entry<KTo, VTo>> entrySet() {
        return new TransformingSet<>(this.delegate.entrySet(), entry -> {
            return new AbstractMap.SimpleImmutableEntry<>(keyFwd(entry.getKey()), valueFwd(entry.getValue()));
        }, entry -> {
            return new AbstractMap.SimpleImmutableEntry<>(keyBack(entry.getKey()), valueBack(entry.getValue()));
        });
    }

    @Override
    public BiMap<VTo, KTo> inverse() {
        return new TransformingBiMap<>(this.delegate.inverse(), this.valueFwd, this.valueBack, this.keyFwd, this.keyBack);
    }

    static class TransformingSet<TypeFrom, TypeTo> implements Set<TypeTo> {
        private final Set<TypeFrom> delegate;
        private final Function<TypeFrom, TypeTo> forward;
        private final Function<TypeTo, TypeFrom> reverse;

        public TransformingSet(Set<TypeFrom> set, Function<TypeFrom, TypeTo> forward, Function<TypeTo, TypeFrom> reverse) {
            this.delegate = set;
            this.forward = forward;
            this.reverse = reverse;
        }

        private TypeTo forward(TypeFrom t) {
            return t == null ? null : this.forward.apply(t);
        }

        private TypeFrom reverse(TypeTo t) {
            return t == null ? null : this.reverse.apply(t);
        }

        @Override
        public int size() {
            return this.delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return this.delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return this.delegate.contains(reverse((TypeTo)o));
        }

        @NotNull
        @Override
        public Iterator<TypeTo> iterator() {
            return Iterators.transform(this.delegate.iterator(), this::forward);
        }

        @NotNull
        @Override
        public Object[] toArray() {
            Object[] array = this.delegate.toArray();
            for(int i = 0; i < array.length; i++) {
                array[i] = this.forward((TypeFrom)array[i]);
            }
            return array;
        }

        @NotNull
        @Override
        public <T> T[] toArray(@NotNull T[] ts) {
            if(ts.length >= this.delegate.size()) {
                Object[] setContents = toArray();
                System.arraycopy(setContents, 0, ts, 0, Math.min(setContents.length, ts.length));
                if(ts.length > setContents.length)
                    ts[setContents.length] = null;
                return ts;
            } else {
                T[] realArray = Arrays.copyOf(ts, this.delegate.size());
                Iterator<TypeTo> iterator = this.iterator();
                int i = 0;
                while(iterator.hasNext())
                    realArray[i++] = (T)iterator.next();
                return realArray;
            }
        }

        @Override
        public boolean add(TypeTo typeFrom) {
            return this.delegate.add(reverse(typeFrom));
        }

        @Override
        public boolean remove(Object o) {
            return this.delegate.remove(reverse((TypeTo)o));
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> collection) {
            return this.delegate.containsAll(Collections2.transform(collection, obj -> reverse((TypeTo)obj)));
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends TypeTo> collection) {
            return this.delegate.addAll(Collections2.transform(collection, this::reverse));
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> collection) {
            return this.delegate.retainAll(Collections2.transform(collection, obj -> reverse((TypeTo)obj)));
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> collection) {
            return this.delegate.removeAll(Collections2.transform(collection, obj -> reverse((TypeTo)obj)));
        }

        @Override
        public void clear() {
            this.delegate.clear();
        }
    }
}
