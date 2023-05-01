package org.embeddedt.modernfix.mixin.perf.dynamic_resources.ae2;

import appeng.core.AppEng;
import appeng.init.client.InitAutoRotatingModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraftforge.common.MinecraftForge;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.dynamicresources.DynamicModelBakeEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Function;

@Mixin(InitAutoRotatingModel.class)
@RequiresMod("appliedenergistics2")
@ClientOnlyMixin
public class RegistrationMixin {
    @Shadow @Final private static Map<String, Function<BakedModel, BakedModel>> CUSTOMIZERS;
    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private static void doRegisterDynBake(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.addListener(RegistrationMixin::onDynamicModelBake);
    }

    private static void onDynamicModelBake(DynamicModelBakeEvent event) {
        if (!event.getLocation().getNamespace().equals(AppEng.MOD_ID)) {
            return;
        }
        BakedModel missing = event.getModelLoader().getBakedTopLevelModels().get(ModelBakery.MISSING_MODEL_LOCATION);
        if(event.getModel() == missing)
            return;
        Function<BakedModel, BakedModel> customizerFn = CUSTOMIZERS.get(event.getLocation().getPath());
        if(customizerFn != null)
            event.setModel(customizerFn.apply(event.getModel()));
    }
}
