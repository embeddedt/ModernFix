package org.embeddedt.modernfix.fabric.datagen;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.impl.datagen.FabricDataGenHelper;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.TextComponent;
import org.embeddedt.modernfix.ModernFix;

import java.lang.reflect.Method;

public class RuntimeDatagen {
    private static final boolean SHOULD_RUNTIME_DATAGEN = System.getProperty("fabric-api.datagen.output-dir") != null;

    private static void runRuntimeDatagen() {
        // call runInternal directly to avoid exiting immediately
        try {
            System.setProperty("fabric-api.datagen", "true");
            Method method = FabricDataGenHelper.class.getDeclaredMethod("runInternal");
            method.setAccessible(true);
            method.invoke(null);
        } catch(Throwable e) {
            ModernFix.LOGGER.error("Error running datagen", e);
        } finally {
            System.clearProperty("fabric-api.datagen");
        }
    }

    public static void init() {
        if(!SHOULD_RUNTIME_DATAGEN)
            return;
        ScreenEvents.AFTER_INIT.register(((client, s, scaledWidth, scaledHeight) -> {
            if(s instanceof TitleScreen screen) {
                screen.addRenderableWidget(new Button(screen.width / 2 - 100 - 50, screen.height / 4 + 48, 50, 20, new TextComponent("DG"), (arg) -> {
                    runRuntimeDatagen();
                }));
            }
        }));
    }
}
