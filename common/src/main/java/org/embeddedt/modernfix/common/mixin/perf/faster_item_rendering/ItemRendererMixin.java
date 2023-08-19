package org.embeddedt.modernfix.common.mixin.perf.faster_item_rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.render.FastItemRenderType;
import org.embeddedt.modernfix.render.RenderState;
import org.embeddedt.modernfix.render.SimpleItemModelView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemRenderer.class, priority = 600)
public abstract class ItemRendererMixin {
    private ItemTransforms.TransformType transformType;
    private final SimpleItemModelView modelView = new SimpleItemModelView();
    private boolean mfix$isTopLevelSimpleModel;

    @Inject(method = "render", at = @At("HEAD"))
    private void markRenderingType(ItemStack itemStack, ItemTransforms.TransformType transformType, boolean leftHand, PoseStack matrixStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model, CallbackInfo ci) {
        this.transformType = transformType;
        // used as renderModelLists may be called by custom model renderers
        this.mfix$isTopLevelSimpleModel = model != null && model.getClass() == SimpleBakedModel.class;
    }

    /**
     * If a model
     * - is a vanilla item model (SimpleBakedModel),
     * - has no custom GUI transforms, and
     * - is being rendered in 2D on a GUI
     * we do not need to go through the process of rendering every quad. Just render the south ones (the ones facing the
     * camera).
     */
    @ModifyVariable(method = "renderModelLists", at = @At("HEAD"), index = 1, argsOnly = true)
    private BakedModel useSimpleWrappedItemModel(BakedModel model, BakedModel arg, ItemStack stack, int combinedLight, int combinedOverlay, PoseStack matrixStack, VertexConsumer buffer) {
        if(!RenderState.IS_RENDERING_LEVEL && !stack.isEmpty() && mfix$isTopLevelSimpleModel && model.getClass() == SimpleBakedModel.class && transformType == ItemTransforms.TransformType.GUI) {
            FastItemRenderType type;
            ItemTransform transform = model.getTransforms().gui;
            if(transform == ItemTransform.NO_TRANSFORM)
                type = FastItemRenderType.SIMPLE_ITEM;
            else if(stack.getItem() instanceof BlockItem && isBlockTransforms(transform))
                type = FastItemRenderType.SIMPLE_BLOCK;
            else
                return model;
            modelView.setItem(model);
            modelView.setType(type);
            return modelView;
        } else
            return model;
    }

    private boolean isBlockTransforms(ItemTransform transform) {
        return transform.rotation.x() == 30f
                && transform.rotation.y() == 225f
                && transform.rotation.z() == 0f;
    }
}
