package org.embeddedt.modernfix.neoforge.mixin.feature.measure_time;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net/neoforged/neoforge/event/AddReloadListenerEvent$WrappedStateAwareListener")
public abstract class AddReloadListenerEventWrapperMixin implements PreparableReloadListener {
    @Shadow @Final private PreparableReloadListener wrapped;

    /**
     * @author embeddedt
     * @reason make a proper name show up in ProfiledReloadInstance
     */
    @Override
    public String getName() {
        return this.wrapped.getClass().getName();
    }
}
