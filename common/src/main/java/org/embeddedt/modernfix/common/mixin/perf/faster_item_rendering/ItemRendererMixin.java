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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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

    private ItemDisplayContext transformType;

    @Inject(method = "render", at = @At("HEAD"))
    private void markRenderingType(ItemStack itemStack, ItemDisplayContext transformType, boolean leftHand, PoseStack matrixStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model, CallbackInfo ci) {
        this.transformType = transformType;
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
        if(!stack.isEmpty() && model.getClass() == SimpleBakedModel.class && transformType == ItemDisplayContext.GUI && model.getTransforms().gui == ItemTransform.NO_TRANSFORM) {
            ci.cancel();
            PoseStack.Pose pose = matrixStack.last();
            int[] combinedLights = new int[] {combinedLight, combinedLight, combinedLight, combinedLight};
            List<BakedQuad> culledFaces = model.getQuads(null, Direction.SOUTH, dummyRandom);
            List<BakedQuad> unculledFaces = model.getQuads(null, null, dummyRandom);
            /* check size to avoid instantiating iterator when the list is empty */
            if(culledFaces.size() > 0) {
                for(BakedQuad quad : culledFaces) {
                    render2dItemFace(quad, stack, buffer, pose, combinedLights, combinedOverlay);
                }
            }
            for(BakedQuad quad : unculledFaces) {
                if(quad.getDirection() == Direction.SOUTH)
                    render2dItemFace(quad, stack, buffer, pose, combinedLights, combinedOverlay);
            }
        }
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
