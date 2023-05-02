package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources;

import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import org.apache.commons.lang3.tuple.Triple;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider;
import org.embeddedt.modernfix.forge.dynamicresources.DynamicModelBakeEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(ModelBakery.ModelBakerImpl.class)
public abstract class ModelBakerImplMixin {
    private static final boolean debugDynamicModelLoading = Boolean.getBoolean("modernfix.debugDynamicModelLoading");
    @Shadow @Final private ModelBakery field_40571;

    @Shadow public abstract UnbakedModel getModel(ResourceLocation arg);

    @Inject(method = "bake(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/resources/model/ModelState;Ljava/util/function/Function;)Lnet/minecraft/client/resources/model/BakedModel;", at = @At("HEAD"), cancellable = true, remap = false)
    public void getOrLoadBakedModelDynamic(ResourceLocation arg, ModelState arg2, Function<Material, TextureAtlasSprite> textureGetter, CallbackInfoReturnable<BakedModel> cir) {
        ModelBakery.BakedCacheKey key = new ModelBakery.BakedCacheKey(arg, arg2.getRotation(), arg2.isUvLocked());
        BakedModel existing = this.field_40571.bakedCache.get(key);
        if (existing != null) {
            cir.setReturnValue(existing);
        } else {
            synchronized (this) {
                if(debugDynamicModelLoading)
                    ModernFix.LOGGER.info("Baking {}", arg);
                UnbakedModel iunbakedmodel = this.getModel(arg);
                IExtendedModelBakery extendedBakery = (IExtendedModelBakery)this.field_40571;
                if(iunbakedmodel == extendedBakery.mfix$getUnbakedMissingModel() && debugDynamicModelLoading)
                    ModernFix.LOGGER.warn("Model {} not present", arg);
                // TODO: make sure parent resolution doesn't re-run many times
                iunbakedmodel.resolveParents(this::getModel);
                BakedModel ibakedmodel = null;
                if (iunbakedmodel instanceof BlockModel) {
                    BlockModel blockmodel = (BlockModel)iunbakedmodel;
                    if (blockmodel.getRootModel() == ModelBakery.GENERATION_MARKER) {
                        ibakedmodel = ModelBakery.ITEM_MODEL_GENERATOR.generateBlockModel(textureGetter, blockmodel).bake((ModelBaker)this, blockmodel, textureGetter, arg2, arg, false);
                    }
                }
                if(ibakedmodel == null) {
                    if(iunbakedmodel == extendedBakery.mfix$getUnbakedMissingModel()) {
                        // use a shared baked missing model
                        if(extendedBakery.getBakedMissingModel() == null) {
                            extendedBakery.setBakedMissingModel(iunbakedmodel.bake((ModelBaker)this, textureGetter, arg2, arg));
                            ((DynamicBakedModelProvider)this.field_40571.getBakedTopLevelModels()).setMissingModel(extendedBakery.getBakedMissingModel());
                        }
                        ibakedmodel = extendedBakery.getBakedMissingModel();
                    } else
                        ibakedmodel = iunbakedmodel.bake((ModelBaker)this, textureGetter, arg2, arg);
                }
                DynamicModelBakeEvent event = new DynamicModelBakeEvent(arg, iunbakedmodel, ibakedmodel, (ModelBaker)this, this.field_40571);
                MinecraftForge.EVENT_BUS.post(event);
                this.field_40571.bakedCache.put(key, event.getModel());
                cir.setReturnValue(event.getModel());
            }
        }
    }
}
