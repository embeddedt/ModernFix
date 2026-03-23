package org.embeddedt.modernfix.common.mixin.perf.attribute_supplier_dedup;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(AttributeSupplier.class)
public class AttributeSupplierMixin {
    @Shadow
    @Final
    @Mutable
    private Map<Holder<Attribute>, AttributeInstance> instances;

    /**
     * @author embeddedt
     * @reason Java 9's Map.of() implementation is significantly more compact than ImmutableMap, and we do not
     * care about insertion order in this context
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void useCompactJavaMap(Map<Holder<Attribute>, AttributeInstance> instances, CallbackInfo ci) {
        this.instances = Map.copyOf(this.instances);
    }
}