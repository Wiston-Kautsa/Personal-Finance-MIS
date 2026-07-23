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

public class DataRecordsSectionController {
    private static final Map<String, String> LAST_SELECTED_TAB = new HashMap<>();

    @FXML private Label sectionTitleLabel;
    @FXML private TabPane dataRecordsTabs;

    @FXML private Tab supplementaryInputsTab;
    @FXML private Tab fileImportTab;
    @FXML private Tab importValidationTab;
    @FXML private Tab postingApprovalTab;
    @FXML private Tab rejectedRecordsTab;
    @FXML private Tab importHistoryTab;

    @FXML private Tab activeRecordsTab;
    @FXML private Tab draftRecordsTab;
    @FXML private Tab frozenRecordsTab;
    @FXML private Tab cancelledReversedTab;
    @FXML private Tab archivedRecordsTab;
    @FXML private Tab correctionRequestsTab;

    @FXML private Tab dataHealthOverviewTab;
    @FXML private Tab missingInformationTab;
    @FXML private Tab duplicateRecordsTab;
    @FXML private Tab accountReconciliationTab;
    @FXML private Tab relationshipErrorsTab;
    @FXML private Tab exceptionsTab;

    @FXML private Tab activityAuditTab;
    @FXML private Tab financialRecordHistoryTab;
    @FXML private Tab authenticationHistoryTab;
    @FXML private Tab administrativeActionsTab;
    @FXML private Tab dataDisposalHistoryTab;
    @FXML private Tab auditExportTab;

    @FXML private Tab syncStatusTab;
    @FXML private Tab pendingQueueTab;
    @FXML private Tab failedRecordsTab;
    @FXML private Tab conflictsTab;
    @FXML private Tab quarantineTab;
    @FXML private Tab syncHistoryTab;
    @FXML private Tab recoveryTab;

    @FXML private Tab recordDisposalTab;
    @FXML private Tab clearTestDataTab;
    @FXML private Tab purgeArchivedTab;
    @FXML private Tab resetWorkspaceTab;
    @FXML private Tab deleteWorkspaceTab;
    @FXML private Tab maintenanceHistoryTab;

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
        dataRecordsTabs.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
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
        add(supplementaryInputsTab, "supplementaryInputsTab", "ReportInputs.fxml", null, false);
        add(fileImportTab, "fileImportTab", "DataRecordsPolicy.fxml", "File Import", false);
        add(importValidationTab, "importValidationTab", "DataRecordsPolicy.fxml", "Import Validation", false);
        add(postingApprovalTab, "postingApprovalTab", "DataRecordsPolicy.fxml", "Posting and Approval", false);
        add(rejectedRecordsTab, "rejectedRecordsTab", "DataRecordsPolicy.fxml", "Rejected Records", false);
        add(importHistoryTab, "importHistoryTab", "DataRecordsPolicy.fxml", "Import History", false);

        add(activeRecordsTab, "activeRecordsTab", "DataRecordsPolicy.fxml", "Active Records", false);
        add(draftRecordsTab, "draftRecordsTab", "DataRecordsPolicy.fxml", "Draft Records", false);
        add(frozenRecordsTab, "frozenRecordsTab", "DataRecordsPolicy.fxml", "Frozen Records", false);
        add(cancelledReversedTab, "cancelledReversedTab", "DataRecordsPolicy.fxml", "Cancelled and Reversed", false);
        add(archivedRecordsTab, "archivedRecordsTab", "DataRecordsPolicy.fxml", "Archived Records", false);
        add(correctionRequestsTab, "correctionRequestsTab", "DataRecordsPolicy.fxml", "Correction Requests", false);

        add(dataHealthOverviewTab, "dataHealthOverviewTab", "DataRecordsPolicy.fxml", "Data Health Overview", false);
        add(missingInformationTab, "missingInformationTab", "DataRecordsPolicy.fxml", "Missing Information", false);
        add(duplicateRecordsTab, "duplicateRecordsTab", "DataRecordsPolicy.fxml", "Duplicate Records", false);
        add(accountReconciliationTab, "accountReconciliationTab", "DataRecordsPolicy.fxml", "Account Reconciliation", false);
        add(relationshipErrorsTab, "relationshipErrorsTab", "DataRecordsPolicy.fxml", "Relationship Errors", false);
        add(exceptionsTab, "exceptionsTab", "DataRecordsPolicy.fxml", "Exceptions", false);

        add(activityAuditTab, "activityAuditTab", "AuditLogs.fxml", null, false);
        add(financialRecordHistoryTab, "financialRecordHistoryTab", "DataRecordsPolicy.fxml", "Financial Record History", false);
        add(authenticationHistoryTab, "authenticationHistoryTab", "SecurityHistory.fxml", null, false);
        add(administrativeActionsTab, "administrativeActionsTab", "DataRecordsPolicy.fxml", "Administrative Actions", false);
        add(dataDisposalHistoryTab, "dataDisposalHistoryTab", "DataRecordsPolicy.fxml", "Data Disposal History", false);
        add(auditExportTab, "auditExportTab", "DataRecordsPolicy.fxml", "Audit Export", false);

        add(syncStatusTab, "syncStatusTab", "SyncCenter.fxml", null, false);
        add(pendingQueueTab, "pendingQueueTab", "DataRecordsPolicy.fxml", "Pending Queue", false);
        add(failedRecordsTab, "failedRecordsTab", "DataRecordsPolicy.fxml", "Failed Records", false);
        add(conflictsTab, "conflictsTab", "DataRecordsPolicy.fxml", "Conflicts", false);
        add(quarantineTab, "quarantineTab", "DataRecordsPolicy.fxml", "Quarantine", false);
        add(syncHistoryTab, "syncHistoryTab", "DataRecordsPolicy.fxml", "Sync History", false);
        add(recoveryTab, "recoveryTab", "BackupRestore.fxml", null, false);

        add(recordDisposalTab, "recordDisposalTab", "DataRecordsPolicy.fxml", "Record Disposal", true);
        add(clearTestDataTab, "clearTestDataTab", "DataRecordsPolicy.fxml", "Clear Test or Demo Data", true);
        add(purgeArchivedTab, "purgeArchivedTab", "DataRecordsPolicy.fxml", "Purge Archived Records", true);
        add(resetWorkspaceTab, "resetWorkspaceTab", "DataRecordsPolicy.fxml", "Reset Workspace", true);
        add(deleteWorkspaceTab, "deleteWorkspaceTab", "DataRecordsPolicy.fxml", "Delete Workspace", true);
        add(maintenanceHistoryTab, "maintenanceHistoryTab", "DataRecordsPolicy.fxml", "Maintenance History", true);
    }

    private void add(Tab tab, String tabKey, String fxmlFile, String dataArea, boolean superAdminOnly) {
        if (tab != null) {
            tabSpecs.put(tab, new TabSpec(tabKey, fxmlFile, dataArea, superAdminOnly));
        }
    }

    private void selectInitialTab() {
        if (dataRecordsTabs == null || dataRecordsTabs.getTabs().isEmpty()) {
            return;
        }
        String rememberedTab = LAST_SELECTED_TAB.get(sectionName());
        Tab target = dataRecordsTabs.getTabs().stream()
                .filter(tab -> {
                    TabSpec spec = tabSpecs.get(tab);
                    return spec != null && spec.tabKey().equals(rememberedTab);
                })
                .findFirst()
                .orElse(dataRecordsTabs.getTabs().getFirst());
        dataRecordsTabs.getSelectionModel().select(target);
        loadTab(target);
    }

    private void loadTab(Tab tab) {
        if (tab == null || tab.getContent() != null) {
            return;
        }
        TabSpec spec = tabSpecs.get(tab);
        if (spec == null) {
            tab.setContent(messageContent("Section unavailable", "This Data and Records tab is not mapped to a workspace page."));
            return;
        }
        if (spec.superAdminOnly() && !UserSession.isSuperAdmin()) {
            tab.setContent(messageContent(
                    "Restricted to Super Administrators",
                    "Physical deletion, purging, clearing and workspace reset are controlled Data Maintenance functions."
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
            if (spec.dataArea() != null && controller instanceof DataRecordsPolicyController dataPolicyController) {
                dataPolicyController.selectArea(spec.dataArea());
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
        return title == null || title.isBlank() ? "Data and Records" : title.trim();
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
            String dataArea,
            boolean superAdminOnly
    ) {
    }
}
