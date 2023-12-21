package org.embeddedt.modernfix.dynamicresources;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Delegate model that stores the location of an actual baked model, for use in ItemOverrides.
 */
public class ItemOverrideBakedModel implements BakedModel {
    public final ResourceLocation realLocation;
    private WeakReference<BakedModel> realModel = new WeakReference<>(null);

    private ItemOverrideBakedModel(ResourceLocation realLocation) {
        this.realLocation = realLocation;
    }

    public static ItemOverrideBakedModel of(ResourceLocation realLocation) {
        return new ItemOverrideBakedModel(realLocation);
    }

    public BakedModel getRealModel() {
        BakedModel m = realModel.get();
        if(m == null) {
            m = DynamicBakedModelProvider.currentInstance.get(realLocation);
            realModel = new WeakReference<>(m);
        }
        return m;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
        return getRealModel().getQuads(state, direction, random);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return getRealModel().useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return getRealModel().isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return getRealModel().usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return getRealModel().isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return getRealModel().getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return getRealModel().getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return getRealModel().getOverrides();
    }
}
