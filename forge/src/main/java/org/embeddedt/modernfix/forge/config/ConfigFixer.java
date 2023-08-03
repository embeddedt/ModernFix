package org.embeddedt.modernfix.forge.config;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.config.ModConfig;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ConfigFixer {
    /**
     * To prevent mods from crashing if their ModConfigEvent is invoked by Night Config's watch thread and Forge
     * at the same time, wrap their config handler so that it only executes the event in serial for that mod.
     *
     * Should have no noticeable performance impact as config handlers are virtually instant.
     */
    public static void replaceConfigHandlers() {
        if(!ModernFixMixinPlugin.instance.isOptionEnabled("bugfix.fix_config_crashes.ConfigFixerMixin"))
            return;
        ModList.get().forEachModContainer((id, container) -> {
            try {
                Optional<Consumer<ModConfig.ModConfigEvent>> configOpt = ObfuscationReflectionHelper.getPrivateValue(ModContainer.class, container, "configHandler");
                if(configOpt.isPresent()) {
                    ObfuscationReflectionHelper.setPrivateValue(ModContainer.class, container, Optional.of(new LockingConfigHandler(id, configOpt.get())), "configHandler");
                }
            } catch(RuntimeException e) {
                ModernFix.LOGGER.error("Error replacing config handler", e);
            }
        });
    }

    private static class LockingConfigHandler implements Consumer<ModConfig.ModConfigEvent> {
        private final Consumer<ModConfig.ModConfigEvent> actualHandler;
        private final String modId;
        private final Lock lock = new ReentrantLock();

        LockingConfigHandler(String id, Consumer<ModConfig.ModConfigEvent> actualHandler) {
            this.modId = id;
            this.actualHandler = actualHandler;
        }

        @Override
        public void accept(ModConfig.ModConfigEvent modConfigEvent) {
            try {
                if(lock.tryLock(2, TimeUnit.SECONDS)) {
                    try {
                        this.actualHandler.accept(modConfigEvent);
                    } finally {
                        lock.unlock();
                    }
                } else
                    ModernFix.LOGGER.error("Failed to post config event for {}, someone else is holding the lock", modId);
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public String toString() {
            return "LockingConfigHandler{id=" + modId + "}";
        }
    }
}
