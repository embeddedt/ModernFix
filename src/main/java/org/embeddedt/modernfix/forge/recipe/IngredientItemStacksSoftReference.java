package org.embeddedt.modernfix.forge.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

public class IngredientItemStacksSoftReference extends SoftReference<ItemStack[]> {
    private final Ingredient ingredient;

    private static final ReferenceQueue<ItemStack[]> QUEUE = new ReferenceQueue<>();
    private static final Thread DISCARD_THREAD = new Thread(IngredientItemStacksSoftReference::clearReferences, "Ingredient reference clearing thread");

    static {
        DISCARD_THREAD.setPriority(Thread.NORM_PRIORITY + 2);
        DISCARD_THREAD.setDaemon(true);
        DISCARD_THREAD.start();
    }

    public IngredientItemStacksSoftReference(Ingredient ingredient, ItemStack[] stacks) {
        super(stacks, QUEUE);
        this.ingredient = ingredient;
    }

    private static void clearReferences() {
        while (true) {
            Reference<? extends ItemStack[]> ref;
            try {
                ref = QUEUE.remove();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (ref instanceof IngredientItemStacksSoftReference ingRef && ingRef.ingredient instanceof ExtendedIngredient extIng) {
                // Null out the reference to the SoftReference object, to allow the SoftReference itself to be garbage collected.
                extIng.mfix$clearReference();
            }
        }
    }
}
