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

@Mixin(targets = {"net/minecraft/client/gui/font/providers/UnihexProvider$ByteContents"})
@ClientOnlyMixin
public class UnihexProviderByteContentsMixin {
    @Inject(method = "read", at = @At(value = "NEW", target = "([B)Lnet/minecraft/client/gui/font/providers/UnihexProvider$ByteContents;"), cancellable = true)
    private static void useCompactIfPossible(int index, ByteList byteList, CallbackInfoReturnable<UnihexProvider.LineData> cir, @Local(ordinal = 0) byte[] contents) {
        if (contents.length == 16) {
            cir.setReturnValue(new CompactUnihexContents.Bytes(contents));
        }
    }
}
