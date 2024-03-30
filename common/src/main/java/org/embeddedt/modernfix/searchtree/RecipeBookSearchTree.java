package org.embeddedt.modernfix.searchtree;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RecipeBookSearchTree extends DummySearchTree<RecipeCollection> {
    private final SearchTree<ItemStack> stackCollector;
    private Map<Item, List<RecipeCollection>> collectionsByItem = null;
    private final List<RecipeCollection> allCollections = new ArrayList<>();

    public RecipeBookSearchTree(SearchTree<ItemStack> stackCollector) {
        this.stackCollector = stackCollector;
    }

    private Map<Item, List<RecipeCollection>> populateCollectionMap() {
        Map<Item, List<RecipeCollection>> collections = this.collectionsByItem;
        if(collections == null) {
            collections = new Object2ObjectOpenHashMap<>();
            Map<Item, List<RecipeCollection>> finalCollection = collections;
            for(RecipeCollection collection : allCollections) {
                collection.getRecipes().stream().map(recipe -> recipe.getResultItem().getItem()).distinct().forEach(item -> {
                    finalCollection.computeIfAbsent(item, k -> new ArrayList<>()).add(collection);
                });
            }
            this.collectionsByItem = collections;
        }
        return collections;
    }

    @Override
    public void add(RecipeCollection pObj) {
        this.allCollections.add(pObj);
    }

    @Override
    public void clear() {
        this.allCollections.clear();
    }

    @Override
    public void refresh() {
        this.collectionsByItem = null;
    }

    @Override
    public List<RecipeCollection> search(String pSearchText) {
        // Avoid constructing the recipe collection map until the first real search
        if(pSearchText.trim().length() == 0) {
            return this.allCollections;
        }
        List<ItemStack> stacks = stackCollector.search(pSearchText);
        Map<Item, List<RecipeCollection>> collections = this.populateCollectionMap();
        return stacks.stream().map(ItemStack::getItem).distinct().flatMap(item -> collections.getOrDefault(item, Collections.emptyList()).stream()).collect(Collectors.toList());
    }
}
