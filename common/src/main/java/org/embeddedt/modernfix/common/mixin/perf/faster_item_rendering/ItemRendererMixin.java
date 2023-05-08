package org.embeddedt.modernfix.common.mixin.perf.faster_item_rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.render.FastItemRenderType;
import org.embeddedt.modernfix.render.RenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Shadow @Final private ItemColors itemColors;

    private final RandomSource dummyRandom = RandomSource.createNewThreadLocalInstance();

    private static final float[] COLOR_MULTIPLIER = new float[]{1.0F, 1.0F, 1.0F, 1.0F};

    private ItemTransforms.TransformType transformType;

    @Inject(method = "render", at = @At("HEAD"))
    private void markRenderingType(ItemStack itemStack, ItemTransforms.TransformType transformType, boolean leftHand, PoseStack matrixStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model, CallbackInfo ci) {
        this.transformType = transformType;
    }

    private static final Direction[] ITEM_DIRECTIONS = new Direction[] { Direction.SOUTH };
    private static final Direction[] BLOCK_DIRECTIONS = new Direction[] { Direction.UP, Direction.EAST, Direction.NORTH };

    private boolean isCorrectDirectionForType(FastItemRenderType type, Direction direction) {
        if(type == FastItemRenderType.SIMPLE_ITEM)
            return direction == Direction.SOUTH;
        else {
            return direction == Direction.UP || direction == Direction.EAST || direction == Direction.NORTH;
        }
    }

    /**
     * If a model
     * - is a vanilla item model (SimpleBakedModel),
     * - has no custom GUI transforms, and
     * - is being rendered in 2D on a GUI
     * we do not need to go through the process of rendering every quad. Just render the south ones (the ones facing the
     * camera).
     */
    @Inject(method = "renderModelLists", at = @At("HEAD"), cancellable = true)
    private void fasterItemRender(BakedModel model, ItemStack stack, int combinedLight, int combinedOverlay, PoseStack matrixStack, VertexConsumer buffer, CallbackInfo ci) {
        if(!RenderState.IS_RENDERING_LEVEL && !stack.isEmpty() && model.getClass() == SimpleBakedModel.class && transformType == ItemTransforms.TransformType.GUI) {
            FastItemRenderType type;
            ItemTransform transform = model.getTransforms().gui;
            if(transform == ItemTransform.NO_TRANSFORM)
                type = FastItemRenderType.SIMPLE_ITEM;
            else if(stack.getItem() instanceof BlockItem && isBlockTransforms(transform))
                type = FastItemRenderType.SIMPLE_BLOCK;
            else
                return;
            ci.cancel();
            PoseStack.Pose pose = matrixStack.last();
            int[] combinedLights = new int[] {combinedLight, combinedLight, combinedLight, combinedLight};
            Direction[] directions = type == FastItemRenderType.SIMPLE_ITEM ? ITEM_DIRECTIONS : BLOCK_DIRECTIONS;
            for(Direction direction : directions) {
                List<BakedQuad> culledFaces = model.getQuads(null, direction, dummyRandom);
                /* check size to avoid instantiating iterator when the list is empty */
                if(culledFaces.size() > 0) {
                    for(BakedQuad quad : culledFaces) {
                        render2dItemFace(quad, stack, buffer, pose, combinedLights, combinedOverlay);
                    }
                }
            }
            List<BakedQuad> unculledFaces = model.getQuads(null, null, dummyRandom);
            for(BakedQuad quad : unculledFaces) {
                if(isCorrectDirectionForType(type, quad.getDirection()))
                    render2dItemFace(quad, stack, buffer, pose, combinedLights, combinedOverlay);
            }
        }
    }

    private boolean isBlockTransforms(ItemTransform transform) {
        return transform.rotation.x() == 30f
                && transform.rotation.y() == 225f
                && transform.rotation.z() == 0f;
    }

    private void render2dItemFace(BakedQuad quad, ItemStack stack, VertexConsumer buffer, PoseStack.Pose pose, int[] combinedLights, int combinedOverlay) {
        int i = -1;
        if (quad.isTinted()) {
            i = this.itemColors.getColor(stack, quad.getTintIndex());
        }

        float f = (float)(i >> 16 & 255) / 255.0F;
        float f1 = (float)(i >> 8 & 255) / 255.0F;
        float f2 = (float)(i & 255) / 255.0F;
        buffer.putBulkData(pose, quad, COLOR_MULTIPLIER, f, f1, f2, combinedLights, combinedOverlay, true);
    }
}
