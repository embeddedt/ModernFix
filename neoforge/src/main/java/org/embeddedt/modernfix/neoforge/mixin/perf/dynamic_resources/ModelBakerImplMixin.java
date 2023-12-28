package org.embeddedt.modernfix.neoforge.mixin.perf.dynamic_resources;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
import org.embeddedt.modernfix.duck.IExtendedModelBaker;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.dynamicresources.ModelMissingException;
import org.embeddedt.modernfix.neoforge.dynresources.IModelBakerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(value = ModelBakery.ModelBakerImpl.class, priority = 600)
@ClientOnlyMixin
public abstract class ModelBakerImplMixin implements IModelBakerImpl, IExtendedModelBaker {
    private static final boolean debugDynamicModelLoading = Boolean.getBoolean("modernfix.debugDynamicModelLoading");
    @Shadow @Final private ModelBakery field_40571;

    private boolean mfix$ignoreCache = false;

    @Shadow @Final private Function<Material, TextureAtlasSprite> modelTextureGetter;

    @Override
    public void mfix$ignoreCache() {
        mfix$ignoreCache = true;
    }

    private boolean throwIfMissing;

    @Override
    public void throwOnMissingModel() {
        throwIfMissing = true;
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void obtainModel(ResourceLocation arg, CallbackInfoReturnable<UnbakedModel> cir) {
        if(debugDynamicModelLoading)
            ModernFix.LOGGER.info("Baking {}", arg);
        IExtendedModelBakery extendedBakery = (IExtendedModelBakery)this.field_40571;
        if(arg instanceof ModelResourceLocation && arg != ModelBakery.MISSING_MODEL_LOCATION) {
            // synchronized because we use topLevelModels
            synchronized (this.field_40571) {
                this.field_40571.loadTopLevel((ModelResourceLocation)arg);
                cir.setReturnValue(this.field_40571.topLevelModels.getOrDefault(arg, extendedBakery.mfix$getUnbakedMissingModel()));
                // avoid leaks
                this.field_40571.topLevelModels.clear();
            }
        } else
            cir.setReturnValue(this.field_40571.getModel(arg));
        UnbakedModel toReplace = cir.getReturnValue();
        if(true) {
            for(ModernFixClientIntegration integration : ModernFixClient.CLIENT_INTEGRATIONS) {
                try {
                    toReplace = integration.onUnbakedModelPreBake(arg, toReplace, this.field_40571);
                } catch(RuntimeException e) {
                    ModernFix.LOGGER.error("Exception firing model pre-bake event for {}", arg, e);
                }
            }
        }
        cir.setReturnValue(toReplace);
        cir.getReturnValue().resolveParents(this.field_40571::getModel);
        if(cir.getReturnValue() == extendedBakery.mfix$getUnbakedMissingModel()) {
            if(arg != ModelBakery.MISSING_MODEL_LOCATION) {
                if(debugDynamicModelLoading)
                    ModernFix.LOGGER.warn("Model {} not present", arg);
                if(throwIfMissing)
                    throw new ModelMissingException();
            }
        }
    }

    @ModifyExpressionValue(method = "bake(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/resources/model/ModelState;Ljava/util/function/Function;)Lnet/minecraft/client/resources/model/BakedModel;", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0), remap = false)
    private Object ignoreCacheIfRequested(Object o) {
        return mfix$ignoreCache ? null : o;
    }

    @WrapOperation(method = "bake(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/resources/model/ModelState;Ljava/util/function/Function;)Lnet/minecraft/client/resources/model/BakedModel;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/UnbakedModel;bake(Lnet/minecraft/client/resources/model/ModelBaker;Ljava/util/function/Function;Lnet/minecraft/client/resources/model/ModelState;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/resources/model/BakedModel;"))
    private BakedModel callBakedModelIntegration(UnbakedModel unbakedModel, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state, ResourceLocation location, Operation<BakedModel> operation) {
        BakedModel model = operation.call(unbakedModel, baker, spriteGetter, state, location);

        for(ModernFixClientIntegration integration : ModernFixClient.CLIENT_INTEGRATIONS) {
            model = integration.onBakedModelLoad(location, unbakedModel, model, state, this.field_40571);
        }

        return model;
    }
}
