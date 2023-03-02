package org.embeddedt.modernfix.mixin.bugfix.mc218112;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

@Mixin(SynchedEntityData.class)
public class SynchedEntityDataMixin {
    @Shadow @Final private Map<Integer, SynchedEntityData.DataItem<?>> itemsById;

    @Shadow @Final private ReadWriteLock lock;

    @Shadow private boolean isEmpty;

    @Inject(method = "createDataItem", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private <T> void putWithLock(EntityDataAccessor<T> key, T value, CallbackInfo ci, SynchedEntityData.DataItem<T> item) {
        ci.cancel();
        try {
            this.itemsById.put(key.getId(), item);
            this.isEmpty = false;
        } finally {
            this.lock.writeLock().unlock();
        }
    }
}
