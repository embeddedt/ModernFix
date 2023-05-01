package org.embeddedt.modernfix.forge.searchtree;

import mezz.jei.Internal;
import mezz.jei.ingredients.IIngredientListElementInfo;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.runtime.JeiRuntime;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.forge.mixin.perf.blast_search_trees.IngredientFilterInvoker;
import org.embeddedt.modernfix.searchtree.DummySearchTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Uses JEI to handle search tree lookups.
 */
public class JEIBackedSearchTree extends DummySearchTree<ItemStack> {
    private final boolean filteringByTag;
    private String lastSearchText = "";
    private final List<ItemStack> listCache = new ArrayList<>();

    public JEIBackedSearchTree(boolean filteringByTag) {
        this.filteringByTag = filteringByTag;
    }
    @Override
    public List<ItemStack> search(String pSearchText) {
        JeiRuntime runtime = Internal.getRuntime();
        if(runtime != null) {
            return this.searchJEI(Internal.getIngredientFilter(), pSearchText);
        } else {
            /* Use the default, dummy implementation */
            return super.search(pSearchText);
        }
    }

    private List<ItemStack> searchJEI(IngredientFilter filter, String pSearchText) {
        if(!pSearchText.equals(lastSearchText)) {
            listCache.clear();
            List<IIngredientListElementInfo<?>> ingredients = ((IngredientFilterInvoker)filter).invokeGetIngredientListUncached(filteringByTag ? ("$" + pSearchText) : pSearchText);
            for(IIngredientListElementInfo<?> ingredient : ingredients) {
                if(ingredient.getElement().getIngredient() instanceof ItemStack) {
                    listCache.add((ItemStack)ingredient.getElement().getIngredient());
                }
            }
            lastSearchText = pSearchText;
        }
        return listCache;
    }
}
