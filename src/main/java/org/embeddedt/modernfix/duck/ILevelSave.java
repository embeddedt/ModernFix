package org.embeddedt.modernfix.duck;

import net.minecraft.world.level.storage.LevelStorageSource;

public interface ILevelSave {
    public void runWorldPersistenceHooks(LevelStorageSource format);
}
