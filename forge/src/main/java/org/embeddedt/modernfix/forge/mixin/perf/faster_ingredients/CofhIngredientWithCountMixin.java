package org.embeddedt.modernfix.forge.mixin.perf.faster_ingredients;

import cofh.lib.util.crafting.IngredientWithCount;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.stream.Stream;

@Mixin(IngredientWithCount.class)
@RequiresMod("cofh_core")
public class CofhIngredientWithCountMixin extends Ingredient {
    @Shadow @Final private Ingredient wrappedIngredient;
    @Shadow @Final private int count;

    @Unique
    private ItemStack[] mfix$itemStacksWithAdjustedCount;

    protected CofhIngredientWithCountMixin(Stream<? extends Value> values) {
        super(values);
    }

    /**
     * @author embeddedt
     * @reason reimplement in a way that doesn't rely on the itemStacks implementation detail of the wrapped ingredient
     */
    @Overwrite
    public ItemStack[] getItems() {
        if (this.mfix$itemStacksWithAdjustedCount == null) {
            ItemStack[] originalItems = this.wrappedIngredient.getItems();
            var newItems = new ItemStack[originalItems.length];
            for (int i = 0; i < originalItems.length; i++) {
                newItems[i] = originalItems[i].copy();
                newItems[i].setCount(this.count);
            }
            this.mfix$itemStacksWithAdjustedCount = newItems;
        }
        return this.mfix$itemStacksWithAdjustedCount;
    }
}
