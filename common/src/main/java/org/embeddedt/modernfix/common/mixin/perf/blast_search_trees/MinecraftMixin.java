package org.embeddedt.modernfix.common.mixin.perf.blast_search_trees;

import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SearchRegistry;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.searchtree.DummySearchTree;
import org.embeddedt.modernfix.searchtree.SearchTreeProviderRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
@ClientOnlyMixin
public class MinecraftMixin {
    @Shadow @Final private SearchRegistry searchRegistry;

    @Inject(method = "createSearchTrees", at = @At("HEAD"), cancellable = true)
    private void replaceSearchTrees(CallbackInfo ci) {
        SearchTreeProviderRegistry.Provider provider = SearchTreeProviderRegistry.getSearchTreeProvider();
        if(provider == null)
            return;
        ModernFix.LOGGER.info("Replacing search trees with '{}' provider", provider.getName());
        this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, provider.getSearchTree(false));
        this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, provider.getSearchTree(true));
        this.searchRegistry.register(SearchRegistry.RECIPE_COLLECTIONS, new DummySearchTree<>());
        ci.cancel();
    }
}
