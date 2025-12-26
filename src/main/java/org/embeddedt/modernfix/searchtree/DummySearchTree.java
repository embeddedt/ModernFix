package org.embeddedt.modernfix.searchtree;

import net.minecraft.client.searchtree.RefreshableSearchTree;
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
