package org.embeddedt.modernfix.forge.mixin.core;

import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.logging.CrashReportAnalyser;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(CrashReportAnalyser.class)
public class CrashReportAnalyserMixin {
    @Shadow @Final private static Map<IModInfo, String[]> SUSPECTED_MODS;

    /**
     * @author embeddedt
     * @reason Remove ModernFix from the list of suspected mods when a crash happens. Otherwise, we get blamed
     * for "registry object not present" crashes if users don't interpret the crash before reporting
     * it.
     *
     * It seems unlikely ModernFix will simultaneously cause a crash while it's not obvious it caused it.
     */
    @Inject(method = "buildSuspectedModsSection", at = @At("HEAD"), require = 0, remap = false)
    private static void removeOurselvesFromSuspectedMods(StringBuilder stringBuilder, CallbackInfo ci) {
        SUSPECTED_MODS.keySet().removeIf(iModInfo -> iModInfo.getModId().equals("modernfix"));
    }

}
