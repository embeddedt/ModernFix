package org.embeddedt.modernfix.common.mixin.feature.measure_time;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ProfiledReloadInstance;
import org.embeddedt.modernfix.util.NamedPreparableResourceListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(ProfiledReloadInstance.class)
public class ProfiledReloadInstanceMixin {
    /**
     * @author embeddedt
     * @reason Decorate reload listeners with their class name as well as the simple name
     */
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static List<PreparableReloadListener> getWrappedListeners(List<PreparableReloadListener> listeners) {
        List<PreparableReloadListener> newList = new ArrayList<>(listeners.size());
        for(PreparableReloadListener listener : listeners) {
            // No need to wrap listeners that are already wrapped/provided by a mod loader
            String className = listener.getClass().getName();
            if (className.startsWith("net.minecraftforge.") || className.startsWith("net.neoforged.") || className.startsWith("net.fabricmc.")) {
                newList.add(listener);
            } else {
                newList.add(new NamedPreparableResourceListener(listener));
            }
        }
        return newList;
    }

    /**
     * @author embeddedt
     * @reason Place most expensive reload listeners first
     */
    @ModifyVariable(method = "finish", ordinal = 0, argsOnly = true, at = @At("HEAD"))
    private List<ProfiledReloadInstance.State> sortStates(List<ProfiledReloadInstance.State> datapoints) {
        datapoints = new ArrayList<>(datapoints);
        datapoints.sort(Comparator.<ProfiledReloadInstance.State>comparingLong(s -> s.preparationNanos().get() + s.reloadNanos().get()).reversed());
        return datapoints;
    }
}
