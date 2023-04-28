package org.embeddedt.modernfix.registry;

import com.google.common.collect.BiMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.apache.commons.lang3.tuple.MutablePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FastForgeRegistry<V extends IForgeRegistryEntry<V>> {
    private BiMap<Integer, V> ids;
    private BiMap<ResourceLocation, V> names;
    private BiMap<ResourceKey<V>, V> keys;
    private ResourceKey<Registry<V>> registryKey;

    private ObjectArrayList<V> valuesById;
    private Map<V, MutablePair<ResourceKey<V>, Integer>> infoByValue;
    private Map<ResourceKey<V>, V> valuesByKey = new Object2ObjectOpenHashMap<>();

    private void storeId(V value, int id) {
        MutablePair<ResourceKey<V>, Integer> pair = infoByValue.computeIfAbsent(value, k -> new MutablePair<>(null, null));
        pair.setRight(id);
    }

    private void updateInfoPairAndClearIfNull(V v, Consumer<MutablePair<ResourceKey<V>, Integer>> consumer) {
        infoByValue.compute(v, (oldValue, oldPair) -> {
            if(oldPair == null)
                oldPair = new MutablePair<>(null, null);
            consumer.accept(oldPair);
            if(oldPair.getLeft() == null && oldPair.getRight() == null)
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

    public FastForgeRegistry(ResourceKey<Registry<V>> registryKey) {
        this.registryKey = registryKey;
        this.valuesById = new ObjectArrayList<>();
        this.infoByValue = new HashMap<>();
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
                    updateInfoPairAndClearIfNull(oldValue, pair -> pair.setRight(null));
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
                        MutablePair<ResourceKey<V>, Integer> pair = infoByValue.get(key);
                        if(pair == null)
                            return null;
                        return pair.getRight();
                    }

                    @Override
                    public Integer remove(Object key) {
                        MutablePair<ResourceKey<V>, Integer> pair = infoByValue.get(key);
                        if(pair == null)
                            return null;
                        int id = pair.getRight();
                        valuesById.set(id, null);
                        updateInfoPairAndClearIfNull((V)key, p -> p.setRight(null));
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
                    pair.setRight(null);
                    return pair.getLeft() == null && pair.getRight() == null;
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
        this.keys = new BiMap<ResourceKey<V>, V>() {
            @Nullable
            @Override
            public V put(@Nullable ResourceKey<V> key, @Nullable V value) {
                if(valuesByKey.get(key) != null)
                    throw new IllegalArgumentException("Existing mapping");
                return forcePut(key, value);
            }

            @Nullable
            @Override
            public V forcePut(@Nullable ResourceKey<V> key, @Nullable V value) {
                V oldValue = valuesByKey.put(key, value);
                if(oldValue != null) {
                    updateInfoPairAndClearIfNull(oldValue, p -> p.setLeft(null));
                }
                updateInfoPairAndClearIfNull(value, p -> p.setLeft(key));
                return oldValue;
            }

            @Override
            public void putAll(Map<? extends ResourceKey<V>, ? extends V> map) {
                map.forEach(this::put);
            }

            @Override
            public Set<V> values() {
                throw new UnsupportedOperationException();
            }

            @Override
            public BiMap<V, ResourceKey<V>> inverse() {
                return new BiMap<V, ResourceKey<V>>() {
                    @Nullable
                    @Override
                    public ResourceKey<V> put(@Nullable V key, @Nullable ResourceKey<V> value) {
                        throw new UnsupportedOperationException();
                    }

                    @Nullable
                    @Override
                    public ResourceKey<V> forcePut(@Nullable V key, @Nullable ResourceKey<V> value) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void putAll(Map<? extends V, ? extends ResourceKey<V>> map) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Set<ResourceKey<V>> values() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public BiMap<ResourceKey<V>, V> inverse() {
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
                    public ResourceKey<V> get(Object key) {
                        MutablePair<ResourceKey<V>, Integer> pair = infoByValue.get(key);
                        if(pair == null)
                            return null;
                        else
                            return pair.getLeft();
                    }

                    @Override
                    public ResourceKey<V> remove(Object key) {
                        MutablePair<ResourceKey<V>, Integer> pair = infoByValue.get(key);
                        if(pair == null)
                            return null;
                        else {
                            ResourceKey<V> rk = pair.getLeft();
                            valuesByKey.remove(rk);
                            updateInfoPairAndClearIfNull((V)key, p -> p.setLeft(null));
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
                    public Set<Entry<V, ResourceKey<V>>> entrySet() {
                        throw new UnsupportedOperationException();
                    }
                };
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
            public V get(Object key) {
                return null;
            }

            @Override
            public V remove(Object key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {
                valuesByKey.values().forEach(v -> updateInfoPairAndClearIfNull(v, p -> p.setLeft(null)));
                valuesByKey.clear();
            }

            @NotNull
            @Override
            public Set<ResourceKey<V>> keySet() {
                throw new UnsupportedOperationException();
            }

            @NotNull
            @Override
            public Set<Entry<ResourceKey<V>, V>> entrySet() {
                return valuesByKey.entrySet();
            }
        };
        this.names = new BiMap<ResourceLocation, V>() {
            @Nullable
            @Override
            public V put(@Nullable ResourceLocation key, @Nullable V value) {
                // not needed
                return null;
            }

            @Nullable
            @Override
            public V forcePut(@Nullable ResourceLocation key, @Nullable V value) {
                return null;
            }

            @Override
            public void putAll(Map<? extends ResourceLocation, ? extends V> map) {
                map.forEach(this::put);
            }

            @Override
            public Set<V> values() {
                return infoByValue.keySet();
            }

            @Override
            public BiMap<V, ResourceLocation> inverse() {
                return new BiMap<V, ResourceLocation>() {
                    @Nullable
                    @Override
                    public ResourceLocation put(@Nullable V key, @Nullable ResourceLocation value) {
                        throw new UnsupportedOperationException();
                    }

                    @Nullable
                    @Override
                    public ResourceLocation forcePut(@Nullable V key, @Nullable ResourceLocation value) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void putAll(Map<? extends V, ? extends ResourceLocation> map) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Set<ResourceLocation> values() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public BiMap<ResourceLocation, V> inverse() {
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
                    public ResourceLocation get(Object key) {
                        MutablePair<ResourceKey<V>, Integer> pair = infoByValue.get(key);
                        if(pair == null || pair.getLeft() == null)
                            return null;
                        else
                            return pair.getLeft().location();
                    }

                    @Override
                    public ResourceLocation remove(Object key) {
                        throw new UnsupportedOperationException();
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
                    public Set<Entry<V, ResourceLocation>> entrySet() {
                        throw new UnsupportedOperationException();
                    }
                };
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
                ResourceKey<V> rk = ResourceKey.create(FastForgeRegistry.this.registryKey, (ResourceLocation)key);
                return valuesByKey.containsKey(rk);
            }

            @Override
            public boolean containsValue(Object value) {
                return infoByValue.containsValue(value);
            }

            @Override
            public V get(Object key) {
                ResourceKey<V> rk = ResourceKey.create(FastForgeRegistry.this.registryKey, (ResourceLocation)key);
                return valuesByKey.get(rk);
            }

            @Override
            public V remove(Object key) {
                // we need to return a non-null value to prevent Forge throwing, but the actual removal is done by this.keys
                return get(key);
            }

            @Override
            public void clear() {
                // ditto
            }

            @NotNull
            @Override
            public Set<ResourceLocation> keySet() {
                return new FastForgeRegistryLocationSet(valuesByKey.keySet());
            }

            @NotNull
            @Override
            public Set<Entry<ResourceLocation, V>> entrySet() {
                return valuesByKey.entrySet().stream().map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey().location(), entry.getValue())).collect(Collectors.toSet());
            }
        };
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
}
