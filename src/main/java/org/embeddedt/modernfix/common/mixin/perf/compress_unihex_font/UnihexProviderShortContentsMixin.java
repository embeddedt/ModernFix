package org.embeddedt.modernfix.common.mixin.perf.compress_unihex_font;

import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.bytes.ByteList;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.render.font.CompactUnihexContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"net/minecraft/client/gui/font/providers/UnihexProvider$ShortContents"})
@ClientOnlyMixin
public class UnihexProviderShortContentsMixin {
    @Inject(method = "read", at = @At(value = "NEW", target = "([S)Lnet/minecraft/client/gui/font/providers/UnihexProvider$ShortContents;"), cancellable = true)
    private static void useCompactIfPossible(int index, ByteList byteList, CallbackInfoReturnable<UnihexProvider.LineData> cir, @Local(ordinal = 0) short[] contents) {
        if (contents.length == 16) {
            cir.setReturnValue(new CompactUnihexContents.Shorts(contents));
        }
    }
}
