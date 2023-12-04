package org.embeddedt.modernfix.forge.rs;

import net.minecraftforge.fluids.capability.IFluidHandler;

public interface IFluidExternalStorageCache {
    boolean initCache(IFluidHandler handler);
}
