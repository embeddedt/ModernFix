package org.embeddedt.modernfix.testing.util;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Simple extension to run vanilla bootstrap, inspired by AE2.
 */
public class BootstrapMinecraftExtension implements Extension, BeforeAllCallback, AfterAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {

    }
}
