package org.embeddedt.modernfix.mixin.perf.kubejs;

import com.google.gson.JsonObject;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import dev.latvian.kubejs.recipe.RecipeEventJS;
import dev.latvian.kubejs.recipe.RecipeJS;
import dev.latvian.kubejs.recipe.filter.RecipeFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.embeddedt.modernfix.util.KubeUtil;
import org.embeddedt.modernfix.util.ModUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Mixin(RecipeEventJS.class)
public class RecipeEventJSMixin {
    @Shadow(remap = false) @Final private List<RecipeJS> originalRecipes;

    /**
     * @author embeddedt
     * @reason parallelize filtering, then run the consumer on one thread
     */
    @Overwrite(remap = false)
    public void forEachRecipe(RecipeFilter filter, Consumer<RecipeJS> consumer) {
        if (filter == RecipeFilter.ALWAYS_TRUE) {
            this.originalRecipes.forEach(consumer);
        } else if (filter != RecipeFilter.ALWAYS_FALSE) {
            List<RecipeJS> filtered = LamdbaExceptionUtils.uncheck(() -> ModUtil.commonPool.submit(() -> this.originalRecipes.parallelStream().filter(filter).collect(Collectors.toList())).get());
            filtered.forEach(consumer);
        }
    }

    @Inject(method = "post(Lnet/minecraft/world/item/crafting/RecipeManager;Ljava/util/Map;)V", at = @At(value = "INVOKE", target = "Ldev/latvian/kubejs/recipe/RecipeEventJS;post(Ldev/latvian/kubejs/script/ScriptType;Ljava/lang/String;)Z", remap = false))
    private void buildRecipeRegistry(RecipeManager manager, Map<ResourceLocation, JsonObject> jsonMap, CallbackInfo ci) {
        for(RecipeJS recipe : this.originalRecipes) {
            KubeUtil.originalRecipesByHash.put(recipe.getOrCreateId(), recipe);
        }
    }

    @Inject(method = "post(Lnet/minecraft/world/item/crafting/RecipeManager;Ljava/util/Map;)V", at = @At("RETURN"))
    private void clearRecipeRegistry(RecipeManager manager, Map<ResourceLocation, JsonObject> jsonMap, CallbackInfo ci) {
        KubeUtil.originalRecipesByHash.clear();
    }
}
