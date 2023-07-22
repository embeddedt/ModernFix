package org.embeddedt.modernfix.common.mixin.bugfix.concurrency;

import net.minecraft.client.renderer.RenderType;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

@Mixin(targets = { "net/minecraft/client/renderer/RenderType$CompositeRenderType$EqualsStrategy"})
@ClientOnlyMixin
public class RenderTypeEqualsStrategyMixin {
    @Redirect(method = "equals(Lnet/minecraft/client/renderer/RenderType$CompositeRenderType;Lnet/minecraft/client/renderer/RenderType$CompositeRenderType;)Z", at = @At(value = "INVOKE", target = "Ljava/util/Objects;equals(Ljava/lang/Object;Ljava/lang/Object;)Z", ordinal = 0))
    private boolean alsoCheckName(Object a, Object b, RenderType.CompositeRenderType type1, RenderType.CompositeRenderType type2) {
        boolean supposedlyEqual = Objects.equals(a, b);
        return supposedlyEqual && Objects.equals(type1.name, type2.name);
    }
}
