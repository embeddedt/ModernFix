package org.embeddedt.modernfix.mixin.perf.kubejs;

import dev.latvian.kubejs.item.ItemStackJS;
import dev.latvian.kubejs.item.ingredient.IngredientJS;
import dev.latvian.kubejs.item.ingredient.TagIngredientJS;
import net.minecraft.tags.Tag;
import net.minecraft.world.item.Item;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.duck.ICachedIngredientJS;
import org.embeddedt.modernfix.util.KubeUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(TagIngredientJS.class)
@RequiresMod("kubejs")
public abstract class TagIngredientJSMixin implements ICachedIngredientJS {
    @Shadow public abstract Tag<Item> getActualTag();

    @Shadow(remap = false) public abstract Set<ItemStackJS> getStacks();

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

    @Override
    public Set<ItemStackJS> getCachedStacks() {
        Tag<Item> ourTag = this.getActualTag();
        Set<ItemStackJS> itemSet = KubeUtil.tagItemCache.get(ourTag);
        if(itemSet == null) {
            itemSet = this.getStacks();
            KubeUtil.tagItemCache.put(ourTag, itemSet);
        }
        return itemSet;
    }
}
