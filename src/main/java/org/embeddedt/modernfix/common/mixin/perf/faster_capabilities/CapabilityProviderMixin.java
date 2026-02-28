package org.embeddedt.modernfix.common.mixin.perf.faster_capabilities;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Objects;

@Mixin(value = CapabilityProvider.class, remap = false)
@SuppressWarnings("rawtypes")
public class CapabilityProviderMixin {
    @Shadow
    private boolean isLazy;

    @Shadow
    private boolean initialized;

    @Shadow
    private CompoundTag lazyData;

    /**
     * @author embeddedt
     * @reason avoid initializing capabilities in the case where we can reasonably expect the lazy data
     * to deserialize to the same concrete caps
     */
    @WrapMethod(method = "areCapsCompatible(Lnet/minecraftforge/common/capabilities/CapabilityProvider;)Z")
    private boolean mfix$skipLazyInit(CapabilityProvider other, Operation<Boolean> original) {
        if (this.isLazy && !this.initialized) {
            var otherAcc = ((CapabilityProviderMixin)(Object)other);
            if (otherAcc.isLazy && !otherAcc.initialized && otherAcc.getClass() == this.getClass() && Objects.equals(this.lazyData, otherAcc.lazyData)) {
                return true;
            }
        }
        return original.call(other);
    }
}
