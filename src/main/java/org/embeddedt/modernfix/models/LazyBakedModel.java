package org.embeddedt.modernfix.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.client.model.data.IModelData;
import org.embeddedt.modernfix.ModernFix;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class LazyBakedModel implements BakedModel {
    private BakedModel delegate = null;
    private Supplier<BakedModel> delegateSupplier;

    /**
     * This flag is changed to true when we should bake instead of returning reasonable defaults for certain
     * method calls.
     */
    public static boolean allowBakeForFlags = false;

    public LazyBakedModel(Supplier<BakedModel> delegateSupplier) {
        this.delegateSupplier = delegateSupplier;
    }

    public BakedModel computeDelegate() {
        if(this.delegate == null) {
            this.delegate = this.delegateSupplier.get();
            this.delegateSupplier = null;
        }
        return this.delegate;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState pState, @Nullable Direction pSide, Random pRand) {
        return computeDelegate().getQuads(pState, pSide, pRand);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return computeDelegate().useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return computeDelegate().isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return computeDelegate().usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        if(this.delegate != null)
            return this.delegate.isCustomRenderer();
        if(!LazyBakedModel.allowBakeForFlags)
            return false;
        return computeDelegate().isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return computeDelegate().getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return computeDelegate().getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return computeDelegate().getOverrides();
    }

    @Override
    public BakedModel getBakedModel() {
        return computeDelegate().getBakedModel();
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull Random rand, @Nonnull IModelData extraData) {
        return computeDelegate().getQuads(state, side, rand, extraData);
    }

    @Override
    public boolean isAmbientOcclusion(BlockState state) {
        return computeDelegate().isAmbientOcclusion(state);
    }

    @Override
    public boolean doesHandlePerspectives() {
        return computeDelegate().doesHandlePerspectives();
    }

    @Override
    public BakedModel handlePerspective(ItemTransforms.TransformType cameraTransformType, PoseStack mat) {
        return computeDelegate().handlePerspective(cameraTransformType, mat);
    }

    @Nonnull
    @Override
    public IModelData getModelData(@Nonnull BlockAndTintGetter world, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull IModelData tileData) {
        return computeDelegate().getModelData(world, pos, state, tileData);
    }

    @Override
    public TextureAtlasSprite getParticleTexture(@Nonnull IModelData data) {
        return computeDelegate().getParticleTexture(data);
    }

    @Override
    public boolean isLayered() {
        return computeDelegate().isLayered();
    }

    @Override
    public List<Pair<BakedModel, RenderType>> getLayerModels(ItemStack itemStack, boolean fabulous) {
        return computeDelegate().getLayerModels(itemStack, fabulous);
    }
}
