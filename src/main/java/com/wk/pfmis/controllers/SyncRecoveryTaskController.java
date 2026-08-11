package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.SystemLogRecord;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.PrivilegedActionService;
import com.wk.pfmis.security.RiskLevel;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.utils.ExportPathService;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SyncRecoveryTaskController {
    private static final String UNAVAILABLE = "Not available in the current system mode";

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label guidanceLabel;
    @FXML private VBox contentContainer;
    @FXML private TextArea resultArea;
    @FXML private Button supportingActionButton;
    @FXML private Button mainActionButton;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final PrivilegedActionService privilegedActionService = PrivilegedActionService.getInstance();

    private String currentArea = "Sync Status";
    private ComboBox<String> workspaceBox;
    private ComboBox<String> typeBox;
    private ComboBox<String> statusBox;
    private ComboBox<String> reasonBox;
    private ComboBox<String> resultBox;
    private ComboBox<String> userBox;
    private ComboBox<String> operationBox;
    private ComboBox<String> recoveryActionBox;
    private TextField searchField;
    private DatePicker fromPicker;
    private DatePicker toPicker;
    private TableView<SyncTableRow> recordsTable;
    private BackupRecord latestRecoveryBackup;
    private boolean latestRecoveryBackupValid;
    private RecoveryStage recoveryStage = RecoveryStage.REVIEW;
    private SecurityVerificationPane recoveryVerificationPane;
    private CheckBox recoveryFinalCheckBox;

    @FXML
    public void initialize() {
        selectArea(currentArea);
    }

    public void selectArea(String area) {
        currentArea = area == null || area.isBlank() ? "Sync Status" : area.trim();
        recoveryStage = RecoveryStage.REVIEW;
        render();
    }

    public void refresh() {
        render();
    }

    @FXML
    private void runSupportingAction() {
        switch (currentArea) {
            case "Sync Status" -> refreshStatus();
            case "Pending Queue" -> syncSelected();
            case "Failed Records" -> viewError();
            case "Conflicts" -> viewComparison();
            case "Quarantine" -> openRecord();
            case "Sync History" -> viewDetails();
            case "Recovery" -> cancelRecovery();
            default -> render();
        }
    }

    @FXML
    private void runMainAction() {
        try {
            switch (currentArea) {
                case "Sync Status" -> syncNow();
                case "Pending Queue" -> syncAll();
                case "Failed Records" -> retrySelected();
                case "Conflicts" -> resolveSelectedConflict();
                case "Quarantine" -> resolveSelectedQuarantine();
                case "Sync History" -> exportHistory();
                case "Recovery" -> runRecovery();
                default -> render();
            }
        } catch (RuntimeException exception) {
            UiAlerts.error("Sync and Recovery action failed", exception);
        }
    }

    private void render() {
        contentContainer.getChildren().clear();
        recordsTable = null;
        latestRecoveryBackup = null;
        latestRecoveryBackupValid = false;
        recoveryVerificationPane = null;
        recoveryFinalCheckBox = null;
        mainActionButton.getStyleClass().setAll("primary-button");
        supportingActionButton.getStyleClass().setAll("secondary-button");
        switch (currentArea) {
            case "Pending Queue" -> renderPendingQueuePage();
            case "Failed Records" -> renderFailedRecordsPage();
            case "Conflicts" -> renderConflictsPage();
            case "Quarantine" -> renderQuarantinePage();
            case "Sync History" -> renderSyncHistoryPage();
            case "Recovery" -> renderRecoveryPage();
            default -> renderSyncStatusPage();
        }
    }

    private void renderSyncStatusPage() {
        titleLabel.setText("Sync Status");
        subtitleLabel.setText("Show whether the local PFMIS workspace is connected and whether records are up to date.");
        guidanceLabel.setText("Refresh status checks the local workspace only. Sync Now is unavailable until a server sync connector is configured.");
        setButtons("Refresh Status", "Sync Now", true, false);

        GridPane summary = summaryGrid(List.of(
                row("Connection", Files.isRegularFile(DatabaseHandler.databasePath()) ? "Local workspace open" : "Local workspace missing"),
                row("Server", "Not configured"),
                row("Last successful", latestSyncEvent(false)),
                row("Last failed", latestSyncEvent(true)),
                row("Pending", "0"),
                row("Failed", "0"),
                row("Conflicts", "0"),
                row("Quarantined", "0"),
                row("Database check", safeIntegrityStatus()),
                row("Latest backup", latestBackupText())
        ));
        contentContainer.getChildren().add(summary);
        resultArea.setText(lines(
                "Sync status loaded.",
                "",
                "Remote synchronisation: " + UNAVAILABLE,
                "Reason: no server connector or sync queue table is configured in this workspace.",
                "Refresh Status does not change financial records."
        ));
    }

    private void renderPendingQueuePage() {
        titleLabel.setText("Pending Queue");
        subtitleLabel.setText("Show records waiting to be synchronised.");
        guidanceLabel.setText("Sync Selected and Sync All will be enabled after a working sync queue and server connector are available.");
        setButtons("Sync Selected", "Sync All", false, false);

        workspaceBox = combo(workspaceOptions(), workspacePrompt());
        typeBox = combo(List.of("All record types", "Transactions", "Accounts", "Budgets", "Projects", "Goals", "Loans"), "All record types");
        searchField = textField("Record ID or description");
        FlowPane filters = fields(field("Workspace", workspaceBox), field("Type", typeBox), wideField("Search", searchField));

        recordsTable = table(
                "No pending queue records are available.",
                true,
                List.of(column("Date", 135), column("Record", 220), column("Action", 120), column("Created By", 160), column("Status", 140)),
                List.of()
        );
        contentContainer.getChildren().addAll(filters, recordsTable);
        resultArea.setText(lines(
                "Pending records found: 0",
                "Eligible to sync: 0",
                "",
                UNAVAILABLE + " - no sync queue table or server connector is configured."
        ));
    }

    private void renderFailedRecordsPage() {
        titleLabel.setText("Failed Records");
        subtitleLabel.setText("Show records that could not be synchronised.");
        guidanceLabel.setText("View an error, correct the record, then retry selected records when a sync service is available.");
        setButtons("View Error", "Retry Selected", false, false);

        typeBox = combo(List.of("All types", "Transactions", "Accounts", "Budgets", "Projects", "Goals", "Loans"), "All types");
        reasonBox = combo(List.of("All errors", "Missing required information", "Invalid reference", "Network failure", "Server rejection", "Duplicate record", "Unsupported operation", "Permission failure"), "All errors");
        searchField = textField("Record ID or description");
        FlowPane filters = fields(field("Type", typeBox), field("Reason", reasonBox), wideField("Search", searchField));

        recordsTable = table(
                "No failed sync records are available.",
                true,
                List.of(column("Record", 220), column("Operation", 130), column("Error", 300), column("Retry Count", 120), column("Failed At", 170)),
                List.of()
        );
        contentContainer.getChildren().addAll(filters, recordsTable);
        resultArea.setText(lines(
                "Failed records found: 0",
                "",
                UNAVAILABLE + " - retry requires a working sync queue and server acknowledgement."
        ));
    }

    private void renderConflictsPage() {
        titleLabel.setText("Conflicts");
        subtitleLabel.setText("Handle records changed locally and centrally before synchronisation.");
        guidanceLabel.setText("The system must never overwrite a local or server version silently.");
        setButtons("View Comparison", "Resolve Selected", false, false);

        typeBox = combo(List.of("All record types", "Transactions", "Accounts", "Budgets", "Projects", "Goals", "Loans"), "All record types");
        statusBox = combo(List.of("Unresolved", "Resolved", "All statuses"), "Unresolved");
        searchField = textField("Record ID or description");
        FlowPane filters = fields(field("Type", typeBox), field("Status", statusBox), wideField("Search", searchField));

        recordsTable = table(
                "No sync conflicts are available.",
                true,
                List.of(column("Record", 190), column("Local Value", 190), column("Server Value", 190), column("Difference", 210), column("Date", 150)),
                List.of()
        );
        contentContainer.getChildren().addAll(filters, recordsTable);
        resultArea.setText(lines(
                "Conflicts found: 0",
                "",
                UNAVAILABLE + " - conflict comparison and resolution require server-side record versions."
        ));
    }

    private void renderQuarantinePage() {
        titleLabel.setText("Quarantine");
        subtitleLabel.setText("Hold records that are unsafe or invalid and must not be written to the central database.");
        guidanceLabel.setText("Correct the record first. Resolve Selected becomes available when quarantine storage is implemented.");
        setButtons("Open Record", "Resolve Selected", false, false);

        typeBox = combo(List.of("All record types", "Transactions", "Accounts", "Budgets", "Projects", "Goals", "Loans"), "All record types");
        reasonBox = combo(List.of("All reasons", "Missing account", "Invalid currency", "Duplicate transaction", "Broken reference", "Invalid amount", "Wrong workspace", "Unsupported status", "Corrupted import data"), "All reasons");
        searchField = textField("Record ID or description");
        FlowPane filters = fields(field("Type", typeBox), field("Reason", reasonBox), wideField("Search", searchField));

        recordsTable = table(
                "No quarantined records are available.",
                true,
                List.of(column("Record", 220), column("Reason", 280), column("Created By", 150), column("Date", 150), column("Status", 150)),
                List.of()
        );
        contentContainer.getChildren().addAll(filters, recordsTable);
        resultArea.setText(lines(
                "Quarantined records found: 0",
                "",
                UNAVAILABLE + " - quarantine storage is not configured for this workspace."
        ));
    }

    private void renderSyncHistoryPage() {
        titleLabel.setText("Sync History");
        subtitleLabel.setText("Provide a read-only record of previous synchronisation activity.");
        guidanceLabel.setText("Sync history is read-only. There are no edit, delete or clear history actions.");
        setButtons("View Details", "Export History", true, true);

        fromPicker = new DatePicker();
        fromPicker.setValue(LocalDate.now().minusMonths(1));
        toPicker = new DatePicker();
        toPicker.setValue(LocalDate.now());
        resultBox = combo(List.of("All results", "Completed", "Completed With Warnings", "Failed", "Cancelled"), "All results");
        userBox = combo(List.of("All users", signedInUserText()), "All users");
        workspaceBox = combo(List.of("All workspaces", workspacePrompt()), "All workspaces");
        FlowPane filters = fields(
                field("From", fromPicker),
                field("To", toPicker),
                field("Result", resultBox),
                field("User", userBox),
                field("Workspace", workspaceBox)
        );

        List<SyncTableRow> rows = syncHistoryRows();
        recordsTable = table(
                "No sync history records are available.",
                false,
                List.of(column("Date", 175), column("Session", 120), column("Workspace", 180), column("Sent", 80), column("Received", 95), column("Failed", 80), column("Result", 260)),
                rows
        );
        contentContainer.getChildren().addAll(filters, recordsTable);
        resultArea.setText(rows.isEmpty()
                ? "No sync sessions have been recorded. Export History will create a read-only evidence file for the current filter."
                : "Select a history row, then use View Details or Export History.");
    }

    private void renderRecoveryPage() {
        titleLabel.setText("Recovery");
        subtitleLabel.setText("Recover synchronisation when ordinary sync and retry actions cannot resolve the problem.");
        guidanceLabel.setText("Only recovery actions supported by the current local system are shown.");

        latestRecoveryBackup = database.latestDailyBackupRecord();
        latestRecoveryBackupValid = latestRecoveryBackup != null && backupIsValid(latestRecoveryBackup);
        boolean canRun = UserSession.isSuperAdmin() && latestRecoveryBackup != null && latestRecoveryBackupValid;
        String mainText = switch (recoveryStage) {
            case VERIFY -> "Verify & Continue";
            case FINAL_REVIEW -> "Run Recovery";
            default -> "Review Recovery";
        };
        setButtons("Cancel", mainText, true, canRun);
        mainActionButton.getStyleClass().setAll(recoveryStage == RecoveryStage.FINAL_REVIEW ? "maintenance-danger-button" : "primary-button");

        workspaceBox = combo(workspaceOptions(), workspacePrompt());
        recoveryActionBox = new ComboBox<>();
        recoveryActionBox.setMaxWidth(Double.MAX_VALUE);
        recoveryActionBox.getStyleClass().add("maintenance-input");
        if (latestRecoveryBackup == null) {
            recoveryActionBox.getItems().setAll("No recovery action available");
            recoveryActionBox.getSelectionModel().selectFirst();
            recoveryActionBox.setDisable(true);
        } else {
            recoveryActionBox.getItems().setAll("Restore latest local recovery backup");
            recoveryActionBox.getSelectionModel().selectFirst();
        }

        VBox description = labelledBlock("Recovery action", new Label(recoveryDescription()));
        contentContainer.getChildren().addAll(
                recoveryStepStrip(),
                field("Workspace", workspaceBox),
                field("Choose recovery action", recoveryActionBox),
                description,
                summaryGrid(List.of(
                        row("Backup date", latestRecoveryBackup == null ? "No backup available" : latestRecoveryBackup.getCreatedAt()),
                        row("Backup checksum", latestRecoveryBackup == null ? "Not available" : shortChecksum(latestRecoveryBackup.getChecksum())),
                        row("Schema version", safeSchemaVersion()),
                        row("Backup verification", latestRecoveryBackup == null ? "Not available" : (latestRecoveryBackupValid ? "Passed" : "Failed"))
                ))
        );

        if (!UserSession.isSuperAdmin()) {
            resultArea.setText(lines(
                    "Recovery is restricted to Super Administrators.",
                    "",
                    "Basic retry recovery is " + UNAVAILABLE.toLowerCase(Locale.ENGLISH) + " because no sync queue service is configured."
            ));
        } else if (latestRecoveryBackup == null) {
            resultArea.setText("No local recovery backup is available. Run a backup from Administration before recovery can be used.");
        } else if (!latestRecoveryBackupValid) {
            resultArea.setText("The latest local recovery backup failed verification. Recovery is blocked until a valid backup is available.");
        } else {
            resultArea.setText(lines(
                    "Ready to restore the latest local recovery backup.",
                    "",
                    "The current workspace will be backed up first.",
                    "The selected backup will then replace the current local workspace.",
                    "Super Administrator password confirmation is required."
            ));
        }

        if (canRun && recoveryStage == RecoveryStage.VERIFY) {
            boolean passwordRequired = !privilegedActionService.hasValidSession(RiskLevel.CRITICAL, true);
            recoveryVerificationPane = new SecurityVerificationPane(
                    "Restore latest local recovery backup",
                    lines(
                            "The selected backup will replace the current local workspace.",
                            "Backup: " + fileName(latestRecoveryBackup.getBackupFile()),
                            "A pre-restore backup will be created automatically."
                    ),
                    RiskLevel.CRITICAL,
                    "",
                    passwordRequired,
                    true,
                    ""
            );
            contentContainer.getChildren().add(recoveryVerificationPane);
        } else if (canRun && recoveryStage == RecoveryStage.FINAL_REVIEW) {
            Label title = new Label("Final Review");
            title.getStyleClass().add("maintenance-step-title");
            Label details = new Label(lines(
                    "Workspace: " + workspaceOwnerText(),
                    "Backup: " + fileName(latestRecoveryBackup.getBackupFile()),
                    "Backup verification: Passed",
                    "Pre-restore backup: Will be created automatically",
                    "Audit: Restore operation will be recorded"
            ));
            details.setWrapText(true);
            details.getStyleClass().add("settings-status-text");
            recoveryFinalCheckBox = new CheckBox("I have reviewed the recovery impact and want to restore this workspace.");
            recoveryFinalCheckBox.getStyleClass().add("maintenance-checkbox");
            VBox finalReview = new VBox(10, title, details, recoveryFinalCheckBox);
            finalReview.getStyleClass().add("security-impact-warning");
            contentContainer.getChildren().add(finalReview);
        }
    }

    private void refreshStatus() {
        String integrity = safeIntegrityStatus();
        database.recordSystemLog("Sync And Recovery", "Refresh Status", "INFO", "Local sync status checked. Database: " + integrity);
        renderSyncStatusPage();
        resultArea.setText(lines(
                "Status refreshed.",
                "",
                "Database check: " + integrity,
                "Remote synchronisation: " + UNAVAILABLE
        ));
    }

    private void syncNow() {
        unavailable("Sync Now", "No server connector or sync queue table is configured.");
    }

    private void syncSelected() {
        unavailable("Sync Selected", "Select records after a sync queue table is available.");
    }

    private void syncAll() {
        unavailable("Sync All", "No server connector or sync queue table is configured.");
    }

    private void viewError() {
        SyncTableRow row = selectedRow();
        if (row == null) {
            UiAlerts.info("Select a failed record first.");
            return;
        }
        UiAlerts.info(lines(
                "Record: " + row.value(0),
                "Problem: " + row.value(2),
                "Recommended action: Open the record and correct the missing or invalid information."
        ));
    }

    private void retrySelected() {
        unavailable("Retry Selected", "Retry requires a working sync queue and server acknowledgement.");
    }

    private void viewComparison() {
        SyncTableRow row = selectedRow();
        if (row == null) {
            UiAlerts.info("Select a conflict first.");
            return;
        }
        UiAlerts.info(lines(
                "Record: " + row.value(0),
                "Local version: " + row.value(1),
                "Server version: " + row.value(2),
                "Difference: " + row.value(3)
        ));
    }

    private void resolveSelectedConflict() {
        unavailable("Resolve Selected", "Conflict resolution requires server-side record versions and merge rules.");
    }

    private void openRecord() {
        SyncTableRow row = selectedRow();
        if (row == null) {
            UiAlerts.info("Select a quarantined record first.");
            return;
        }
        UiAlerts.info("Open the original financial screen for: " + row.value(0));
    }

    private void resolveSelectedQuarantine() {
        unavailable("Resolve Selected", "Quarantine resolution requires quarantine storage and revalidation support.");
    }

    private void viewDetails() {
        SystemLogRecord record = selectedLogRecord();
        if (record == null) {
            resultArea.setText("No sync history record is available.");
            return;
        }
        resultArea.setText(lines(
                "Session ID: " + record.getId(),
                "Started by: " + signedInUserText(),
                "Workspace: " + workspaceOwnerText(),
                "Device: Local desktop",
                "Start time: " + record.getCreatedAt(),
                "Completion time: " + record.getCreatedAt(),
                "Records sent: 0",
                "Records received: 0",
                "Records rejected: " + ("ERROR".equalsIgnoreCase(record.getSeverity()) ? "1" : "0"),
                "Conflicts created: 0",
                "Quarantined records: 0",
                "Error details: " + nullToBlank(record.getDetails()),
                "Final result: " + record.getSeverity() + " - " + record.getActionName()
        ));
    }

    private void exportHistory() {
        try {
            Path exportFile = ExportPathService.writeTextExport(
                    ExportPathService.defaultFileName("Sync History", "txt"),
                    historyExportBody()
            );
            database.recordSystemLog("Sync And Recovery", "Export History", "INFO", "Sync history exported to " + exportFile);
            resultArea.setText(ExportPathService.successMessage(exportFile));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export sync history", exception);
        }
    }

    private void cancelRecovery() {
        recoveryStage = RecoveryStage.REVIEW;
        renderRecoveryPage();
    }

    private void runRecovery() {
        if (latestRecoveryBackup == null) {
            resultArea.setText("No local recovery backup is available.");
            return;
        }
        if (!UserSession.isSuperAdmin()) {
            resultArea.setText("Recovery is restricted to Super Administrators.");
            return;
        }
        if (recoveryStage == RecoveryStage.REVIEW) {
            recoveryStage = RecoveryStage.VERIFY;
            renderRecoveryPage();
            return;
        }
        if (recoveryStage == RecoveryStage.VERIFY) {
            if (recoveryVerificationPane == null) {
                resultArea.setText("Security verification is not ready. Reopen Recovery and try again.");
                return;
            }
            if (!recoveryVerificationPane.validateInput()) {
                return;
            }
            try {
                PrivilegedActionService.VerificationResult verification = privilegedActionService.verifyCurrentUser(
                        recoveryVerificationPane.passwordChars(),
                        RiskLevel.CRITICAL,
                        true
                );
                recoveryVerificationPane.markVerified(verification.statusText());
                recoveryStage = RecoveryStage.FINAL_REVIEW;
                renderRecoveryPage();
            } catch (RuntimeException exception) {
                recoveryVerificationPane.setError(rootMessage(exception));
            } finally {
                recoveryVerificationPane.clearPassword();
            }
            return;
        }
        if (recoveryFinalCheckBox == null || !recoveryFinalCheckBox.isSelected()) {
            resultArea.setText("Review the final recovery impact and tick the confirmation box before continuing.");
            return;
        }
        Path backupFile = Path.of(latestRecoveryBackup.getBackupFile());
        String verification = database.validateBackup(backupFile);
        database.restoreBackup(backupFile);
        String integrity = safeIntegrityStatus();
        database.recordSystemLog("Sync And Recovery", "Restore latest local recovery backup", "WARN", backupFile.toString());
        DataRefreshBus.notifyDataChanged();
        recoveryStage = RecoveryStage.REVIEW;
        renderRecoveryPage();
        resultArea.setText(lines(
                "Recovery completed.",
                "",
                "Backup verified: " + verification,
                "Database check: " + integrity,
                "Audit recorded."
        ));
    }

    private void unavailable(String action, String reason) {
        String message = lines(action + ".", "", UNAVAILABLE + ".", "Reason: " + reason);
        resultArea.setText(message);
        database.recordSystemLog("Sync And Recovery", action, "INFO", UNAVAILABLE + ": " + reason);
    }

    private void setButtons(String supportingText, String mainText, boolean supportingEnabled, boolean mainEnabled) {
        supportingActionButton.setText(supportingText);
        supportingActionButton.setDisable(!supportingEnabled);
        mainActionButton.setText(mainText);
        mainActionButton.setDisable(!mainEnabled);
    }

    private FlowPane fields(Node... nodes) {
        FlowPane pane = new FlowPane(10, 10, nodes);
        pane.setPrefWrapLength(1120);
        return pane;
    }

    private VBox field(String label, Node node) {
        VBox box = new VBox(5);
        box.getStyleClass().add("maintenance-simple-field");
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("field-label");
        box.getChildren().addAll(fieldLabel, node);
        return box;
    }

    private VBox wideField(String label, Node node) {
        VBox box = field(label, node);
        box.getStyleClass().setAll("maintenance-simple-field-wide");
        return box;
    }

    private VBox labelledBlock(String label, Node node) {
        VBox box = new VBox(8);
        box.getStyleClass().add("maintenance-simple-block");
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("field-label");
        if (node instanceof Label valueLabel) {
            valueLabel.setWrapText(true);
            valueLabel.getStyleClass().add("settings-status-text");
        }
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

    private TextField textField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("maintenance-input");
        return field;
    }

    private GridPane summaryGrid(List<SummaryRow> rows) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("maintenance-simple-block");
        grid.setHgap(12);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(150);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(labelColumn, valueColumn);
        for (int index = 0; index < rows.size(); index++) {
            SummaryRow row = rows.get(index);
            Label name = new Label(row.name());
            name.getStyleClass().add("field-label");
            Label value = new Label(row.value());
            value.setWrapText(true);
            value.getStyleClass().add("settings-status-text");
            grid.add(name, 0, index);
            grid.add(value, 1, index);
        }
        return grid;
    }

    private TableView<SyncTableRow> table(String placeholder, boolean selectable, List<ColumnSpec> columns, List<SyncTableRow> rows) {
        TableView<SyncTableRow> tableView = new TableView<>();
        tableView.setEditable(selectable);
        tableView.setPrefHeight(330);
        tableView.setPlaceholder(new Label(placeholder));
        if (selectable) {
            TableColumn<SyncTableRow, Boolean> selectColumn = new TableColumn<>("Select");
            selectColumn.setPrefWidth(70);
            selectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
            selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
            tableView.getColumns().add(selectColumn);
        }
        for (int index = 0; index < columns.size(); index++) {
            ColumnSpec spec = columns.get(index);
            final int valueIndex = index;
            TableColumn<SyncTableRow, String> column = new TableColumn<>(spec.title());
            column.setPrefWidth(spec.width());
            column.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().value(valueIndex)));
            tableView.getColumns().add(column);
        }
        tableView.getItems().setAll(rows);
        rows.forEach(row -> row.selectedProperty().addListener((observable, oldValue, newValue) -> updateSelectedButtons()));
        TableActions.configureScrollableTable(tableView);
        return tableView;
    }

    private void updateSelectedButtons() {
        boolean hasSelected = recordsTable != null && recordsTable.getItems().stream().anyMatch(SyncTableRow::isSelected);
        switch (currentArea) {
            case "Pending Queue" -> supportingActionButton.setDisable(!hasSelected);
            case "Failed Records", "Conflicts", "Quarantine" -> {
                supportingActionButton.setDisable(!hasSelected);
                mainActionButton.setDisable(!hasSelected);
            }
            default -> {
            }
        }
    }

    private SyncTableRow selectedRow() {
        if (recordsTable == null) {
            return null;
        }
        return recordsTable.getItems().stream()
                .filter(SyncTableRow::isSelected)
                .findFirst()
                .orElse(recordsTable.getSelectionModel().getSelectedItem());
    }

    private SystemLogRecord selectedLogRecord() {
        SyncTableRow selected = selectedRow();
        if (selected != null && selected.logRecord() != null) {
            return selected.logRecord();
        }
        List<SystemLogRecord> records = syncHistoryRecords();
        return records.isEmpty() ? null : records.getFirst();
    }

    private List<SyncTableRow> syncHistoryRows() {
        List<SyncTableRow> rows = new ArrayList<>();
        for (SystemLogRecord record : syncHistoryRecords()) {
            rows.add(new SyncTableRow(
                    List.of(
                            nullToBlank(record.getCreatedAt()),
                            "#" + record.getId(),
                            workspaceOwnerText(),
                            "0",
                            "0",
                            "ERROR".equalsIgnoreCase(record.getSeverity()) ? "1" : "0",
                            record.getSeverity() + " - " + record.getActionName()
                    ),
                    record
            ));
        }
        return rows;
    }

    private List<SystemLogRecord> syncHistoryRecords() {
        return database.listSystemLogHistory(200).stream()
                .filter(record -> containsSyncHistoryText(record.getModuleName())
                        || containsSyncHistoryText(record.getActionName()))
                .limit(50)
                .toList();
    }

    private boolean containsSyncHistoryText(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ENGLISH);
        return lower.contains("sync") || lower.contains("recovery");
    }

    private String latestSyncEvent(boolean failed) {
        return database.listSystemLogHistory(200).stream()
                .filter(record -> containsSyncHistoryText(record.getModuleName()) || containsSyncHistoryText(record.getActionName()))
                .filter(record -> failed == "ERROR".equalsIgnoreCase(record.getSeverity()))
                .map(SystemLogRecord::getCreatedAt)
                .findFirst()
                .orElse("None");
    }

    private boolean backupIsValid(BackupRecord backup) {
        try {
            database.validateBackup(Path.of(backup.getBackupFile()));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String historyExportBody() {
        StringBuilder builder = new StringBuilder("Sync History").append(System.lineSeparator()).append(System.lineSeparator());
        List<SystemLogRecord> records = syncHistoryRecords();
        if (records.isEmpty()) {
            builder.append("No sync history records are available.").append(System.lineSeparator());
        }
        for (SystemLogRecord record : records) {
            builder.append(record.getCreatedAt())
                    .append(" | Session #").append(record.getId())
                    .append(" | ").append(workspaceOwnerText())
                    .append(" | Sent: 0")
                    .append(" | Received: 0")
                    .append(" | Failed: ").append("ERROR".equalsIgnoreCase(record.getSeverity()) ? "1" : "0")
                    .append(" | ").append(record.getSeverity())
                    .append(" | ").append(record.getActionName())
                    .append(" | ").append(nullToBlank(record.getDetails()))
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private HBox recoveryStepStrip() {
        HBox strip = new HBox(8);
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.getStyleClass().add("maintenance-step-strip");
        List<String> labels = List.of("1 Review", "2 Verify", "3 Execute");
        int current = switch (recoveryStage) {
            case VERIFY -> 2;
            case FINAL_REVIEW -> 3;
            default -> 1;
        };
        for (int index = 0; index < labels.size(); index++) {
            Label step = new Label(labels.get(index));
            step.getStyleClass().add(index + 1 == current ? "maintenance-step-badge-current" : "maintenance-step-badge");
            strip.getChildren().add(step);
        }
        return strip;
    }

    private String recoveryDescription() {
        if (latestRecoveryBackup == null) {
            return "No local recovery backup is available. Server download, rebuild and retry recovery actions are not shown because the current system has no remote sync connector.";
        }
        return "Restore latest local recovery backup\n\n"
                + "The current local workspace will be backed up and replaced with the latest verified local backup. "
                + "Server download, server rebuild and sync retry recovery actions are not shown because the current system has no remote sync connector.";
    }

    private String safeIntegrityStatus() {
        try {
            return database.databaseIntegrityStatus();
        } catch (RuntimeException exception) {
            return rootMessage(exception);
        }
    }

    private String safeSchemaVersion() {
        try {
            return database.schemaVersionSummary();
        } catch (RuntimeException exception) {
            return "Unavailable";
        }
    }

    private String latestBackupText() {
        try {
            BackupRecord backup = database.latestDailyBackupRecord();
            return backup == null ? "No backup recorded" : backup.getCreatedAt() + " | " + backup.getStatus();
        } catch (RuntimeException exception) {
            return "Unavailable";
        }
    }

    private String workspacePrompt() {
        SystemUser user = workspaceUserOrNull();
        return user == null ? "No active workspace" : user.getDisplayName() + " - Workspace " + user.getId();
    }

    private List<String> workspaceOptions() {
        return List.of(workspacePrompt());
    }

    private String workspaceOwnerText() {
        SystemUser user = workspaceUserOrNull();
        return user == null ? "No active workspace" : user.getDisplayName();
    }

    private String signedInUserText() {
        try {
            return UserSession.getAuthenticatedUser().getDisplayName();
        } catch (RuntimeException exception) {
            return "No signed-in user";
        }
    }

    private SystemUser workspaceUserOrNull() {
        try {
            return UserSession.getWorkspaceUser();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String shortChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return "Not available";
        }
        return checksum.length() <= 18 ? checksum : checksum.substring(0, 18);
    }

    private String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "No file";
        }
        return Path.of(path).getFileName().toString();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
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

    private String lines(String... values) {
        return String.join(System.lineSeparator(), values);
    }

    private SummaryRow row(String name, String value) {
        return new SummaryRow(name, value);
    }

    private ColumnSpec column(String title, double width) {
        return new ColumnSpec(title, width);
    }

    private record SummaryRow(String name, String value) {
    }

    private record ColumnSpec(String title, double width) {
    }

    private enum RecoveryStage {
        REVIEW,
        VERIFY,
        FINAL_REVIEW
    }

    private static final class SyncTableRow {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private final List<String> values;
        private final SystemLogRecord logRecord;

        private SyncTableRow(List<String> values) {
            this(values, null);
        }

        private SyncTableRow(List<String> values, SystemLogRecord logRecord) {
            this.values = values == null ? List.of() : List.copyOf(values);
            this.logRecord = logRecord;
        }

        private BooleanProperty selectedProperty() {
            return selected;
        }

        private boolean isSelected() {
            return selected.get();
        }

        private String value(int index) {
            return index >= 0 && index < values.size() ? values.get(index) : "";
        }

        private SystemLogRecord logRecord() {
            return logRecord;
        }
    }
}
