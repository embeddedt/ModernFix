package org.embeddedt.modernfix.util;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Lifecycle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.Set;

public class DummyServerConfiguration implements WorldData {
    @Override
    public DataPackConfig getDataPackConfig() {
        return DataPackConfig.DEFAULT;
    }

    @Override
    public void setDataPackConfig(DataPackConfig codec) {

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

    @Override
    public CompoundTag getCustomBossEvents() {
        return null;
    }

    @Override
    public void setCustomBossEvents(CompoundTag nbt) {

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
    public CompoundTag createTag(RegistryAccess registries, CompoundTag hostPlayerNBT) {
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
    public WorldGenSettings worldGenSettings() {
        return null;
    }

    @Override
    public Lifecycle worldGenSettingsLifecycle() {
        return Lifecycle.stable();
    }
}
