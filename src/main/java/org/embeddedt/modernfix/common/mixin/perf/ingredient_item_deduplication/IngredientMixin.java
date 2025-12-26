package org.embeddedt.modernfix.common.mixin.perf.ingredient_item_deduplication;

import net.minecraft.world.item.crafting.Ingredient;
import org.embeddedt.modernfix.forge.recipe.IngredientValueDeduplicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.stream.Stream;

@Mixin(Ingredient.class)
public class IngredientMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Stream<? extends Ingredient.Value> injectDeduplicationPass(Stream<? extends Ingredient.Value> stream) {
        return stream.map(IngredientValueDeduplicator::deduplicate);
    }
}
