package org.embeddedt.modernfix.mixin.perf.blast_search_trees;

import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.CreativeModeTabSearchRegistry;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow @Final private SearchRegistry searchRegistry;

    @Shadow public abstract <T> void populateSearchTree(SearchRegistry.Key<T> arg, List<T> list);

    @Inject(method = "createSearchTrees", at = @At("HEAD"), cancellable = true)
    private void replaceSearchTrees(CallbackInfo ci) {
        ci.cancel();
        Optional<? extends ModContainer> jeiContainer = ModList.get().getModContainerById("jei");
        SearchRegistry.TreeBuilderSupplier<ItemStack> nameSupplier, tagSupplier;
        if(ModList.get().isLoaded("roughlyenoughitems")) {
            ModernFix.LOGGER.info("Replaced creative search logic with REI");
            nameSupplier = list -> new REIBackedSearchTree(false);
            tagSupplier = list -> new REIBackedSearchTree(true);
        } else if(jeiContainer.isPresent()) {
            ModernFix.LOGGER.info("Replaced creative search logic with JEI");
            nameSupplier = list -> new JEIBackedSearchTree(false);
            tagSupplier = list -> new JEIBackedSearchTree(true);
        } else {
            ModernFix.LOGGER.info("Replaced creative search logic with dummy implementation");
            nameSupplier = tagSupplier = list -> new DummySearchTree<>();
        }
        this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, nameSupplier);
        for(SearchRegistry.Key<ItemStack> nameKey : CreativeModeTabSearchRegistry.getNameSearchKeys().values()) {
            this.searchRegistry.register(nameKey, nameSupplier);
        }
        this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, tagSupplier);
        for(SearchRegistry.Key<ItemStack> tagKey : CreativeModeTabSearchRegistry.getTagSearchKeys().values()) {
            this.searchRegistry.register(tagKey, tagSupplier);
        }
        Map<CreativeModeTab, SearchRegistry.Key<ItemStack>> tagSearchKeys = CreativeModeTabSearchRegistry.getTagSearchKeys();
        CreativeModeTabSearchRegistry.getNameSearchKeys().forEach((tab, nameSearchKey) -> {
            SearchRegistry.Key<ItemStack> tagSearchKey = tagSearchKeys.get(tab);
            tab.setSearchTreeBuilder((contents) -> {
                this.populateSearchTree(nameSearchKey, contents);
                this.populateSearchTree(tagSearchKey, contents);
            });
        });
        this.searchRegistry.register(SearchRegistry.RECIPE_COLLECTIONS, list -> new DummySearchTree<>());
    }
}
