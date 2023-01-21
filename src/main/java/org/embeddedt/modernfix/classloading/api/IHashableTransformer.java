package org.embeddedt.modernfix.classloading.api;

public interface IHashableTransformer {
    /**
     * Called on an ILaunchPluginService or ITransformer to obtain a unique hash of the transformations that will be applied.
     * Used to invalidate the transformation cache when needed.
     * @param className Name of class being transformed
     * @return A unique hash of the transformations that will be applied
     */
    byte[] getHashForClass(String className);
}
