package org.embeddedt.modernfix.fabric.mixin.feature.branding;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.embeddedt.modernfix.ModernFixClientFabric;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
@ClientOnlyMixin
public class GuiMixin {
    @ModifyVariable(method = "getSystemInformation", at = @At("STORE"), ordinal = 0, require = 0)
    private List<String> addModernFix(List<String> list) {
        list.add("");
        list.add(ModernFixClientFabric.commonMod.brandingString);
        return list;
    }
}
