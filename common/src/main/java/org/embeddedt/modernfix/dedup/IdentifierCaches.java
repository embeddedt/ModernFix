package org.embeddedt.modernfix.dedup;


import org.embeddedt.modernfix.ModernFix;

public class IdentifierCaches {
    public static final DeduplicationCache<String> NAMESPACES = new DeduplicationCache<>();
    public static final DeduplicationCache<String> PATH = new DeduplicationCache<>();
    public static final DeduplicationCache<String> PROPERTY = new DeduplicationCache<>();
}
