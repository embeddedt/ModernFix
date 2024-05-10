package org.embeddedt.modernfix.common.mixin.perf.blast_search_trees;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.searchtree.RecipeBookSearchTree;
import org.embeddedt.modernfix.searchtree.SearchTreeProviderRegistry;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(SessionSearchTrees.class)
@ClientOnlyMixin
public abstract class SessionSearchTreesMixin {
    @Shadow private CompletableFuture<SearchTree<RecipeCollection>> recipeSearch;
    @Shadow private CompletableFuture<SearchTree<ItemStack>> creativeByNameSearch;
    private SearchTreeProviderRegistry.Provider mfix$provider;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        mfix$provider = SearchTreeProviderRegistry.getSearchTreeProvider();
        if(mfix$provider != null) {
            ModernFix.LOGGER.info("Replacing search trees with '{}' provider", mfix$provider.getName());
        }

        // grab components for all key mappings in order to prevent them from being loaded off-thread later
        // this populates the LazyLoadedValues
        // we also need to suppress GLFW errors to prevent crashes if a key is missing
        GLFWErrorCallback oldCb = GLFW.glfwSetErrorCallback(null);
        for(KeyMapping mapping : KeyMapping.ALL.values()) {
            mapping.getTranslatedKeyMessage();
        }
        GLFW.glfwSetErrorCallback(oldCb);
    }

    @ModifyArg(method = "updateRecipes", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/SessionSearchTrees;register(Lnet/minecraft/client/multiplayer/SessionSearchTrees$Key;Ljava/lang/Runnable;)V"), index = 1)
    private Runnable useModernFixRecipeTree(Runnable r, @Local(ordinal = 0, argsOnly = true) ClientRecipeBook clientRecipeBook) {
        if(mfix$provider == null) {
            return r;
        } else {
            return () -> {
                List<RecipeCollection> list = clientRecipeBook.getCollections();
                CompletableFuture<?> old = this.recipeSearch;
                this.recipeSearch = CompletableFuture.supplyAsync(() -> {
                    return new RecipeBookSearchTree(mfix$provider.getSearchTree(false), list);
                });
                old.cancel(true);
            };
        }
    }

    @ModifyArg(method = "updateCreativeTooltips", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/SessionSearchTrees;register(Lnet/minecraft/client/multiplayer/SessionSearchTrees$Key;Ljava/lang/Runnable;)V"), index = 1)
    private Runnable useSearchModItems(Runnable r) {
        if(mfix$provider == null) {
            return r;
        } else {
            return () -> {
                CompletableFuture<?> old = this.creativeByNameSearch;
                this.creativeByNameSearch = CompletableFuture.supplyAsync(() -> {
                    return mfix$provider.getSearchTree(false);
                });
                old.cancel(true);
            };
        }
    }

    @ModifyArg(method = "updateCreativeTags", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/SessionSearchTrees;register(Lnet/minecraft/client/multiplayer/SessionSearchTrees$Key;Ljava/lang/Runnable;)V"), index = 1)
    private Runnable useSearchModTags(Runnable r) {
        if(mfix$provider == null) {
            return r;
        } else {
            return () -> {
                CompletableFuture<?> old = this.creativeByNameSearch;
                this.creativeByNameSearch = CompletableFuture.supplyAsync(() -> {
                    return mfix$provider.getSearchTree(true);
                });
                old.cancel(true);
            };
        }
    }
}
