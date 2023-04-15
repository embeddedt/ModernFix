package org.embeddedt.modernfix.util;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Lifecycle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.ServerLevelData;

import javax.annotation.Nullable;
import java.util.Set;

public class DummyServerConfiguration implements WorldData {
    @Override
    public WorldDataConfiguration getDataConfiguration() {
        return null;
    }

    @Override
    public void setDataConfiguration(WorldDataConfiguration arg) {

    }

    @Override
    public boolean wasModded() {
        return true;
    }

    @Override
    public Set<String> getKnownServerBrands() {
        return ImmutableSet.of("forge");
    }

    @Override
    public void setModdedInfo(String name, boolean isModded) {

    }

    @Nullable
    @Override
    public CompoundTag getCustomBossEvents() {
        return null;
    }

    @Override
    public void setCustomBossEvents(@Nullable CompoundTag nbt) {

    }

    @Override
    public ServerLevelData overworldData() {
        return null;
    }

    @Override
    public LevelSettings getLevelSettings() {
        return null;
    }

    @Override
    public CompoundTag createTag(RegistryAccess registries, @Nullable CompoundTag hostPlayerNBT) {
        return null;
    }

    @Override
    public boolean isHardcore() {
        return false;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public String getLevelName() {
        return null;
    }

    @Override
    public GameType getGameType() {
        return null;
    }

    @Override
    public void setGameType(GameType type) {

    }

    @Override
    public boolean getAllowCommands() {
        return false;
    }

    @Override
    public Difficulty getDifficulty() {
        return null;
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {

    }

    @Override
    public boolean isDifficultyLocked() {
        return false;
    }

    @Override
    public void setDifficultyLocked(boolean locked) {

    }

    @Override
    public GameRules getGameRules() {
        return null;
    }

    @Override
    public CompoundTag getLoadedPlayerTag() {
        return null;
    }

    @Override
    public CompoundTag endDragonFightData() {
        return null;
    }

    @Override
    public void setEndDragonFightData(CompoundTag nbt) {

    }

    @Override
    public WorldOptions worldGenOptions() {
        return null;
    }

    @Override
    public boolean isFlatWorld() {
        return false;
    }

    @Override
    public boolean isDebugWorld() {
        return false;
    }

    @Override
    public Lifecycle worldGenSettingsLifecycle() {
        return Lifecycle.stable();
    }
}
