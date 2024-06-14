package org.embeddedt.modernfix.common.mixin.perf.deduplicate_location;

import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.dedup.IdentifierCaches;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResourceLocation.class)
public class MixinResourceLocation {
    @Mutable
    @Shadow
    @Final
    private String namespace;

    @Mutable
    @Shadow
    @Final
    private String path;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void reinit(String string, String string2, CallbackInfo ci) {
        this.namespace = IdentifierCaches.NAMESPACES.deduplicate(this.namespace);
        this.path = IdentifierCaches.PATH.deduplicate(this.path);
    }
}
