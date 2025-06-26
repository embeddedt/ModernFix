package org.embeddedt.modernfix.common.mixin.perf.lazy_search_tree_registry;

/* TODO: Remove or reimplement
import net.minecraft.client.searchtree.SearchRegistry;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.searchtree.LazySearchTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SearchRegistry.class)
@ClientOnlyMixin
public class SearchRegistryMixin {
    @ModifyVariable(method = "register", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private <T> SearchRegistry.TreeBuilderSupplier<T> useLazyBuilder(SearchRegistry.TreeBuilderSupplier<T> supplier) {
        return LazySearchTree.decorate(supplier);
    }
}
*/