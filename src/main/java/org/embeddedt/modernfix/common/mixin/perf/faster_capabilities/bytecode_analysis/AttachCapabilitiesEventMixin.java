package org.embeddedt.modernfix.common.mixin.perf.faster_capabilities.bytecode_analysis;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import org.apache.commons.lang3.tuple.Pair;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalysisResult;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalyzer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(AttachCapabilitiesEvent.class)
public abstract class AttachCapabilitiesEventMixin extends Event {
    @Shadow
    public abstract void addCapability(ResourceLocation key, ICapabilityProvider cap);

    @Unique
    private static final EventPriority MFIX_LAST_PRIO = EventPriority.values()[EventPriority.values().length - 1];

    @Unique
    private final List<Pair<ResourceLocation, ICapabilityProvider>> mfix$batchedCaps = new ArrayList<>();

    private static final Comparator<Pair<ResourceLocation, ICapabilityProvider>> MFIX_COMPARATOR = Comparator.comparingInt(pair -> {
        var result = CapabilityAnalyzer.analyze(pair.getRight().getClass());
        return result instanceof CapabilityAnalysisResult.Indeterminate ? 1 : 0;
    });

    @Unique
    private boolean insertingBatch;

    /**
     * @author embeddedt
     * @reason batch additions of capability providers within the same phase so that we can hoist all
     * the ones with statically known capability types to the beginning of the provider list
     */
    @WrapMethod(method = "addCapability", remap = false)
    private void mfix$batchCaps(ResourceLocation key, ICapabilityProvider cap, Operation<Void> original) {
        // For simplicity, we don't try to batch on the last phase
        if (this.insertingBatch || this.getPhase() == MFIX_LAST_PRIO) {
            original.call(key, cap);
        } else {
            mfix$batchedCaps.add(Pair.of(key, cap));
        }
    }

    @Override
    public void setPhase(@NotNull EventPriority value) {
        if (!this.mfix$batchedCaps.isEmpty()) {
            this.mfix$batchedCaps.sort(MFIX_COMPARATOR);
            this.insertingBatch = true;
            try {
                this.mfix$batchedCaps.forEach(p -> this.addCapability(p.getKey(), p.getValue()));
            } finally {
                this.insertingBatch = false;
                this.mfix$batchedCaps.clear();
            }
        }
        super.setPhase(value);
    }
}
