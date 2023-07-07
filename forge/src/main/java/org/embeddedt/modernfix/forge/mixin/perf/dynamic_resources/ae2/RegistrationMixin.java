package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources.ae2;

import appeng.bootstrap.components.IModelBakeComponent;
import appeng.bootstrap.components.ModelOverrideComponent;
import appeng.core.Api;
import appeng.core.AppEng;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.dynamicresources.ModelBakeryHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;

@Mixin(targets = { "appeng/core/Registration" })
@RequiresMod("appliedenergistics2")
@ClientOnlyMixin
public class RegistrationMixin {
    private static Field customizerField;
    @Inject(method = "registerClientEvents", at = @At("TAIL"), remap = false)
    private void doRegisterDynBake(CallbackInfo ci) {
        customizerField = ObfuscationReflectionHelper.findField(ModelOverrideComponent.class, "customizer");
        ModernFixClient.CLIENT_INTEGRATIONS.add(ModelBakeryHelpers.bakedModelWrapper((location, pair) -> {
                BakedModel originalModel = pair.getSecond();
                if(location.getNamespace().equals(AppEng.MOD_ID)) {
                    Iterator<IModelBakeComponent> components = Api.INSTANCE.definitions().getRegistry().getBootstrapComponents(IModelBakeComponent.class);
                    while(components.hasNext()) {
                        IModelBakeComponent c = components.next();
                        if(c instanceof ModelOverrideComponent) {
                            Map<String, BiFunction<ResourceLocation, BakedModel, BakedModel>> customizer;
                            try {
                                customizer = (Map<String, BiFunction<ResourceLocation, BakedModel, BakedModel>>)customizerField.get(c);
                            } catch(ReflectiveOperationException e) {
                                ModernFix.LOGGER.error("Can't replace model", e);
                                continue;
                            }
                            BiFunction<ResourceLocation, BakedModel, BakedModel> fn = customizer.get(location.getPath());
                            if(fn != null)
                                originalModel = fn.apply(location, originalModel);
                        }
                    }
                }
                return originalModel;
        }));
    }
}
