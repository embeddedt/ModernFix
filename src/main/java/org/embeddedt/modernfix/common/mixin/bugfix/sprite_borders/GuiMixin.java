package org.embeddedt.modernfix.common.mixin.bugfix.sprite_borders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import net.minecraft.util.Mth;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ExperienceBarRenderer.class)
@ClientOnlyMixin
public class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * @author embeddedt
     * @reason Fix experience bar rendering too many pixels when full
     */
    @ModifyArg(
            method = "extractBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"
            ),
            index = 8
    )
    private int fixExperienceBar(int originalWidth) {
        return Mth.lerpDiscrete(this.minecraft.player.experienceProgress, 0, 182);
    }
}
