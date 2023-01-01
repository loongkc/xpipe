package io.xpipe.core.impl;

import io.xpipe.core.process.ShellProcessControl;
import io.xpipe.core.process.ShellTypes;

import java.util.ServiceLoader;

public abstract class LocalProcessControlProvider {

    private static LocalProcessControlProvider INSTANCE;

    public static LocalProcessControlProvider get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Process control not initialized");
        }

        return INSTANCE;
    }

    public static void init(ModuleLayer layer) {
        INSTANCE = layer != null
                ? ServiceLoader.load(layer, LocalProcessControlProvider.class)
                .findFirst()
                .orElseThrow()
                : ServiceLoader.load(LocalProcessControlProvider.class)
                .findFirst()
                .orElseThrow();
    }

    public static ShellProcessControl create() {
        return INSTANCE.createProcessControl();
    }

    public abstract ShellProcessControl createProcessControl();

    public abstract void openInTerminal(String title, String command) throws Exception;
}
