package org.embeddedt.modernfix.util;

import dev.latvian.kubejs.item.ItemStackJS;
import dev.latvian.kubejs.recipe.RecipeJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

public class KubeUtil {
    public static final HashMap<String, Set<ResourceLocation>> matchedIdsForRegex = new HashMap<>();

    public static final HashMap<ResourceLocation, RecipeJS> originalRecipesByHash = new HashMap<>();
    public static Map<Ingredient, Set<ItemStackJS>> ingredientItemCache = Collections.synchronizedMap(new WeakHashMap<>());
    public static Map<Tag<Item>, Set<ItemStackJS>> tagItemCache = Collections.synchronizedMap(new WeakHashMap<>());

    @SubscribeEvent
    public static void clearRegexCache(AddReloadListenerEvent event) {
        matchedIdsForRegex.clear();
        ingredientItemCache.clear();
        tagItemCache.clear();
    }
}
