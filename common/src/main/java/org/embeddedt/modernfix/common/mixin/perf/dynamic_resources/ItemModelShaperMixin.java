package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.embeddedt.modernfix.dynamicresources.ModelLocationCache;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(ItemModelShaper.class)
public abstract class ItemModelShaperMixin {

    @Shadow public abstract ModelManager getModelManager();

    private Map<Item, ModelResourceLocation> overrideLocationsVanilla;

    public ItemModelShaperMixin() {
        super();
    }

    private static final ModelResourceLocation SENTINEL_VANILLA = new ModelResourceLocation(new ResourceLocation("modernfix", "sentinel"), "sentinel");

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceLocationMap(CallbackInfo ci) {
        overrideLocationsVanilla = new HashMap<>();
    }

    @Unique
    private ModelResourceLocation mfix$getLocation(Item item) {
        ModelResourceLocation map = overrideLocationsVanilla.getOrDefault(item, SENTINEL_VANILLA);
        if(map == SENTINEL_VANILLA) {
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
    public BakedModel getItemModel(Item item) {
        ModelResourceLocation map = mfix$getLocation(item);
        return map == null ? null : getModelManager().getModel(map);
    }

    /**
     * @reason Don't get all models during init (with dynamic loading, that would
     * generate them all). Just store location instead.
     **/
    @Overwrite
    public void register(Item item, ModelResourceLocation location) {
        overrideLocationsVanilla.put(item, location);
    }

    /**
     * @reason Disable cache rebuilding (with dynamic loading, that would generate
     * all models).
     **/
    @Overwrite
    public void rebuildCache() {}
}
