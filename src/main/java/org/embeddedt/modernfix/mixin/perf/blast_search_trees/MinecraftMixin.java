package org.embeddedt.modernfix.mixin.perf.blast_search_trees;

import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.embeddedt.modernfix.ModernFix;
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
public class MinecraftMixin {
    @Shadow @Final private SearchRegistry searchRegistry;

    @Inject(method = "createSearchTrees", at = @At("HEAD"), cancellable = true)
    private void replaceSearchTrees(CallbackInfo ci) {
        ci.cancel();
        Optional<? extends ModContainer> jeiContainer = ModList.get().getModContainerById("jei");
        if(ModList.get().isLoaded("roughlyenoughitems")) {
            ModernFix.LOGGER.info("Replaced creative search logic with REI");
            this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, list -> new REIBackedSearchTree(false));
            this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, list -> new REIBackedSearchTree(true));
        } else if(jeiContainer.isPresent() && jeiContainer.get().getModInfo().getVersion().getMajorVersion() >= 10) {
            ModernFix.LOGGER.info("Replaced creative search logic with JEI");
            this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, list -> new JEIBackedSearchTree(false));
            this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, list -> new JEIBackedSearchTree(true));
        } else {
            ModernFix.LOGGER.info("Replaced creative search logic with dummy implementation");
            this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, list -> new DummySearchTree<>());
            this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, list -> new DummySearchTree<>());
        }
        this.searchRegistry.register(SearchRegistry.RECIPE_COLLECTIONS, list -> new DummySearchTree<>());
    }
}
