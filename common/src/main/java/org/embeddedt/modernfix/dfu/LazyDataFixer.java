package org.embeddedt.modernfix.dfu;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

public class LazyDataFixer implements DataFixer {
    private static final Logger LOGGER = LogManager.getLogger("ModernFix");
    private DataFixer backingDataFixer;
    private final Supplier<DataFixer> dfuSupplier;

    public LazyDataFixer(Supplier<DataFixer> dfuSupplier) {
        LOGGER.info("Bypassed Mojang DFU");
        this.backingDataFixer = null;
        this.dfuSupplier = dfuSupplier;
    }

    private DataFixer getDataFixer() {
        synchronized (this) {
            if(backingDataFixer == null) {
                LOGGER.info("Instantiating Mojang DFU");
                DFUBlaster.blastMaps();
                backingDataFixer = dfuSupplier.get();
            }
        }
        return backingDataFixer;
    }

    @Override
    public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int version, int newVersion) {
        if(version >= newVersion)
            return input;
        return getDataFixer().update(type, input, version, newVersion);
    }

    @Override
    public Schema getSchema(int key) {
        return getDataFixer().getSchema(key);
    }
}
