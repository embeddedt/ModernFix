package org.embeddedt.modernfix.mixin.perf.kubejs;

import dev.latvian.kubejs.recipe.RecipeJS;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RecipeJS.class)
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
}
