package org.embeddedt.modernfix.mixin.perf.async_jei;

import com.google.common.collect.ImmutableListMultimap;
import mezz.jei.recipes.RecipeManagerInternal;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.jei.async.IAsyncJeiStarter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManagerInternal.class)
public class RecipeManagerInternalMixin {
    @Inject(method = "addRecipes", at = @At(value = "INVOKE", target = "Lmezz/jei/recipes/RecipeManagerInternal;addRecipeTyped(Ljava/lang/Object;Lnet/minecraft/resources/ResourceLocation;)V"))
    private void checkForInterrupt(ImmutableListMultimap<ResourceLocation, Object> recipes, CallbackInfo ci) {
        IAsyncJeiStarter.checkForLoadInterruption();
    }
}
