package org.embeddedt.modernfix.searchtree;

import net.minecraft.client.searchtree.RefreshableSearchTree;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * Dummy search tree that stores nothing and returns nothing on searches.
 */
public class DummySearchTree<T> implements RefreshableSearchTree<T> {
    public DummySearchTree() {
        super();
    }

    @Override
    public void refresh() {
        // quirk: call fillItemCategory on all items in the registry in case they do classloading inside it
        // see https://github.com/Shadows-of-Fire/GatewaysToEternity/issues/29 for an example of this
        NonNullList<ItemStack> stacks = NonNullList.create();
        for(Item item : Registry.ITEM) {
            stacks.clear();
            item.fillItemCategory(CreativeModeTab.TAB_SEARCH, stacks);
        }
    }

    @Override
    public List<T> search(String pSearchText) {
        return Collections.emptyList();
    }

    static final SearchTreeProviderRegistry.Provider PROVIDER = new SearchTreeProviderRegistry.Provider() {

        @Override
        public RefreshableSearchTree<ItemStack> getSearchTree(boolean tag) {
            return new DummySearchTree<>();
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public String getName() {
            return "Dummy";
        }
    };
}
