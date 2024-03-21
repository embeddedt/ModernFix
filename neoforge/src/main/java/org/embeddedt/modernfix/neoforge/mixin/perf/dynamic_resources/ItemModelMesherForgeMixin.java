package org.embeddedt.modernfix.neoforge.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.RegistryAwareItemModelShaper;
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

@Mixin(RegistryAwareItemModelShaper.class)
@ClientOnlyMixin
public abstract class ItemModelMesherForgeMixin extends ItemModelShaper {
    @Shadow(remap = false) @Final @Mutable private Map<Item, ModelResourceLocation> locations;

    private Map<Item, ModelResourceLocation> overrideLocations;

    private final DynamicModelCache<Item> mfix$modelCache = new DynamicModelCache<>(k -> this.mfix$getModelSlow((Item)k), true);

    public ItemModelMesherForgeMixin(ModelManager arg) {
        super(arg);
    }

    private static final ModelResourceLocation SENTINEL = new ModelResourceLocation(new ResourceLocation("modernfix", "sentinel"), "sentinel");

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceLocationMap(CallbackInfo ci) {
        overrideLocations = new HashMap<>();
        // need to replace this map because mods query locations through it
        locations = new ItemMesherMap<>(this::mfix$getLocationForge);
    }

    @Unique
    private ModelResourceLocation mfix$getLocationForge(Item item) {
        ModelResourceLocation map = overrideLocations.getOrDefault(item, SENTINEL);
        if(map == SENTINEL) {
            /* generate the appropriate location from our cache */
            map = ModelLocationCache.get(item);
        }
        return map;
    }

    private BakedModel mfix$getModelSlow(Item key) {
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
        return this.mfix$modelCache.get(item);
    }

    /**
     * @author embeddedt
     * @reason Don't get all models during init (with dynamic loading, that would
     * generate them all). Just store location instead.
     **/
    @Overwrite
    @Override
    public void register(Item item, ModelResourceLocation location) {
        overrideLocations.put(item, location);
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
