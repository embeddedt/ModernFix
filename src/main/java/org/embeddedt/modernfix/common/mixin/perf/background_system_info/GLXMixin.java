package org.embeddedt.modernfix.common.mixin.perf.background_system_info;

import com.mojang.blaze3d.platform.GLX;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.util.SharedSystemInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import oshi.SystemInfo;

@Mixin(GLX.class)
@ClientOnlyMixin
public class GLXMixin {
    @Redirect(method = "_init", at = @At(value = "NEW", target = "oshi/SystemInfo", remap = false))
    private static SystemInfo useSharedSystemInfo() {
        return SharedSystemInfo.get();
    }
}
