package org.embeddedt.modernfix.neoforge.mixin.perf.dynamic_resources;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = {"net/minecraft/client/resources/model/ModelBakery$ModelBakerImpl"})
@ClientOnlyMixin
public class ModelBakerImplMixin {
    @Shadow @Final private ModelBakery field_40571;

    /**
     * @author embeddedt
     * @reason Handle dynamic model loading
     */
    @Overwrite(remap = false)
    public UnbakedModel getTopLevelModel(ModelResourceLocation location) {
        IExtendedModelBakery bakery = (IExtendedModelBakery)this.field_40571;
        UnbakedModel model = bakery.mfix$loadUnbakedModelDynamic(location);
        return model == bakery.mfix$getMissingModel() ? null : model;
    }
}
