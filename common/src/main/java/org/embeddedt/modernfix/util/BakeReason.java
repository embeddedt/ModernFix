package org.embeddedt.modernfix.util;

import org.embeddedt.modernfix.ModernFix;

public enum BakeReason {
    FREEZE,
    REMOTE_SNAPSHOT_INJECT,
    LOCAL_SNAPSHOT_INJECT,
    REVERT,
    UNKNOWN;
    private static BakeReason currentBakeReason = null;
    private static boolean bakeReasonWarned = false;

    public static BakeReason getCurrentBakeReason() {
        if(currentBakeReason == null && !bakeReasonWarned) {
            ModernFix.LOGGER.warn("No bake reason found, mixin probably not applied correctly", new IllegalStateException());
            bakeReasonWarned = false;
        }
        return currentBakeReason;
    }

    public static void setCurrentBakeReason(BakeReason reason) {
        currentBakeReason = reason;
    }
}
