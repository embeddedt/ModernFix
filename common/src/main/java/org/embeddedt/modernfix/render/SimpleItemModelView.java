package org.embeddedt.modernfix.render;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * Wrapper class that presents a fake view of item models (only showing the simple front-facing quads), rather
 * than every quad.
 */
public class SimpleItemModelView implements BakedModel {
    private BakedModel wrappedItem;
    private FastItemRenderType type;

    public void setItem(BakedModel model) {
        this.wrappedItem = model;
    }

    public void setType(FastItemRenderType type) {
        this.type = type;
    }

    private boolean isCorrectDirectionForType(Direction direction) {
        if(type == FastItemRenderType.SIMPLE_ITEM)
            return direction == Direction.SOUTH;
        else {
            return direction == Direction.UP || direction == Direction.EAST || direction == Direction.NORTH;
        }
    }

    private final List<BakedQuad> nullQuadList = new ObjectArrayList<>();

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, Random rand) {
        if(side != null) {
            return isCorrectDirectionForType(side) ? wrappedItem.getQuads(state, side, rand) : ImmutableList.of();
        } else {
            nullQuadList.clear();
            List<BakedQuad> realList = wrappedItem.getQuads(state, null, rand);
            for(int i = 0; i < realList.size(); i++) {
                BakedQuad quad = realList.get(i);
                if(isCorrectDirectionForType(quad.getDirection())) {
                    nullQuadList.add(quad);
                }
            }
            return nullQuadList;
        }
    }

    @Override
    public boolean useAmbientOcclusion() {
        return wrappedItem.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return wrappedItem.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return wrappedItem.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return wrappedItem.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return wrappedItem.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return wrappedItem.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return wrappedItem.getOverrides();
    }
}
