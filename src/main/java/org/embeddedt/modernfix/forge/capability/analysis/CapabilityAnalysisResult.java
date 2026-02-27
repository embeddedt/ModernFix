package org.embeddedt.modernfix.forge.capability.analysis;

import java.util.Set;

/**
 * Result of analyzing a capability provider's {@code getCapability} bytecode.
 */
public sealed interface CapabilityAnalysisResult {
    /**
     * The provider can only return non-empty for these specific capabilities.
     */
    record KnownCapabilities(Set<CapabilityRef> capabilities) implements CapabilityAnalysisResult {}

    /**
     * The provider always returns {@code LazyOptional.empty()}.
     */
    record AlwaysEmpty() implements CapabilityAnalysisResult {}

    /**
     * Analysis could not determine the set of capabilities; fall back to unguarded dispatch.
     */
    record Indeterminate(String reason) implements CapabilityAnalysisResult {}
}
