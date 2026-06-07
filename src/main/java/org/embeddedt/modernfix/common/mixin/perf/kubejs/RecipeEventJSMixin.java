package org.embeddedt.modernfix.common.mixin.perf.kubejs;

import com.google.gson.JsonElement;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.RecipesEventJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.core.config.ModernFixEarlyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

@Mixin(RecipesEventJS.class)
@RequiresMod("kubejs")
public class RecipeEventJSMixin {

    /**
     * The recipe event object can be leaked in scripts and this wastes 40MB of memory.
     */
    @Inject(method = "post", at = @At("RETURN"), remap = false)
    private void clearRecipeLists(CallbackInfo ci) {
        ModernFix.LOGGER.info("Clearing KubeJS recipe lists...");
        // Even though we are a mixin class, use reflection so this works across a variety of versions
        Field[] fields = RecipesEventJS.class.getDeclaredFields();
        for(Field f : fields) {
            try {
                if(!Modifier.isStatic(f.getModifiers())
                        && (Collection.class.isAssignableFrom(f.getType())
                            || Map.class.isAssignableFrom(f.getType()))
                ) {
                    f.setAccessible(true);
                    Object collection = f.get(this);
                    int size;
                    if(collection instanceof Map) {
                        size = ((Map<?, ?>)collection).size();
                        ((Map<?, ?>)collection).clear();
                    } else if(collection instanceof Collection) {
                        size = ((Collection<?>)collection).size();
                        ((Collection<?>)collection).clear();
                    } else
                        throw new IllegalStateException();
                    ModernFix.LOGGER.debug("Cleared {} with {} entries", f.getName(), size);
                }
            } catch(RuntimeException | ReflectiveOperationException e) {
                ModernFix.LOGGER.debug("Uh oh, couldn't clear field", e);
            }
        }
    }

    /**
     * @author embeddedt
     * @reason once datapackRecipeMap is iterated, it is never referenced again, so clear it to avoid retaining
     * references to the JSON objects
     */
    @Inject(method = "post", at = @At(value = "NEW", target = "()Ljava/util/concurrent/ConcurrentLinkedQueue;", ordinal = 0), remap = false)
    private void modernfix$clearDatapackRecipeMap(RecipeManager recipeManager, Map<ResourceLocation, JsonElement> datapackRecipeMap, CallbackInfo ci) {
        if (ModernFixEarlyConfig.ACTIVE_FEATURE_LEVEL.isAtLeast(FeatureLevel.BETA)) {
            datapackRecipeMap.clear();
        }
    }

    /**
     * @author embeddedt
     * @reason As we start materializing the final recipe objects, null out the JSON references so we avoid having
     * to keep both in memory at the same time
     */
    @Inject(method = "createRecipe", at = @At("RETURN"), remap = false)
    private void modernfix$clearJson(RecipeJS r, CallbackInfoReturnable<Recipe<?>> cir) {
        if (!ModernFixEarlyConfig.ACTIVE_FEATURE_LEVEL.isAtLeast(FeatureLevel.BETA)) {
            return;
        }
        r.json = null;
        r.originalJson = null;
    }
}
