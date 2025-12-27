package org.embeddedt.modernfix.common.mixin.perf.ingredient_item_deduplication;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Ingredient.ItemValue.class)
public class IngredientItemValueMixin {
    /**
     * @author embeddedt
     * @reason Defensively copy the item so that the deduplication is not visible to most mods (unless they introspect
     * the item held within this object directly). This is necessary since some mods edit the returned stack.
     */
    @ModifyExpressionValue(method = "getItems", at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/crafting/Ingredient$ItemValue;item:Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mfix$defensiveCopy(ItemStack original) {
        return original.copy();
    }
}
