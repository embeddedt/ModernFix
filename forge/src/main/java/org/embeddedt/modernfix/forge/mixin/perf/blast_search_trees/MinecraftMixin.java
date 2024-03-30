package org.embeddedt.modernfix.forge.mixin.perf.blast_search_trees;

import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.forge.searchtree.RecipeBookSearchTree;
import org.embeddedt.modernfix.searchtree.DummySearchTree;
import org.embeddedt.modernfix.forge.searchtree.JEIBackedSearchTree;
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
        ci.cancel();
        mfix$runItemFillingQuirk();
        if(ModList.get().getModFileById("jei") != null && ModList.get().getModFileById("roughlyenoughitems") == null) {
            this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, new JEIBackedSearchTree(false));
            this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, new JEIBackedSearchTree(true));
            this.searchRegistry.register(SearchRegistry.RECIPE_COLLECTIONS, new RecipeBookSearchTree(new JEIBackedSearchTree(false)));
        } else {
            this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, new DummySearchTree<>());
            this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, new DummySearchTree<>());
            this.searchRegistry.register(SearchRegistry.RECIPE_COLLECTIONS, new DummySearchTree<>());
        }
    }


    private void mfix$runItemFillingQuirk() {
        // quirk: call fillItemCategory on all items in the registry in case they do classloading inside it
        // see https://github.com/Shadows-of-Fire/GatewaysToEternity/issues/29 for an example of this
        NonNullList<ItemStack> stacks = NonNullList.create();
        for(Item item : Registry.ITEM) {
            stacks.clear();
            item.fillItemCategory(CreativeModeTab.TAB_SEARCH, stacks);
        }
    }
}
