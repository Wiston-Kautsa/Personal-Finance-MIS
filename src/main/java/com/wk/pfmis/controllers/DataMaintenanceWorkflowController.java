package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.SystemLogRecord;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.PrivilegedActionService;
import com.wk.pfmis.security.RiskLevel;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.utils.ExportPathService;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public class DataMaintenanceWorkflowController {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label guidanceLabel;
    @FXML private VBox formContainer;
    @FXML private TextArea summaryArea;
    @FXML private Button primaryActionButton;
    @FXML private Button cancelButton;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final PrivilegedActionService privilegedActionService = PrivilegedActionService.getInstance();

    private String currentArea = "Clear Test or Demo Data";
    private ComboBox<String> workspaceBox;
    private ComboBox<String> dataTypeBox;
    private ComboBox<String> statusBox;
    private ComboBox<String> operationBox;
    private ComboBox<String> historyStatusBox;
    private TextField searchField;
    private DatePicker archivedBeforePicker;
    private DatePicker historyFromPicker;
    private DatePicker historyToPicker;
    private CheckBox testCheckBox;
    private CheckBox demoCheckBox;
    private CheckBox sampleCheckBox;
    private CheckBox trainingCheckBox;
    private ToggleGroup resetTypeGroup;
    private GridPane historyTable;
    private WorkflowStage workflowStage = WorkflowStage.FORM;
    private PendingMaintenanceAction pendingAction = PendingMaintenanceAction.NONE;
    private String pendingResetType = "";
    private boolean pendingTestSelected = true;
    private boolean pendingDemoSelected = true;
    private boolean pendingSampleSelected;
    private boolean pendingTrainingSelected;
    private LocalDate pendingArchivedBefore;
    private String pendingArchivedType = "All archived records";
    private SecurityVerificationPane securityVerificationPane;
    private CheckBox finalConfirmationCheckBox;
    private TextArea finalReasonArea;

    @FXML
    public void initialize() {
        selectArea(currentArea);
    }

    public void selectArea(String area) {
        currentArea = area == null || area.isBlank() ? "Clear Test or Demo Data" : area.trim();
        resetWorkflowState();
        render();
    }

    public void refresh() {
        render();
    }

    @FXML
    private void runPrimaryAction() {
        try {
            switch (currentArea) {
                case "Purge Archived Records" -> deleteArchivedRecords();
                case "Reset Workspace" -> resetWorkspace();
                case "Delete Workspace" -> deleteWorkspace();
                case "Maintenance History" -> exportHistory();
                default -> clearTestData();
            }
        } catch (RuntimeException exception) {
            UiAlerts.error("Maintenance action failed", exception);
        }
    }

    @FXML
    private void cancelTask() {
        if ("Maintenance History".equals(currentArea)) {
            showSelectedHistoryDetails();
            return;
        }
        resetWorkflowState();
        render();
    }

    private void render() {
        formContainer.getChildren().clear();
        securityVerificationPane = null;
        finalConfirmationCheckBox = null;
        finalReasonArea = null;
        switch (currentArea) {
            case "Purge Archived Records" -> renderArchivedRecordsPage();
            case "Reset Workspace" -> renderResetWorkspacePage();
            case "Delete Workspace" -> renderDeleteWorkspacePage();
            case "Maintenance History" -> renderMaintenanceHistoryPage();
            default -> renderClearTestDataPage();
        }
    }

    private void renderClearTestDataPage() {
        titleLabel.setText("Clear Test or Demo Data");
        subtitleLabel.setText("Remove records specifically marked as test, demo, sample or training data.");
        guidanceLabel.setText("The system checks affected records, creates a recovery backup, verifies the database and records the audit automatically.");
        primaryActionButton.setText(workflowStage == WorkflowStage.FINAL_REVIEW ? "Clear Test Data" : "Review Impact");
        primaryActionButton.getStyleClass().setAll(workflowStage == WorkflowStage.FINAL_REVIEW ? "maintenance-danger-button" : "primary-button");
        cancelButton.setText("Cancel");

        workspaceBox = combo(workspaceOptions(), workspacePrompt());
        testCheckBox = checked("Test", workflowStage == WorkflowStage.FORM || pendingTestSelected);
        demoCheckBox = checked("Demo", workflowStage == WorkflowStage.FORM || pendingDemoSelected);
        sampleCheckBox = checked("Sample", workflowStage != WorkflowStage.FORM && pendingSampleSelected);
        trainingCheckBox = checked("Training", workflowStage != WorkflowStage.FORM && pendingTrainingSelected);

        FlowPane classifications = new FlowPane(12, 8, testCheckBox, demoCheckBox, sampleCheckBox, trainingCheckBox);
        classifications.getStyleClass().add("maintenance-inline-checks");
        VBox scope = new VBox(12,
                stepStrip(1),
                field("Workspace", workspaceBox),
                labelledBlock("Data to clear", classifications)
        );
        formContainer.getChildren().addAll(
                scope
        );
        updateClearTestSummary();
        if (workflowStage == WorkflowStage.FINAL_REVIEW) {
            renderFinalConfirmation(
                    "Final Review - Clear Test Data",
                    lines(
                            "Records affected: " + eligible(findTestDataCandidates()).size() + " eligible test/demo records",
                            "Backup: Will be created and verified",
                            "Account balances: Protected records remain unchanged",
                            "Audit: Maintenance log will be recorded"
                    ),
                    false
            );
        }
    }

    private void renderArchivedRecordsPage() {
        titleLabel.setText("Purge Archived Records");
        subtitleLabel.setText("Permanently remove old archived records that are no longer required.");
        guidanceLabel.setText("Only archived records that have passed retention and safety checks can be removed.");
        primaryActionButton.setText(workflowStage == WorkflowStage.FINAL_REVIEW ? "Delete Eligible Archived Records" : "Review Impact");
        primaryActionButton.getStyleClass().setAll(workflowStage == WorkflowStage.FINAL_REVIEW ? "maintenance-danger-button" : "primary-button");
        cancelButton.setText("Cancel");

        workspaceBox = combo(workspaceOptions(), workspacePrompt());
        archivedBeforePicker = new DatePicker();
        archivedBeforePicker.getStyleClass().add("maintenance-input");
        if (workflowStage != WorkflowStage.FORM) {
            archivedBeforePicker.setValue(pendingArchivedBefore);
        }
        dataTypeBox = combo(List.of("All archived records", "Transaction", "Account", "Budget", "Project", "Goal", "Loan", "Report Input"), "All archived records");
        if (workflowStage != WorkflowStage.FORM) {
            dataTypeBox.setValue(pendingArchivedType);
        }

        formContainer.getChildren().addAll(
                stepStrip(1),
                field("Workspace", workspaceBox),
                field("Archived before", archivedBeforePicker),
                field("Record type", dataTypeBox)
        );
        updateArchivedSummary();
        if (workflowStage == WorkflowStage.FINAL_REVIEW) {
            List<DatabaseHandler.RecordDisposalCandidateData> candidates = findArchivedCandidates();
            renderFinalConfirmation(
                    "Final Review - Purge Archived Records",
                    lines(
                            "Archived records found: " + candidates.size(),
                            "Eligible for deletion: " + eligible(candidates).size(),
                            "Backup: Will be created and verified",
                            "Audit: Maintenance log will be recorded"
                    ),
                    true
            );
        }
    }

    private void renderResetWorkspacePage() {
        titleLabel.setText("Reset Workspace");
        subtitleLabel.setText("Clear selected workspace data and return the workspace to a clean state while keeping the user account.");
        guidanceLabel.setText("Choose what to clear and review what will remain before resetting.");
        primaryActionButton.setText(switch (workflowStage) {
            case VERIFY -> "Verify & Continue";
            case FINAL_REVIEW -> "Execute Reset";
            default -> "Review Impact";
        });
        primaryActionButton.getStyleClass().setAll(workflowStage == WorkflowStage.FINAL_REVIEW ? "maintenance-danger-button" : "primary-button");
        cancelButton.setText("Cancel");

        workspaceBox = combo(workspaceOptions(), workspacePrompt());
        resetTypeGroup = new ToggleGroup();
        String selectedResetType = pendingResetType.isBlank()
                ? "Clear everything and restore default settings"
                : pendingResetType;
        VBox resetOptions = new VBox(7,
                radio("Clear transactions only", resetTypeGroup, "Clear transactions only".equals(selectedResetType)),
                radio("Clear financial data but keep categories and settings", resetTypeGroup, "Clear financial data but keep categories and settings".equals(selectedResetType)),
                radio("Clear everything and restore default settings", resetTypeGroup, "Clear everything and restore default settings".equals(selectedResetType))
        );
        resetOptions.getStyleClass().add("maintenance-radio-group");

        formContainer.getChildren().addAll(
                stepStrip(stepNumber()),
                field("Workspace", workspaceBox),
                labelledBlock("Choose reset type", resetOptions)
        );
        updateResetSummary();
        if (workflowStage == WorkflowStage.VERIFY) {
            pendingResetType = pendingResetType.isBlank() ? selectedResetType() : pendingResetType;
            renderSecurityVerification(
                    "Reset Workspace",
                    lines(
                            "The selected workspace data will be cleared according to the chosen reset type.",
                            "Reset type: " + pendingResetType,
                            "The user identity and security history will remain."
                    ),
                    RiskLevel.HIGH,
                    "",
                    false
            );
        } else if (workflowStage == WorkflowStage.FINAL_REVIEW) {
            renderFinalConfirmation(
                    "Final Review - Reset Workspace",
                    lines(
                            "Workspace: " + workspaceOwnerText(),
                            "Reset type: " + pendingResetType,
                            "Backup: Will be created and verified",
                            "User identity: Will remain",
                            "Security verification: " + privilegedActionService.currentStatusText(RiskLevel.HIGH).orElse("Verified")
                    ),
                    true
            );
        }
    }

    private void renderDeleteWorkspacePage() {
        titleLabel.setText("Delete Workspace");
        subtitleLabel.setText("Remove the complete financial workspace while retaining the central user identity and maintenance history.");
        guidanceLabel.setText("This removes the complete financial workspace. A verified final backup is required.");
        primaryActionButton.setText(switch (workflowStage) {
            case VERIFY -> "Verify & Continue";
            case FINAL_REVIEW -> "Delete Workspace";
            default -> "Review Impact";
        });
        primaryActionButton.getStyleClass().setAll(workflowStage == WorkflowStage.FINAL_REVIEW ? "maintenance-danger-button" : "primary-button");
        cancelButton.setText("Cancel");

        workspaceBox = combo(workspaceOptions(), workspacePrompt());
        formContainer.getChildren().addAll(stepStrip(stepNumber()), field("Workspace", workspaceBox));
        updateDeleteSummary();
        if (workflowStage == WorkflowStage.VERIFY) {
            String phrase = deleteWorkspacePhrase();
            DatabaseHandler.WorkspaceMaintenanceSummary summary = database.workspaceMaintenanceSummary();
            renderSecurityVerification(
                    "Delete Workspace",
                    lines(
                            "All financial records in this workspace will be removed.",
                            "Financial records: " + summary.financialRecords(),
                            "A final verified backup will be created first.",
                            "User identity will remain in the central user registry."
                    ),
                    RiskLevel.CRITICAL,
                    phrase,
                    true
            );
        } else if (workflowStage == WorkflowStage.FINAL_REVIEW) {
            DatabaseHandler.WorkspaceMaintenanceSummary summary = database.workspaceMaintenanceSummary();
            renderFinalConfirmation(
                    "Final Review - Delete Workspace",
                    lines(
                            "Records affected: " + summary.financialRecords() + " financial records",
                            "Backup: Will be created and verified",
                            "Workspace: " + workspaceOwnerText(),
                            "User identity: Will remain",
                            "Security verification: Fresh verification completed"
                    ),
                    true
            );
        }
    }

    private void renderMaintenanceHistoryPage() {
        titleLabel.setText("Maintenance History");
        subtitleLabel.setText("Search and review previous maintenance actions, backups and verification results.");
        guidanceLabel.setText("Read-only page - no financial records can be changed here.");
        primaryActionButton.setText("Export History");
        primaryActionButton.getStyleClass().setAll("primary-button");
        cancelButton.setText("View Details");

        operationBox = combo(List.of("All operations", "Record Disposal", "Clear Test Data", "Purge Archived Records", "Reset Workspace", "Delete Workspace"), "All operations");
        workspaceBox = combo(List.of("All workspaces", workspacePrompt()), "All workspaces");
        historyStatusBox = combo(List.of("All statuses", "Completed", "Completed With Warnings", "Failed", "Cancelled"), "All statuses");
        historyFromPicker = new DatePicker();
        historyToPicker = new DatePicker();
        historyFromPicker.getStyleClass().add("maintenance-input");
        historyToPicker.getStyleClass().add("maintenance-input");
        historyTable = new GridPane();
        historyTable.getStyleClass().add("maintenance-table");

        FlowPane filters = new FlowPane(10, 10,
                field("Operation", operationBox),
                field("Workspace", workspaceBox),
                field("Status", historyStatusBox),
                field("From", historyFromPicker),
                field("To", historyToPicker)
        );
        filters.setPrefWrapLength(1120);
        formContainer.getChildren().addAll(filters, historyTable);
        loadHistoryTable();
        summaryArea.setText("Select a history row, then use View Details or Export History.");
    }

    private void clearTestData() {
        if (workflowStage == WorkflowStage.FORM) {
            if (!anyClassificationSelected()) {
                UiAlerts.info("Select at least one data classification to clear.");
                return;
            }
            List<DatabaseHandler.RecordDisposalCandidateData> candidates = findTestDataCandidates();
            List<DatabaseHandler.RecordDisposalCandidateData> removable = eligible(candidates);
            int protectedCount = candidates.size() - removable.size();
            if (removable.isEmpty()) {
                summaryArea.setText(lines(
                        "Records found: " + candidates.size(),
                        "Safe to clear: 0",
                        "Protected because linked to real data: " + protectedCount,
                        "",
                        "No records were cleared."
                ));
                return;
            }
            pendingTestSelected = testCheckBox != null && testCheckBox.isSelected();
            pendingDemoSelected = demoCheckBox != null && demoCheckBox.isSelected();
            pendingSampleSelected = sampleCheckBox != null && sampleCheckBox.isSelected();
            pendingTrainingSelected = trainingCheckBox != null && trainingCheckBox.isSelected();
            pendingAction = PendingMaintenanceAction.CLEAR_TEST_DATA;
            workflowStage = WorkflowStage.FINAL_REVIEW;
            render();
            return;
        }
        if (!finalConfirmationAccepted(false)) {
            return;
        }
        if (!anyClassificationSelected()) {
            UiAlerts.info("Select at least one data classification to clear.");
            return;
        }
        List<DatabaseHandler.RecordDisposalCandidateData> candidates = findTestDataCandidates();
        List<DatabaseHandler.RecordDisposalCandidateData> removable = eligible(candidates);
        int protectedCount = candidates.size() - removable.size();
        if (removable.isEmpty()) {
            summaryArea.setText(lines(
                    "Records found: " + candidates.size(),
                    "Safe to clear: 0",
                    "Protected because linked to real data: " + protectedCount,
                    "",
                    "No records were cleared."
            ));
            return;
        }
        executeRecordMaintenance(
                "Clear Test Data",
                removable,
                finalReasonOrDefault("Test/demo cleanup"),
                "Inline maintenance confirmation"
        );
        resetWorkflowState();
    }

    private void deleteArchivedRecords() {
        if (workflowStage == WorkflowStage.FORM) {
            List<DatabaseHandler.RecordDisposalCandidateData> candidates = findArchivedCandidates();
            List<DatabaseHandler.RecordDisposalCandidateData> removable = eligible(candidates);
            int protectedCount = candidates.size() - removable.size();
            if (removable.isEmpty()) {
                summaryArea.setText(lines(
                        "Archived records found: " + candidates.size(),
                        "Eligible for deletion: 0",
                        "Protected records: " + protectedCount,
                        "",
                        "No archived records were deleted."
                ));
                return;
            }
            pendingArchivedBefore = archivedBeforePicker == null ? null : archivedBeforePicker.getValue();
            pendingArchivedType = dataTypeBox == null || dataTypeBox.getValue() == null
                    ? "All archived records"
                    : dataTypeBox.getValue();
            pendingAction = PendingMaintenanceAction.PURGE_ARCHIVED_RECORDS;
            workflowStage = WorkflowStage.FINAL_REVIEW;
            render();
            return;
        }
        if (!finalConfirmationAccepted(true)) {
            return;
        }
        List<DatabaseHandler.RecordDisposalCandidateData> candidates = findArchivedCandidates();
        List<DatabaseHandler.RecordDisposalCandidateData> removable = eligible(candidates);
        int protectedCount = candidates.size() - removable.size();
        if (removable.isEmpty()) {
            summaryArea.setText(lines(
                    "Archived records found: " + candidates.size(),
                    "Eligible for deletion: 0",
                    "Protected records: " + protectedCount,
                    "",
                    "No archived records were deleted."
            ));
            return;
        }
        executeRecordMaintenance(
                "Delete Archived Records",
                removable,
                finalReasonOrDefault("Expired archived records"),
                "Inline maintenance confirmation"
        );
        resetWorkflowState();
    }

    private void resetWorkspace() {
        if (workflowStage == WorkflowStage.FORM) {
            pendingAction = PendingMaintenanceAction.RESET_WORKSPACE;
            pendingResetType = selectedResetType();
            workflowStage = WorkflowStage.VERIFY;
            render();
            return;
        }
        if (workflowStage == WorkflowStage.VERIFY) {
            if (!verifyInlineSecurity(RiskLevel.HIGH, false)) {
                return;
            }
            workflowStage = WorkflowStage.FINAL_REVIEW;
            render();
            return;
        }
        if (!finalConfirmationAccepted(true)) {
            return;
        }
        BackupRecord backup = createVerifiedBackup("reset-workspace");
        DatabaseHandler.WorkspaceMaintenanceExecutionResult result = database.resetWorkspaceData(pendingResetType, backup.getBackupFile(), backup.getChecksum());
        DataRefreshBus.notifyDataChanged();
        summaryArea.setText(lines(
                "Workspace reset successfully.",
                "Backup: Verified",
                "Database check: " + result.integrityCheck(),
                "Records affected: " + result.recordsAffected(),
                "Completed by: " + result.executedBy(),
                "Completion time: " + result.executionTime()
        ));
        resetWorkflowState();
    }

    private void deleteWorkspace() {
        if (workflowStage == WorkflowStage.FORM) {
            pendingAction = PendingMaintenanceAction.DELETE_WORKSPACE;
            workflowStage = WorkflowStage.VERIFY;
            render();
            return;
        }
        if (workflowStage == WorkflowStage.VERIFY) {
            if (!verifyInlineSecurity(RiskLevel.CRITICAL, true)) {
                return;
            }
            workflowStage = WorkflowStage.FINAL_REVIEW;
            render();
            return;
        }
        if (!finalConfirmationAccepted(true)) {
            return;
        }
        BackupRecord backup = createVerifiedBackup("delete-workspace");
        DatabaseHandler.WorkspaceMaintenanceExecutionResult result = database.deleteWorkspaceData(backup.getBackupFile(), backup.getChecksum());
        DataRefreshBus.notifyDataChanged();
        summaryArea.setText(lines(
                "Workspace status: Deleted",
                "User identity: Retained",
                "Final backup: Verified",
                "Database check: " + result.integrityCheck(),
                "Records affected: " + result.recordsAffected(),
                "Deletion completed by: " + result.executedBy(),
                "Completion time: " + result.executionTime()
        ));
        resetWorkflowState();
    }

    private void executeRecordMaintenance(
            String operation,
            List<DatabaseHandler.RecordDisposalCandidateData> records,
            String reason,
            String authority
    ) {
        BackupRecord backup = createVerifiedBackup(slug(operation));
        DatabaseHandler.RecordDisposalExecutionResult result = database.executeRecordDisposal(records, reason, authority, backup.getBackupFile(), backup.getChecksum());
        DataRefreshBus.notifyDataChanged();
        summaryArea.setText(lines(
                operation + " completed.",
                "Backup: Verified",
                "Database check: " + result.integrityCheck(),
                "Records selected: " + result.recordsRequested(),
                "Records removed: " + result.recordsDisposed(),
                "Records skipped: " + result.recordsSkipped(),
                "Completed by: " + result.executedBy(),
                "Completion time: " + result.executionTime()
        ));
    }

    private BackupRecord createVerifiedBackup(String name) {
        BackupRecord backup = database.createBackup(DatabaseHandler.defaultBackupDirectory(), name + "-automatic-backup");
        database.validateBackup(Path.of(backup.getBackupFile()));
        return backup;
    }

    private void updateClearTestSummary() {
        List<DatabaseHandler.RecordDisposalCandidateData> candidates = findTestDataCandidates();
        int safe = eligible(candidates).size();
        summaryArea.setText(lines(
                "Records found: " + candidates.size(),
                "Safe to clear: " + safe,
                "Protected because linked to real data: " + Math.max(0, candidates.size() - safe)
        ));
    }

    private void updateArchivedSummary() {
        List<DatabaseHandler.RecordDisposalCandidateData> candidates = findArchivedCandidates();
        int safe = eligible(candidates).size();
        summaryArea.setText(lines(
                "Archived records found: " + candidates.size(),
                "Eligible for deletion: " + safe,
                "Protected records: " + Math.max(0, candidates.size() - safe)
        ));
    }

    private void updateResetSummary() {
        summaryArea.setText(lines(
                "Will be removed:",
                "Transactions, accounts, budgets, projects, goals and loans according to the selected reset type.",
                "",
                "Will remain:",
                "User login, security history, maintenance history and backups."
        ));
    }

    private void updateDeleteSummary() {
        DatabaseHandler.WorkspaceMaintenanceSummary summary = database.workspaceMaintenanceSummary();
        summaryArea.setText(lines(
                "Owner: " + workspaceOwnerText(),
                "Accounts: " + summary.accounts(),
                "Transactions: " + summary.transactions(),
                "Projects: " + summary.projects(),
                "Goals: " + summary.goals(),
                "Last backup: " + latestBackupText(),
                "",
                "The user login will remain.",
                "All workspace financial data will be removed."
        ));
    }

    private List<DatabaseHandler.RecordDisposalCandidateData> findTestDataCandidates() {
        return database.searchRecordDisposalCandidates("Other", "", "All", null, null, "", true);
    }

    private List<DatabaseHandler.RecordDisposalCandidateData> findArchivedCandidates() {
        String recordType = dataTypeBox == null || dataTypeBox.getValue() == null || dataTypeBox.getValue().startsWith("All")
                ? "Other"
                : dataTypeBox.getValue();
        LocalDate cutoff = archivedBeforePicker == null ? null : archivedBeforePicker.getValue();
        return database.searchRecordDisposalCandidates(recordType, "", "ARCHIVED", null, cutoff, "", false);
    }

    private List<DatabaseHandler.RecordDisposalCandidateData> eligible(List<DatabaseHandler.RecordDisposalCandidateData> candidates) {
        return candidates.stream()
                .filter(candidate -> "ELIGIBLE".equals(candidate.eligibility()))
                .toList();
    }

    private boolean anyClassificationSelected() {
        return (testCheckBox != null && testCheckBox.isSelected())
                || (demoCheckBox != null && demoCheckBox.isSelected())
                || (sampleCheckBox != null && sampleCheckBox.isSelected())
                || (trainingCheckBox != null && trainingCheckBox.isSelected());
    }

    private String selectedResetType() {
        if (resetTypeGroup == null || resetTypeGroup.getSelectedToggle() == null) {
            return "Clear everything and restore default settings";
        }
        Object userData = resetTypeGroup.getSelectedToggle().getUserData();
        return userData == null ? "Clear everything and restore default settings" : userData.toString();
    }

    private void loadHistoryTable() {
        historyTable.getChildren().clear();
        historyTable.getColumnConstraints().clear();
        List<String> columns = List.of("Date", "Operation", "Workspace", "Result", "Records", "Performed By");
        for (int i = 0; i < columns.size(); i++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setHgrow(Priority.ALWAYS);
            constraints.setPercentWidth(100.0 / columns.size());
            historyTable.getColumnConstraints().add(constraints);
            historyTable.add(tableCell(columns.get(i), true), i, 0);
        }
        List<SystemLogRecord> records = database.listSystemLogHistory(20);
        int row = 1;
        for (SystemLogRecord record : records) {
            historyTable.add(tableCell(nullToBlank(record.getCreatedAt()), false), 0, row);
            historyTable.add(tableCell(nullToBlank(record.getActionName()), false), 1, row);
            historyTable.add(tableCell(workspaceOwnerText(), false), 2, row);
            historyTable.add(tableCell(nullToBlank(record.getSeverity()), false), 3, row);
            historyTable.add(tableCell("-", false), 4, row);
            historyTable.add(tableCell(nullToBlank(record.getModuleName()), false), 5, row);
            row++;
        }
        if (records.isEmpty()) {
            historyTable.add(tableCell("No maintenance history found.", false), 0, 1);
        }
    }

    private void showSelectedHistoryDetails() {
        List<SystemLogRecord> records = database.listSystemLogHistory(1);
        if (records.isEmpty()) {
            summaryArea.setText("No maintenance record is available.");
            return;
        }
        SystemLogRecord record = records.getFirst();
        summaryArea.setText(lines(
                "Operation ID: " + record.getId(),
                "Operation type: " + record.getActionName(),
                "Workspace: " + workspaceOwnerText(),
                "Reason: " + record.getDetails(),
                "Records affected: See operation summary",
                "Backup reference: " + latestBackupText(),
                "Performed by: " + record.getModuleName(),
                "Date and time: " + record.getCreatedAt(),
                "Verification result: Recorded after operation"
        ));
    }

    private void exportHistory() {
        try {
            Path exportFile = ExportPathService.writeTextExport(
                    ExportPathService.defaultFileName("Maintenance History", "txt"),
                    buildHistoryExport()
            );
            summaryArea.setText(ExportPathService.successMessage(exportFile));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export maintenance history", exception);
        }
    }

    private String buildHistoryExport() {
        StringBuilder builder = new StringBuilder("Maintenance History\n\n");
        for (SystemLogRecord record : database.listSystemLogHistory(200)) {
            builder.append(record.getCreatedAt())
                    .append(" | ").append(record.getActionName())
                    .append(" | ").append(record.getSeverity())
                    .append(" | ").append(record.getDetails())
                    .append('\n');
        }
        return builder.toString();
    }

    private void renderSecurityVerification(
            String actionName,
            String impact,
            RiskLevel riskLevel,
            String phrase,
            boolean reasonRequired
    ) {
        boolean forceFresh = riskLevel == RiskLevel.CRITICAL;
        boolean passwordRequired = !privilegedActionService.hasValidSession(riskLevel, forceFresh);
        securityVerificationPane = new SecurityVerificationPane(
                actionName,
                impact,
                riskLevel,
                phrase,
                passwordRequired,
                reasonRequired,
                privilegedActionService.currentStatusText(riskLevel).orElse("")
        );
        formContainer.getChildren().add(securityVerificationPane);
    }

    private boolean verifyInlineSecurity(RiskLevel riskLevel, boolean forceFreshVerification) {
        if (securityVerificationPane == null) {
            summaryArea.setText("Security verification is not ready. Reopen this workflow and try again.");
            return false;
        }
        if (!securityVerificationPane.validateInput()) {
            return false;
        }
        if (!securityVerificationPane.isPasswordRequired()
                && privilegedActionService.hasValidSession(riskLevel, forceFreshVerification)) {
            securityVerificationPane.markVerified(privilegedActionService.currentStatusText(riskLevel).orElse("Identity verified for this high-risk action."));
            return true;
        }
        try {
            PrivilegedActionService.VerificationResult result = privilegedActionService.verifyCurrentUser(
                    securityVerificationPane.passwordChars(),
                    riskLevel,
                    true
            );
            securityVerificationPane.markVerified(result.statusText());
            return true;
        } catch (RuntimeException exception) {
            securityVerificationPane.setError(rootMessage(exception));
            return false;
        } finally {
            securityVerificationPane.clearPassword();
        }
    }

    private void renderFinalConfirmation(String title, String details, boolean reasonRequired) {
        Label heading = new Label(title);
        heading.getStyleClass().add("maintenance-step-title");
        Label detailLabel = new Label(details);
        detailLabel.setWrapText(true);
        detailLabel.getStyleClass().add("settings-status-text");
        finalReasonArea = new TextArea();
        finalReasonArea.setPromptText(reasonRequired ? "Required reason or maintenance note" : "Optional maintenance note");
        finalReasonArea.setWrapText(true);
        finalReasonArea.setPrefRowCount(3);
        finalReasonArea.getStyleClass().add("maintenance-text-area");
        finalConfirmationCheckBox = new CheckBox("I have reviewed the impact and want to continue.");
        finalConfirmationCheckBox.getStyleClass().add("maintenance-checkbox");
        VBox review = new VBox(10, heading, detailLabel, field("Reason", finalReasonArea), finalConfirmationCheckBox);
        review.getStyleClass().add("security-impact-warning");
        formContainer.getChildren().add(review);
    }

    private boolean finalConfirmationAccepted(boolean reasonRequired) {
        if (finalConfirmationCheckBox == null || !finalConfirmationCheckBox.isSelected()) {
            summaryArea.setText("Review the final impact and tick the confirmation box before continuing.");
            return false;
        }
        if (reasonRequired && (finalReasonArea == null || finalReasonArea.getText().trim().isBlank())) {
            summaryArea.setText("Enter a reason before continuing.");
            finalReasonArea.requestFocus();
            return false;
        }
        return true;
    }

    private String finalReasonOrDefault(String fallback) {
        if (finalReasonArea == null || finalReasonArea.getText().trim().isBlank()) {
            return fallback;
        }
        return finalReasonArea.getText().trim();
    }

    private HBox stepStrip(int currentStep) {
        List<String> labels = switch (currentArea) {
            case "Reset Workspace" -> List.of("1 Scope", "2 Verify", "3 Execute");
            case "Delete Workspace" -> List.of("1 Workspace", "2 Verify", "3 Delete");
            default -> List.of("1 Scope", "2 Review", "3 Execute");
        };
        HBox strip = new HBox(8);
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.getStyleClass().add("maintenance-step-strip");
        for (int index = 0; index < labels.size(); index++) {
            Label step = new Label(labels.get(index));
            step.getStyleClass().add(index + 1 == currentStep ? "maintenance-step-badge-current" : "maintenance-step-badge");
            strip.getChildren().add(step);
        }
        return strip;
    }

    private int stepNumber() {
        return switch (workflowStage) {
            case VERIFY -> 2;
            case FINAL_REVIEW -> 3;
            default -> 1;
        };
    }

    private void resetWorkflowState() {
        workflowStage = WorkflowStage.FORM;
        pendingAction = PendingMaintenanceAction.NONE;
        pendingResetType = "";
        pendingTestSelected = true;
        pendingDemoSelected = true;
        pendingSampleSelected = false;
        pendingTrainingSelected = false;
        pendingArchivedBefore = null;
        pendingArchivedType = "All archived records";
        securityVerificationPane = null;
        finalConfirmationCheckBox = null;
        finalReasonArea = null;
    }

    private VBox field(String label, Node node) {
        VBox box = new VBox(5);
        box.getStyleClass().add("maintenance-simple-field");
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("form-label");
        box.getChildren().addAll(fieldLabel, node);
        return box;
    }

    private VBox labelledBlock(String label, Node node) {
        VBox box = new VBox(8);
        box.getStyleClass().add("maintenance-simple-block");
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("form-label");
        box.getChildren().addAll(fieldLabel, node);
        return box;
    }

    private ComboBox<String> combo(List<String> values, String selected) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(values);
        comboBox.setValue(selected);
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.getStyleClass().add("maintenance-input");
        return comboBox;
    }

    private CheckBox checked(String text, boolean selected) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setSelected(selected);
        checkBox.getStyleClass().add("maintenance-checkbox");
        checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateClearTestSummary());
        return checkBox;
    }

    private RadioButton radio(String text, ToggleGroup group, boolean selected) {
        RadioButton radioButton = new RadioButton(text);
        radioButton.setToggleGroup(group);
        radioButton.setSelected(selected);
        radioButton.setUserData(text);
        radioButton.getStyleClass().add("maintenance-checkbox");
        radioButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                updateResetSummary();
            }
        });
        return radioButton;
    }

    private Node tableCell(String value, boolean header) {
        Label label = new Label(value == null ? "" : value);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add(header ? "maintenance-table-header-cell" : "maintenance-table-cell");
        return label;
    }

    private String workspacePrompt() {
        return workspaceOwnerText() + " - Workspace " + workspaceIdText();
    }

    private List<String> workspaceOptions() {
        return List.of(workspacePrompt());
    }

    private String workspaceOwnerText() {
        SystemUser user = workspaceUserOrNull();
        return user == null ? "No active workspace" : user.getDisplayName();
    }

    private String workspaceIdText() {
        SystemUser user = workspaceUserOrNull();
        return user == null ? "Unknown" : String.valueOf(user.getId());
    }

    private SystemUser workspaceUserOrNull() {
        try {
            return UserSession.getWorkspaceUser();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String latestBackupText() {
        try {
            List<BackupRecord> backups = database.listBackupHistory();
            if (backups.isEmpty()) {
                return "No backup recorded";
            }
            BackupRecord backup = backups.getFirst();
            return backup.getCreatedAt();
        } catch (RuntimeException exception) {
            return "Unavailable";
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String slug(String value) {
        String clean = value == null ? "maintenance" : value.toLowerCase(Locale.ENGLISH);
        clean = clean.replaceAll("[^a-z0-9]+", "-").replaceAll("-+", "-");
        clean = clean.replaceAll("^-|-$", "");
        return clean.isBlank() ? "maintenance" : clean;
    }

    private String lines(String... values) {
        return String.join(System.lineSeparator(), values);
    }

    private String deleteWorkspacePhrase() {
        return "DELETE " + workspaceOwnerText().toUpperCase(Locale.ENGLISH) + " WORKSPACE";
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

    private enum WorkflowStage {
        FORM,
        VERIFY,
        FINAL_REVIEW
    }

    private enum PendingMaintenanceAction {
        NONE,
        CLEAR_TEST_DATA,
        PURGE_ARCHIVED_RECORDS,
        RESET_WORKSPACE,
        DELETE_WORKSPACE
    }
}
