package org.embeddedt.modernfix.duck.reuse_datapacks;

import net.minecraft.server.ServerResources;

import java.util.Collection;

public interface ICachingResourceClient {
    void setCachedResources(ServerResources r);
    void setCachedDataPackConfig(Collection<String> c);
}
