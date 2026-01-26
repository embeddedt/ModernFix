package org.embeddedt.modernfix.common.mixin.perf.forge_cap_retrieval;

import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AttachCapabilitiesEvent.class)
public abstract class AttachCapabilitiesEventMixin extends Event {
    /**
     * @author embeddedt
     * @reason EventSubclassTransformer is supposed to inject an override returning a constant on the class to avoid the
     * {@link net.minecraftforge.eventbus.api.EventListenerHelper#isCancelable(Class)} slow path.
     * However, the false case is only done for direct subclasses of Event (the true case is done for
     * any cancelable event). This works for normal events because they must subclass Event directly, or be a subclass
     * of an event that does. However, AttachCapabilitiesEvent subclasses GenericEvent, which does not pass through
     * the EventSubclassTransformer as it comes from the EventBus library (where transformers are not run) rather than
     * Forge which is on the GAME layer. The transformer on AttachCapabilitiesEvent then does not add the override as
     * it expects it to be present on GenericEvent already.
     * <p>
     * The simplest workaround to that whole mess is to just inject the override ourselves.
     */
    @Override
    public boolean isCancelable() {
        return false;
    }
}
