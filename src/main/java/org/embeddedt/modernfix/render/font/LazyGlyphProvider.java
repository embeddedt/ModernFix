package org.embeddedt.modernfix.render.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.gui.font.providers.GlyphProviderType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.function.Function;

public class LazyGlyphProvider implements GlyphProvider {
    private final GlyphProviderDefinition.Loader loader;
    private final ResourceManager manager;

    private SoftReference<GlyphProvider> innerProvider = new SoftReference<>(null);

    private IntSet supportedGlyphs;

    LazyGlyphProvider(GlyphProviderDefinition.Loader loader, ResourceManager manager) {
        this.loader = loader;
        this.manager = manager;
    }

    @Override
    public void close() {
        // best effort
        var prov = innerProvider.get();
        if (prov != null) {
            prov.close();
        }
    }

    private @Nullable GlyphProvider getGlyphProvider() {
        GlyphProvider prov = innerProvider.get();
        if (prov == null) {
            try {
                prov = this.loader.load(this.manager);
            } catch (IOException e) {
                return null;
            }
            innerProvider = new SoftReference<>(prov);
        }
        return prov;
    }

    @Override
    public @Nullable GlyphInfo getGlyph(int character) {
        var prov = getGlyphProvider();
        if (prov != null) {
            return prov.getGlyph(character);
        } else {
            return null;
        }
    }

    @Override
    public IntSet getSupportedGlyphs() {
        if (supportedGlyphs == null) {
            var prov = getGlyphProvider();
            if (prov != null) {
                supportedGlyphs = new IntOpenHashSet(prov.getSupportedGlyphs());
            } else {
                supportedGlyphs = IntSet.of();
            }
        }
        return supportedGlyphs;
    }

    private static class Definition implements GlyphProviderDefinition {
        private final GlyphProviderDefinition delegate;

        public Definition(GlyphProviderDefinition delegate) {
            this.delegate = delegate;
        }

        @Override
        public GlyphProviderType type() {
            return this.delegate.type();
        }

        @Override
        public Either<Loader, Reference> unpack() {
            return this.delegate.unpack().mapBoth(
                    loader -> resourceManager -> new LazyGlyphProvider(loader, resourceManager),
                    Function.identity()
            );
        }

        @SuppressWarnings("unchecked")
        public <T extends GlyphProviderDefinition> T delegate() {
            return (T)this.delegate;
        }
    }

    public static MapCodec<? extends GlyphProviderDefinition> wrap(MapCodec<? extends GlyphProviderDefinition> codec) {
        return codec.xmap(Definition::new, Definition::delegate);
    }
}
