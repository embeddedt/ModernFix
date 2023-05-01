package org.embeddedt.modernfix.forge.searchtree;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.Internal;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.ingredients.IngredientFilterApi;
import mezz.jei.library.runtime.JeiRuntime;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.forge.mixin.perf.blast_search_trees.IngredientFilterInvoker;
import org.embeddedt.modernfix.searchtree.DummySearchTree;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Uses JEI to handle search tree lookups.
 */
public class JEIBackedSearchTree extends DummySearchTree<ItemStack> {
    private final boolean filteringByTag;
    private String lastSearchText = "";
    private final List<ItemStack> listCache = new ArrayList<>();

    private static Field filterField = null;

    public JEIBackedSearchTree(boolean filteringByTag) {
        this.filteringByTag = filteringByTag;
    }
    @Override
    public List<ItemStack> search(String pSearchText) {
        Optional<JeiRuntime> runtime = JEIRuntimeCapturer.runtime();
        if(runtime.isPresent()) {
            IngredientFilterApi iFilterApi = (IngredientFilterApi)runtime.get().getIngredientFilter();
            IngredientFilter filter;
            try {
                if(filterField == null) {
                    filterField = IngredientFilterApi.class.getDeclaredField("ingredientFilter");
                    filterField.setAccessible(true);
                }
                filter = (IngredientFilter)filterField.get(iFilterApi);
            } catch(ReflectiveOperationException e) {
                ModernFix.LOGGER.error(e);
                return Collections.emptyList();
            }
            return this.searchJEI(filter, pSearchText);
        } else {
            /* Use the default, dummy implementation */
            return super.search(pSearchText);
        }
    }

    private List<ItemStack> searchJEI(IngredientFilter filter, String pSearchText) {
        if(!pSearchText.equals(lastSearchText)) {
            listCache.clear();
            List<ITypedIngredient<?>> ingredients = ((IngredientFilterInvoker)filter).invokeGetIngredientListUncached(filteringByTag ? ("$" + pSearchText) : pSearchText);
            for(ITypedIngredient<?> ingredient : ingredients) {
                if(ingredient.getIngredient() instanceof ItemStack) {
                    listCache.add((ItemStack)ingredient.getIngredient());
                }
            }
            lastSearchText = pSearchText;
        }
        return listCache;
    }
}