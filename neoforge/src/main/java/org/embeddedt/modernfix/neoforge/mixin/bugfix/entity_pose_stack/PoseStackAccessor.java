package org.embeddedt.modernfix.neoforge.mixin.bugfix.entity_pose_stack;

import com.mojang.blaze3d.vertex.PoseStack;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Deque;

@Mixin(PoseStack.class)
@ClientOnlyMixin
public interface PoseStackAccessor {
    @Accessor
    Deque<PoseStack.Pose> getPoseStack();
}
