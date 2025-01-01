package io.xpipe.app.core.mode;

import io.xpipe.app.beacon.AppBeaconServer;
import io.xpipe.app.core.*;
import io.xpipe.app.core.check.AppDebugModeCheck;
import io.xpipe.app.core.check.AppTempCheck;
import io.xpipe.app.core.window.AppMainWindow;
import io.xpipe.app.issue.*;
import io.xpipe.app.prefs.AppPrefs;
import io.xpipe.app.prefs.CloseBehaviour;
import io.xpipe.app.util.*;
import io.xpipe.core.process.OsType;
import io.xpipe.core.util.FailableRunnable;
import io.xpipe.core.util.XPipeDaemonMode;
import io.xpipe.core.util.XPipeInstallation;

import javafx.application.Platform;

import lombok.Getter;
import lombok.SneakyThrows;

import java.util.List;

public abstract class OperationMode {

    public static final OperationMode BACKGROUND = new BaseMode();
    public static final OperationMode TRAY = new TrayMode();
    public static final OperationMode GUI = new GuiMode();
    private static final List<OperationMode> ALL = List.of(BACKGROUND, TRAY, GUI);

    @Getter
    private static boolean inStartup;

    @Getter
    private static boolean inShutdown;

    @Getter
    private static boolean inShutdownHook;

    private static OperationMode CURRENT = null;

    public static OperationMode map(XPipeDaemonMode mode) {
        return switch (mode) {
            case BACKGROUND -> BACKGROUND;
            case TRAY -> TRAY;
            case GUI -> GUI;
        };
    }

    public static XPipeDaemonMode map(OperationMode mode) {
        if (mode == BACKGROUND) {
            return XPipeDaemonMode.BACKGROUND;
        }

        if (mode == TRAY) {
            return XPipeDaemonMode.TRAY;
        }

        if (mode == GUI) {
            return XPipeDaemonMode.GUI;
        }

        return null;
    }

    private static void setup(String[] args) {
        try {
            // Only for handling SIGTERM
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // If we used System.exit(), we don't want to do this
                if (OperationMode.isInShutdown()) {
                    return;
                }

                TrackEvent.info("Received SIGTERM externally");
                OperationMode.shutdown(true, false);
            }));

            // Handle uncaught exceptions
            Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
                // It seems like a few exceptions are thrown in the quantum renderer
                // when in shutdown. We can ignore these
                if (OperationMode.isInShutdown()
                        && Platform.isFxApplicationThread()
                        && ex instanceof NullPointerException) {
                    return;
                }

                // Handle any startup uncaught errors
                if (OperationMode.isInStartup() && thread.threadId() == 1) {
                    ex.printStackTrace();
                    OperationMode.halt(1);
                }

                ErrorEvent.fromThrowable(ex).unhandled(true).build().handle();
            });

            TrackEvent.info("Initial setup");
            AppMainWindow.loadingText("initializingApp");
            AppProperties.init(args);
            AppTempCheck.check();
            AppLogs.init();
            AppDebugModeCheck.printIfNeeded();
            AppProperties.logSystemProperties();
            AppProperties.logPassedProperties();
            AppExtensionManager.init(true);
            AppI18n.init();
            AppPrefs.initLocal();
            AppBeaconServer.setupPort();
            AppInstance.init();
            // Initialize early to load in parallel
            PlatformInit.init(false);
            ThreadHelper.runAsync(() -> {
                PlatformInit.init(true);
                AppMainWindow.init(OperationMode.getStartupMode() == XPipeDaemonMode.GUI);
            });
            TrackEvent.info("Finished initial setup");
        } catch (Throwable ex) {
            ErrorEvent.fromThrowable(ex).term().handle();
        }
    }

    public static XPipeDaemonMode getStartupMode() {
        var arg = AppProperties.get().getArguments().getModeArg();
        if (arg != null) {
            return arg;
        }

        var prop = AppProperties.get().getExplicitMode();
        if (prop != null) {
            return prop;
        }

        return AppPrefs.get() != null
                ? AppPrefs.get().startupBehaviour().getValue().getMode()
                : XPipeDaemonMode.GUI;
    }

    @SneakyThrows
    public static void init(String[] args) {
        inStartup = true;
        setup(args);

        if (AppProperties.get().isAotTrainMode()) {
            OperationMode.switchToSyncOrThrow(BACKGROUND);
            inStartup = false;
            // Linux runners don't support graphics
            if (OsType.getLocal() != OsType.LINUX) {
                OperationMode.switchToSyncOrThrow(OperationMode.GUI);
            }
            OperationMode.shutdown(false, false);
            return;
        }

        switchToSyncOrThrow(map(getStartupMode()));
        inStartup = false;
        AppOpenArguments.init();
    }

    public static void switchToAsync(OperationMode newMode) {
        ThreadHelper.createPlatformThread("mode switcher", false, () -> {
                    switchToSyncIfPossible(newMode);
                })
                .start();
    }

    public static void switchToSyncOrThrow(OperationMode newMode) throws Throwable {
        TrackEvent.info("Attempting to switch mode to " + newMode.getId());

        if (!newMode.isSupported()) {
            throw PlatformState.getLastError() != null
                    ? PlatformState.getLastError()
                    : new IllegalStateException("Unsupported operation mode: " + newMode.getId());
        }

        set(newMode);
    }

    public static boolean switchToSyncIfPossible(OperationMode newMode) {
        TrackEvent.info("Attempting to switch mode to " + newMode.getId());

        if (newMode.equals(TRAY) && !TRAY.isSupported()) {
            TrackEvent.info("Tray is not available, using base instead");
            set(BACKGROUND);
            return false;
        }

        if (newMode.equals(GUI) && !GUI.isSupported()) {
            TrackEvent.info("Gui is not available, using base instead");
            set(BACKGROUND);
            return false;
        }

        set(newMode);
        return true;
    }

    public static void switchUp(OperationMode newMode) {
        if (newMode == BACKGROUND) {
            return;
        }

        TrackEvent.info("Attempting to switch mode up to " + newMode.getId());

        if (newMode.equals(TRAY) && TRAY.isSupported() && OperationMode.get() == BACKGROUND) {
            set(TRAY);
            return;
        }

        if (newMode.equals(GUI) && GUI.isSupported()) {
            set(GUI);
        }
    }

    public static void close() {
        set(null);
    }

    public static List<OperationMode> getAll() {
        return ALL;
    }

    public static void startNewInstance() throws Exception {
        var loc = AppProperties.get().isDevelopmentEnvironment()
                ? XPipeInstallation.getLocalDefaultInstallationBasePath()
                : XPipeInstallation.getCurrentInstallationBasePath().toString();
        var exec = XPipeInstallation.createExternalAsyncLaunchCommand(loc, XPipeDaemonMode.GUI, "", true);
        LocalShell.getShell().executeSimpleCommand(exec);
    }

    public static void restart() {
        OperationMode.executeAfterShutdown(() -> {
            startNewInstance();
        });
    }

    public static void executeAfterShutdown(FailableRunnable<Exception> r) {
        Runnable exec = () -> {
            if (inShutdown) {
                return;
            }

            inShutdown = true;
            inShutdownHook = false;
            try {
                if (CURRENT != null) {
                    CURRENT.finalTeardown();
                }
                CURRENT = null;
                // Restart local shell
                LocalShell.init();
                r.run();
            } catch (Throwable ex) {
                ErrorEvent.fromThrowable(ex).handle();
                OperationMode.halt(1);
            }

            OperationMode.halt(0);
        };

        // Creates separate non daemon thread to force execution after shutdown even if current thread is a daemon
        var t = new Thread(exec);
        t.setDaemon(false);
        t.start();
    }

    private static final Object HALT_LOCK = new Object();

    public static void halt(int code) {
        synchronized (HALT_LOCK) {
            TrackEvent.info("Halting now!");
            AppLogs.teardown();
            Runtime.getRuntime().halt(code);
        }
    }

    public static void onWindowClose() {
        CloseBehaviour action;
        if (AppPrefs.get() != null && !isInStartup() && !isInShutdown()) {
            action = AppPrefs.get().closeBehaviour().getValue();
        } else {
            action = CloseBehaviour.QUIT;
        }
        ThreadHelper.runAsync(() -> {
            action.run();
        });
    }

    public static void shutdown(boolean inShutdownHook, boolean hasError) {
        if (isInStartup()) {
            TrackEvent.info("Received shutdown request while in startup. Halting ...");
            OperationMode.halt(1);
        }

        // In case we are stuck while in shutdown, instantly exit this application
        if (inShutdown && inShutdownHook) {
            TrackEvent.info("Received another shutdown request while in shutdown hook. Halting ...");
            OperationMode.halt(1);
        }

        if (inShutdown) {
            return;
        }

        // Run a timer to always exit after some time in case we get stuck
        if (!hasError && !AppProperties.get().isDevelopmentEnvironment()) {
            ThreadHelper.runAsync(() -> {
                ThreadHelper.sleep(25000);
                TrackEvent.info("Shutdown took too long. Halting ...");
                OperationMode.halt(1);
            });
        }

        TrackEvent.info("Starting shutdown ...");

        inShutdown = true;
        OperationMode.inShutdownHook = inShutdownHook;
        // Keep a non-daemon thread running
        var thread = ThreadHelper.createPlatformThread("shutdown", false, () -> {
            try {
                if (CURRENT != null) {
                    CURRENT.finalTeardown();
                }
                CURRENT = null;
            } catch (Throwable t) {
                ErrorEvent.fromThrowable(t).term().handle();
                OperationMode.halt(1);
            }

            OperationMode.halt(hasError ? 1 : 0);
        });
        thread.start();
    }

    private static synchronized void set(OperationMode newMode) {
        if (inShutdown) {
            return;
        }

        if (CURRENT == null && newMode == null) {
            return;
        }

        if (CURRENT != null && CURRENT.equals(newMode)) {
            return;
        }

        try {
            if (newMode == null) {
                shutdown(false, false);
                return;
            }

            if (CURRENT != null) {
                CURRENT.onSwitchFrom();
            }

            BACKGROUND.onSwitchTo();
            if (newMode != GUI
                    && AppMainWindow.getInstance() != null
                    && AppMainWindow.getInstance().getStage().isShowing()) {
                GUI.onSwitchTo();
                newMode = GUI;
            } else {
                newMode.onSwitchTo();
            }
            CURRENT = newMode;
        } catch (Throwable ex) {
            ErrorEvent.fromThrowable(ex).terminal(true).build().handle();
        }
    }

    public static OperationMode get() {
        return CURRENT;
    }

    public abstract boolean isSupported();

    public abstract String getId();

    public abstract void onSwitchTo() throws Throwable;

    public abstract void onSwitchFrom();

    public abstract void finalTeardown() throws Throwable;

    public ErrorHandler getErrorHandler() {
        return new SyncErrorHandler(new GuiErrorHandler());
    }
}
