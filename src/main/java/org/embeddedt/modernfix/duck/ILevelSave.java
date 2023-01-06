package org.embeddedt.modernfix.duck;

import net.minecraft.world.storage.SaveFormat;

public interface ILevelSave {
    public void runWorldPersistenceHooks(SaveFormat format);
}
