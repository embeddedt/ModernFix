package org.embeddedt.modernfix.forge.mixin.perf.kubejs;

import dev.latvian.kubejs.item.ItemStackJS;
import dev.latvian.kubejs.item.ingredient.CustomIngredient;
import net.minecraft.world.item.crafting.Ingredient;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.duck.ICachedIngredientJS;
import org.embeddedt.modernfix.forge.util.KubeUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.Set;

@Mixin(CustomIngredient.class)
@RequiresMod("kubejs")
public abstract class CustomIngredientMixin implements ICachedIngredientJS {
    @Shadow @Final private Ingredient ingredient;

    @Shadow(remap = false) public abstract Set<ItemStackJS> getStacks();

    @Override
    public Set<ItemStackJS> getCachedStacks() {
        Set<ItemStackJS> itemSet = KubeUtil.ingredientItemCache.get(this.ingredient);
        if(itemSet == null) {
            itemSet = this.getStacks();
            KubeUtil.ingredientItemCache.put(this.ingredient, itemSet);
        }
        return itemSet;
    }
}
