package org.embeddedt.modernfix.neoforge.dynresources;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import net.minecraft.client.resources.model.ModelBakery;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.embeddedt.modernfix.dynamicresources.DynamicModelProvider;

import java.util.*;

/**
 * Stores a list of all known default block/item models in the game, and provides a namespaced version
 * of the model registry that emulates vanilla keySet behavior.
 */
public class ModelBakeEventHelper {
    private final DynamicModelProvider modelRegistry;
    private final MutableGraph<String> dependencyGraph;
    public ModelBakeEventHelper(DynamicModelProvider modelRegistry) {
        this.modelRegistry = modelRegistry;
        this.dependencyGraph = GraphBuilder.undirected().build();
        ModList.get().forEachModContainer((id, mc) -> {
            this.dependencyGraph.addNode(id);
            for(IModInfo.ModVersion version : mc.getModInfo().getDependencies()) {
                this.dependencyGraph.addNode(version.getModId());
            }
        });
        for(String id : this.dependencyGraph.nodes()) {
            Optional<? extends ModContainer> mContainer = ModList.get().getModContainerById(id);
            if(mContainer.isPresent()) {
                for(IModInfo.ModVersion version : mContainer.get().getModInfo().getDependencies()) {
                    // avoid self-loops
                    if(!Objects.equals(id, version.getModId()))
                        this.dependencyGraph.putEdge(id, version.getModId());
                }
            }
        }
    }

    public ModelBakery.BakingResult createDynamicResult() {
        return new ModelBakery.BakingResult(
                new ModelBakery.MissingModels(
                        this.modelRegistry.getMissingBakedModel(),
                        this.modelRegistry.getMissingItemModel()
                ),
                this.modelRegistry.getTopLevelEmulatedRegistry(),
                this.modelRegistry.getItemModelEmulatedRegistry(),
                this.modelRegistry.getItemPropertiesEmulatedRegistry()
                // TODO: Supply the StandaloneModelLoader.BakedModels?
        );
    }
}
