package org.embeddedt.modernfix.neoforge.mixin.perf.dynamic_resources;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Function;

@Mixin(ModelBakery.ModelBakerImpl.class)
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

    @WrapMethod(method = "bake(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/resources/model/ModelState;Ljava/util/function/Function;)Lnet/minecraft/client/resources/model/BakedModel;", remap = false)
    private BakedModel mfix$lockWhenBaking(ResourceLocation location, ModelState transform, Function<Material, TextureAtlasSprite> textureGetter, Operation<BakedModel> original) {
        var lock = ((IExtendedModelBakery)this.field_40571).mfix$getLock();
        lock.lock();
        try {
            return original.call(location, transform, textureGetter);
        } finally {
            lock.unlock();
        }
    }
}
