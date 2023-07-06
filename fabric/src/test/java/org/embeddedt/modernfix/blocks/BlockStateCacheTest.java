/*
package org.embeddedt.modernfix.blocks;

import net.minecraft.DetectedVersion;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.*;

public class BlockStateCacheTest {
    @BeforeAll
    public static void setup() {
        DetectedVersion.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testItemRegistry() {
        ResourceLocation location = Registry.ITEM.getKey(Items.DIAMOND);
        assertEquals(location.toString(), "minecraft:diamond");
    }
}

 */