package org.embeddedt.modernfix.mixin.feature.branding;

import com.google.common.collect.ImmutableList;
import net.minecraftforge.internal.BrandingControl;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.Optional;

@Mixin(value = BrandingControl.class, remap = false, priority = 1100)
public class BrandingControlMixin {
    @Inject(method = "computeBranding", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/ModList;get()Lnet/minecraftforge/fml/ModList;"), locals = LocalCapture.CAPTURE_FAILHARD, require = 0)
    private static void addModernFixBranding(CallbackInfo ci, ImmutableList.Builder<String> builder) {
        Optional<? extends ModContainer> mfContainer = ModList.get().getModContainerById("modernfix");
        if(mfContainer.isPresent())
            builder.add("ModernFix " + mfContainer.get().getModInfo().getVersion().toString());
    }
}
