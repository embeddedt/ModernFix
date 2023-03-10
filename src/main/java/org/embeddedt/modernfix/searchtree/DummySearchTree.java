package org.embeddedt.modernfix.searchtree;

import net.minecraft.client.searchtree.MutableSearchTree;
import net.minecraft.client.searchtree.ReloadableIdSearchTree;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Dummy search tree that stores nothing and returns nothing on searches.
 */
public class DummySearchTree<T> extends ReloadableIdSearchTree<T> implements MutableSearchTree<T> {
    public DummySearchTree() {
        super(t -> Stream.empty());
    }

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
