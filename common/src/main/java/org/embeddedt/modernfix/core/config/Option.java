package org.embeddedt.modernfix.core.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class Option {
    private final String name;

    private Set<String> modDefined = null;
    private boolean enabled;
    private boolean userDefined;
    private Option parent = null;

    public Option(String name, boolean enabled, boolean userDefined) {
        this.name = name;
        this.enabled = enabled;
        this.userDefined = userDefined;
    }

    public void setEnabled(boolean enabled, boolean userDefined) {
        if(this.enabled == enabled)
            return;
        this.enabled = enabled;
        this.userDefined = userDefined;
    }

    public void addModOverride(boolean enabled, String modId) {
        if(this.enabled == enabled)
            return;
        this.enabled = enabled;

        if (this.modDefined == null) {
            this.modDefined = new LinkedHashSet<>();
        }

        this.modDefined.add(modId);
    }

    public void setParent(Option option) {
        this.parent = option;
    }

    public Option getParent() {
        return this.parent;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Checks if this option will effectively be disabled (regardless of its own status)
     * by the parent rule being disabled.
     */
    public boolean isEffectivelyDisabledByParent() {
        return this.parent != null && (!this.parent.enabled || this.parent.isEffectivelyDisabledByParent());
    }

    public boolean isOverridden() {
        return this.isUserDefined() || this.isModDefined();
    }

    public boolean isUserDefined() {
        return this.userDefined;
    }

    public boolean isModDefined() {
        return this.modDefined != null;
    }

    public String getName() {
        return this.name;
    }

    public void clearModsDefiningValue() {
        this.modDefined = null;
    }

    public void clearUserDefined() {
        this.userDefined = false;
    }

    public Collection<String> getDefiningMods() {
        return this.modDefined != null ? Collections.unmodifiableCollection(this.modDefined) : Collections.emptyList();
    }
}