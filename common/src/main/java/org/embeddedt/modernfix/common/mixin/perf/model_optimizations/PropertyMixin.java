package org.embeddedt.modernfix.common.mixin.perf.model_optimizations;

import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Property.class)
public class PropertyMixin {

    @Shadow @Final private String name;

    @Shadow @Final private Class clazz;

    @ModifyVariable(method = "<init>", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static String internName(String name) {
        return name.intern();
    }

    /**
     * @author embeddedt
     * @reason compare hashcodes if generated, use reference equality for speed
     */
    @Overwrite(remap = false)
    public boolean equals(Object p_equals_1_) {
        if (this == p_equals_1_) {
            return true;
        } else if (!(p_equals_1_ instanceof Property)) {
            return false;
        } else {
            Property<?> property = (Property)p_equals_1_;
            /* reference equality is safe here because of interning above */
            //noinspection StringEquality
            return this.clazz == property.getValueClass() && this.name == property.getName();
        }
    }
}
