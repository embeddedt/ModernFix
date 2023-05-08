package org.embeddedt.modernfix.forge.mixin.perf.blast_search_trees;

import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.searchtree.DummySearchTree;
import org.embeddedt.modernfix.searchtree.REIBackedSearchTree;
import org.embeddedt.modernfix.forge.searchtree.JEIBackedSearchTree;
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
        Optional<? extends ModContainer> jeiContainer = ModList.get().getModContainerById("jei");
        if(ModList.get().isLoaded("roughlyenoughitems")) {
            ModernFix.LOGGER.info("Replaced creative search logic with REI");
            this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, new REIBackedSearchTree(false));
            this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, new REIBackedSearchTree(true));
        } else if(jeiContainer.isPresent()) {
            /* ugly hack since getMajorVersion() returns 0 */
            if(jeiContainer.get().getModInfo().getVersion().toString().startsWith("9.")) {
                ModernFix.LOGGER.warn("Not disabling creative search as JEI 9 is in use");
                return;
            }
            ModernFix.LOGGER.info("Replaced creative search logic with JEI");
            this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, new JEIBackedSearchTree(false));
            this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, new JEIBackedSearchTree(true));
        } else {
            ModernFix.LOGGER.info("Completely removed creative search logic");
            this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, new DummySearchTree<>());
            this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, new DummySearchTree<>());
        }
        this.searchRegistry.register(SearchRegistry.RECIPE_COLLECTIONS, new DummySearchTree<>());
        ci.cancel();
    }
}
