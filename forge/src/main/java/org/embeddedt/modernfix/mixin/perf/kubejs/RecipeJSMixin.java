package org.embeddedt.modernfix.mixin.perf.kubejs;

import dev.latvian.kubejs.item.ItemStackJS;
import dev.latvian.kubejs.item.ingredient.IngredientJS;
import dev.latvian.kubejs.item.ingredient.TagIngredientJS;
import dev.latvian.kubejs.recipe.RecipeJS;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.duck.ICachedIngredientJS;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@Mixin(RecipeJS.class)
@RequiresMod("kubejs")
public class RecipeJSMixin {
    /**
     * @author embeddedt
     * @reason some mods seem to not like this being called concurrently, not sure why this doesn't crash in other scripts
     */
    @Redirect(method = "hasOutput", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/Recipe;getResultItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack syncResultItem(Recipe<?> recipe) {
        synchronized (RecipeJS.class) {
            return recipe.getResultItem();
        }
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Ldev/latvian/kubejs/item/ingredient/IngredientJS;anyStackMatches(Ldev/latvian/kubejs/item/ingredient/IngredientJS;)Z", remap = false))
    private boolean optimizeMatching(IngredientJS target, IngredientJS given) {
        if((target instanceof TagIngredientJS && given instanceof ItemStackJS) || !(target instanceof ICachedIngredientJS)) {
            /* we already have an optimized code path for this */
            return target.anyStackMatches(given);
        } else {
            Set<ItemStackJS> givenStacks = ((ICachedIngredientJS)target).getCachedStacks();
            for(ItemStackJS stack : givenStacks) {
                if(given.test(stack))
                    return true;
            }
            return false;
        }
    }
}
