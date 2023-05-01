package org.embeddedt.modernfix.duck;

import dev.latvian.kubejs.item.ItemStackJS;

import java.util.Set;

public interface ICachedIngredientJS {
    /**
     * Returns a cached list of item stacks for the given ingredient. The user must not attempt to modify any contents
     * of these stacks.
     * @return cached set of stacks
     */
    Set<ItemStackJS> getCachedStacks();
}
