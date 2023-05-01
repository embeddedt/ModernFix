package org.embeddedt.modernfix.fabric.mixin.feature.branding;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.embeddedt.modernfix.ModernFixClientFabric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class GuiMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void addModernFix(CallbackInfoReturnable<List<String>> cir) {
        cir.getReturnValue().add(ModernFixClientFabric.commonMod.brandingString);
    }
}
