package org.embeddedt.modernfix.registry;

import com.google.common.collect.BiMap;
import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FastForgeRegistry<V extends IForgeRegistryEntry<V>> {
    private final BiMap<Integer, V> ids;
    private final DataFieldBiMap<ResourceLocation> names;
    private final DataFieldBiMap<ResourceKey<V>> keys;
    private final DataFieldBiMap<?> owners;
    private final ResourceKey<Registry<V>> registryKey;

    private final ObjectArrayList<V> valuesById;
    private final Object2ObjectOpenHashMap<V, RegistryValueData> infoByValue;

    private void storeId(V value, int id) {
        RegistryValueData pair = infoByValue.computeIfAbsent(value, k -> new RegistryValueData());
        pair.id = id;
    }

    private void updateInfoPairAndClearIfNull(V v, Consumer<RegistryValueData> consumer) {
        infoByValue.compute(v, (oldValue, oldPair) -> {
            if(oldPair == null)
                oldPair = new RegistryValueData();
            consumer.accept(oldPair);
            if(oldPair.isEmpty())
                return null;
            else
                return oldPair;
        });
    }

    private void ensureArrayCanFitId(int id) {
        int desiredSize = id + 1;
        while(valuesById.size() < desiredSize) {
            valuesById.add(null);
        }
    }

    public void clear() {
        this.infoByValue.clear();
        for(int i = 0; i < this.valuesById.size(); i++) {
            this.valuesById.set(i, null);
        }
        this.names.clearUnsafe();
        this.keys.clearUnsafe();
        this.owners.clearUnsafe();
    }

    public FastForgeRegistry(ResourceKey<Registry<V>> registryKey) {
        this.registryKey = registryKey;
        this.valuesById = new ObjectArrayList<>();
        this.infoByValue = new Object2ObjectOpenHashMap<>();
        this.keys = new DataFieldBiMap<>(p -> (ResourceKey<V>) p.key, (p, k) -> p.key = k);
        this.owners = new DataFieldBiMap<>(p -> p.overrideOwner, (p, k) -> p.overrideOwner = k);
        this.names = new DataFieldBiMap<>(p -> p.location, (p, l) -> p.location = l);
        // IDs require a specialized implementation, as we back the K->V direction with an array
        this.ids = new BiMap<Integer, V>() {
            @Nullable
            @Override
            public V put(@Nullable Integer key, @Nullable V value) {
                ensureArrayCanFitId(key);
                V oldValue = valuesById.get(key);
                if(oldValue != null)
                    throw new IllegalArgumentException("Existing mapping");
                valuesById.set(key, value);
                storeId(value, key);
                return null;
            }

            @Nullable
            @Override
            public V forcePut(@Nullable Integer key, @Nullable V value) {
                ensureArrayCanFitId(key);
                V oldValue = valuesById.set(key, value);
                if(oldValue != null) {
                    updateInfoPairAndClearIfNull(oldValue, pair -> pair.id = null);
                }
                storeId(value, key);
                return oldValue;
            }

            @Override
            public void putAll(Map<? extends Integer, ? extends V> map) {
                map.forEach(this::put);
            }

            @Override
            public Set<V> values() {
                return Collections.unmodifiableSet(infoByValue.keySet());
            }

            @Override
            public BiMap<V, Integer> inverse() {
                return new BiMap<V, Integer>() {
                    @Nullable
                    @Override
                    public Integer put(@Nullable V key, @Nullable Integer value) {
                        throw new UnsupportedOperationException();
                    }

                    @Nullable
                    @Override
                    public Integer forcePut(@Nullable V key, @Nullable Integer value) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void putAll(Map<? extends V, ? extends Integer> map) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Set<Integer> values() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public BiMap<Integer, V> inverse() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int size() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean isEmpty() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean containsKey(Object key) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean containsValue(Object value) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Integer get(Object key) {
                        RegistryValueData pair = infoByValue.get(key);
                        if(pair == null)
                            return null;
                        return pair.id;
                    }

                    @Override
                    public Integer remove(Object key) {
                        RegistryValueData pair = infoByValue.get(key);
                        if(pair == null)
                            return null;
                        int id = pair.id;
                        valuesById.set(id, null);
                        updateInfoPairAndClearIfNull((V)key, p -> p.id = null);
                        return id;
                    }

                    @Override
                    public void clear() {
                        throw new UnsupportedOperationException();
                    }

                    @NotNull
                    @Override
                    public Set<V> keySet() {
                        throw new UnsupportedOperationException();
                    }

                    @NotNull
                    @Override
                    public Set<Entry<V, Integer>> entrySet() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return infoByValue.size();
            }

            @Override
            public boolean isEmpty() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean containsKey(Object key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean containsValue(Object value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public V get(Object key) {
                int id = (Integer)key;
                if(id < 0 || id >= valuesById.size())
                    return null;
                else
                    return valuesById.get(id);
            }

            @Override
            public V remove(Object key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {
                valuesById.clear();
                infoByValue.values().removeIf(pair -> {
                    pair.id = null;
                    return pair.isEmpty();
                });
            }

            @NotNull
            @Override
            public Set<Integer> keySet() {
                throw new UnsupportedOperationException();
            }

            @NotNull
            @Override
            public Set<Entry<Integer, V>> entrySet() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void forEach(BiConsumer<? super Integer, ? super V> action) {
                for(int i = 0 ; i < valuesById.size(); i++) {
                    V val = valuesById.get(i);
                    if(val != null)
                        action.accept(i, val);
                }
            }
        };
    }

    public void optimize() {
        this.keys.optimize();
        this.owners.optimize();
        this.names.optimize();
        this.infoByValue.trim();
    }

    public BiMap<Integer, V> getIds() {
        return ids;
    }

    public BiMap<ResourceKey<V>, V> getKeys() {
        return keys;
    }

    public BiMap<ResourceLocation, V> getNames() {
        return names;
    }

    public DataFieldBiMap<?> getOwners() {
        return owners;
    }

    /**
     * Custom BiMap implementation that uses one internal hash map for the K->V direction, and the shared global
     * information hash map for the V->K direction.
     */
    class DataFieldBiMap<K> implements BiMap<K, V> {
        public final Object2ObjectOpenHashMap<K, V> valuesByKey = new Object2ObjectOpenHashMap<>();
        private final Function<RegistryValueData, K> getter;
        private final BiConsumer<RegistryValueData, K> setter;

        public DataFieldBiMap(Function<RegistryValueData, K> getter, BiConsumer<RegistryValueData, K> setter) {
            this.getter = getter;
            this.setter = setter;
        }

        public void optimize() {
            this.valuesByKey.trim();
        }

        public void clearUnsafe() {
            this.valuesByKey.clear();
        }

        private V forcePut(@Nullable K key, @Nullable V value, boolean throwOnExisting) {
            V oldValue = valuesByKey.put(key, value);
            if(oldValue != null) {
                if(throwOnExisting) {
                    valuesByKey.put(key, oldValue);
                    throw new IllegalArgumentException("Existing mapping");
                } else {
                    updateInfoPairAndClearIfNull(oldValue, p -> setter.accept(p, null));
                }
            }
            updateInfoPairAndClearIfNull(value, p -> setter.accept(p, key));
            return oldValue;
        }

        @Nullable
        @Override
        public V put(@Nullable K key, @Nullable V value) {
            return forcePut(key, value, true);
        }

        @Nullable
        @Override
        public V forcePut(@Nullable K key, @Nullable V value) {
            return forcePut(key, value, false);
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> map) {
            map.forEach(this::put);
        }

        @Override
        public Set<V> values() {
            return Collections.unmodifiableSet(infoByValue.keySet());
        }

        private DataFieldBiMapInverse<K> inverse = null;

        @Override
        public BiMap<V, K> inverse() {
            if(inverse == null)
                inverse = new DataFieldBiMapInverse<>(this);
            return inverse;
        }

        @Override
        public int size() {
            return valuesByKey.size();
        }

        @Override
        public boolean isEmpty() {
            return valuesByKey.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return valuesByKey.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return infoByValue.containsKey(value);
        }

        @Override
        public V get(Object key) {
            return valuesByKey.get(key);
        }

        @Override
        public V remove(Object key) {
            V value = get(key);
            if(value == null)
                return null;
            inverse().remove(value);
            return value;
        }

        @Override
        public void clear() {
            valuesByKey.values().forEach(v -> updateInfoPairAndClearIfNull(v, p -> p.key = null));
            valuesByKey.clear();
        }

        @NotNull
        @Override
        public Set<K> keySet() {
            return Collections.unmodifiableSet(valuesByKey.keySet());
        }

        @NotNull
        @Override
        public Set<Map.Entry<K, V>> entrySet() {
            return Collections.unmodifiableSet(valuesByKey.entrySet());
        }


    }

    class DataFieldBiMapInverse<K> implements BiMap<V, K> {
        private final DataFieldBiMap<K> forward;

        public DataFieldBiMapInverse(DataFieldBiMap<K> forward) {
            this.forward = forward;
        }

        @Nullable
        @Override
        public K put(@Nullable V key, @Nullable K value) {
            throw new UnsupportedOperationException();
        }

        @Nullable
        @Override
        public K forcePut(@Nullable V key, @Nullable K value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(Map<? extends V, ? extends K> map) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<K> values() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BiMap<K, V> inverse() {
            return forward;
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEmpty() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsKey(Object key) {
            return infoByValue.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return forward.valuesByKey.containsKey(value);
        }

        @Override
        public K get(Object key) {
            RegistryValueData pair = infoByValue.get(key);
            if(pair == null)
                return null;
            else
                return forward.getter.apply(pair);
        }

        @Override
        public K remove(Object key) {
            RegistryValueData pair = infoByValue.get(key);
            if(pair == null)
                return null;
            else {
                K rk = forward.getter.apply(pair);
                forward.valuesByKey.remove(rk);
                updateInfoPairAndClearIfNull((V)key, p -> forward.setter.accept(p, null));
                return rk;
            }
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public Set<V> keySet() {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public Set<Entry<V, K>> entrySet() {
            throw new UnsupportedOperationException();
        }
    }

    class FastForgeRegistryLocationSet implements Set<ResourceLocation> {
        private final Set<ResourceKey<V>> backingSet;

        public FastForgeRegistryLocationSet(Set<ResourceKey<V>> backingSet) {
            this.backingSet = backingSet;
        }

        @Override
        public int size() {
            return backingSet.size();
        }

        @Override
        public boolean isEmpty() {
            return backingSet.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return backingSet.contains(ResourceKey.create(FastForgeRegistry.this.registryKey, (ResourceLocation)o));
        }

        @NotNull
        @Override
        public Iterator<ResourceLocation> iterator() {
            return Iterators.transform(backingSet.iterator(), ResourceKey::location);
        }

        @NotNull
        @Override
        public Object[] toArray() {
            Object[] keyArray = backingSet.toArray();
            for(int i = 0; i < keyArray.length; i++) {
                keyArray[i] = ((ResourceKey<V>)keyArray[i]).location();
            }
            return keyArray;
        }

        @NotNull
        @Override
        public <T> T[] toArray(@NotNull T[] a) {
            Object[] keyArray = backingSet.toArray();
            T[] finalArray = Arrays.copyOf(a, keyArray.length);
            for(int i = 0; i < keyArray.length; i++) {
                finalArray[i] = (T)((ResourceKey<V>)keyArray[i]).location();
            }
            return finalArray;
        }

        @Override
        public boolean add(ResourceLocation resourceLocation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            for(Object o : c) {
                if(!contains(o))
                    return false;
            }
            return true;
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends ResourceLocation> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }
    }

    static class RegistryValueData {
        public ResourceKey<?> key;
        public ResourceLocation location;
        public Integer id;
        public Object overrideOwner;

        boolean isEmpty() {
            return key == null && location == null && id == null && overrideOwner == null;
        }
    }
}
