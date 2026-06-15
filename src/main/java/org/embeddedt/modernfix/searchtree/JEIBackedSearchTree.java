package org.embeddedt.modernfix.searchtree;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.ingredients.IngredientFilterApi;
import mezz.jei.library.runtime.JeiRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.searchtree.PlainTextSearchTree;
import net.minecraft.client.searchtree.RefreshableSearchTree;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Uses JEI to handle search tree lookups.
 */
public class JEIBackedSearchTree extends DummySearchTree<ItemStack> {
    private final boolean filteringByTag;
    private String lastSearchText = "";
    private final List<ItemStack> listCache = new ArrayList<>();
    private final @Nullable CreativeModeTab filterTab;

    private PlainTextSearchTree<ItemStack> fallbackSearchTree;

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

    public JEIBackedSearchTree(boolean filteringByTag, @Nullable CreativeModeTab tab) {
        this.filteringByTag = filteringByTag;
        this.filterTab = tab;
    }

    @Override
    public List<ItemStack> search(String pSearchText) {
        Optional<JeiRuntime> runtime = JEIRuntimeCapturer.runtime();
        if (runtime.isPresent() && (filterTab == null || JEIRuntimeCapturer.getRepresentedTabs().contains(filterTab))) {
            IngredientFilterApi iFilterApi = (IngredientFilterApi)runtime.get().getIngredientFilter();
            IngredientFilter filter;
            try {
                filter = (IngredientFilter)filterField.get(iFilterApi);
            } catch(ReflectiveOperationException e) {
                ModernFix.LOGGER.error(e);
                return Collections.emptyList();
            }
            return this.searchJEI(filter, pSearchText);
        } else if (filterTab != null) {
            /* Construct a search tree for that particular tab */
            return this.searchFallback(pSearchText);
        } else {
            /* Use the default, dummy implementation */
            return super.search(pSearchText);
        }
    }

    private List<ItemStack> searchJEI(IngredientFilter filter, String pSearchText) {
        if(!pSearchText.equals(lastSearchText)) {
            listCache.clear();
            Stream<ITypedIngredient<?>> ingredients;
            String finalSearchTerm = filteringByTag ? ("$" + pSearchText) : pSearchText;
            try {
                ingredients = (Stream<ITypedIngredient<?>>)getIngredientListUncached.invokeExact(filter, finalSearchTerm);
            } catch(Throwable e) {
                ModernFix.LOGGER.error("Error searching", e);
                ingredients = Stream.empty();
            }
            var filteredSet = filterTab != null ? filterTab.getSearchTabDisplayItems() : null;
            ingredients.toList().forEach(ingredient -> {
                if(ingredient.getIngredient() instanceof ItemStack stack) {
                    // Only show the item if it would appear in the corresponding creative tab
                    if (filteredSet == null || filteredSet.contains(stack)) {
                        listCache.add(stack);
                    }
                }
            });
            lastSearchText = pSearchText;
        }
        return listCache;
    }

    private List<ItemStack> searchFallback(String pSearchText) {
        Objects.requireNonNull(filterTab);

        if (fallbackSearchTree == null) {
            fallbackSearchTree = PlainTextSearchTree.create(new ArrayList<>(filterTab.getSearchTabDisplayItems()), stack ->
                    stack.getTooltipLines(null, TooltipFlag.Default.NORMAL.asCreative()).stream()
                        .map(c -> ChatFormatting.stripFormatting(c.getString()).trim())
                        .filter(s -> !s.isEmpty()));
        }

        return fallbackSearchTree.search(pSearchText);
    }

    public static final SearchTreeProviderRegistry.Provider PROVIDER = new SearchTreeProviderRegistry.Provider() {
        @Override
        public RefreshableSearchTree<ItemStack> getSearchTree(boolean tag, @Nullable CreativeModeTab tab) {
            return new JEIBackedSearchTree(tag, tab);
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
