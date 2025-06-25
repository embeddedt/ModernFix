package org.embeddedt.modernfix.neoforge.mixin.perf.dynamic_resources;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.neoforged.fml.ModLoader;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamicresources.DynamicModelProvider;
import org.embeddedt.modernfix.neoforge.dynresources.ModelBakeEventHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ModelManager.class)
@ClientOnlyMixin
public class ModelManagerMixinNeo {
    @ModifyArg(method = "lambda$loadModels$18", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;onModifyBakingResult(Lnet/minecraft/client/resources/model/ModelBakery$BakingResult;Ljava/util/Map;Lnet/minecraft/client/resources/model/ModelBakery;)V"), remap = false, index = 0)
    private static ModelBakery.BakingResult useDynamicBakingResult(ModelBakery.BakingResult bakingResult) {
        var currentReloadingProvider = DynamicModelProvider.currentReloadingModelProvider.get();
        if(ModLoader.hasErrors() || currentReloadingProvider == null) {
            ModernFix.LOGGER.error("Errors encountered - not using dynamic model BakingResult");
            return bakingResult;
        }
        return new ModelBakeEventHelper(currentReloadingProvider).createDynamicResult();
    }
}
