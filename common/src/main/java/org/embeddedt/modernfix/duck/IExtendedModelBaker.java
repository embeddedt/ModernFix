package org.embeddedt.modernfix.duck;

public interface IExtendedModelBaker {
    /**
     * Causes the ModelBaker to throw when it finds a missing model instead of proceeding with the bake.
     * @return the previous value of this flag
     */
    boolean throwOnMissingModel(boolean flag);
}
