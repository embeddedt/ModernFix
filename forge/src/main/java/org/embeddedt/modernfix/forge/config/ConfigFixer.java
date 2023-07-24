package org.embeddedt.modernfix.forge.config;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;

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
        if(!ModernFixMixinPlugin.instance.isOptionEnabled("bugfix.fix_config_crashes.ConfigFixerMixin"))
            return;
        ModList.get().forEachModContainer((id, container) -> {
            try {
                Optional<Consumer<IConfigEvent>> configOpt = ObfuscationReflectionHelper.getPrivateValue(ModContainer.class, container, "configHandler");
                if(configOpt.isPresent()) {
                    ObfuscationReflectionHelper.setPrivateValue(ModContainer.class, container, Optional.of(new LockingConfigHandler(id, configOpt.get())), "configHandler");
                }
            } catch(RuntimeException e) {
                ModernFix.LOGGER.error("Error replacing config handler", e);
            }
        });
    }

    private static class LockingConfigHandler implements Consumer<IConfigEvent> {
        private final Consumer<IConfigEvent> actualHandler;
        private final String modId;

        LockingConfigHandler(String id, Consumer<IConfigEvent> actualHandler) {
            this.modId = id;
            this.actualHandler = actualHandler;
        }

        @Override
        public void accept(IConfigEvent modConfigEvent) {
            Object cfgObj = NightConfigFixer.toWriteSyncConfig(modConfigEvent.getConfig().getConfigData());
            if(cfgObj != null) {
                // don't synchronize on 'this' as it produces a deadlock when used alongside NightConfigFixer
                synchronized (cfgObj) {
                    this.actualHandler.accept(modConfigEvent);
                }
            } else {
                this.actualHandler.accept(modConfigEvent);
            }
        }

        @Override
        public String toString() {
            return "LockingConfigHandler{id=" + modId + "}";
        }
    }
}
