package org.embeddedt.modernfix.fabric.mixin.perf.dynamic_resources;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Mixin(value = ModelBakery.ModelBakerImpl.class, priority = 600)
public abstract class ModelBakerImplMixin {
    private static final boolean debugDynamicModelLoading = Boolean.getBoolean("modernfix.debugDynamicModelLoading");
    @Shadow @Final private ModelBakery field_40571;

    @Shadow public abstract UnbakedModel getModel(ResourceLocation arg);

    @Shadow @Final private Function<Material, TextureAtlasSprite> modelTextureGetter;

    private static final MethodHandle blockStateLoaderHandle;
    static {
        try {
            blockStateLoaderHandle = MethodHandles.lookup().unreflect(ModelBakery.class.getDeclaredMethod(
                    FabricLoader.getInstance().getMappingResolver().mapMethodName(
                            "intermediary",
                            "net.minecraft.client.resources.model.ModelBakery",
                            "method_4716",
                            "(Lnet/minecraft/world/level/block/state/BlockState;)V"
                    ),
                    BlockState.class
            ));
        } catch(ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void createBakedMissingModelIfNeeded(IExtendedModelBakery extendedBakery, UnbakedModel iunbakedmodel, ModelState arg2, ResourceLocation arg) {
        if(extendedBakery.getBakedMissingModel() == null) {
            extendedBakery.setBakedMissingModel(iunbakedmodel.bake((ModelBaker)this, this.modelTextureGetter, arg2, arg));
            ((DynamicBakedModelProvider)this.field_40571.getBakedTopLevelModels()).setMissingModel(extendedBakery.getBakedMissingModel());
        }
    }

    private boolean wasMissingModel = false;

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void obtainModel(ResourceLocation arg, CallbackInfoReturnable<UnbakedModel> cir) {
        if(debugDynamicModelLoading)
            ModernFix.LOGGER.info("Baking {}", arg);
        IExtendedModelBakery extendedBakery = (IExtendedModelBakery)this.field_40571;
        if(arg instanceof ModelResourceLocation && arg != ModelBakery.MISSING_MODEL_LOCATION) {
            /* to emulate vanilla model loading, treat as top-level */
            Optional<Block> blockOpt = Objects.equals(((ModelResourceLocation)arg).getVariant(), "inventory") ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(arg.getNamespace(), arg.getPath()));
            if(blockOpt.isPresent()) {
                /* load via lambda for mods that expect blockstate to get loaded */
                for(BlockState state : extendedBakery.getBlockStatesForMRL(blockOpt.get().getStateDefinition(), (ModelResourceLocation)arg)) {
                    try {
                        blockStateLoaderHandle.invokeExact(this.field_40571, state);
                    } catch(Throwable e) {
                        ModernFix.LOGGER.error("Error loading model", e);
                    }
                }
            } else {
                this.field_40571.loadTopLevel((ModelResourceLocation)arg);
            }
            cir.setReturnValue(this.field_40571.topLevelModels.getOrDefault(arg, extendedBakery.mfix$getUnbakedMissingModel()));
            // avoid leaks
            this.field_40571.topLevelModels.clear();
        } else
            cir.setReturnValue(this.field_40571.getModel(arg));
        if(cir.getReturnValue() == extendedBakery.mfix$getUnbakedMissingModel()) {
            if(arg != ModelBakery.MISSING_MODEL_LOCATION && debugDynamicModelLoading)
                ModernFix.LOGGER.warn("Model {} not present", arg);
            wasMissingModel = true;
        } else
            wasMissingModel = false; /* sometimes this runs more than once e.g. for recursive model baking */
        cir.getReturnValue().resolveParents(this.field_40571::getModel);
    }

    @ModifyVariable(method = "bake", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/client/resources/model/UnbakedModel;bake(Lnet/minecraft/client/resources/model/ModelBaker;Ljava/util/function/Function;Lnet/minecraft/client/resources/model/ModelState;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/resources/model/BakedModel;"))
    private BakedModel unifyMissingBakedModel(BakedModel model) {
        if(wasMissingModel) {
            // use a shared baked missing model
            IExtendedModelBakery extendedBakery = (IExtendedModelBakery)this.field_40571;
            BakedModel missing;
            synchronized (this.field_40571) {
                if(extendedBakery.getBakedMissingModel() == null) {
                    extendedBakery.setBakedMissingModel(model);
                    missing = model;
                } else {
                    missing = extendedBakery.getBakedMissingModel();
                }
            }
            return missing;
        }
        return model;
    }



    /**
     * @author embeddedt
     * @reason emulate old function, to allow injectors to work
     */
    /*
    @Overwrite
    public BakedModel bake(ResourceLocation arg, ModelState arg2) {
        ModelBakery.BakedCacheKey key = new ModelBakery.BakedCacheKey(arg, arg2.getRotation(), arg2.isUvLocked());
        BakedModel existing = this.field_40571.bakedCache.get(key);
        if (existing != null) {
            return existing;
        } else {
            synchronized (this.field_40571) {
                if(debugDynamicModelLoading)
                    ModernFix.LOGGER.info("Baking {}", arg);
                UnbakedModel iunbakedmodel = this.getModel(arg);
                // TODO: make sure parent resolution doesn't re-run many times
                iunbakedmodel.resolveParents(this::getModel);
                BakedModel ibakedmodel = null;
                if (iunbakedmodel instanceof BlockModel) {
                    BlockModel blockmodel = (BlockModel)iunbakedmodel;
                    if (blockmodel.getRootModel() == ModelBakery.GENERATION_MARKER) {
                        ibakedmodel = ModelBakery.ITEM_MODEL_GENERATOR.generateBlockModel(this.modelTextureGetter, blockmodel).bake((ModelBaker)this, blockmodel, this.modelTextureGetter, arg2, arg, false);
                    }
                }
                if(ibakedmodel == null) {
                    // leave the original assignment in the same spot so wrapping injectors work
                    // this means two bakes might happen for missing models, but not much we can do
                    ibakedmodel = iunbakedmodel.bake((ModelBaker)this, this.modelTextureGetter, arg2, arg);
                    IExtendedModelBakery extendedBakery = (IExtendedModelBakery)this.field_40571;
                    if(iunbakedmodel == extendedBakery.mfix$getUnbakedMissingModel()) {
                        // use a shared baked missing model
                        createBakedMissingModelIfNeeded(extendedBakery, iunbakedmodel, arg2, arg);
                        ibakedmodel = extendedBakery.getBakedMissingModel();
                    }
                }
                this.field_40571.bakedCache.put(key, ibakedmodel);
                return ibakedmodel;
            }
        }
    }

     */
}
