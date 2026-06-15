package org.embeddedt.modernfix.searchtree;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.library.runtime.JeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.embeddedt.modernfix.ModernFix;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@JeiPlugin
public class JEIRuntimeCapturer implements IModPlugin {
    private static JeiRuntime runtimeHandle = null;

    private static Set<CreativeModeTab> representedTabs = null;

    public static Optional<JeiRuntime> runtime() {
        return Optional.ofNullable(runtimeHandle);
    }

    /**
     * Return the set of all tabs which currently appear to be represented in JEI.
     */
    public static Set<CreativeModeTab> getRepresentedTabs() {
        if (representedTabs == null) {
            Reference2IntOpenHashMap<CreativeModeTab> countsByTab = new Reference2IntOpenHashMap<>();

            var allTabs = CreativeModeTabs.allTabs().stream().filter(t -> t.getType() != CreativeModeTab.Type.SEARCH).toList();

            for (var stack : runtimeHandle.getIngredientManager().getAllItemStacks()) {
                //noinspection ForLoopReplaceableByForEach
                for (int i = 0; i < allTabs.size(); i++) {
                    var tab = allTabs.get(i);
                    if (tab.getSearchTabDisplayItems().contains(stack)) {
                        countsByTab.addTo(tab, 1);
                    }
                }
            }

            Set<CreativeModeTab> tabs = new HashSet<>();

            // Always allow search tab to be shown
            tabs.add(CreativeModeTabs.searchTab());

            for (var tab : allTabs) {
                int expectedDisplayItems = tab.getSearchTabDisplayItems().size();
                int actualItems = countsByTab.getOrDefault(tab, 0);
                // Allow JEI to be used as the backing store if it has at least 25% of the items
                if (actualItems >= (expectedDisplayItems / 4)) {
                    tabs.add(tab);
                }
            }

            representedTabs = Set.copyOf(tabs);
        }

        return representedTabs;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(ModernFix.MODID, "capturer");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        if (jeiRuntime instanceof JeiRuntime)
            runtimeHandle = (JeiRuntime)jeiRuntime;


    }

    @Override
    public void onRuntimeUnavailable() {
        runtimeHandle = null;
        representedTabs = null;
    }
}
