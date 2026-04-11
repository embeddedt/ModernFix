package org.embeddedt.modernfix.common.mixin.perf.attribute_supplier_dedup;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.embeddedt.modernfix.entity.AttributeInstanceTemplates;
import org.embeddedt.modernfix.forge.init.ModernFixForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(AttributeSupplier.Builder.class)
public class AttributeSupplierBuilderMixin {
    @Shadow
    @Final
    private Map<Attribute, AttributeInstance> builder;

    /**
     * @author embeddedt
     * @reason canonicalize identical AttributeInstance templates, many entities are created with the same values
     */
    @Inject(method = "build", at = @At(value = "NEW", target = "(Ljava/util/Map;)Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier;"))
    private void deduplicateInstances(CallbackInfoReturnable<AttributeSupplier> cir) {
        // The interning has overhead, so we only apply it early during the launch, when mods are normally
        // registering the custom attribute suppliers.
        if (ModernFixForge.registryEventsFired) {
            return;
        }
        this.builder.replaceAll((a, i) -> AttributeInstanceTemplates.intern(i));
    }
}
