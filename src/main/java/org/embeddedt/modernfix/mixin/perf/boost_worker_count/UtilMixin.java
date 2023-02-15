package org.embeddedt.modernfix.mixin.perf.boost_worker_count;

import net.minecraft.Util;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Util.class)
public class UtilMixin {
    @ModifyConstant(method = "makeExecutor", constant = @Constant(intValue = 7))
    private static int useHigherThreadCount(int old) {
        String requestedMax = System.getProperty("max.bg.threads");
        if(requestedMax != null) {
            try {
                int newMax = Integer.parseInt(requestedMax);
                if(newMax >= 1 && newMax <= 255)
                    return newMax;
            } catch(NumberFormatException e) {
                ModernFix.LOGGER.error("max.bg.threads is not a number");
            }
        }
        return 255;
    }
}
