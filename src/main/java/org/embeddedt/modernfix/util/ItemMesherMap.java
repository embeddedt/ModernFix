package org.embeddedt.modernfix.util;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ItemMesherMap<K> implements Map<K, ModelResourceLocation> {
    private final Function<K, ModelResourceLocation> getLocation;

    public ItemMesherMap(Function<K, ModelResourceLocation> getLocation) {
        this.getLocation = getLocation;
    }

    @Override
    public int size() {
        return BuiltInRegistries.ITEM.keySet().size();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(Object key) {
        return true;
    }

    @Override
    public boolean containsValue(Object value) {
        return false;
    }

    @Override
    public ModelResourceLocation get(Object key) {
        return getLocation.apply((K)key);
    }

    @Nullable
    @Override
    public ModelResourceLocation put(K key, ModelResourceLocation value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModelResourceLocation remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends ModelResourceLocation> m) {
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
    public Collection<ModelResourceLocation> values() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Set<Map.Entry<K, ModelResourceLocation>> entrySet() {
        throw new UnsupportedOperationException();
    }
}
