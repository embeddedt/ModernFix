package org.embeddedt.modernfix.forge.mixin.perf.forge_cap_retrieval;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    /**
     * @author embeddedt (issue noted by XFactHD)
     * @reason check capability equality before checking that entity is alive, the latter requires a lot more
     * indirection
     */
    @Redirect(method = "getCapability", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isAlive()Z"))
    private <T> boolean checkAliveAfterCap(LivingEntity entity, Capability<T> capability, @Nullable Direction facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && entity.isAlive();
    }
}
