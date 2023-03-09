package org.embeddedt.modernfix.searchtree;

import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.impl.client.search.AsyncSearchManager;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class REIBackedSearchTree extends DummySearchTree<ItemStack> {
    private final AsyncSearchManager searchManager = new AsyncSearchManager(EntryRegistry.getInstance()::getPreFilteredList, () -> {
        return stack -> true;
    }, EntryStack::normalize);

    private final boolean filteringByTag;
    private String lastSearchText = "";
    private final List<ItemStack> listCache = new ArrayList<>();

    public REIBackedSearchTree(boolean filteringByTag) {
        this.filteringByTag = filteringByTag;
    }
    @Override
    public List<ItemStack> search(String pSearchText) {
        if(true) {
            return this.searchREI(pSearchText);
        } else {
            /* Use the default, dummy implementation */
            return super.search(pSearchText);
        }
    }

    private List<ItemStack> searchREI(String pSearchText) {
        if(!pSearchText.equals(lastSearchText)) {
            listCache.clear();
            this.searchManager.updateFilter(pSearchText);
            List<EntryStack<?>> stacks = this.searchManager.getNow();
            for(EntryStack<?> stack : stacks) {
                if(stack.getType() == VanillaEntryTypes.ITEM) {
                    listCache.add(stack.cheatsAs().getValue());
                }
            }
            lastSearchText = pSearchText;
        }
        return listCache;
    }
}
