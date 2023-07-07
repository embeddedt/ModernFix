package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import org.embeddedt.modernfix.forge.dynresources.ModelBakeEventHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

@Mixin(ForgeHooksClient.class)
public class ForgeHooksClientMixin {
    /**
     * Generate a more realistic keySet that contains every item and block model location, to help with mod compat.
     */
    @ModifyVariable(method = "onModelBake", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static Map<ResourceLocation, BakedModel> generateModelKeySet(Map<ResourceLocation, BakedModel> modelRegistry) {
        return ModelBakeEventHelper.wrapRegistry(modelRegistry);
    }
}
