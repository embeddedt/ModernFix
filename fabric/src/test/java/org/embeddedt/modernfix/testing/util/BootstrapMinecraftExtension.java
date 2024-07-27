package org.embeddedt.modernfix.testing.util;

import net.minecraft.DetectedVersion;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;

/**
 * Simple extension to run vanilla bootstrap, inspired by AE2.
 */
public class BootstrapMinecraftExtension implements Extension, BeforeAllCallback, AfterAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        DetectedVersion.tryDetectVersion();
        Bootstrap.bootStrap();
        // Allow blocks to be created in tests
        Field field = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        field.setAccessible(true);
        if(field.get(BuiltInRegistries.BLOCK) == null) {
            field.set(BuiltInRegistries.BLOCK, new IdentityHashMap<>());
            field = MappedRegistry.class.getDeclaredField("frozen");
            field.setAccessible(true);
            field.setBoolean(BuiltInRegistries.BLOCK, false);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {

    }
}
