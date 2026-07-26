package org.embeddedt.modernfix.common.mixin.perf.faster_capabilities;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.embeddedt.modernfix.forge.capability.CapabilityProviderDispatcherGenerator;
import org.embeddedt.modernfix.forge.capability.GeneratedDispatcher;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(CapabilityDispatcher.class)
public class CapabilityDispatcherMixin {
    @Shadow
    @Final
    private ICapabilityProvider[] caps;
    private GeneratedDispatcher mfix$turboDispatcher;

    @Inject(method = "<init>(Ljava/util/Map;Ljava/util/List;Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)V", at = @At("RETURN"))
    private void createTurboDispatcher(Map list, List listeners, ICapabilityProvider parent, CallbackInfo ci) {
        this.mfix$turboDispatcher = CapabilityProviderDispatcherGenerator.getOrGenerateDispatcher(this.caps);
    }

    /**
     * @author embeddedt
     * @reason use ASM-generated dispatcher
     */
    @Overwrite(remap = false)
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return this.mfix$turboDispatcher.getCapability(cap, side);
    }
}
