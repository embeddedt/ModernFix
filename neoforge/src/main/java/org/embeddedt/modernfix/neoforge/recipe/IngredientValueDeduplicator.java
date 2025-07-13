package org.embeddedt.modernfix.neoforge.recipe;

import com.google.common.math.IntMath;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.embeddedt.modernfix.neoforge.mixin.perf.ingredient_item_deduplication.PatchedDataComponentMapAccessor;

/**
 * @author embeddedt (original inspiration from Uncandango's AllTheLeaks mod)
 */
public class IngredientValueDeduplicator {
    private static final ObjectOpenCustomHashSet<Ingredient.ItemValue> VALUES = new ObjectOpenCustomHashSet<>(new Hash.Strategy<>() {
        @Override
        public int hashCode(Ingredient.ItemValue o) {
            if (o == null) {
                return 0;
            }
            var stack = o.item();
            int hash = 31 * stack.getItem().hashCode();
            if (stack.getComponents() instanceof PatchedDataComponentMapAccessor comps) {
                var patch = comps.mfix$getPatch();
                for (var entry : Reference2ObjectMaps.fastIterable(patch)) {
                    int keyHash = System.identityHashCode(entry.getKey()) & 0xff;
                    var value = entry.getValue();
                    int valueHash = value.isPresent() ? System.identityHashCode(value.get()) : 0;
                    hash += IntMath.pow(31, keyHash) * valueHash;
                }
            } else {
                hash += System.identityHashCode(stack.getComponents());
            }
            return hash;
        }

        private boolean areComponentsSame(ItemStack a, ItemStack b) {
            // Compare using stricter logic than vanilla: require the prototype maps to be identity-equal, and require
            // the values in the patch to also be identity-equal. This works around Neo allowing Holder.Reference objects
            // made with the server & client registries to be considered equal.
            if (a.getComponents() instanceof PatchedDataComponentMapAccessor aComps && b.getComponents() instanceof PatchedDataComponentMapAccessor bComps) {
                if (aComps.mfix$getPrototype() != bComps.mfix$getPrototype()) {
                    return false;
                }
                var aPatch = aComps.mfix$getPatch();
                var bPatch = bComps.mfix$getPatch();
                if (aPatch != bPatch) {
                    if (aPatch.size() != bPatch.size()) {
                        return false;
                    }
                    for (var entry : Reference2ObjectMaps.fastIterable(aPatch)) {
                        var value = bPatch.get(entry.getKey());
                        if (value == null) {
                            return false;
                        }
                        if (value.isPresent() != entry.getValue().isPresent()) {
                            return false;
                        } else if (value.isPresent() && value.get() != entry.getValue().get()) {
                            return false;
                        }
                    }
                }
                return true;
            } else {
                return a.getComponents() == b.getComponents();
            }
        }

        private boolean areStacksSame(ItemStack a, ItemStack b) {
            if (!a.is(b.getItem())) {
                return false;
            }
            if (a.isEmpty() != b.isEmpty()) {
                return false;
            }
            if (!areComponentsSame(a, b)) {
                return false;
            }
            return a.getCount() == b.getCount();
        }

        @Override
        public boolean equals(Ingredient.ItemValue a, Ingredient.ItemValue b) {
            return a == b || a != null && b != null && areStacksSame(a.item(), b.item());
        }
    });

    public static Ingredient.Value deduplicate(Ingredient.Value value) {
        if (value != null && value.getClass() == Ingredient.ItemValue.class) {
            synchronized (VALUES) {
                return VALUES.addOrGet((Ingredient.ItemValue)value);
            }
        } else {
            return value;
        }
    }
}
