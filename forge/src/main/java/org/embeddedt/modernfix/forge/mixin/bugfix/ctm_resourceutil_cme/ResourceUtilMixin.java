package org.embeddedt.modernfix.forge.mixin.bugfix.ctm_resourceutil_cme;

import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.chisel.ctm.client.util.ResourceUtil;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

@Mixin(ResourceUtil.class)
@RequiresMod("ctm")
@ClientOnlyMixin
@SuppressWarnings({"rawtypes", "unchecked"})
public class ResourceUtilMixin {
    @Shadow @Final @Mutable
    private static Map metadataCache;

    /**
     * @author embeddedt
     * @reason quick fix to prevent rare CMEs
     */
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void synchronizeMetadataCache(CallbackInfo ci) {
        if(!(metadataCache instanceof ConcurrentMap))
            metadataCache = Collections.synchronizedMap(metadataCache);
    }
}
