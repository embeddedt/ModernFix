package org.embeddedt.modernfix.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.ItemModelMesherForge;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IRegistryDelegate;
import org.embeddedt.modernfix.dynamicresources.ModelLocationCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Mixin(ItemModelMesherForge.class)
public abstract class ItemModelShaperMixin extends ItemModelShaper {
    @Shadow @Final @Mutable private Map<IRegistryDelegate<Item>, ModelResourceLocation> locations;

    private Map<IRegistryDelegate<Item>, ModelResourceLocation> overrideLocations;

    public ItemModelShaperMixin(ModelManager arg) {
        super(arg);
    }

    private static final ModelResourceLocation SENTINEL = new ModelResourceLocation("modernfix", "sentinel");

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceLocationMap(CallbackInfo ci) {
        overrideLocations = new HashMap<>();
        // need to replace this map because mods query locations through it
        locations = new Map<IRegistryDelegate<Item>, ModelResourceLocation>() {
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
                return getLocation(((IRegistryDelegate<Item>)key).get());
            }

            @Nullable
            @Override
            public ModelResourceLocation put(IRegistryDelegate<Item> key, ModelResourceLocation value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ModelResourceLocation remove(Object key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void putAll(@NotNull Map<? extends IRegistryDelegate<Item>, ? extends ModelResourceLocation> m) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }

            @NotNull
            @Override
            public Set<IRegistryDelegate<Item>> keySet() {
                throw new UnsupportedOperationException();
            }

            @NotNull
            @Override
            public Collection<ModelResourceLocation> values() {
                throw new UnsupportedOperationException();
            }

            @NotNull
            @Override
            public Set<Entry<IRegistryDelegate<Item>, ModelResourceLocation>> entrySet() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private ModelResourceLocation getLocation(Item item) {
        ModelResourceLocation map = overrideLocations.getOrDefault(item.delegate, SENTINEL);
        if(map == SENTINEL) {
            /* generate the appropriate location from our cache */
            map = ModelLocationCache.get(item);
        }
        return map;
    }

    /**
     * @reason Get the stored location for that item and meta, and get the model
     * from that location from the model manager.
     **/
    @Overwrite
    @Override
    public BakedModel getItemModel(Item item) {
        ModelResourceLocation map = getLocation(item);
        return map == null ? null : getModelManager().getModel(map);
    }

    /**
     * @reason Don't get all models during init (with dynamic loading, that would
     * generate them all). Just store location instead.
     **/
    @Overwrite
    @Override
    public void register(Item item, ModelResourceLocation location) {
        overrideLocations.put(item.delegate, location);
    }

    /**
     * @reason Disable cache rebuilding (with dynamic loading, that would generate
     * all models).
     **/
    @Overwrite
    @Override
    public void rebuildCache() {}
}
