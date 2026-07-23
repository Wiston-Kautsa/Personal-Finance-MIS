package com.wk.pfmis.controllers;

import com.wk.pfmis.security.UserSession;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class SetupSectionController {
    private static final Map<String, String> LAST_SELECTED_TAB = new HashMap<>();

    @FXML private Label sectionTitleLabel;
    @FXML private TabPane setupTabs;

    @FXML private Tab overviewTab;
    @FXML private Tab systemHealthTab;
    @FXML private Tab workspaceManagementTab;

    @FXML private Tab myAccountTab;
    @FXML private Tab userManagementTab;
    @FXML private Tab securityHistoryTab;
    @FXML private Tab sessionAutoLockTab;

    @FXML private Tab categoriesTab;
    @FXML private Tab paymentMethodsTab;
    @FXML private Tab currenciesTab;
    @FXML private Tab financialProfileTab;
    @FXML private Tab reportPreferencesTab;

    @FXML private Tab aiConfigurationTab;
    @FXML private Tab smartRulesTab;
    @FXML private Tab alertsTab;
    @FXML private Tab automationTab;

    @FXML private Tab dataQualityTab;
    @FXML private Tab importExportTab;
    @FXML private Tab backupRestoreTab;
    @FXML private Tab auditTrailTab;
    @FXML private Tab archiveRestoreTab;
    @FXML private Tab maintenanceTab;

    private final Map<Tab, TabSpec> tabSpecs = new IdentityHashMap<>();
    private final Map<Tab, Object> loadedControllers = new IdentityHashMap<>();

    public static void rememberTab(String section, String tabKey) {
        if (section != null && !section.isBlank() && tabKey != null && !tabKey.isBlank()) {
            LAST_SELECTED_TAB.put(section, tabKey);
        }
    }

    @FXML
    public void initialize() {
        configureTabs();
        setupTabs.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (selected == null) {
                return;
            }
            TabSpec spec = tabSpecs.get(selected);
            if (spec != null) {
                LAST_SELECTED_TAB.put(sectionName(), spec.tabKey());
            }
            loadTab(selected);
        });
        selectInitialTab();
        DataRefreshBus.addListener(this::refreshLoadedTabs);
    }

    private void configureTabs() {
        add(overviewTab, "overviewTab", "Administration.fxml", null, false);
        add(systemHealthTab, "systemHealthTab", "Maintenance.fxml", null, false);
        add(workspaceManagementTab, "workspaceManagementTab", "SetupPolicy.fxml", "Workspace Management", false);

        add(myAccountTab, "myAccountTab", "MyAccount.fxml", null, false);
        add(userManagementTab, "userManagementTab", "UserManagement.fxml", null, true);
        add(securityHistoryTab, "securityHistoryTab", "SecurityHistory.fxml", null, false);
        add(sessionAutoLockTab, "sessionAutoLockTab", "SetupPolicy.fxml", "Session and Auto-Lock Settings", false);

        add(categoriesTab, "categoriesTab", "Categories.fxml", null, false);
        add(paymentMethodsTab, "paymentMethodsTab", "PaymentMethods.fxml", null, false);
        add(currenciesTab, "currenciesTab", "Currencies.fxml", null, false);
        add(financialProfileTab, "financialProfileTab", "SetupPolicy.fxml", "Financial Profile", false);
        add(reportPreferencesTab, "reportPreferencesTab", "SetupPolicy.fxml", "Report Preferences", false);

        add(aiConfigurationTab, "aiConfigurationTab", "Settings.fxml", null, false);
        add(smartRulesTab, "smartRulesTab", "SetupPolicy.fxml", "Smart Rules and Thresholds", false);
        add(alertsTab, "alertsTab", "SetupPolicy.fxml", "Alerts and Notifications", false);
        add(automationTab, "automationTab", "SetupPolicy.fxml", "Automation Schedules", false);

        add(dataQualityTab, "dataQualityTab", "SetupPolicy.fxml", "Data Quality and Reconciliation", false);
        add(importExportTab, "importExportTab", "SetupPolicy.fxml", "Import and Export", false);
        add(backupRestoreTab, "backupRestoreTab", "BackupRestore.fxml", null, false);
        add(auditTrailTab, "auditTrailTab", "AuditLogs.fxml", null, false);
        add(archiveRestoreTab, "archiveRestoreTab", "SetupPolicy.fxml", "Archive and Restore", false);
        add(maintenanceTab, "maintenanceTab", "Maintenance.fxml", null, false);
    }

    private void add(Tab tab, String tabKey, String fxmlFile, String setupArea, boolean superAdminOnly) {
        if (tab == null) {
            return;
        }
        tabSpecs.put(tab, new TabSpec(tabKey, fxmlFile, setupArea, superAdminOnly));
    }

    private void selectInitialTab() {
        if (setupTabs == null || setupTabs.getTabs().isEmpty()) {
            return;
        }
        String rememberedTab = LAST_SELECTED_TAB.get(sectionName());
        Tab target = setupTabs.getTabs().stream()
                .filter(tab -> {
                    TabSpec spec = tabSpecs.get(tab);
                    return spec != null && spec.tabKey().equals(rememberedTab);
                })
                .findFirst()
                .orElse(setupTabs.getTabs().getFirst());
        setupTabs.getSelectionModel().select(target);
        loadTab(target);
    }

    private void loadTab(Tab tab) {
        if (tab == null || tab.getContent() != null) {
            return;
        }
        TabSpec spec = tabSpecs.get(tab);
        if (spec == null) {
            tab.setContent(messageContent("Section unavailable", "This setup tab is not mapped to a workspace page."));
            return;
        }
        if (spec.superAdminOnly() && !UserSession.isSuperAdmin()) {
            tab.setContent(messageContent(
                    "Restricted to Super Administrators",
                    "This tab manages the central user registry and other users' workspaces."
            ));
            return;
        }
        try {
            URL resource = getClass().getResource("/com/wk/pfmis/views/" + spec.fxmlFile());
            if (resource == null) {
                throw new IOException("Missing FXML: " + spec.fxmlFile());
            }
            FXMLLoader loader = new FXMLLoader(resource);
            Parent view = loader.load();
            Object controller = loader.getController();
            if (spec.setupArea() != null && controller instanceof SetupPolicyController setupPolicyController) {
                setupPolicyController.selectArea(spec.setupArea());
            }
            tab.setContent(unwrapScrollPane(view));
            if (controller != null) {
                loadedControllers.put(tab, controller);
            }
        } catch (IOException | RuntimeException exception) {
            tab.setContent(messageContent("Unable to load this section", rootMessage(exception)));
        }
    }

    private Node unwrapScrollPane(Parent view) {
        if (view instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            Node content = scrollPane.getContent();
            scrollPane.setContent(null);
            return content;
        }
        return view;
    }

    private Node messageContent(String title, String message) {
        VBox box = new VBox(8);
        box.getStyleClass().addAll("panel", "setup-tab-message");
        box.setPadding(new Insets(18));
        Label heading = new Label(title);
        heading.getStyleClass().add("section-heading");
        Label body = new Label(message == null || message.isBlank() ? "No additional details were provided." : message);
        body.setWrapText(true);
        body.getStyleClass().add("muted-label");
        box.getChildren().setAll(heading, body);
        return box;
    }

    private void refreshLoadedTabs() {
        for (Object controller : loadedControllers.values()) {
            if (controller == null) {
                continue;
            }
            boolean refreshed = invokeNoArg(controller, "refresh");
            if (!refreshed) {
                refreshed = invokeNoArg(controller, "refreshDashboard");
            }
            if (!refreshed) {
                invokeNoArg(controller, "loadSettings");
            }
            invokeNoArg(controller, "refreshLocalAiStatus");
        }
    }

    private boolean invokeNoArg(Object controller, String methodName) {
        try {
            Method method = controller.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(controller);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private String sectionName() {
        String title = sectionTitleLabel == null ? "" : sectionTitleLabel.getText();
        return title == null || title.isBlank() ? "Setup" : title.trim();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private record TabSpec(
            String tabKey,
            String fxmlFile,
            String setupArea,
            boolean superAdminOnly
    ) {
    }
}
