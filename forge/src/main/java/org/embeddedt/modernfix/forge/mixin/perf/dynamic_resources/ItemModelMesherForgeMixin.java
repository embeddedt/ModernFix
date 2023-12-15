package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.ForgeItemModelShaper;
import net.minecraftforge.registries.ForgeRegistries;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamicresources.DynamicModelCache;
import org.embeddedt.modernfix.dynamicresources.ModelLocationCache;
import org.embeddedt.modernfix.util.ItemMesherMap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(ForgeItemModelShaper.class)
@ClientOnlyMixin
public abstract class ItemModelMesherForgeMixin extends ItemModelShaper {
    @Shadow(remap = false) @Final @Mutable private Map<Holder.Reference<Item>, ModelResourceLocation> locations;

    private Map<Holder.Reference<Item>, ModelResourceLocation> overrideLocations;

    private final DynamicModelCache<Holder.Reference<Item>> mfix$modelCache = new DynamicModelCache<>(k -> this.mfix$getModelSlow((Holder.Reference<Item>)k), true);

    public ItemModelMesherForgeMixin(ModelManager arg) {
        super(arg);
    }

    private static final ModelResourceLocation SENTINEL = new ModelResourceLocation("modernfix", "sentinel");

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceLocationMap(CallbackInfo ci) {
        overrideLocations = new HashMap<>();
        // need to replace this map because mods query locations through it
        locations = new ItemMesherMap<>(this::mfix$getLocationForge);
    }

    @Unique
    private ModelResourceLocation mfix$getLocationForge(Holder.Reference<Item> item) {
        ModelResourceLocation map = overrideLocations.getOrDefault(item, SENTINEL);
        if(map == SENTINEL) {
            /* generate the appropriate location from our cache */
            map = ModelLocationCache.get(item.get());
        }
        return map;
    }

    private BakedModel mfix$getModelSlow(Holder.Reference<Item> key) {
        ModelResourceLocation map = mfix$getLocationForge(key);
        return map == null ? null : getModelManager().getModel(map);
    }

    /**
     * @author embeddedt
     * @reason Get the stored location for that item and meta, and get the model
     * from that location from the model manager.
     **/
    @Overwrite
    @Override
    public BakedModel getItemModel(Item item) {
        return this.mfix$modelCache.get(ForgeRegistries.ITEMS.getDelegateOrThrow(item));
    }

    /**
     * @author embeddedt
     * @reason Don't get all models during init (with dynamic loading, that would
     * generate them all). Just store location instead.
     **/
    @Overwrite
    @Override
    public void register(Item item, ModelResourceLocation location) {
        overrideLocations.put(ForgeRegistries.ITEMS.getDelegateOrThrow(item), location);
    }

    /**
     * @author embeddedt
     * @reason Disable cache rebuilding (with dynamic loading, that would generate
     * all models).
     **/
    @Overwrite
    @Override
    public void rebuildCache() {
        this.mfix$modelCache.clear();
    }
}
