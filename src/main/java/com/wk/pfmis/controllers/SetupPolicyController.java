package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.SetupPolicyRecord;
import com.wk.pfmis.models.SystemLogRecord;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.awt.Desktop;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SetupPolicyController {
    private static final List<String> AREAS = List.of(
            "Smart Rules and Thresholds",
            "Financial Profile",
            "Alerts and Notifications",
            "Automation Schedules",
            "Report Preferences",
            "Session and Auto-Lock Settings",
            "Workspace Management",
            "Data Quality and Reconciliation",
            "Import and Export",
            "Archive and Restore",
            "Danger Zone"
    );

    @FXML private ComboBox<String> areaBox;
    @FXML private TextField itemNameField;
    @FXML private TextField configTypeField;
    @FXML private TextField conditionField;
    @FXML private TextField thresholdField;
    @FXML private ComboBox<String> severityBox;
    @FXML private TextField targetScreenField;
    @FXML private ComboBox<String> enabledBox;
    @FXML private TextArea recommendationArea;
    @FXML private TextArea notesArea;
    @FXML private Label selectedAreaLabel;
    @FXML private Label statusLabel;
    @FXML private Label conclusionLabel;
    @FXML private TextArea setupResultArea;
    @FXML private TableView<SetupPolicyRecord> policyTable;
    @FXML private TableColumn<SetupPolicyRecord, String> areaColumn;
    @FXML private TableColumn<SetupPolicyRecord, String> itemNameColumn;
    @FXML private TableColumn<SetupPolicyRecord, String> configTypeColumn;
    @FXML private TableColumn<SetupPolicyRecord, String> conditionColumn;
    @FXML private TableColumn<SetupPolicyRecord, String> thresholdColumn;
    @FXML private TableColumn<SetupPolicyRecord, String> severityColumn;
    @FXML private TableColumn<SetupPolicyRecord, String> targetScreenColumn;
    @FXML private TableColumn<SetupPolicyRecord, String> enabledColumn;
    @FXML private TableColumn<SetupPolicyRecord, String> updatedColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private int selectedRecordId;
    private String pendingArea;

    @FXML
    public void initialize() {
        configureChoices();
        configureTable();
        configureContextMenu();
        areaBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            refresh();
            clearForm();
        });
        policyTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                populateForm(selected);
            }
        });
        areaBox.getSelectionModel().select(pendingArea == null || pendingArea.isBlank() ? AREAS.getFirst() : pendingArea);
        refresh();
        clearForm();
    }

    public void selectArea(String area) {
        pendingArea = area;
        if (areaBox == null || area == null || area.isBlank()) {
            return;
        }
        areaBox.getSelectionModel().select(area);
        refresh();
        clearForm();
    }

    @FXML
    private void savePolicy() {
        try {
            database.saveSetupPolicyRecord(
                    selectedRecordId > 0 ? selectedRecordId : null,
                    currentArea(),
                    defaultedText(itemNameField, defaultFormFor(currentArea()).itemName()),
                    defaultedText(configTypeField, defaultFormFor(currentArea()).configType()),
                    text(conditionField),
                    text(thresholdField),
                    severityValue(),
                    text(recommendationArea),
                    text(targetScreenField),
                    "Enabled".equals(enabledBox.getValue()),
                    text(notesArea)
            );
            refresh();
            clearForm();
            statusLabel.setText("Setup item saved.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save setup item", exception);
        }
    }

    @FXML
    private void clearForm() {
        selectedRecordId = 0;
        if (policyTable != null) {
            policyTable.getSelectionModel().clearSelection();
        }
        FormDefault defaults = defaultFormFor(currentArea());
        itemNameField.setText(defaults.itemName());
        configTypeField.setText(defaults.configType());
        conditionField.setText(defaults.conditionText());
        thresholdField.setText(defaults.thresholdValue());
        severityBox.getSelectionModel().select(defaults.severity());
        targetScreenField.setText(defaults.targetScreen());
        enabledBox.getSelectionModel().select(defaults.enabled() ? "Enabled" : "Disabled");
        recommendationArea.setText(defaults.recommendation());
        notesArea.setText(defaults.notes());
        updateAreaText();
        statusLabel.setText("Ready.");
    }

    @FXML
    private void enableSelected() {
        updateSelectedStatus(true);
    }

    @FXML
    private void disableSelected() {
        updateSelectedStatus(false);
    }

    @FXML
    private void enableArea() {
        updateAreaStatus(true);
    }

    @FXML
    private void disableArea() {
        updateAreaStatus(false);
    }

    @FXML
    private void restoreDefaults() {
        try {
            database.restoreDefaultSetupPolicies(currentArea());
            refresh();
            clearForm();
            statusLabel.setText(currentArea() + " defaults restored.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to restore setup defaults", exception);
        }
    }

    @FXML
    private void runSelectedAction() {
        SetupPolicyRecord selected = policyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a setup item first.");
            return;
        }
        String area = selected.getPolicyArea();
        if ("Data Quality and Reconciliation".equals(area)) {
            runFullDataCheck();
            return;
        }
        if ("Danger Zone".equals(area)) {
            setupResultArea.setText("Guarded action preview only.\n\n"
                    + "Selected: " + selected.getItemName() + "\n"
                    + "Required controls: backup, password re-entry, confirmation phrase, reason, audit record, and integrity check.");
            statusLabel.setText("Danger-zone action was not executed.");
            return;
        }
        database.recordSystemLog("Setup", "Test Setup Policy", "INFO", selected.getPolicyArea() + ": " + selected.getItemName());
        setupResultArea.setText("Test completed for " + selected.getItemName() + ".\n\n"
                + "Area: " + selected.getPolicyArea() + "\n"
                + "Condition: " + selected.getConditionText() + "\n"
                + "Threshold: " + selected.getThresholdValue() + "\n"
                + "Recommendation: " + selected.getRecommendation());
        statusLabel.setText("Selected setup item tested.");
    }

    @FXML
    private void runFullDataCheck() {
        try {
            setupResultArea.setText(database.dataQualitySummary());
            statusLabel.setText("Data quality and reconciliation check completed.");
            updateAreaText();
        } catch (RuntimeException exception) {
            UiAlerts.error("Data quality check failed", exception);
        }
    }

    @FXML
    private void checkDatabaseIntegrity() {
        try {
            String integrity = database.databaseIntegrityStatus();
            setupResultArea.setText("Database integrity: " + integrity);
            statusLabel.setText("Database integrity check completed.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Database integrity check failed", exception);
        }
    }

    @FXML
    private void backupNow() {
        try {
            BackupRecord backup = database.createBackup(DatabaseHandler.defaultBackupDirectory(), "setup-safety-backup");
            setupResultArea.setText("Backup created:\n" + backup.getBackupFile() + "\nChecksum: " + backup.getChecksum());
            statusLabel.setText("Safety backup created.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to create backup", exception);
        }
    }

    @FXML
    private void openBackupFolder() {
        try {
            Files.createDirectories(DatabaseHandler.defaultBackupDirectory());
            if (!Desktop.isDesktopSupported()) {
                UiAlerts.info("Backup folder: " + DatabaseHandler.defaultBackupDirectory());
                return;
            }
            Desktop.getDesktop().open(DatabaseHandler.defaultBackupDirectory().toFile());
        } catch (Exception exception) {
            UiAlerts.error("Failed to open backup folder", exception);
        }
    }

    @FXML
    private void markAllReviewed() {
        database.recordSystemLog("Setup", "Mark Setup Alerts Reviewed", "INFO", currentArea() + " reviewed.");
        statusLabel.setText(currentArea() + " items marked reviewed in the audit log.");
    }

    @FXML
    private void viewRunHistory() {
        List<SystemLogRecord> records = database.listSystemLogHistory(30);
        StringBuilder builder = new StringBuilder("Recent setup and system events\n\n");
        for (SystemLogRecord record : records) {
            builder.append(record.getCreatedAt())
                    .append(" | ")
                    .append(record.getModuleName())
                    .append(" | ")
                    .append(record.getActionName())
                    .append(" | ")
                    .append(record.getSeverity())
                    .append('\n');
        }
        setupResultArea.setText(builder.toString());
        statusLabel.setText("Recent run history loaded.");
    }

    @FXML
    private void refresh() {
        if (policyTable == null) {
            return;
        }
        policyTable.setItems(FXCollections.observableArrayList(database.listSetupPolicyRecords(currentArea())));
        updateAreaText();
    }

    private void configureChoices() {
        areaBox.setItems(FXCollections.observableArrayList(AREAS));
        severityBox.setItems(FXCollections.observableArrayList("INFO", "WARNING", "CRITICAL"));
        enabledBox.setItems(FXCollections.observableArrayList("Enabled", "Disabled"));
    }

    private void configureTable() {
        areaColumn.setCellValueFactory(new PropertyValueFactory<>("policyArea"));
        itemNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        configTypeColumn.setCellValueFactory(new PropertyValueFactory<>("configType"));
        conditionColumn.setCellValueFactory(new PropertyValueFactory<>("conditionText"));
        thresholdColumn.setCellValueFactory(new PropertyValueFactory<>("thresholdValue"));
        severityColumn.setCellValueFactory(new PropertyValueFactory<>("severity"));
        targetScreenColumn.setCellValueFactory(new PropertyValueFactory<>("targetScreen"));
        enabledColumn.setCellValueFactory(new PropertyValueFactory<>("enabledDisplay"));
        updatedColumn.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));
    }

    private void configureContextMenu() {
        TableActions.installRowContextMenu(policyTable, this::policyMenuItems);
    }

    private List<MenuItem> policyMenuItems(SetupPolicyRecord record) {
        List<MenuItem> items = new ArrayList<>();
        items.add(TableActions.menuItem("Edit Setup Item", () -> populateForm(record)));
        items.add(TableActions.menuItem("Test / Run", this::runSelectedAction));
        items.add(TableActions.menuItem(record.isEnabled() ? "Disable" : "Enable", () -> {
            database.setSetupPolicyEnabled(record.getId(), !record.isEnabled());
            refresh();
        }));
        items.add(TableActions.separator());
        items.add(TableActions.copyRowItem(policyTable, record));
        items.add(TableActions.exportTableItem(policyTable, currentArea()));
        items.add(TableActions.printTableItem(policyTable, currentArea()));
        items.add(TableActions.refreshItem(this::refresh));
        return items;
    }

    private void populateForm(SetupPolicyRecord selected) {
        selectedRecordId = selected.getId();
        areaBox.getSelectionModel().select(selected.getPolicyArea());
        itemNameField.setText(selected.getItemName());
        configTypeField.setText(selected.getConfigType());
        conditionField.setText(selected.getConditionText());
        thresholdField.setText(selected.getThresholdValue());
        severityBox.getSelectionModel().select(selected.getSeverity());
        targetScreenField.setText(selected.getTargetScreen());
        enabledBox.getSelectionModel().select(selected.isEnabled() ? "Enabled" : "Disabled");
        recommendationArea.setText(selected.getRecommendation());
        notesArea.setText(selected.getNotes());
        statusLabel.setText("Editing " + selected.getItemName() + ".");
        updateAreaText();
    }

    private void updateSelectedStatus(boolean enabled) {
        SetupPolicyRecord selected = policyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a setup item first.");
            return;
        }
        database.setSetupPolicyEnabled(selected.getId(), enabled);
        refresh();
        statusLabel.setText(selected.getItemName() + " " + (enabled ? "enabled." : "disabled."));
    }

    private void updateAreaStatus(boolean enabled) {
        for (SetupPolicyRecord record : database.listSetupPolicyRecords(currentArea())) {
            database.setSetupPolicyEnabled(record.getId(), enabled);
        }
        refresh();
        statusLabel.setText(currentArea() + " items " + (enabled ? "enabled." : "disabled."));
    }

    private void updateAreaText() {
        if (selectedAreaLabel == null || conclusionLabel == null) {
            return;
        }
        String area = currentArea();
        int count = policyTable == null || policyTable.getItems() == null ? 0 : policyTable.getItems().size();
        selectedAreaLabel.setText(area + " | " + count + " configured item(s)");
        int dataIssues;
        try {
            dataIssues = database.unresolvedDataQualityIssueCount();
        } catch (RuntimeException exception) {
            dataIssues = 0;
        }
        conclusionLabel.setText("Setup now records decision rules, preferences, alerts, automation and governance controls. "
                + (dataIssues == 0
                ? "No unresolved data-quality issue count is currently reported."
                : dataIssues + " data-quality signal(s) need review before recommendations are fully reliable."));
    }

    private String currentArea() {
        String value = areaBox == null ? pendingArea : areaBox.getValue();
        return value == null || value.isBlank() ? AREAS.getFirst() : value.trim();
    }

    private String severityValue() {
        String value = severityBox.getValue();
        return value == null || value.isBlank() ? "INFO" : value.trim();
    }

    private String defaultedText(TextField field, String fallback) {
        String value = text(field);
        return value.isBlank() ? fallback : value;
    }

    private String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String text(TextArea area) {
        return area.getText() == null ? "" : area.getText().trim();
    }

    private FormDefault defaultFormFor(String area) {
        return switch (area) {
            case "Financial Profile" -> new FormDefault(
                    "Minimum cash reserve",
                    "Profile",
                    "Cash that must remain available",
                    "MWK 100,000",
                    "WARNING",
                    "Financial Position",
                    true,
                    "Treat the reserve as committed money when giving affordability advice.",
                    "Personalizes recommendations to the user's actual financial tolerance."
            );
            case "Alerts and Notifications" -> new FormDefault(
                    "Low account balance",
                    "Alert",
                    "Account balance falls below configured reserve",
                    "MWK 100,000",
                    "WARNING",
                    "Dashboard",
                    true,
                    "Notify the user before a low balance becomes a failed obligation.",
                    "Use dashboard, popup or email preference when notification routing is implemented."
            );
            case "Automation Schedules" -> new FormDefault(
                    "Daily financial analysis",
                    "Schedule",
                    "Run deterministic checks while the app is open",
                    "Daily",
                    "INFO",
                    "Dashboard",
                    true,
                    "Refresh data-quality, forecast and obligation warnings.",
                    "Scheduled tasks should never block manual financial entry."
            );
            case "Report Preferences" -> new FormDefault(
                    "Include evidence section",
                    "Preference",
                    "Show supporting records with analytical reports",
                    "Yes",
                    "INFO",
                    "Reports",
                    true,
                    "Expose why the report reached its conclusion.",
                    "Supports explainability and auditability."
            );
            case "Session and Auto-Lock Settings" -> new FormDefault(
                    "Inactivity timeout",
                    "Security",
                    "Lock application after inactivity",
                    "15 minutes",
                    "WARNING",
                    "My Account",
                    true,
                    "Require sign-in after idle periods.",
                    "Future enforcement setting."
            );
            case "Workspace Management" -> new FormDefault(
                    "Safety backup before maintenance",
                    "Governance",
                    "High-impact maintenance or import is requested",
                    "Required",
                    "WARNING",
                    "Backup and Restore",
                    true,
                    "Create a backup before operations that can change many records.",
                    "Workspace owner and super administrator duties remain separate."
            );
            case "Data Quality and Reconciliation" -> new FormDefault(
                    "Transactions without categories",
                    "Data Check",
                    "Income or expense has no category",
                    "0 allowed",
                    "WARNING",
                    "Transactions",
                    true,
                    "Assign categories before trusting spending analysis.",
                    "Run Full Data Check to see current issue counts."
            );
            case "Import and Export" -> new FormDefault(
                    "Transaction import template",
                    "Import",
                    "Imported file requires column mapping and validation",
                    "Required",
                    "INFO",
                    "Import and Export",
                    true,
                    "Preview, validate and quarantine invalid rows before commit.",
                    "Future import workflow rule."
            );
            case "Archive and Restore" -> new FormDefault(
                    "Deactivate used master data",
                    "Deletion Policy",
                    "Record is already referenced by financial history",
                    "Archive only",
                    "WARNING",
                    "Archive and Restore",
                    true,
                    "Deactivate, restore or merge used records instead of hard deleting.",
                    "Protects historical reporting."
            );
            case "Danger Zone" -> new FormDefault(
                    "Delete financial data",
                    "Guarded Action",
                    "Owner requests high-impact deletion",
                    "Password plus phrase",
                    "CRITICAL",
                    "Danger Zone",
                    false,
                    "Create backup, show impact, require password and confirmation phrase, then audit.",
                    "Disabled by default. Do not expose ambiguous database deletion."
            );
            default -> new FormDefault(
                    "Minimum account balance",
                    "Rule",
                    "Available balance falls below reserve",
                    "MWK 100,000",
                    "WARNING",
                    "Accounts",
                    true,
                    "Pause non-essential spending or move funds before new commitments.",
                    "Used by smart warnings and financial-health recommendations."
            );
        };
    }

    private record FormDefault(
            String itemName,
            String configType,
            String conditionText,
            String thresholdValue,
            String severity,
            String targetScreen,
            boolean enabled,
            String recommendation,
            String notes
    ) {
    }
}
