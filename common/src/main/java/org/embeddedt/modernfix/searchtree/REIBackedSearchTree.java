package org.embeddedt.modernfix.searchtree;

import com.google.common.base.Predicates;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.impl.client.search.AsyncSearchManager;
import me.shedaniel.rei.impl.common.entry.type.EntryRegistryImpl;
import me.shedaniel.rei.impl.common.util.HashedEntryStackWrapper;
import net.minecraft.client.searchtree.RefreshableSearchTree;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class REIBackedSearchTree extends DummySearchTree<ItemStack> {
    private final AsyncSearchManager searchManager = createSearchManager();

    private final boolean filteringByTag;
    private String lastSearchText = "";
    private final List<ItemStack> listCache = new ArrayList<>();

    public REIBackedSearchTree(boolean filteringByTag) {
        this.filteringByTag = filteringByTag;
    }
    @Override
    public List<ItemStack> search(String pSearchText) {
        if(true) {
            return this.searchREI(pSearchText);
        } else {
            /* Use the default, dummy implementation */
            return super.search(pSearchText);
        }
    }

    private List<ItemStack> searchREI(String pSearchText) {
        if(!pSearchText.equals(lastSearchText)) {
            listCache.clear();
            this.searchManager.updateFilter(pSearchText);
            List stacks;
            try {
                stacks = this.searchManager.getNow();
            } catch(RuntimeException e) {
                ModernFix.LOGGER.error("Couldn't search for '" + pSearchText + "'", e);
                stacks = Collections.emptyList();
            }
            for(Object o : stacks) {
                EntryStack<?> stack;
                if(o instanceof EntryStack<?>)
                    stack = (EntryStack<?>)o;
                else if(o instanceof HashedEntryStackWrapper) {
                    stack = ((HashedEntryStackWrapper)o).unwrap();
                } else {
                    ModernFix.LOGGER.error("Don't know how to handle {}", o.getClass().getName());
                    continue;
                }
                if(stack.getType() == VanillaEntryTypes.ITEM) {
                    listCache.add(stack.cheatsAs().getValue());
                }
            }
            lastSearchText = pSearchText;
        }
        return listCache;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AsyncSearchManager createSearchManager() {
        Method m, normalizeMethod;
        try {
            try {
                m = EntryRegistryImpl.class.getDeclaredMethod("getPreFilteredComplexList");
                m.setAccessible(true);
                normalizeMethod = HashedEntryStackWrapper.class.getDeclaredMethod("normalize");
                normalizeMethod.setAccessible(true);
            } catch(NoSuchMethodException e) {
                m = EntryRegistryImpl.class.getDeclaredMethod("getPreFilteredList");
                m.setAccessible(true);
                normalizeMethod = EntryStack.class.getDeclaredMethod("normalize");
                normalizeMethod.setAccessible(true);
            }
            final MethodHandle getListMethod = MethodHandles.publicLookup().unreflect(m);
            final MethodHandle normalize = MethodHandles.publicLookup().unreflect(normalizeMethod);
            final EntryRegistryImpl registry = (EntryRegistryImpl)EntryRegistry.getInstance();
            Supplier stackListSupplier = () -> {
                try {
                    return (List)getListMethod.invokeExact(registry);
                } catch(Throwable e) {
                    if(e instanceof RuntimeException)
                        throw (RuntimeException)e;
                    throw new RuntimeException(e);
                }
            };
            UnaryOperator normalizeOperator = o -> {
                try {
                    return normalize.invoke(o);
                } catch(Throwable e) {
                    if(e instanceof RuntimeException)
                        throw (RuntimeException)e;
                    throw new RuntimeException(e);
                }
            };
            Supplier<Predicate<Boolean>> shouldShowStack = () -> {
                return Predicates.alwaysTrue();
            };
            try {
                try {
                    // Old constructor taking Supplier as first arg
                    MethodHandle cn = MethodHandles.publicLookup().findConstructor(AsyncSearchManager.class, MethodType.methodType(void.class, Supplier.class, Supplier.class, UnaryOperator.class));
                    return (AsyncSearchManager)cn.invoke(stackListSupplier, shouldShowStack, normalizeOperator);
                } catch(NoSuchMethodException e) {
                    // New constructor taking Function as first arg
                    MethodHandle cn = MethodHandles.publicLookup().findConstructor(AsyncSearchManager.class, MethodType.methodType(void.class, Function.class, Supplier.class, UnaryOperator.class));
                    return (AsyncSearchManager)cn.invoke((Function<?, ?>)o -> stackListSupplier.get(), shouldShowStack, normalizeOperator);
                }
            } catch(Throwable mhThrowable) {
                throw new ReflectiveOperationException(mhThrowable);
            }
        } catch(ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static final SearchTreeProviderRegistry.Provider PROVIDER = new SearchTreeProviderRegistry.Provider() {
        @Override
        public RefreshableSearchTree<ItemStack> getSearchTree(boolean tag) {
            return new REIBackedSearchTree(tag);
        }

        @Override
        public boolean canUse() {
            return ModernFixPlatformHooks.INSTANCE.modPresent("roughlyenoughitems");
        }

        @Override
        public String getName() {
            return "REI";
        }
    };
}
