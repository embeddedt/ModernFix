package org.embeddedt.modernfix.forge.mixin.perf.faster_ingredients;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeHooks;
import org.embeddedt.modernfix.forge.recipe.ExtendedIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ForgeHooks.class, priority = 900)
public class ForgeHooksMixin {
    /**
     * @author embeddedt
     * @reason Exploding the stack list is entirely unnecessary to compute this
     */
    @Inject(method = "hasNoElements", at = @At("HEAD"), cancellable = true, remap = false)
    private static void modernfix$fastHasNoElements(Ingredient ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (ingredient.isVanilla()) {
            cir.setReturnValue(((ExtendedIngredient)ingredient).mfix$hasNoElements());
        }
    }
}
