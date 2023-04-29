package org.embeddedt.modernfix.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.ForgeItemModelShaper;
import net.minecraftforge.registries.ForgeRegistries;
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

@Mixin(ForgeItemModelShaper.class)
public abstract class ItemModelShaperMixin extends ItemModelShaper {
    @Shadow @Final @Mutable private Map<Holder.Reference<Item>, ModelResourceLocation> locations;

    private Map<Holder.Reference<Item>, ModelResourceLocation> overrideLocations;

    public ItemModelShaperMixin(ModelManager arg) {
        super(arg);
    }

    private static final ModelResourceLocation SENTINEL = new ModelResourceLocation(new ResourceLocation("modernfix", "sentinel"), "sentinel");

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceLocationMap(CallbackInfo ci) {
        overrideLocations = new HashMap<>();
        // need to replace this map because mods query locations through it
        locations = new Map<Holder.Reference<Item>, ModelResourceLocation>() {
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
                return getLocation(((Holder.Reference<Item>)key).get());
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
            public Set<Entry<Holder.Reference<Item>, ModelResourceLocation>> entrySet() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private ModelResourceLocation getLocation(Item item) {
        ModelResourceLocation map = overrideLocations.getOrDefault(ForgeRegistries.ITEMS.getDelegateOrThrow(item), SENTINEL);
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
        overrideLocations.put(ForgeRegistries.ITEMS.getDelegateOrThrow(item), location);
    }

    /**
     * @reason Disable cache rebuilding (with dynamic loading, that would generate
     * all models).
     **/
    @Overwrite
    @Override
    public void rebuildCache() {}
}
