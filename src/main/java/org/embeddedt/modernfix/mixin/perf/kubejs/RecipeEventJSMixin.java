package org.embeddedt.modernfix.mixin.perf.kubejs;

import dev.latvian.mods.kubejs.recipe.RecipeEventJS;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

@Mixin(RecipeEventJS.class)
public class RecipeEventJSMixin {

    /**
     * The recipe event object can be leaked in scripts and this wastes 40MB of memory.
     */
    @Inject(method = "post", at = @At("RETURN"))
    private void clearRecipeLists(CallbackInfo ci) {
        ModernFix.LOGGER.info("Clearing KubeJS recipe lists...");
        // Even though we are a mixin class, use reflection so this works across a variety of versions
        Field[] fields = RecipeEventJS.class.getDeclaredFields();
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
}
