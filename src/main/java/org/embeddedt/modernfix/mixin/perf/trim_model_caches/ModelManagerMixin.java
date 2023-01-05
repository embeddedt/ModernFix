package org.embeddedt.modernfix.mixin.perf.trim_model_caches;

import net.minecraft.client.renderer.model.ModelBakery;
import net.minecraft.client.renderer.model.ModelManager;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(ModelManager.class)
public class ModelManagerMixin {
    private void trimBakeryMap(ModelBakery bakery, String fieldName) {
        Map map = ObfuscationReflectionHelper.getPrivateValue(ModelBakery.class, bakery, fieldName);
        int size = map.size();
        ModernFix.LOGGER.warn("Trimming " + fieldName + " with " + size + " entries");
        if(map instanceof HashMap) {
            ObfuscationReflectionHelper.setPrivateValue(ModelBakery.class, bakery, new HashMap<>(), fieldName);
        } else
            map.clear();
    }
    @Inject(method = "apply(Lnet/minecraft/client/renderer/model/ModelBakery;Lnet/minecraft/resources/IResourceManager;Lnet/minecraft/profiler/IProfiler;)V", at = @At("RETURN"))
    private void trimModelCaches(ModelBakery bakery, IResourceManager p_212853_2_, IProfiler p_212853_3_, CallbackInfo ci) {
        trimBakeryMap(bakery, "field_217849_F"); // unbakedCache
        trimBakeryMap(bakery, "field_217850_G"); // bakedCache
        trimBakeryMap(bakery, "field_217851_H"); // topLevelModels
        // bakedTopLevelModels is used as the model registry
    }
}
