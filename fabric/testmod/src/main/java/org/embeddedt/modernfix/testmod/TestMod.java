package org.embeddedt.modernfix.testmod;

import com.google.common.base.Stopwatch;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class TestMod implements ModInitializer {
    public static final String ID = "mfix_testmod";
    public static final Logger LOGGER = LogManager.getLogger("ModernFix TestMod");

    public static final int NUM_COLORS = 100;
    public static final int MAX_COLOR = NUM_COLORS - 1;

    public static final List<BlockState> WOOL_STATES = new ArrayList<>();

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
                    TestBlock block = Registry.register(BuiltInRegistries.BLOCK, name, new TestBlock());
                    WOOL_STATES.add(block.defaultBlockState());
                    Registry.register(BuiltInRegistries.ITEM, name, new TestBlockItem(block));
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

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public static BlockState getColorCubeStateFor(int chunkX, int chunkY, int chunkZ) {
        BlockState blockState = AIR;
        if (chunkX >= 0 && chunkY >= 0 && chunkZ >= 0 && chunkX % 2 == 0 && chunkY % 2 == 0 && chunkZ % 2 == 0) {
            chunkX /= 2;
            chunkY /= 2;
            chunkZ /= 2;
            if(chunkX <= TestMod.MAX_COLOR && chunkY <= TestMod.MAX_COLOR && chunkZ <= TestMod.MAX_COLOR) {
                blockState = TestMod.WOOL_STATES.get((chunkX * TestMod.NUM_COLORS * TestMod.NUM_COLORS) + (chunkY * TestMod.NUM_COLORS) + chunkZ);
            }
        }

        return blockState;
    }
}
