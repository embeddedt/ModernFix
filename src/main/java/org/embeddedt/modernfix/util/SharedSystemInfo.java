package org.embeddedt.modernfix.util;

import oshi.SystemInfo;

/**
 * OSHI caches hardware facts per instance, and some (the CPU identity on Linux runs lshw) take over a second.
 */
public final class SharedSystemInfo {
    private SharedSystemInfo() {
    }

    private static final class Holder {
        static final SystemInfo INSTANCE = new SystemInfo();
    }

    public static SystemInfo get() {
        return Holder.INSTANCE;
    }
}
