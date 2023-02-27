package org.embeddedt.modernfix.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.util.RandomSource;
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
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import org.embeddedt.modernfix.ModernFix;
import org.jetbrains.annotations.NotNull;

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
    public List<BakedQuad> getQuads(@Nullable BlockState pState, @Nullable Direction pSide, RandomSource pRand) {
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

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull RandomSource rand, @Nonnull ModelData extraData, @org.jetbrains.annotations.Nullable RenderType renderType) {
        return computeDelegate().getQuads(state, side, rand, extraData, renderType);
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state) {
        return computeDelegate().useAmbientOcclusion(state);
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
        return computeDelegate().useAmbientOcclusion(state, renderType);
    }

    @Override
    public BakedModel applyTransform(ItemTransforms.TransformType transformType, PoseStack poseStack, boolean applyLeftHandTransform) {
        return computeDelegate().applyTransform(transformType, poseStack, applyLeftHandTransform);
    }

    @Nonnull
    @Override
    public ModelData getModelData(@Nonnull BlockAndTintGetter world, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull ModelData tileData) {
        return computeDelegate().getModelData(world, pos, state, tileData);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(@Nonnull ModelData data) {
        return computeDelegate().getParticleIcon(data);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return computeDelegate().getRenderTypes(state, rand, data);
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
        return computeDelegate().getRenderPasses(itemStack, fabulous);
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack itemStack, boolean fabulous) {
        return computeDelegate().getRenderTypes(itemStack, fabulous);
    }
}
