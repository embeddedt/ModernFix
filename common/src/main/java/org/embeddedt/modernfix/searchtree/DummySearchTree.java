package org.embeddedt.modernfix.searchtree;

import net.minecraft.client.searchtree.RefreshableSearchTree;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

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
}
