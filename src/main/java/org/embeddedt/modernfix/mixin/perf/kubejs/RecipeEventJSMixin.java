package org.embeddedt.modernfix.mixin.perf.kubejs;

import dev.latvian.kubejs.recipe.RecipeEventJS;
import dev.latvian.kubejs.recipe.RecipeJS;
import dev.latvian.kubejs.recipe.filter.RecipeFilter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Mixin(RecipeEventJS.class)
public class RecipeEventJSMixin {
    @Shadow @Final private List<RecipeJS> originalRecipes;

    /**
     * @author embeddedt
     * @reason parallelize filtering, then run the consumer on one thread
     */
    @Overwrite(remap = false)
    public void forEachRecipe(RecipeFilter filter, Consumer<RecipeJS> consumer) {
        if (filter == RecipeFilter.ALWAYS_TRUE) {
            this.originalRecipes.forEach(consumer);
        } else if (filter != RecipeFilter.ALWAYS_FALSE) {
            List<RecipeJS> filtered = this.originalRecipes.parallelStream().filter(filter).collect(Collectors.toList());
            filtered.forEach(consumer);
        }
    }
}
