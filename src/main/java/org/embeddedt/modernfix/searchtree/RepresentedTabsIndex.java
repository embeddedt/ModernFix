package org.embeddedt.modernfix.searchtree;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

/** Narrows tab membership probes by the hash used by vanilla's item set. */
final class RepresentedTabsIndex {
    private static final Hash.Strategy<? super ItemStack> TYPE_AND_TAG = typeAndTag();
    // Method names change between development and production mappings. Checking return
    // types instead also catches covariant overrides and their compiler-generated bridges.
    private static final ClassValue<Boolean> USES_STOCK_COLLECTION_METHODS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                for (Class<?> current = type; current != CreativeModeTab.class; current = current.getSuperclass()) {
                    if (current == null) return false;
                    for (Method method : current.getDeclaredMethods()) {
                        if (Collection.class.isAssignableFrom(method.getReturnType())) return false;
                    }
                }
                return true;
            } catch (SecurityException | LinkageError ignored) {
                return false;
            }
        }
    };
    private RepresentedTabsIndex() {
    }

    private static Hash.Strategy<? super ItemStack> typeAndTag() {
        try {
            if (ItemStackLinkedSet.createTypeAndTagSet() instanceof ObjectLinkedOpenCustomHashSet<ItemStack> s && s.getClass() == ObjectLinkedOpenCustomHashSet.class)
                return s.strategy();
        } catch (RuntimeException ignored) {
            // An unfamiliar item-set implementation retains the original counting loop.
        }
        return null;
    }

    /**
     * In place of {@code runtimeHandle.getIngredientManager().getAllItemStacks()} inside getRepresentedTabs: the original
     * collection is passed in; the counts the original loop would make go into {@code counts}; the return value is what the
     * original loop then iterates (an empty list once counted, or the original collection to let it count itself).
     */
    public static Collection<ItemStack> count(Collection<ItemStack> all, List<CreativeModeTab> tabs, Reference2IntOpenHashMap<CreativeModeTab> counts) {
        if (TYPE_AND_TAG == null || all == null || tabs == null || counts == null) return all;
        // Preparing the index reads each tab before the original loop. Custom getters may have
        // side effects, including mutating another tab's contents, so retain the entire original
        // loop if any tab may override its collection getters. Metadata-only subclasses can
        // inherit the stock getters; unrelated collection methods conservatively fall back too.
        for (CreativeModeTab tab : tabs) {
            if (tab == null || (tab.getClass() != CreativeModeTab.class
                    && !USES_STOCK_COLLECTION_METHODS.get(tab.getClass()))) return all;
        }
        int n = tabs.size();
        Collection<?>[] indexed = new Collection<?>[n];
        Int2ObjectOpenHashMap<IntArrayList> byHash = new Int2ObjectOpenHashMap<>();
        try {
            for (int i = 0; i < n; i++) {
                Collection<ItemStack> c = tabs.get(i).getSearchTabDisplayItems();
                if (c == null || c.getClass() != ObjectLinkedOpenCustomHashSet.class
                        || ((ObjectLinkedOpenCustomHashSet<ItemStack>) c).strategy() != TYPE_AND_TAG) return all;
                boolean usable = true;
                for (ItemStack e : c) {
                    if (e == null || e.isEmpty()) {     // an empty stack equals every empty stack whatever its hash
                        usable = false;
                        break;
                    }
                }
                if (!usable) continue;
                for (ItemStack e : c) {
                    int h = TYPE_AND_TAG.hashCode(e);
                    IntArrayList list = byHash.get(h);
                    if (list == null) byHash.put(h, list = new IntArrayList(2));
                    if (list.isEmpty() || list.getInt(list.size() - 1) != i) list.add(i);
                }
                indexed[i] = c;
            }
        } catch (RuntimeException ignored) {
            return all;
        }
        countInto(all, tabs, indexed, byHash, counts);
        return List.of();
    }

    private static void countInto(Collection<ItemStack> all, List<CreativeModeTab> tabs, Collection<?>[] indexed, Int2ObjectOpenHashMap<IntArrayList> byHash,
                                  Reference2IntOpenHashMap<CreativeModeTab> counts) {
        int n = tabs.size();
        for (ItemStack stack : all) {
            IntArrayList candidates = null;
            boolean hashed = false;
            if (stack != null && !stack.isEmpty()) {
                try {
                    candidates = byHash.get(TYPE_AND_TAG.hashCode(stack));
                    hashed = true;
                } catch (RuntimeException ignored) {
                    hashed = false;              // every tab gets the original contains(), which fails as it did
                }
            }
            int next = 0;
            for (int i = 0; i < n; i++) {
                CreativeModeTab tab = tabs.get(i);
                Collection<ItemStack> c = tab.getSearchTabDisplayItems();
                if (hashed && c != null && c == indexed[i]) {
                    while (candidates != null && next < candidates.size() && candidates.getInt(next) < i) next++;
                    if (candidates == null || next >= candidates.size() || candidates.getInt(next) != i) {
                        continue;
                    }
                }
                if (c.contains(stack)) counts.addTo(tab, 1);
            }
        }
    }

}
