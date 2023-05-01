package org.embeddedt.modernfix.mixin.perf.jeresources_startup;

import jeresources.entry.VillagerEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.ref.WeakReference;

/* Cache the created villager instead of reconstructing it every time */
@Mixin(VillagerEntry.class)
@RequiresMod("jeresources")
public class VillagerEntryMixin {
    private WeakReference<Villager> cachedVillager = new WeakReference<>(null);

    @Inject(method = "getVillagerEntity", at = @At("HEAD"), cancellable = true)
    private void useCachedVillager(CallbackInfoReturnable<Villager> cir) {
        Villager v = cachedVillager.get();
        if(v != null) {
            // Ensure we don't hold on to an old client world unnecessarily
            Level cLevel = Minecraft.getInstance().level;
            if(cLevel != null && v.getLevel() != cLevel)
                v.setLevel(cLevel);
            cir.setReturnValue(v);
        }
    }

    @Inject(method = "getVillagerEntity", at = @At("RETURN"))
    private void storeCachedVillager(CallbackInfoReturnable<Villager> cir) {
        cachedVillager = new WeakReference<>(cir.getReturnValue());
    }
}
