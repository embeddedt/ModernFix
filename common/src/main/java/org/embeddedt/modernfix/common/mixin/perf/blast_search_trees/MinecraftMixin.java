package org.embeddedt.modernfix.common.mixin.perf.blast_search_trees;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.searchtree.RecipeBookSearchTree;
import org.embeddedt.modernfix.searchtree.SearchTreeProviderRegistry;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
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
        mfix$runItemFillingQuirk();
        this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, provider.getSearchTree(false));
        this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, provider.getSearchTree(true));
        this.searchRegistry.register(SearchRegistry.RECIPE_COLLECTIONS, new RecipeBookSearchTree(provider.getSearchTree(false)));
        // grab components for all key mappings in order to prevent them from being loaded off-thread later
        // this populates the LazyLoadedValues
        // we also need to suppress GLFW errors to prevent crashes if a key is missing
        GLFWErrorCallback oldCb = GLFW.glfwSetErrorCallback(null);
        for(KeyMapping mapping : KeyMapping.ALL.values()) {
            mapping.getTranslatedKeyMessage();
        }
        GLFW.glfwSetErrorCallback(oldCb);
        ci.cancel();
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
