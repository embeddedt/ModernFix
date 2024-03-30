package org.embeddedt.modernfix.common.mixin.perf.blast_search_trees;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
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

import java.util.List;

@Mixin(Minecraft.class)
@ClientOnlyMixin
public abstract class MinecraftMixin {
    @Shadow @Final private SearchRegistry searchRegistry;

    @Shadow public abstract <T> void populateSearchTree(SearchRegistry.Key<T> key, List<T> list);

    @Inject(method = "createSearchTrees", at = @At("HEAD"), cancellable = true)
    private void replaceSearchTrees(CallbackInfo ci) {
        SearchTreeProviderRegistry.Provider provider = SearchTreeProviderRegistry.getSearchTreeProvider();
        if(provider == null)
            return;
        ModernFix.LOGGER.info("Replacing search trees with '{}' provider", provider.getName());
        SearchRegistry.TreeBuilderSupplier<ItemStack> nameSupplier = list -> provider.getSearchTree(false);
        SearchRegistry.TreeBuilderSupplier<ItemStack> tagSupplier = list -> provider.getSearchTree(true);
        this.searchRegistry.register(SearchRegistry.CREATIVE_NAMES, nameSupplier);
        this.searchRegistry.register(SearchRegistry.CREATIVE_TAGS, tagSupplier);
        this.searchRegistry.register(SearchRegistry.RECIPE_COLLECTIONS, list -> new RecipeBookSearchTree(provider.getSearchTree(false)));
        ModernFixPlatformHooks.INSTANCE.registerCreativeSearchTrees(this.searchRegistry, nameSupplier, tagSupplier, this::populateSearchTree);
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
}
