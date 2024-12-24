package org.embeddedt.modernfix.dynamicresources;

import net.fabricmc.fabric.api.client.model.loading.v1.BlockStateResolver;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.UnbakedBlockStateModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.modernfix.ModernFix;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FabricDynamicModelHandler implements DynamicModelProvider.DynamicModelPlugin {
    private final List<ModelLoadingPlugin> pluginList;

    // Borrowed from Fabric API, this dispatching logic is extremely trivial

    private static final ResourceLocation[] MODEL_MODIFIER_PHASES = new ResourceLocation[] { ModelModifier.OVERRIDE_PHASE, ModelModifier.DEFAULT_PHASE, ModelModifier.WRAP_PHASE, ModelModifier.WRAP_LAST_PHASE };

    private final Event<ModelModifier.OnLoad> onLoadModifiers = EventFactory.createWithPhases(ModelModifier.OnLoad.class, modifiers -> (model, context) -> {
        for (ModelModifier.OnLoad modifier : modifiers) {
            try {
                model = modifier.modifyModelOnLoad(model, context);
            } catch (Exception exception) {
                ModernFix.LOGGER.error("Failed to modify unbaked model on load", exception);
            }
        }

        return model;
    }, MODEL_MODIFIER_PHASES);

    private final Event<ModelModifier.OnLoadBlock> onLoadBlockModifiers = EventFactory.createWithPhases(ModelModifier.OnLoadBlock.class, modifiers -> (model, context) -> {
        for (ModelModifier.OnLoadBlock modifier : modifiers) {
            try {
                model = modifier.modifyModelOnLoad(model, context);
            } catch (Exception exception) {
                ModernFix.LOGGER.error("Failed to modify unbaked block model on load", exception);
            }
        }

        return model;
    }, MODEL_MODIFIER_PHASES);

    private final Event<ModelModifier.BeforeBakeBlock> beforeBakeBlockModifiers = EventFactory.createWithPhases(ModelModifier.BeforeBakeBlock.class, modifiers -> (model, context) -> {
        for (ModelModifier.BeforeBakeBlock modifier : modifiers) {
            try {
                model = modifier.modifyModelBeforeBake(model, context);
            } catch (Exception exception) {
                ModernFix.LOGGER.error("Failed to modify unbaked block model before bake", exception);
            }
        }

        return model;
    }, MODEL_MODIFIER_PHASES);
    private final Event<ModelModifier.AfterBakeBlock> afterBakeBlockModifiers = EventFactory.createWithPhases(ModelModifier.AfterBakeBlock.class, modifiers -> (model, context) -> {
        for (ModelModifier.AfterBakeBlock modifier : modifiers) {
            try {
                model = modifier.modifyModelAfterBake(model, context);
            } catch (Exception exception) {
                ModernFix.LOGGER.error("Failed to modify baked block model after bake", exception);
            }
        }

        return model;
    }, MODEL_MODIFIER_PHASES);
    private final Event<ModelModifier.BeforeBake> beforeBakeModifiers = EventFactory.createWithPhases(ModelModifier.BeforeBake.class, modifiers -> (model, context) -> {
        for (ModelModifier.BeforeBake modifier : modifiers) {
            try {
                model = modifier.modifyModelBeforeBake(model, context);
            } catch (Exception exception) {
                ModernFix.LOGGER.error("Failed to modify unbaked model before bake", exception);
            }
        }

        return model;
    }, MODEL_MODIFIER_PHASES);
    private final Event<ModelModifier.AfterBake> afterBakeModifiers = EventFactory.createWithPhases(ModelModifier.AfterBake.class, modifiers -> (model, context) -> {
        for (ModelModifier.AfterBake modifier : modifiers) {
            try {
                model = modifier.modifyModelAfterBake(model, context);
            } catch (Exception exception) {
                ModernFix.LOGGER.error("Failed to modify baked model after bake", exception);
            }
        }

        return model;
    }, MODEL_MODIFIER_PHASES);

    public FabricDynamicModelHandler(DynamicModelProvider provider) {
        this.pluginList = ModelLoadingPlugin.getAll();
        var context = new PluginContext(provider);
        for (var plugin : this.pluginList) {
            plugin.initialize(context);
        }
        context.fireResolvers();
    }

    @Override
    public Optional<UnbakedModel> modifyModelOnLoad(Optional<UnbakedModel> model, ResourceLocation id) {
        return Optional.ofNullable(this.onLoadModifiers.invoker().modifyModelOnLoad(model.orElse(null), () -> id));
    }

    @Override
    public UnbakedBlockStateModel modifyBlockModelOnLoad(UnbakedBlockStateModel model, ModelResourceLocation id, BlockState state) {
        return this.onLoadBlockModifiers.invoker().modifyModelOnLoad(model, new ModelModifier.OnLoadBlock.Context() {
            @Override
            public ModelResourceLocation id() {
                return id;
            }

            @Override
            public BlockState state() {
                return state;
            }
        });
    }

    @Override
    public UnbakedModel modifyModelBeforeBake(UnbakedModel model, ResourceLocation id, ModelState state, ModelBaker baker) {
        return beforeBakeModifiers.invoker().modifyModelBeforeBake(model, new ModelModifier.BeforeBake.Context() {
            @Override
            public ResourceLocation id() {
                return id;
            }

            @Override
            public ModelState settings() {
                return state;
            }

            @Override
            public ModelBaker baker() {
                return baker;
            }
        });
    }

    @Override
    public BakedModel modifyModelAfterBake(BakedModel bakedModel, UnbakedModel model, ResourceLocation id, ModelState state, ModelBaker baker) {
        return afterBakeModifiers.invoker().modifyModelAfterBake(bakedModel, new ModelModifier.AfterBake.Context() {
            @Override
            public ResourceLocation id() {
                return id;
            }

            @Override
            public UnbakedModel sourceModel() {
                return model;
            }

            @Override
            public ModelState settings() {
                return state;
            }

            @Override
            public ModelBaker baker() {
                return baker;
            }
        });
    }

    @Override
    public UnbakedBlockStateModel modifyBlockModelBeforeBake(UnbakedBlockStateModel model, ModelResourceLocation id, ModelBaker baker) {
        return beforeBakeBlockModifiers.invoker().modifyModelBeforeBake(model, new ModelModifier.BeforeBakeBlock.Context() {
            @Override
            public ModelResourceLocation id() {
                return id;
            }

            @Override
            public ModelBaker baker() {
                return baker;
            }
        });
    }

    @Override
    public BakedModel modifyBlockModelAfterBake(BakedModel bakedModel, UnbakedBlockStateModel model, ModelResourceLocation id, ModelBaker baker) {
        return afterBakeBlockModifiers.invoker().modifyModelAfterBake(bakedModel, new ModelModifier.AfterBakeBlock.Context() {
            @Override
            public ModelResourceLocation id() {
                return id;
            }

            @Override
            public UnbakedBlockStateModel sourceModel() {
                return model;
            }

            @Override
            public ModelBaker baker() {
                return baker;
            }
        });
    }

    private class PluginContext implements ModelLoadingPlugin.Context {
        private final DynamicModelProvider provider;
        private final Map<Block, BlockStateResolver> resolvers = new HashMap<>();

        private PluginContext(DynamicModelProvider provider) {
            this.provider = provider;
        }

        @Override
        public void addModels(ResourceLocation... ids) {
            /* no-op on dynamic model loader */
        }

        @Override
        public void addModels(Collection<? extends ResourceLocation> ids) {
            /* no-op on dynamic model loader */
        }

        @Override
        public void registerBlockStateResolver(Block block, BlockStateResolver resolver) {
            resolvers.put(block, resolver);
        }

        public void fireResolvers() {
            resolvers.forEach((block, resolver) -> {
                resolver.resolveBlockStates(new BlockStateResolver.Context() {
                    @Override
                    public Block block() {
                        return block;
                    }

                    @Override
                    public void setModel(BlockState state, UnbakedBlockStateModel model) {
                        provider.addUnbakedBlockStateOverride(BlockModelShaper.stateToModelLocation(state), model);
                    }
                });
            });
        }

        @Override
        public Event<ModelModifier.OnLoad> modifyModelOnLoad() {
            return onLoadModifiers;
        }

        @Override
        public Event<ModelModifier.OnLoadBlock> modifyBlockModelOnLoad() {
            return onLoadBlockModifiers;
        }

        @Override
        public Event<ModelModifier.BeforeBake> modifyModelBeforeBake() {
            return beforeBakeModifiers;
        }

        @Override
        public Event<ModelModifier.AfterBake> modifyModelAfterBake() {
            return afterBakeModifiers;
        }

        @Override
        public Event<ModelModifier.BeforeBakeBlock> modifyBlockModelBeforeBake() {
            return beforeBakeBlockModifiers;
        }

        @Override
        public Event<ModelModifier.AfterBakeBlock> modifyBlockModelAfterBake() {
            return afterBakeBlockModifiers;
        }
    }
}
