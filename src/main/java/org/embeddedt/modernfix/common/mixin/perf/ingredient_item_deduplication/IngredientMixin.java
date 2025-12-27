package org.embeddedt.modernfix.common.mixin.perf.ingredient_item_deduplication;

import net.minecraft.world.item.crafting.Ingredient;
import org.embeddedt.modernfix.neoforge.recipe.IngredientValueDeduplicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.stream.Stream;

@Mixin(Ingredient.class)
public class IngredientMixin {
    @ModifyVariable(method = "<init>(Ljava/util/stream/Stream;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Stream<? extends Ingredient.Value> injectDeduplicationPass(Stream<? extends Ingredient.Value> stream) {
        return stream.map(IngredientValueDeduplicator::deduplicate);
    }

    @ModifyVariable(method = "<init>([Lnet/minecraft/world/item/crafting/Ingredient$Value;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Ingredient.Value[] injectDeduplicationPassArray(Ingredient.Value[] values) {
        if (values.length == 0) {
            return values;
        }
        Ingredient.Value[] newValues = new Ingredient.Value[values.length];
        for (int i = 0; i < values.length; i++) {
            newValues[i] = IngredientValueDeduplicator.deduplicate(values[i]);
        }
        return newValues;
    }
}
