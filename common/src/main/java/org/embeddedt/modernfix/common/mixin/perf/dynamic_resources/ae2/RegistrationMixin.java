package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources.ae2;

import appeng.core.AppEng;
import appeng.init.client.InitAutoRotatingModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Function;

@Mixin(InitAutoRotatingModel.class)
@RequiresMod("ae2")
@ClientOnlyMixin
public class RegistrationMixin {
    @Shadow(remap = false) @Final private static Map<String, Function<BakedModel, BakedModel>> CUSTOMIZERS;

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private static void doRegisterDynBake(CallbackInfo ci) {
        ModernFixClient.CLIENT_INTEGRATIONS.add(new ModernFixClientIntegration() {
            @Override
            public BakedModel onBakedModelLoad(ResourceLocation location, UnbakedModel baseModel, BakedModel originalModel, ModelState state, ModelBakery bakery) {
                if(location.getNamespace().equals(AppEng.MOD_ID)) {
                    BakedModel m = bakery.bake(ModelBakery.MISSING_MODEL_LOCATION, BlockModelRotation.X0_Y0);
                    if(originalModel == m)
                        return originalModel;
                    Function<BakedModel, BakedModel> customizerFn = CUSTOMIZERS.get(location.getPath());
                    if(customizerFn != null)
                        originalModel = customizerFn.apply(originalModel);
                }
                return originalModel;
            }
        });
    }
}
