package org.embeddedt.modernfix.forge.capability.analysis;

/**
 * Identifies a capability by the static field that holds it.
 *
 * @param owner     internal class name (e.g. {@code net/minecraftforge/common/capabilities/ForgeCapabilities})
 * @param fieldName field name (e.g. {@code ITEM_HANDLER})
 */
public record CapabilityRef(String owner, String fieldName) {
    @Override
    public String toString() {
        return owner + "." + fieldName;
    }
}
