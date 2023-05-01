package org.embeddedt.modernfix.mixin.safety;

import net.minecraft.client.color.item.ItemColors;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Mixin(value = ItemColors.class, priority = 700)
@ClientOnlyMixin
public class ItemColorsMixin {
    private Lock mapLock = new ReentrantLock();
    @Inject(method = "register", at = @At("HEAD"))
    private void lockMapBeforeAccess(CallbackInfo ci) {
        mapLock.lock();
    }
    @Inject(method = "register", at = @At("TAIL"))
    private void unlockMap(CallbackInfo ci) {
        mapLock.unlock();
    }
}
