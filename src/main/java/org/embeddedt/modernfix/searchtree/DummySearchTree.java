package org.embeddedt.modernfix.searchtree;

import net.minecraft.client.util.IMutableSearchTree;

import java.util.Collections;
import java.util.List;

/**
 * Dummy search tree that stores nothing and returns nothing on searches.
 */
public class DummySearchTree<T> implements IMutableSearchTree<T> {
    @Override
    public void add(T pObj) {

    }

    @Override
    public void clear() {

    }

    @Override
    public void refresh() {

    }

    @Override
    public List<T> search(String pSearchText) {
        return Collections.emptyList();
    }
}
