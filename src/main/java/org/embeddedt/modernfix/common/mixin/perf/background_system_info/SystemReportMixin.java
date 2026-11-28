package org.embeddedt.modernfix.common.mixin.perf.background_system_info;

import net.minecraft.SystemReport;
import org.embeddedt.modernfix.util.SharedSystemInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import oshi.SystemInfo;

@Mixin(SystemReport.class)
public class SystemReportMixin {
    @Redirect(method = "*", at = @At(value = "NEW", target = "oshi/SystemInfo", remap = false), require = 1)
    private SystemInfo useSharedSystemInfo() {
        return SharedSystemInfo.get();
    }
}
