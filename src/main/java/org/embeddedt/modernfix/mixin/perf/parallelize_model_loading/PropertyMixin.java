package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading;

import net.minecraft.state.Property;
import org.embeddedt.modernfix.dedup.IdentifierCaches;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Property.class)
public class PropertyMixin {

    @Shadow @Mutable
    @Final private String name;

    @Shadow private Integer hashCode;

    @Shadow @Final private Class clazz;

    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/state/Property;name:Ljava/lang/String;"))
    private void internName(Property instance, String name) {
        this.name = IdentifierCaches.PROPERTY.deduplicate(name);
    }
    /**
     * @author embeddedt
     * @reason compare hashcodes if generated, use reference equality for speed
     */
    @Overwrite
    public boolean equals(Object p_equals_1_) {
        if (this == p_equals_1_) {
            return true;
        } else if (!(p_equals_1_ instanceof Property)) {
            return false;
        } else {
            Property<?> property = (Property)p_equals_1_;
            /* reference equality is safe here because of deduplication */
            return this.clazz == property.getValueClass() && this.name == property.getName();
        }
    }
}
