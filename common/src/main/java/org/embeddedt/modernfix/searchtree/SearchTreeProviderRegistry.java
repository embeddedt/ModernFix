package org.embeddedt.modernfix.searchtree;

import net.minecraft.client.searchtree.ReloadableIdSearchTree;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SearchTreeProviderRegistry {
    private static final List<Provider> searchTreeProviders = new ArrayList<>();

    public static synchronized Provider getSearchTreeProvider() {
        for(Provider p : searchTreeProviders) {
            if(p.canUse())
                return p;
        }
        return DummySearchTree.PROVIDER;
    }

    public static synchronized void register(Provider p) {
        if(p.canUse())
            searchTreeProviders.add(p);
    }

    public interface Provider {
        ReloadableIdSearchTree<ItemStack> getSearchTree(boolean tag);
        boolean canUse();
        String getName();
    }
}
