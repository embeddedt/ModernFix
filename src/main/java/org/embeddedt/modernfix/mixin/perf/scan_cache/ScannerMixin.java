package org.embeddedt.modernfix.mixin.perf.scan_cache;

import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.Scanner;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.embeddedt.modernfix.scanning.CachedScanner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Scanner.class)
public class ScannerMixin {
    @Shadow @Final private ModFile fileToScan;

    @Inject(method = "scan", at = @At(value = "HEAD"), cancellable = true, remap = false)
    private void useCachedScanResults(CallbackInfoReturnable<ModFileScanData> cir) {
        ModFileScanData cached = CachedScanner.getCachedDataForFile(this.fileToScan);
        if(cached != null)
            cir.setReturnValue(cached);
    }

    @Inject(method = "scan", at = @At(value = "TAIL"), remap = false)
    private void saveCachedScanResults(CallbackInfoReturnable<ModFileScanData> cir) {
        CachedScanner.saveCachedDataForFile(this.fileToScan, cir.getReturnValue());
    }
}
