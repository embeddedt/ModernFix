package org.embeddedt.modernfix.util;

public enum BakeReason {
    FREEZE,
    REMOTE_SNAPSHOT_INJECT,
    LOCAL_SNAPSHOT_INJECT,
    REVERT;
    public static BakeReason currentBakeReason = null;
}
