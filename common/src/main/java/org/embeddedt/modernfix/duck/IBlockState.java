package org.embeddedt.modernfix.duck;


public interface IBlockState {
    void clearCache();
    boolean isCacheInvalid();
}
