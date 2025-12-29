package org.embeddedt.modernfix.common.mixin.bugfix.entity_pose_stack;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AvatarRenderer.class)
@ClientOnlyMixin
public class AvatarRendererMixin {
    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;", ordinal = 0))
    private Event fireCheckingPoseStack(IEventBus instance, Event event) {
        PoseStack stack = ((RenderPlayerEvent)event).getPoseStack();
        int size = ((PoseStackAccessor)stack).mfix$getLastIndex();
        instance.post(event);
        if (((RenderPlayerEvent.Pre)event).isCanceled()) {
            // Pop the stack if someone pushed it in the event
            while (((PoseStackAccessor)stack).mfix$getLastIndex() > size) {
                stack.popPose();
            }
        }
        return event;
    }
}
