package org.embeddedt.modernfix.util;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ItemMesherMap implements Map<Holder.Reference<Item>, ModelResourceLocation> {
    private final Function<Item, ModelResourceLocation> getLocation;

    public ItemMesherMap(Function<Item, ModelResourceLocation> getLocation) {
        this.getLocation = getLocation;
    }

    @Override
    public int size() {
        return ForgeRegistries.ITEMS.getValues().size();
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
        return getLocation.apply(((Holder.Reference<Item>)key).get());
    }

    @Nullable
    @Override
    public ModelResourceLocation put(Holder.Reference<Item> key, ModelResourceLocation value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModelResourceLocation remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(@NotNull Map<? extends Holder.Reference<Item>, ? extends ModelResourceLocation> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Set<Holder.Reference<Item>> keySet() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Collection<ModelResourceLocation> values() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Set<Map.Entry<Holder.Reference<Item>, ModelResourceLocation>> entrySet() {
        throw new UnsupportedOperationException();
    }
}
