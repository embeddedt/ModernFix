package org.embeddedt.modernfix.models;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.client.model.data.IModelData;
import org.embeddedt.modernfix.ModernFix;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class LazyBakedModel implements IBakedModel {
    private IBakedModel delegate = null;
    private Supplier<IBakedModel> delegateSupplier;

    /**
     * This flag is changed to true when we should bake instead of returning reasonable defaults for certain
     * method calls.
     */
    public static boolean allowBakeForFlags = false;

    public LazyBakedModel(Supplier<IBakedModel> delegateSupplier) {
        this.delegateSupplier = delegateSupplier;
    }

    public IBakedModel computeDelegate() {
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
    public ItemCameraTransforms getTransforms() {
        return computeDelegate().getTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return computeDelegate().getOverrides();
    }

    @Override
    public IBakedModel getBakedModel() {
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
    public IBakedModel handlePerspective(ItemCameraTransforms.TransformType cameraTransformType, MatrixStack mat) {
        return computeDelegate().handlePerspective(cameraTransformType, mat);
    }

    @Nonnull
    @Override
    public IModelData getModelData(@Nonnull IBlockDisplayReader world, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull IModelData tileData) {
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
    public List<Pair<IBakedModel, RenderType>> getLayerModels(ItemStack itemStack, boolean fabulous) {
        return computeDelegate().getLayerModels(itemStack, fabulous);
    }
}
