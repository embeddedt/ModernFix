package org.embeddedt.modernfix.common.mixin.perf.faster_item_rendering;

import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.render.FastItemRenderType;
import org.embeddedt.modernfix.render.RenderState;
import org.embeddedt.modernfix.render.SimpleItemModelView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ItemStackRenderState.LayerRenderState.class, priority = 600)
@ClientOnlyMixin
public abstract class LayerRenderStateMixin {
    @Shadow(aliases = {"this$0"}) @Final private ItemStackRenderState field_55345;

    @Shadow abstract ItemTransform transform();

    @Unique
    private final SimpleItemModelView modelView = new SimpleItemModelView();

    /**
     * If a model
     * - is a vanilla item model (SimpleBakedModel),
     * - has no custom GUI transforms, and
     * - is being rendered in 2D on a GUI
     * we do not need to go through the process of rendering every quad. Just render the south ones (the ones facing the
     * camera).
     */
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderItem(Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II[ILnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"), index = 6)
    private BakedModel useSimpleWrappedItemModel(BakedModel model) {
        var transformType = ((ItemStackRenderStateAccessor)this.field_55345).getDisplayContext();
        // Forge composite models split themselves into a smaller simple model, we need to detect that the parent
        // was not simple
        // TODO 1.21.4 - I don't think that is needed anymore with the changes to item rendering
        /*
        if(originalModel != null && originalModel.getClass() != SimpleBakedModel.class) {
            return model;
        }
        */

        if(!RenderState.IS_RENDERING_LEVEL && model.getClass() == SimpleBakedModel.class && transformType == ItemDisplayContext.GUI) {
            FastItemRenderType type;
            ItemTransform transform = this.transform();
            if(transform == ItemTransform.NO_TRANSFORM)
                type = FastItemRenderType.SIMPLE_ITEM;
            else if(model.isGui3d() && isBlockTransforms(transform))
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
