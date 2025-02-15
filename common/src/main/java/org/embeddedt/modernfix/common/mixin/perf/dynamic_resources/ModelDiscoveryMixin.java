package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.resources.model.ModelDiscovery;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelDiscovery.class)
@ClientOnlyMixin
public class ModelDiscoveryMixin {
    /**
     * @author embeddedt
     * @reason We will show the warning ourselves later when loading the model dynamically, this is just spam since
     * the models don't exist during early loading
     */
    @Redirect(method = "method_68027", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V"))
    private void disableMissingModelWarning(Logger instance, String s, Object o) {

    }
}
