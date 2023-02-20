package org.embeddedt.modernfix.mixin.perf.kubejs;

import dev.latvian.kubejs.item.ItemStackJS;
import dev.latvian.kubejs.item.ingredient.IngredientJS;
import dev.latvian.kubejs.item.ingredient.TagIngredientJS;
import net.minecraft.tags.Tag;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TagIngredientJS.class)
public abstract class TagIngredientJSMixin {
    @Shadow public abstract Tag<Item> getActualTag();

    /**
     * @author embeddedt
     * @reason avoid pointless construction of many ItemStack objects
     */
    @Inject(method = "anyStackMatches", at = @At("HEAD"), cancellable = true, remap = false)
    private void checkItemOnly(IngredientJS ingredient, CallbackInfoReturnable<Boolean> cir) {
        if(ingredient instanceof ItemStackJS) {
            cir.setReturnValue(((ItemStackJS)ingredient).getItem().is(this.getActualTag()));
        }
    }
}
