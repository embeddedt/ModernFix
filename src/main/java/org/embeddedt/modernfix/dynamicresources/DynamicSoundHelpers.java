package org.embeddedt.modernfix.dynamicresources;

import com.mojang.blaze3d.audio.SoundBuffer;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.ModernFix;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DynamicSoundHelpers {
    private static final long SOUND_EVICTION_DELAY = TimeUnit.SECONDS.toNanos(30);
    private static final boolean debugDynamicSoundLoading = Boolean.getBoolean("modernfix.debugDynamicSoundLoading");

    public interface SoundBufAccess {
        long mfix$getDurationNanos();
    }

    public static final class Cache extends AbstractMap<ResourceLocation, CompletableFuture<SoundBuffer>> {
        private static class Entry {
            private final CompletableFuture<SoundBuffer> buffer;
            private long lastAccessTime;

            private Entry(CompletableFuture<SoundBuffer> buffer) {
                this.buffer = buffer;
                this.lastAccessTime = System.nanoTime();
            }

            public CompletableFuture<SoundBuffer> getBuffer() {
                this.lastAccessTime = System.nanoTime();
                return this.buffer;
            }

            public long getDuration() {
                var buf = this.buffer.getNow(null);
                if (buf == null) {
                    return 0;
                }
                return ((SoundBufAccess)buf).mfix$getDurationNanos();
            }

            public boolean isExpired(long currentTs) {
                long duration = getDuration();
                return duration > 0 && (currentTs - this.lastAccessTime) >= (duration + SOUND_EVICTION_DELAY);
            }

            public void discard() {
                this.buffer.thenAccept(SoundBuffer::discardAlBuffer);
            }

            @Override
            public String toString() {
                return super.toString();
            }
        }

        private final Object2ObjectLinkedOpenHashMap<ResourceLocation, Entry> store = new Object2ObjectLinkedOpenHashMap<>();

        public Cache(Map<ResourceLocation, CompletableFuture<SoundBuffer>> otherMap) {
            this.putAll(otherMap);
        }

        private void checkExpired() {
            long ts = System.nanoTime();
            var iter = this.store.object2ObjectEntrySet().fastIterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                if (entry.getValue().isExpired(ts)) {
                    if (debugDynamicSoundLoading) {
                        ModernFix.LOGGER.warn("Evicted sound {} with duration {} ms", entry.getKey(), entry.getValue().getDuration() / 1000000);
                    }
                    entry.getValue().discard();
                    iter.remove();
                } else {
                    break;
                }
            }
        }

        @Override
        public CompletableFuture<SoundBuffer> get(Object key) {
            if (key instanceof ResourceLocation rl) {
                var entry = this.store.getAndMoveToLast(rl);
                CompletableFuture<SoundBuffer> result = entry != null ? entry.getBuffer() : null;
                this.checkExpired();
                return result;
            } else {
                return null;
            }
        }

        @Override
        public CompletableFuture<SoundBuffer> put(ResourceLocation key, CompletableFuture<SoundBuffer> value) {
            var entry = new Entry(value);
            if (debugDynamicSoundLoading) {
                ModernFix.LOGGER.info("Loaded sound {}", key);
            }
            var previousEntry = this.store.putAndMoveToLast(key, entry);
            return previousEntry != null ? previousEntry.getBuffer() : null;
        }

        @Override
        public @NotNull Set<Map.Entry<ResourceLocation, CompletableFuture<SoundBuffer>>> entrySet() {
            return new EntrySet();
        }

        private class EntrySet extends AbstractSet<Map.Entry<ResourceLocation, CompletableFuture<SoundBuffer>>> {
            @Override
            public Iterator<Map.Entry<ResourceLocation, CompletableFuture<SoundBuffer>>> iterator() {
                var storeIter = store.entrySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return storeIter.hasNext();
                    }

                    @Override
                    public Map.Entry<ResourceLocation, CompletableFuture<SoundBuffer>> next() {
                        var entry = storeIter.next();
                        return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue().buffer);
                    }
                };
            }

            @Override
            public int size() {
                return store.size();
            }

            @Override
            public void clear() {
                store.clear();
            }
        }
    }
}
