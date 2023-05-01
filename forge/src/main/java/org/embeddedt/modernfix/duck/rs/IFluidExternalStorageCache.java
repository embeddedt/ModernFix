package org.embeddedt.modernfix.duck.rs;

import net.minecraftforge.fluids.capability.IFluidHandler;

public interface IFluidExternalStorageCache {
    boolean initCache(IFluidHandler handler);
}
