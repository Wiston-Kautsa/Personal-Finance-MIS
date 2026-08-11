package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.utils.ReadableTextSupport;
import com.wk.pfmis.utils.RequiredFieldSupport;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class DataRecordsSectionController {
    private static final Map<String, String> LAST_SELECTED_TAB = new HashMap<>();
    private static final DateTimeFormatter ERROR_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH);
    private static int dataIntakeErrorSequence = 1;

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
        add(supplementaryInputsTab, "supplementaryInputsTab", "DataIntakeTask.fxml", "Manual Inputs", false);
        add(fileImportTab, "fileImportTab", "DataIntakeTask.fxml", "Import File", false);
        add(rejectedRecordsTab, "rejectedRecordsTab", "DataIntakeTask.fxml", "Rejected Records", false);
        add(importHistoryTab, "importHistoryTab", "DataIntakeTask.fxml", "Import History", false);

        add(activeRecordsTab, "activeRecordsTab", "RecordsControlTask.fxml", "Active Records", false);
        add(draftRecordsTab, "draftRecordsTab", "RecordsControlTask.fxml", "Draft Records", false);
        add(frozenRecordsTab, "frozenRecordsTab", "RecordsControlTask.fxml", "Frozen Records", false);
        add(cancelledReversedTab, "cancelledReversedTab", "RecordsControlTask.fxml", "Cancelled and Reversed", false);
        add(archivedRecordsTab, "archivedRecordsTab", "RecordsControlTask.fxml", "Archived Records", false);
        add(correctionRequestsTab, "correctionRequestsTab", "RecordsControlTask.fxml", "Correction Requests", false);

        add(dataHealthOverviewTab, "dataHealthOverviewTab", "QualityReconciliationTask.fxml", "Data Health Overview", false);
        add(missingInformationTab, "missingInformationTab", "QualityReconciliationTask.fxml", "Missing Information", false);
        add(duplicateRecordsTab, "duplicateRecordsTab", "QualityReconciliationTask.fxml", "Duplicate Records", false);
        add(accountReconciliationTab, "accountReconciliationTab", "QualityReconciliationTask.fxml", "Account Reconciliation", false);
        add(relationshipErrorsTab, "relationshipErrorsTab", "QualityReconciliationTask.fxml", "Relationship Errors", false);
        add(exceptionsTab, "exceptionsTab", "QualityReconciliationTask.fxml", "Exceptions", false);

        add(activityAuditTab, "activityAuditTab", "AuditHistoryTask.fxml", "Activity Audit", false);
        add(financialRecordHistoryTab, "financialRecordHistoryTab", "AuditHistoryTask.fxml", "Financial Record History", false);
        add(authenticationHistoryTab, "authenticationHistoryTab", "AuditHistoryTask.fxml", "Authentication History", false);
        add(administrativeActionsTab, "administrativeActionsTab", "AuditHistoryTask.fxml", "Administrative Actions", false);
        add(dataDisposalHistoryTab, "dataDisposalHistoryTab", "AuditHistoryTask.fxml", "Data Disposal History", false);

        add(syncStatusTab, "syncStatusTab", "SyncRecoveryTask.fxml", "Sync Status", false);
        add(pendingQueueTab, "pendingQueueTab", "SyncRecoveryTask.fxml", "Pending Queue", false);
        add(failedRecordsTab, "failedRecordsTab", "SyncRecoveryTask.fxml", "Failed Records", false);
        add(conflictsTab, "conflictsTab", "SyncRecoveryTask.fxml", "Conflicts", false);
        add(quarantineTab, "quarantineTab", "SyncRecoveryTask.fxml", "Quarantine", false);
        add(syncHistoryTab, "syncHistoryTab", "SyncRecoveryTask.fxml", "Sync History", false);
        add(recoveryTab, "recoveryTab", "SyncRecoveryTask.fxml", "Recovery", false);

        add(recordDisposalTab, "recordDisposalTab", "RecordDisposal.fxml", null, true);
        add(clearTestDataTab, "clearTestDataTab", "DataMaintenanceWorkflow.fxml", "Clear Test or Demo Data", true);
        add(purgeArchivedTab, "purgeArchivedTab", "DataMaintenanceWorkflow.fxml", "Purge Archived Records", true);
        add(resetWorkspaceTab, "resetWorkspaceTab", "DataMaintenanceWorkflow.fxml", "Reset Workspace", true);
        add(deleteWorkspaceTab, "deleteWorkspaceTab", "DataMaintenanceWorkflow.fxml", "Delete Workspace", true);
        add(maintenanceHistoryTab, "maintenanceHistoryTab", "DataMaintenanceWorkflow.fxml", "Maintenance History", true);
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
            if (spec.dataArea() != null && controller != null) {
                if (controller instanceof DataRecordsPolicyController dataPolicyController) {
                    dataPolicyController.selectArea(spec.dataArea());
                } else {
                    invokeStringArg(controller, "selectArea", spec.dataArea());
                }
            }
            RequiredFieldSupport.apply(view);
            ReadableTextSupport.apply(view);
            tab.setContent(unwrapScrollPane(view));
            if (controller != null) {
                loadedControllers.put(tab, controller);
            }
        } catch (IOException | RuntimeException exception) {
            if (isDataIntakeTab(spec)) {
                tab.setContent(dataIntakeLoadErrorContent(tab, spec, exception));
                return;
            }
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

    private Node dataIntakeLoadErrorContent(Tab tab, TabSpec spec, Throwable exception) {
        String area = spec.dataArea() == null || spec.dataArea().isBlank() ? tab.getText() : spec.dataArea();
        String reference = nextDataIntakeErrorReference();
        recordDataIntakeLoadFailure(reference, spec, exception);

        VBox box = new VBox(10);
        box.getStyleClass().addAll("panel", "setup-tab-message");
        box.setPadding(new Insets(18));
        Label heading = new Label(area + " could not be opened.");
        heading.getStyleClass().add("section-heading");
        Label body = new Label("No data was changed.\nReference: " + reference);
        body.setWrapText(true);
        body.getStyleClass().add("muted-label");
        Button retry = new Button("Retry");
        retry.getStyleClass().add("secondary-button");
        retry.setOnAction(event -> {
            tab.setContent(null);
            loadTab(tab);
        });
        box.getChildren().setAll(heading, body, retry);
        return box;
    }

    private boolean isDataIntakeTab(TabSpec spec) {
        return spec != null && "DataIntakeTask.fxml".equals(spec.fxmlFile());
    }

    private static synchronized String nextDataIntakeErrorReference() {
        return "ERR-DI-" + LocalDateTime.now().format(ERROR_DATE) + "-"
                + String.format(Locale.ENGLISH, "%03d", dataIntakeErrorSequence++);
    }

    private void recordDataIntakeLoadFailure(String reference, TabSpec spec, Throwable exception) {
        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        try {
            DatabaseHandler.getInstance().recordSystemLog(
                    "Data Intake",
                    "Section Load Failed",
                    "ERROR",
                    String.join(System.lineSeparator(),
                            "Reference: " + reference,
                            "FXML resource: /com/wk/pfmis/views/" + spec.fxmlFile(),
                            "Controller: DataIntakeTaskController",
                            "Exception type: " + exception.getClass().getName(),
                            "Message: " + rootMessage(exception),
                            "Workspace: " + workspaceText(),
                            "User: " + userText(),
                            "Timestamp: " + LocalDateTime.now(),
                            "Stack trace:",
                            stackTrace.toString())
            );
        } catch (RuntimeException ignored) {
            // The visible retry message is still shown if the technical log cannot be written.
        }
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

    private boolean invokeStringArg(Object controller, String methodName, String argument) {
        try {
            Method method = controller.getClass().getDeclaredMethod(methodName, String.class);
            method.setAccessible(true);
            method.invoke(controller, argument);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private String sectionName() {
        String title = sectionTitleLabel == null ? "" : sectionTitleLabel.getText();
        return title == null || title.isBlank() ? "Data and Records" : title.trim();
    }

    private String workspaceText() {
        try {
            return UserSession.getWorkspaceUser().getDisplayName()
                    + " (" + UserSession.getWorkspaceUser().getUsername() + ")";
        } catch (RuntimeException exception) {
            return "No active workspace";
        }
    }

    private String userText() {
        try {
            return UserSession.getAuthenticatedUser().getDisplayName()
                    + " (" + UserSession.getAuthenticatedUser().getUsername() + ")";
        } catch (RuntimeException exception) {
            return "No signed-in user";
        }
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
