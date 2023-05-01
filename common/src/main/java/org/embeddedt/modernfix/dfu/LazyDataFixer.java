package org.embeddedt.modernfix.dfu;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.constant.EmptyPart;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

public class LazyDataFixer implements DataFixer {
    private static final Logger LOGGER = LogManager.getLogger("ModernFix");
    private DataFixer backingDataFixer;
    private final Supplier<DataFixer> dfuSupplier;
    private static final Schema FAKE_SCHEMA = new EmptySchema();

    public LazyDataFixer(Supplier<DataFixer> dfuSupplier) {
        LOGGER.info("Bypassed Mojang DFU");
        this.backingDataFixer = null;
        this.dfuSupplier = dfuSupplier;
    }
    @Override
    public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int version, int newVersion) {
        if(version >= newVersion)
            return input;
        synchronized (this) {
            if(backingDataFixer == null) {
                LOGGER.info("Instantiating Mojang DFU");
                backingDataFixer = dfuSupplier.get();
            }
        }
        return backingDataFixer.update(type, input, version, newVersion);
    }

    /**
     * "getSchema is only there for checks that are not important" - fry, 2021
     */
    @Override
    public Schema getSchema(int key) {
        return FAKE_SCHEMA;
    }

    /**
     * Empty schema that also returns empty Type<?> instances to prevent crashes.
     */
    static class EmptySchema extends Schema {
        public EmptySchema() {
            super(DataFixUtils.makeKey(SharedConstants.getCurrentVersion().getWorldVersion()), null);
        }

        private static final Type<?> EMPTY_TYPE = new EmptyPart();
        private static final TypeTemplate FAKE_TEMPLATE = EMPTY_TYPE.template();


        @Override
        protected Map<String, Type<?>> buildTypes() {
            Object2ObjectOpenHashMap<String, Type<?>> map = new Object2ObjectOpenHashMap<>();
            map.defaultReturnValue(new EmptyPart());
            return map;
        }

        @Override
        public TypeTemplate resolveTemplate(String name) {
            return FAKE_TEMPLATE;
        }

        @Override
        public Type<?> getChoiceType(DSL.TypeReference type, String choiceName) {
            return EMPTY_TYPE;
        }

        @Override
        public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes) {
        }

        @Override
        public Map<String, Supplier<TypeTemplate>> registerEntities(Schema schema) {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
            return Collections.emptyMap();
        }
    }
}
