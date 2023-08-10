package org.embeddedt.modernfix.testmod;

import com.google.common.base.Stopwatch;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestMod implements ModInitializer {
    public static final String ID = "mfix_testmod";
    public static final Logger LOGGER = LogManager.getLogger("ModernFix TestMod");

    public static final int NUM_COLORS = 32;
    public static final int MAX_COLOR = NUM_COLORS - 1;

    @Override
    public void onInitialize() {
        // Register 1 million blocks & items
        Stopwatch watch = Stopwatch.createStarted();
        int totalToRegister = NUM_COLORS * NUM_COLORS * NUM_COLORS;
        int progressReport = totalToRegister / 20;
        int numRegistered = 0;
        for(int r = 0; r < NUM_COLORS; r++) {
            for(int g = 0; g < NUM_COLORS; g++) {
                for(int b = 0; b < NUM_COLORS; b++) {
                    ResourceLocation name = new ResourceLocation(ID, "wool_" + r + "_" + g + "_" + b);
                    TestBlock block = Registry.register(Registry.BLOCK, name, new TestBlock());
                    Registry.register(Registry.ITEM, name, new TestBlockItem(block));
                    numRegistered++;
                    if((numRegistered % progressReport) == 0) {
                        LOGGER.info(String.format("Registering... %.02f%%", ((float)numRegistered)/totalToRegister * 100));
                    }
                }
            }
        }
        watch.stop();
        LOGGER.info("Registered {} registry entries in {}", totalToRegister, watch);
    }
}
