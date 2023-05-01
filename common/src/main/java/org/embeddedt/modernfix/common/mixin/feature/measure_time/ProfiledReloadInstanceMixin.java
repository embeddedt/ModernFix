package org.embeddedt.modernfix.common.mixin.feature.measure_time;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ProfiledReloadInstance;
import org.embeddedt.modernfix.util.NamedPreparableResourceListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ProfiledReloadInstance.class)
public class ProfiledReloadInstanceMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static List<PreparableReloadListener> getWrappedListeners(List<PreparableReloadListener> listeners) {
        List<PreparableReloadListener> newList = new ArrayList<>(listeners.size());
        for(PreparableReloadListener listener : listeners) {
            newList.add(new NamedPreparableResourceListener(listener));
        }
        return newList;
    }
}
