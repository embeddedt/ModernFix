package org.embeddedt.modernfix.searchtree;

import com.google.common.collect.ImmutableList;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.ingredients.IngredientFilterApi;
import mezz.jei.library.runtime.JeiRuntime;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

    private static final Field filterField;
    private static final MethodHandle getIngredientListUncached;

    static {
        MethodHandle m;
        Field f;
        try {
            Method jeiMethod = IngredientFilter.class.getDeclaredMethod("getIngredientListUncached", String.class);
            jeiMethod.setAccessible(true);
            m = MethodHandles.lookup().unreflect(jeiMethod);
            f = IngredientFilterApi.class.getDeclaredField("ingredientFilter");
            f.setAccessible(true);
        } catch(ReflectiveOperationException | RuntimeException | NoClassDefFoundError e) {
            m = null;
            f = null;
        }
        getIngredientListUncached = m;
        filterField = f;
    }

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
            List<ITypedIngredient<?>> ingredients;
            String finalSearchTerm = filteringByTag ? ("$" + pSearchText) : pSearchText;
            try {
                ingredients = (List<ITypedIngredient<?>>)getIngredientListUncached.invokeExact(filter, finalSearchTerm);
            } catch(Throwable e) {
                ModernFix.LOGGER.error("Error searching", e);
                ingredients = ImmutableList.of();
            }
            for(ITypedIngredient<?> ingredient : ingredients) {
                if(ingredient.getIngredient() instanceof ItemStack) {
                    listCache.add((ItemStack)ingredient.getIngredient());
                }
            }
            lastSearchText = pSearchText;
        }
        return listCache;
    }

    public static final SearchTreeProviderRegistry.Provider PROVIDER = new SearchTreeProviderRegistry.Provider() {
        @Override
        public SearchTree<ItemStack> getSearchTree(boolean tag) {
            return new JEIBackedSearchTree(tag);
        }

        @Override
        public boolean canUse() {
            return ModernFixPlatformHooks.INSTANCE.modPresent("jei") && !ModernFixPlatformHooks.INSTANCE.modPresent("emi") && getIngredientListUncached != null && filterField != null;
        }

        @Override
        public String getName() {
            return "JEI";
        }
    };
}
