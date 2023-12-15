package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.Item;
import org.embeddedt.modernfix.dynamicresources.DynamicModelCache;
import org.embeddedt.modernfix.dynamicresources.ModelLocationCache;
import org.embeddedt.modernfix.util.DynamicInt2ObjectMap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(ItemModelShaper.class)
public abstract class ItemModelShaperMixin {

    @Shadow public abstract ModelManager getModelManager();

    @Shadow @Final @Mutable private Int2ObjectMap<BakedModel> shapesCache;

    private Map<Item, ModelResourceLocation> overrideLocationsVanilla;

    public ItemModelShaperMixin() {
        super();
    }

    private static final ModelResourceLocation SENTINEL_VANILLA = new ModelResourceLocation("modernfix", "sentinel");

    private final DynamicModelCache<Item> mfix$itemModelCache = new DynamicModelCache<>(k -> this.mfix$getModelForItem((Item)k), true);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceLocationMap(CallbackInfo ci) {
        overrideLocationsVanilla = new HashMap<>();
        this.shapesCache = new DynamicInt2ObjectMap<>(index -> getModelManager().getModel(ModelLocationCache.get(Item.byId(index))));
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


    private BakedModel mfix$getModelForItem(Item item) {
        ModelResourceLocation map = mfix$getLocation(item);
        return map == null ? null : getModelManager().getModel(map);
    }

    /**
     * @author embeddedt
     * @reason Get the stored location for that item and meta, and get the model
     * from that location from the model manager.
     **/
    @Overwrite
    public BakedModel getItemModel(Item item) {
        return this.mfix$itemModelCache.get(item);
    }

    /**
     * @author embeddedt
     * @reason Don't get all models during init (with dynamic loading, that would
     * generate them all). Just store location instead.
     **/
    @Overwrite
    public void register(Item item, ModelResourceLocation location) {
        overrideLocationsVanilla.put(item, location);
    }

    /**
     * @author embeddedt
     * @reason Disable cache rebuilding (with dynamic loading, that would generate
     * all models).
     **/
    @Overwrite
    public void rebuildCache() {
        this.mfix$itemModelCache.clear();
    }
}
