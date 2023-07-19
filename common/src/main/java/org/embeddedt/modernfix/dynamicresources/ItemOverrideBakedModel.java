package org.embeddedt.modernfix.dynamicresources;

import com.google.common.collect.ImmutableList;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delegate model that stores the location of an actual baked model, for use in ItemOverrides.
 */
public class ItemOverrideBakedModel implements BakedModel {
    private static final Map<ResourceLocation, ItemOverrideBakedModel> OVERRIDE_MODELS = new ConcurrentHashMap<>();
    public final ResourceLocation realLocation;

    private ItemOverrideBakedModel(ResourceLocation realLocation) {
        this.realLocation = realLocation;
    }

    public static ItemOverrideBakedModel of(ResourceLocation realLocation) {
        return OVERRIDE_MODELS.computeIfAbsent(realLocation, ItemOverrideBakedModel::new);
    }

    public BakedModel getRealModel() {
        return DynamicBakedModelProvider.currentInstance.get(realLocation);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
        return ImmutableList.of();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return false;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return null;
    }

    @Override
    public ItemTransforms getTransforms() {
        return ItemTransforms.NO_TRANSFORMS;
    }

    @Override
    public ItemOverrides getOverrides() {
        return null;
    }
}
