package org.embeddedt.modernfix.mixin.perf.blast_search_trees;

import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.searchtree.DummySearchTree;
import org.embeddedt.modernfix.searchtree.JEIBackedSearchTree;
import org.embeddedt.modernfix.searchtree.REIBackedSearchTree;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Minecraft.class)
@ClientOnlyMixin
public class MinecraftMixin {
    @Shadow @Final private SearchRegistry searchRegistry;

    @Inject(method = "createSearchTrees", at = @At("HEAD"), cancellable = true)
    private void replaceSearchTrees(CallbackInfo ci) {
        ci.cancel();
        Optional<? extends ModContainer> jeiContainer = ModList.get().getModContainerById("jei");
        if(ModList.get().isLoaded("roughlyenoughitems")) {
            this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, new REIBackedSearchTree(false));
            this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, new REIBackedSearchTree(true));
        } else if(jeiContainer.isPresent() && jeiContainer.get().getModInfo().getVersion().getMajorVersion() >= 10) {
            this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, new JEIBackedSearchTree(false));
            this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, new JEIBackedSearchTree(true));
        } else {
            this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, new DummySearchTree<>());
            this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, new DummySearchTree<>());
        }
        this.searchRegistry.register(SearchRegistry.RECIPE_COLLECTIONS, new DummySearchTree<>());
    }
}
