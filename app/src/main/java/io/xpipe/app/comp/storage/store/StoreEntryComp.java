package io.xpipe.app.comp.storage.store;

import atlantafx.base.theme.Styles;
import io.xpipe.app.core.AppFont;
import io.xpipe.app.core.AppI18n;
import io.xpipe.app.ext.ActionProvider;
import io.xpipe.app.fxcomps.Comp;
import io.xpipe.app.fxcomps.SimpleComp;
import io.xpipe.app.fxcomps.augment.ContextMenuAugment;
import io.xpipe.app.fxcomps.impl.*;
import io.xpipe.app.fxcomps.util.BindingsHelper;
import io.xpipe.app.fxcomps.util.PlatformThread;
import io.xpipe.app.fxcomps.util.SimpleChangeListener;
import io.xpipe.app.prefs.AppPrefs;
import io.xpipe.app.storage.DataStorage;
import io.xpipe.app.util.DesktopHelper;
import io.xpipe.app.util.ThreadHelper;
import io.xpipe.core.store.FixedHierarchyStore;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;

public abstract class StoreEntryComp extends SimpleComp {

    public static Comp<?> customSection(StoreEntryWrapper e) {
        var prov = e.getEntry().getProvider();
        if (prov != null) {
            return prov.customDisplay(e);
        } else {
            return new StandardStoreEntryComp(e);
        }
    }

    public static final double NAME_WIDTH = 0.30;
    public static final double STORE_TYPE_WIDTH = 0.08;
    public static final double DETAILS_WIDTH = 0.52;
    public static final double BUTTONS_WIDTH = 0.1;
    public static final PseudoClass FAILED = PseudoClass.getPseudoClass("failed");
    public static final PseudoClass INCOMPLETE = PseudoClass.getPseudoClass("incomplete");
    protected final StoreEntryWrapper entry;

    public StoreEntryComp(StoreEntryWrapper entry) {
        this.entry = entry;
    }

    protected Label createInformation() {
        var information = new Label();
        information.textProperty().bind(PlatformThread.sync(entry.getInformation()));
        information.getStyleClass().add("information");
        AppFont.header(information);
        return information;
    }

    protected Label createSummary() {
        var summary = new Label();
        summary.textProperty().bind(PlatformThread.sync(entry.getSummary()));
        summary.getStyleClass().add("summary");
        AppFont.small(summary);
        return summary;
    }

    protected void applyState(Node node) {
        SimpleChangeListener.apply(PlatformThread.sync(entry.getState()), val -> {
            switch (val) {
                case LOAD_FAILED -> {
                    node.pseudoClassStateChanged(FAILED, true);
                    node.pseudoClassStateChanged(INCOMPLETE, false);
                }
                case INCOMPLETE -> {
                    node.pseudoClassStateChanged(FAILED, false);
                    node.pseudoClassStateChanged(INCOMPLETE, true);
                }
                default -> {
                    node.pseudoClassStateChanged(FAILED, false);
                    node.pseudoClassStateChanged(INCOMPLETE, false);
                }
            }
        });
    }

    protected Comp<?> createName() {
        var filtered = BindingsHelper.filteredContentBinding(
                StoreViewState.get().getAllEntries(),
                other -> other.getEntry().getState().isUsable()
                        && entry.getEntry()
                                .getStore()
                                .equals(other.getEntry()
                                        .getProvider()
                                        .getLogicalParent(other.getEntry().getStore())));
        LabelComp name = new LabelComp(Bindings.createStringBinding(
                () -> {
                    return entry.getName()
                            + (entry.getInformation().get() != null
                                    ? "    [" + entry.getInformation().get() + "]"
                                    : "")
                            + (filtered.size() > 0 && entry.getEntry().getStore() instanceof FixedHierarchyStore
                                    ? "     (" + filtered.size() + ")"
                                    : "");
                },
                entry.nameProperty(),
                entry.getInformation(),
                filtered));
        name.apply(struc -> struc.get().setTextOverrun(OverrunStyle.CENTER_ELLIPSIS))
                .apply(struc -> struc.get().setPadding(new Insets(5, 5, 5, 0)));
        name.apply(s -> AppFont.header(s.get()));
        return name;
    }

    protected Node createIcon(int w, int h) {
        var img = entry.isDisabled()
                ? "disabled_icon.png"
                : entry.getEntry()
                        .getProvider()
                        .getDisplayIconFileName(entry.getEntry().getStore());
        var imageComp = new PrettyImageComp(new SimpleStringProperty(img), w, h);
        var storeIcon = imageComp.createRegion();
        storeIcon.getStyleClass().add("icon");
        if (entry.getState().getValue().isUsable()) {
            new FancyTooltipAugment<>(new SimpleStringProperty(
                            entry.getEntry().getProvider().getDisplayName()))
                    .augment(storeIcon);
        }
        return storeIcon;
    }

    protected Comp<?> createButtonBar() {
        var list = new ArrayList<Comp<?>>();
        for (var p : entry.getActionProviders().entrySet()) {
            var actionProvider = p.getKey().getDataStoreCallSite();
            if (!actionProvider.isMajor()
                    || p.getKey().equals(entry.getDefaultActionProvider().getValue())) {
                continue;
            }

            var button = new IconButtonComp(
                    actionProvider.getIcon(entry.getEntry().getStore().asNeeded()), () -> {
                        ThreadHelper.runFailableAsync(() -> {
                            var action = actionProvider.createAction(
                                    entry.getEntry().getStore().asNeeded());
                            action.execute();
                        });
                    });
            button.apply(new FancyTooltipAugment<>(
                    actionProvider.getName(entry.getEntry().getStore().asNeeded())));
            if (actionProvider.activeType() == ActionProvider.DataStoreCallSite.ActiveType.ONLY_SHOW_IF_ENABLED) {
                button.hide(Bindings.not(p.getValue()));
            } else if (actionProvider.activeType() == ActionProvider.DataStoreCallSite.ActiveType.ALWAYS_SHOW) {
                button.disable(Bindings.not(p.getValue()));
            }
            list.add(button);
        }

        var settingsButton = createSettingsButton();
        list.add(settingsButton);
        if (list.size() > 1) {
            list.get(0).styleClass(Styles.LEFT_PILL);
            for (int i = 1; i < list.size() - 1; i++) {
                list.get(i).styleClass(Styles.CENTER_PILL);
            }
            list.get(list.size() - 1).styleClass(Styles.RIGHT_PILL);
        }
        list.forEach(comp -> {
            comp.apply(struc -> struc.get().getStyleClass().remove(Styles.FLAT));
        });
        return new HorizontalComp(list)
                .apply(struc -> {
                    struc.get().setAlignment(Pos.CENTER_RIGHT);
                    struc.get().setPadding(new Insets(5));
                })
                .styleClass("button-bar");
    }

    protected Comp<?> createSettingsButton() {
        var settingsButton = new IconButtonComp("mdomz-settings");
        settingsButton.styleClass("settings");
        settingsButton.accessibleText("Settings");
        settingsButton.apply(new ContextMenuAugment<>(
                event -> event.getButton() == MouseButton.PRIMARY, () -> StoreEntryComp.this.createContextMenu()));
        settingsButton.apply(new FancyTooltipAugment<>("more"));
        return settingsButton;
    }

    protected ContextMenu createContextMenu() {
        var contextMenu = new ContextMenu();
        AppFont.normal(contextMenu.getStyleableNode());

        for (var p : entry.getActionProviders().entrySet()) {
            var actionProvider = p.getKey().getDataStoreCallSite();
            if (actionProvider.isMajor()) {
                continue;
            }

            var name = actionProvider.getName(entry.getEntry().getStore().asNeeded());
            var icon = actionProvider.getIcon(entry.getEntry().getStore().asNeeded());
            var item = new MenuItem(null, new FontIcon(icon));
            item.setOnAction(event -> {
                ThreadHelper.runFailableAsync(() -> {
                    var action = actionProvider.createAction(
                            entry.getEntry().getStore().asNeeded());
                    action.execute();
                });
            });
            item.textProperty().bind(name);
            if (actionProvider.activeType() == ActionProvider.DataStoreCallSite.ActiveType.ONLY_SHOW_IF_ENABLED) {
                item.visibleProperty().bind(p.getValue());
            } else if (actionProvider.activeType() == ActionProvider.DataStoreCallSite.ActiveType.ALWAYS_SHOW) {
                item.disableProperty().bind(Bindings.not(p.getValue()));
            }
            contextMenu.getItems().add(item);
        }

        if (entry.getActionProviders().size() > 0) {
            contextMenu.getItems().add(new SeparatorMenuItem());
        }

        if (AppPrefs.get().developerMode().getValue()) {
            var browse = new MenuItem(AppI18n.get("browse"), new FontIcon("mdi2f-folder-open-outline"));
            browse.setOnAction(
                    event -> DesktopHelper.browsePath(entry.getEntry().getDirectory()));
            contextMenu.getItems().add(browse);
        }

        var refresh = new MenuItem(AppI18n.get("refresh"), new FontIcon("mdal-360"));
        refresh.disableProperty().bind(entry.getRefreshable().not());
        refresh.setOnAction(event -> {
            DataStorage.get().refreshAsync(entry.getEntry(), true);
        });
        contextMenu.getItems().add(refresh);

        var del = new MenuItem(AppI18n.get("delete"), new FontIcon("mdal-delete_outline"));
        del.disableProperty().bind(entry.getDeletable().not());
        del.setOnAction(event -> entry.delete());
        contextMenu.getItems().add(del);

        return contextMenu;
    }

    protected ColumnConstraints createShareConstraint(Region r, double share) {
        var cc = new ColumnConstraints();
        cc.prefWidthProperty().bind(Bindings.createDoubleBinding(() -> r.getWidth() * share, r.widthProperty()));
        return cc;
    }
}
