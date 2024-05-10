package org.embeddedt.modernfix.searchtree;

import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * Dummy search tree that stores nothing and returns nothing on searches.
 */
public class DummySearchTree<T> implements SearchTree<T> {
    public DummySearchTree() {
        super();
    }

    @Override
    public List<T> search(String pSearchText) {
        return Collections.emptyList();
    }

    static final SearchTreeProviderRegistry.Provider PROVIDER = new SearchTreeProviderRegistry.Provider() {

        @Override
        public SearchTree<ItemStack> getSearchTree(boolean tag) {
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
