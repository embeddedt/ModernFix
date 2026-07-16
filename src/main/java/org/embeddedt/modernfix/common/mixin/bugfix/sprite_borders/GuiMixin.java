package org.embeddedt.modernfix.common.mixin.bugfix.sprite_borders;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.Mth;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Gui.class)
@ClientOnlyMixin
public class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * @author embeddedt
     * @reason Fix jump bar rendering too many pixels when full (MC-269295)
     */
    @ModifyArg(
            method = "renderJumpMeter",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite("
                            + "Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"
            ),
            index = 7
    )
    private int fixJumpBar(int originalWidth, @Local(ordinal = 0) float f) {
        return Mth.lerpDiscrete(f, 0, 182);
    }

    /**
     * @author embeddedt
     * @reason Fix experience bar rendering too many pixels when full
     */
    @ModifyArg(
            method = "renderExperienceBar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite("
                            + "Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"
            ),
            index = 7
    )
    private int fixExperienceBar(int originalWidth) {
        return Mth.lerpDiscrete(this.minecraft.player.experienceProgress, 0, 182);
    }
}
