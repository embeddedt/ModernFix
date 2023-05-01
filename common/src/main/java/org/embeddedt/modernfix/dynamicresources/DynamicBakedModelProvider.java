package org.embeddedt.modernfix.dynamicresources;

import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.BuiltInModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class DynamicBakedModelProvider implements Map<ResourceLocation, BakedModel> {
    private final ModelBakery bakery;
    private final Map<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> bakedCache;
    private final Map<ResourceLocation, BakedModel> permanentOverrides;
    private BakedModel missingModel;
    private static final BakedModel SENTINEL = new BuiltInModel(null, null, null, false);

    public DynamicBakedModelProvider(ModelBakery bakery, Map<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> cache) {
        this.bakery = bakery;
        this.bakedCache = cache;
        this.permanentOverrides = new Object2ObjectOpenHashMap<>();
    }

    public void setMissingModel(BakedModel model) {
        this.missingModel = model;
    }

    private static Triple<ResourceLocation, Transformation, Boolean> vanillaKey(Object o) {
        return Triple.of((ResourceLocation)o, BlockModelRotation.X0_Y0.getRotation(), false);
    }
    @Override
    public int size() {
        return bakedCache.size();
    }

    @Override
    public boolean isEmpty() {
        return bakedCache.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return permanentOverrides.getOrDefault(o, SENTINEL) != null;
    }

    @Override
    public boolean containsValue(Object o) {
        return permanentOverrides.containsValue(o) || bakedCache.containsValue(o);
    }

    @Override
    public BakedModel get(Object o) {
        BakedModel model = permanentOverrides.getOrDefault(o, SENTINEL);
        if(model != SENTINEL)
            return model;
        else {
            model = bakery.bake((ResourceLocation)o, BlockModelRotation.X0_Y0);
            if(model == missingModel) {
                // to correctly emulate the original map, we return null for missing models
                permanentOverrides.put((ResourceLocation) o, null);
                return null;
            } else
                return model;
        }
    }

    @Override
    public BakedModel put(ResourceLocation resourceLocation, BakedModel bakedModel) {
        BakedModel m = permanentOverrides.put(resourceLocation, bakedModel);
        if(m != null)
            return m;
        else
            return bakedCache.get(vanillaKey(resourceLocation));
    }

    @Override
    public BakedModel remove(Object o) {
        BakedModel m = permanentOverrides.remove(o);
        if(m != null)
            return m;
        return bakedCache.remove(vanillaKey(o));
    }

    @Override
    public void putAll(@NotNull Map<? extends ResourceLocation, ? extends BakedModel> map) {
        permanentOverrides.putAll(map);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Set<ResourceLocation> keySet() {
        return bakedCache.keySet().stream().map(Triple::getLeft).collect(Collectors.toSet());
    }

    @NotNull
    @Override
    public Collection<BakedModel> values() {
        return bakedCache.values();
    }

    @NotNull
    @Override
    public Set<Entry<ResourceLocation, BakedModel>> entrySet() {
        return bakedCache.entrySet().stream().map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey().getLeft(), entry.getValue())).collect(Collectors.toSet());
    }

    @Override
    public void replaceAll(BiFunction<? super ResourceLocation, ? super BakedModel, ? extends BakedModel> function) {
        Set<ResourceLocation> overridenLocations = permanentOverrides.keySet();
        permanentOverrides.replaceAll(function);
        boolean uvLock = BlockModelRotation.X0_Y0.isUvLocked();
        Transformation rotation = BlockModelRotation.X0_Y0.getRotation();
        bakedCache.replaceAll((loc, oldModel) -> {
            if(loc.getMiddle() != rotation || loc.getRight() != uvLock || overridenLocations.contains(loc.getLeft()))
                return oldModel;
            else
                return function.apply(loc.getLeft(), oldModel);
        });
    }
}
