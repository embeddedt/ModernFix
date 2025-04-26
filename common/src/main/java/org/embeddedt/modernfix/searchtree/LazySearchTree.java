package org.embeddedt.modernfix.searchtree;

import com.google.common.base.Stopwatch;
import net.minecraft.client.searchtree.RefreshableSearchTree;
import net.minecraft.client.searchtree.SearchRegistry;
import org.embeddedt.modernfix.ModernFix;

import java.util.List;
import java.util.function.Function;

public class LazySearchTree<T> implements RefreshableSearchTree<T> {
    private final List<T> contents;
    private final Function<List<T>, RefreshableSearchTree<T>> treeBuilder;

    private volatile RefreshableSearchTree<T> realTree;

    public LazySearchTree(List<T> contents, Function<List<T>, RefreshableSearchTree<T>> treeBuilder) {
        this.contents = contents;
        this.treeBuilder = treeBuilder;
    }

    private RefreshableSearchTree<T> getRealTree() {
        var t = realTree;
        if (t == null) {
            synchronized (this) {
                t = realTree;
                if (t == null) {
                    ModernFix.LOGGER.info("Building search tree for {} items (this may take a while)...", contents.size());
                    Stopwatch s = Stopwatch.createStarted();
                    t = this.treeBuilder.apply(contents);
                    t.refresh();
                    s.stop();
                    ModernFix.LOGGER.info("Building search tree for {} items took {}", contents.size(), s);
                    realTree = t;
                }
            }
        }
        return t;
    }

    @Override
    public List<T> search(String query) {
        if (query.isEmpty()) {
            return this.contents;
        }
        return getRealTree().search(query);
    }

    @Override
    public void refresh() {
        var t = this.realTree;
        if (t != null) {
            t.refresh();
        }
    }

    public static <T> SearchRegistry.TreeBuilderSupplier<T> decorate(SearchRegistry.TreeBuilderSupplier<T> originalSupplier) {
        return list -> new LazySearchTree<>(list, originalSupplier);
    }
}
