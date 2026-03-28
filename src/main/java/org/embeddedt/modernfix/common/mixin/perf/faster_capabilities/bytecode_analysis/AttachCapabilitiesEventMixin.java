package org.embeddedt.modernfix.common.mixin.perf.faster_capabilities.bytecode_analysis;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import org.embeddedt.modernfix.duck.IBatchingCapEvent;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalysisResult;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalyzer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Mixin(AttachCapabilitiesEvent.class)
public abstract class AttachCapabilitiesEventMixin extends Event implements IBatchingCapEvent {
    @Shadow @Final
    private Map<ResourceLocation, ICapabilityProvider> caps;

    @Unique
    private final Map<ResourceLocation, EventPriority> mfix$phaseMap = new HashMap<>();

    /**
     * @author embeddedt
     * @reason record the current dispatch phase so we can sort within phase groups later
     */
    @WrapMethod(method = "addCapability", remap = false)
    private void mfix$trackPhase(ResourceLocation key, ICapabilityProvider cap, Operation<Void> original) {
        original.call(key, cap);
        mfix$phaseMap.put(key, this.getPhase());
    }

    @Override
    public void mfix$sortCaps() {
        if (caps.size() < 2) {
            return;
        }
        var entries = new ArrayList<>(caps.entrySet());
        entries.sort(Comparator.comparingInt(e -> {
            EventPriority phase = mfix$phaseMap.getOrDefault(e.getKey(), EventPriority.NORMAL);
            var result = CapabilityAnalyzer.analyze(e.getValue().getClass());
            // Primary: preserve phase ordering (HIGHEST=0 .. LOWEST=4)
            // Secondary: Known/AlwaysEmpty before Indeterminate within each phase
            int capKey = result instanceof CapabilityAnalysisResult.Indeterminate ? 1 : 0;
            return phase.ordinal() * 2 + capKey;
        }));
        caps.clear();
        entries.forEach(e -> caps.put(e.getKey(), e.getValue()));
        mfix$phaseMap.clear();
    }
}
