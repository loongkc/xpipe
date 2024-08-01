package io.xpipe.app.prefs;

import io.xpipe.app.core.AppFont;
import io.xpipe.app.core.AppI18n;
import io.xpipe.app.core.AppProperties;
import io.xpipe.app.core.mode.OperationMode;
import io.xpipe.app.core.window.AppWindowHelper;
import io.xpipe.app.issue.ErrorEvent;
import io.xpipe.app.util.DesktopHelper;
import io.xpipe.app.util.DesktopShortcuts;
import io.xpipe.app.util.OptionsBuilder;
import io.xpipe.app.util.ThreadHelper;
import io.xpipe.core.process.OsType;
import io.xpipe.core.util.XPipeInstallation;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import org.apache.commons.io.FileUtils;

import java.nio.file.Files;

public class ProfileCreationAlert {

    public static void showAsync() {
        ThreadHelper.runFailableAsync(() -> {
            show();
        });
    }

    private static void show() throws Exception {
        var name = new SimpleObjectProperty<>("New profile");
        var path = new SimpleObjectProperty<>(AppProperties.get().getDataDir());
        var show = AppWindowHelper.showBlockingAlert(alert -> {
                    alert.setTitle(AppI18n.get("profileCreationAlertTitle"));
                    var content = new OptionsBuilder()
                            .nameAndDescription("profileName")
                            .addString(name)
                            .nameAndDescription("profilePath")
                            .addPath(path)
                            .buildComp()
                            .minWidth(500)
                            .padding(new Insets(5, 20, 20, 20))
                            .apply(struc -> AppFont.small(struc.get()))
                            .createRegion();
                    alert.getButtonTypes().add(ButtonType.CANCEL);
                    alert.getButtonTypes().add(ButtonType.OK);
                    alert.getDialogPane().setContent(content);
                })
                .map(b -> b.getButtonData().isDefaultButton())
                .orElse(false);

        if (!show || name.get() == null || path.get() == null) {
            return;
        }

        if (Files.exists(path.get()) && !FileUtils.isEmptyDirectory(path.get().toFile())) {
            ErrorEvent.fromMessage("New profile directory is not empty").expected().handle();
            return;
        }

        var shortcutName = (AppProperties.get().isStaging() ? "XPipe PTB" : "XPipe") + " (" + name.get() + ")";
        var file = switch (OsType.getLocal()) {
            case OsType.Windows w -> {
              var exec = XPipeInstallation.getCurrentInstallationBasePath().resolve(XPipeInstallation.getDaemonExecutablePath(w)).toString();
              yield DesktopShortcuts.create(exec, "-Dio.xpipe.app.dataDir=\"" + path.get().toString() + "\" -Dio.xpipe.app.acceptEula=true", shortcutName);
          }
            default -> {
                var exec = XPipeInstallation.getCurrentInstallationBasePath().resolve(XPipeInstallation.getRelativeCliExecutablePath(OsType.getLocal())).toString();
                yield DesktopShortcuts.create(exec, "-d \"" + path.get().toString() + "\" --accept-eula", shortcutName);
            }
        };
        DesktopHelper.browseFileInDirectory(file);
        OperationMode.close();
    }
}
