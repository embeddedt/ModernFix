package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelBakery.ModelBakerImpl.class)
@ClientOnlyMixin
public abstract class ModelBakerImplMixin {
    @Shadow public abstract UnbakedModel getModel(ResourceLocation location);

    @Shadow @Final private ModelBakery field_40571;
    @Unique
    private int mfix$getDepth = 0;

    /**
     * @author embeddedt
     * @reason force parent resolution to happen before model gets baked
     */
    @ModifyReturnValue(method = "getModel", at = @At("RETURN"))
    private UnbakedModel resolveParents(UnbakedModel model) {
        mfix$getDepth++;
        if(mfix$getDepth == 1) {
            try {
                model.resolveParents(this::getModel);
            } catch(Exception e) {
                ModernFix.LOGGER.warn("Exception encountered resolving parents", e);
            }
        }
        mfix$getDepth--;
        return model;
    }

    @WrapMethod(method = "bake(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/resources/model/ModelState;)Lnet/minecraft/client/resources/model/BakedModel;")
    private BakedModel mfix$lockWhenBaking(ResourceLocation location, ModelState transform, Operation<BakedModel> original) {
        var lock = ((IExtendedModelBakery)this.field_40571).mfix$getLock();
        lock.lock();
        try {
            return original.call(location, transform);
        } finally {
            lock.unlock();
        }
    }
}
