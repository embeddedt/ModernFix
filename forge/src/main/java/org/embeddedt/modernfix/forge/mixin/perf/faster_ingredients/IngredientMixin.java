package org.embeddedt.modernfix.forge.mixin.perf.faster_ingredients;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.embeddedt.modernfix.forge.recipe.ExtendedIngredient;
import org.embeddedt.modernfix.forge.recipe.IngredientItemStacksSoftReference;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(value = Ingredient.class, priority = 700)
public abstract class IngredientMixin implements ExtendedIngredient {
    @Shadow
    public abstract boolean isVanilla();

    @Shadow @Final
    private Ingredient.Value[] values;

    @Shadow private @Nullable IntList stackingIds;

    @Shadow @Nullable private ItemStack[] itemStacks;

    private volatile IngredientItemStacksSoftReference mfix$cachedItemStacks;

    /**
     * @author embeddedt
     * @reason tag ingredients can be tested without iterating over all items
     */
    @Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/Ingredient;getItems()[Lnet/minecraft/world/item/ItemStack;"), cancellable = true)
    private void modernfix$fasterTagIngredientTest(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (this.isVanilla() && this.values.length == 1 && this.values[0] instanceof Ingredient.TagValue tagValue) {
            cir.setReturnValue(stack.getItemHolder().is(tagValue.tag));
        }
    }

    @Override
    public boolean mfix$hasNoElements() {
        return !this.containsItems();
    }

    @Unique
    private boolean isEmptyTagStack(ItemStack item) {
        return item.getItem() == net.minecraft.world.item.Items.BARRIER && item.getHoverName() instanceof net.minecraft.network.chat.MutableComponent hoverName && hoverName.getString().startsWith("Empty Tag: ");
    }

    @Unique
    private boolean containsItems() {
        for (Ingredient.Value value : this.values) {
            if (value instanceof Ingredient.ItemValue) {
                return true;
            } else if (value instanceof Ingredient.TagValue tagValue) {
                var holderSetOpt = BuiltInRegistries.ITEM.getTag(tagValue.tag);
                if (holderSetOpt.isPresent() && holderSetOpt.get().size() > 0) {
                    return true;
                }
            } else {
                var items = value.getItems();
                if (items.isEmpty() || isEmptyTagStack(items.iterator().next())) {
                    // Doesn't have items
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * @author embeddedt
     * @reason tag ingredients can be converted to stacking IDs without expanding into stacks, since stacking only
     * goes by item ID
     */
    @Inject(method = "getStackingIds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/Ingredient;getItems()[Lnet/minecraft/world/item/ItemStack;"), cancellable = true)
    private void modernfix$fasterTagIngredientStacking(CallbackInfoReturnable<IntList> cir) {
        if (this.isVanilla() && this.values.length == 1 && this.values[0] instanceof Ingredient.TagValue tagValue) {
            var tag = BuiltInRegistries.ITEM.getTag(tagValue.tag);
            if (!tag.isPresent() || tag.get().size() == 0) {
                return;
            }
            var list = new IntArrayList(tag.get().stream().mapToInt(h -> BuiltInRegistries.ITEM.getId(h.value())).toArray());
            list.sort(IntComparators.NATURAL_COMPARATOR);
            this.stackingIds = list;
            cir.setReturnValue(list);
        }
    }

    /**
     * @author embeddedt
     * @reason Change caching of item stacks to use a soft reference, which allows the GC to evict the array under
     * memory pressure/when it hasn't been used.
     */
    @Overwrite
    public ItemStack[] getItems() {
        // For compatibility if mods explicitly force a set of item stacks to be used
        if (this.itemStacks != null) {
            return this.itemStacks;
        }
        var cache = this.mfix$cachedItemStacks;
        if (cache != null) {
            var stacks = cache.get();
            if (stacks != null) {
                return stacks;
            }
        }
        ItemStack[] result = computeItemsArray();
        this.mfix$cachedItemStacks = new IngredientItemStacksSoftReference((Ingredient)(Object)this, result);
        return result;
    }

    private ItemStack[] computeItemsArray() {
        // Fast path for case with one item
        if (this.values.length == 1) {
            if (this.values[0] instanceof Ingredient.ItemValue itemValue) {
                return new ItemStack[] { itemValue.item };
            } else if (this.values[0] instanceof Ingredient.TagValue tagValue) {
                var tag = BuiltInRegistries.ITEM.getTag(tagValue.tag);
                if (tag.isPresent() && tag.get().size() > 0) {
                    var holderSet = tag.get();
                    ItemStack[] result = new ItemStack[holderSet.size()];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = new ItemStack(holderSet.get(i));
                    }
                    return result;
                }
            }
        }
        ArrayList<ItemStack> itemList = new ArrayList<>(2);
        for (var value : this.values) {
            var collection = value.getItems();
            itemList.ensureCapacity(collection.size() + itemList.size());
            for (var item : collection) {
                itemList.add(item);
            }
        }
        return itemList.toArray(ItemStack[]::new);
    }

    @Override
    public void mfix$clearReference() {
        this.mfix$cachedItemStacks = null;
    }
}
