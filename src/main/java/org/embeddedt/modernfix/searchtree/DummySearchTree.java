package org.embeddedt.modernfix.searchtree;

import net.minecraft.client.util.IMutableSearchTree;
import net.minecraft.client.util.SearchTree;
import net.minecraft.client.util.SearchTreeReloadable;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Dummy search tree that stores nothing and returns nothing on searches.
 */
public class DummySearchTree<T> extends SearchTreeReloadable<T> implements IMutableSearchTree<T> {
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
