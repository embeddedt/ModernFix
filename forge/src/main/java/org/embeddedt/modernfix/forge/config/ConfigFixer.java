package org.embeddedt.modernfix.forge.config;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.config.ModConfig;
import org.embeddedt.modernfix.ModernFix;

import java.util.Optional;
import java.util.function.Consumer;

public class ConfigFixer {
    /**
     * To prevent mods from crashing if their ModConfigEvent is invoked by Night Config's watch thread and Forge
     * at the same time, wrap their config handler so that it only executes the event in serial for that mod.
     *
     * Should have no noticeable performance impact as config handlers are virtually instant.
     */
    public static void replaceConfigHandlers() {
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

        LockingConfigHandler(String id, Consumer<ModConfig.ModConfigEvent> actualHandler) {
            this.modId = id;
            this.actualHandler = actualHandler;
        }

        @Override
        public void accept(ModConfig.ModConfigEvent modConfigEvent) {
            synchronized (this) {
                this.actualHandler.accept(modConfigEvent);
            }
        }

        @Override
        public String toString() {
            return "LockingConfigHandler{id=" + modId + "}";
        }
    }
}
