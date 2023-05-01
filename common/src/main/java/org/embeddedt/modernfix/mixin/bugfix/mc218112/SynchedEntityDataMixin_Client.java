package org.embeddedt.modernfix.mixin.bugfix.mc218112;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

@Mixin(SynchedEntityData.class)
@ClientOnlyMixin
public abstract class SynchedEntityDataMixin_Client {
    @Shadow @Final private ReadWriteLock lock;

    @Shadow private boolean isDirty;

    @Shadow protected abstract <T> void assignValue(SynchedEntityData.DataItem<T> target, SynchedEntityData.DataItem<?> source);

    @Shadow @Final private Entity entity;

    @Shadow @Final private Map<Integer, SynchedEntityData.DataItem<?>> itemsById;

    /**
     * @author embeddedt
     * @reason always unlock
     */
    @Overwrite
    public void assignValues(List<SynchedEntityData.DataItem<?>> entries) {
        this.lock.writeLock().lock();
        try {
            for(SynchedEntityData.DataItem<?> dataentry : entries) {
                SynchedEntityData.DataItem<?> dataentry1 = this.itemsById.get(dataentry.getAccessor().getId());
                if (dataentry1 != null) {
                    this.assignValue(dataentry1, dataentry);
                    this.entity.onSyncedDataUpdated(dataentry.getAccessor());
                }
            }
        } finally {
            this.lock.writeLock().unlock();
        }
        this.isDirty = true;
    }
}
