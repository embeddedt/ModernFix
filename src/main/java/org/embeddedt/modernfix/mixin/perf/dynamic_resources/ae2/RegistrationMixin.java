package org.embeddedt.modernfix.mixin.perf.dynamic_resources.ae2;

import appeng.bootstrap.components.IModelBakeComponent;
import appeng.bootstrap.components.ModelOverrideComponent;
import appeng.core.Api;
import appeng.core.AppEng;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.dynamicresources.DynamicModelBakeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.BiFunction;

@Mixin(targets = { "appeng/core/Registration" })
public class RegistrationMixin {
    private static Field customizerField;
    @Inject(method = "registerClientEvents", at = @At("TAIL"), remap = false)
    private void doRegisterDynBake(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.addListener(this::onDynamicModelBake);
        customizerField = ObfuscationReflectionHelper.findField(ModelOverrideComponent.class, "customizer");
    }

    private void onDynamicModelBake(DynamicModelBakeEvent event) {
        if (!event.getLocation().getNamespace().equals(AppEng.MOD_ID)) {
            return;
        }
        BakedModel missing = event.getModelLoader().getBakedTopLevelModels().get(ModelBakery.MISSING_MODEL_LOCATION);
        if(event.getModel() == missing)
            return;
        Api.INSTANCE.definitions().getRegistry().getBootstrapComponents(IModelBakeComponent.class).forEachRemaining(c -> {
            if(c instanceof ModelOverrideComponent)
                handleModelOverride((ModelOverrideComponent)c, event);
        });
    }

    private void handleModelOverride(ModelOverrideComponent c, DynamicModelBakeEvent event) {
        Map<String, BiFunction<ResourceLocation, BakedModel, BakedModel>> customizer;
        try {
            customizer = (Map<String, BiFunction<ResourceLocation, BakedModel, BakedModel>>)customizerField.get(c);
        } catch(ReflectiveOperationException e) {
            ModernFix.LOGGER.error("Can't replace model", e);
            return;
        }
        BiFunction<ResourceLocation, BakedModel, BakedModel> fn = customizer.get(event.getLocation().getPath());
        if(fn != null) {
            event.setModel(fn.apply(event.getLocation(), event.getModel()));
        }
    }
}
