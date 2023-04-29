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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(ForgeItemModelShaper.class)
public abstract class ItemModelShaperMixin extends ItemModelShaper {
    @Shadow @Final private Map<Holder.Reference<Item>, ModelResourceLocation> locations;

    public ItemModelShaperMixin(ModelManager arg) {
        super(arg);
    }

    private static final ModelResourceLocation SENTINEL = new ModelResourceLocation(new ResourceLocation("modernfix", "sentinel"), "sentinel");

    /**
     * @reason Get the stored location for that item and meta, and get the model
     * from that location from the model manager.
     **/
    @Overwrite
    @Override
    public BakedModel getItemModel(Item item) {
        ModelResourceLocation map = locations.getOrDefault(ForgeRegistries.ITEMS.getDelegateOrThrow(item), SENTINEL);
        if(map == SENTINEL) {
            /* generate the appropriate location from our cache */
            map = ModelLocationCache.get(item);
        }
        return map == null ? null : getModelManager().getModel(map);
    }

    /**
     * @reason Don't get all models during init (with dynamic loading, that would
     * generate them all). Just store location instead.
     **/
    @Overwrite
    @Override
    public void register(Item item, ModelResourceLocation location) {
        locations.put(ForgeRegistries.ITEMS.getDelegateOrThrow(item), location);
    }

    /**
     * @reason Disable cache rebuilding (with dynamic loading, that would generate
     * all models).
     **/
    @Overwrite
    @Override
    public void rebuildCache() {}
}
