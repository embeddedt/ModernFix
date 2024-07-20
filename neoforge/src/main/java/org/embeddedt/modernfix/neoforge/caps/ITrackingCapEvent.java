package org.embeddedt.modernfix.neoforge.caps;

import net.neoforged.neoforge.capabilities.BaseCapability;

import java.util.Set;

public interface ITrackingCapEvent {
    Set<BaseCapability<?, ?>> mfix$getTrackedCaps();
}
