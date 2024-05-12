package org.embeddedt.modernfix.searchtree;

import net.minecraft.client.searchtree.RefreshableSearchTree;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;

import java.util.ArrayList;
import java.util.List;

public class SearchTreeProviderRegistry {
    private static final List<Provider> searchTreeProviders = new ArrayList<>();

    public static synchronized Provider getSearchTreeProvider() {
        for(Provider p : searchTreeProviders) {
            if(p.canUse())
                return p;
        }
        if(ModernFixMixinPlugin.instance.isOptionEnabled("perf.blast_search_trees.force.Registry"))
            return DummySearchTree.PROVIDER;
        else
            return null;
    }

    public static synchronized void register(Provider p) {
        if(p.canUse())
            searchTreeProviders.add(p);
    }

    public interface Provider {
        RefreshableSearchTree<ItemStack> getSearchTree(boolean tag);
        boolean canUse();
        String getName();
    }
}
