package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.PrivilegedActionService;
import com.wk.pfmis.security.RiskLevel;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;

public class RecordDisposalController {
    @FXML private Label statusLabel;
    @FXML private ComboBox<String> recordTypeBox;
    @FXML private ComboBox<String> workspaceBox;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TableView<DisposalCandidateRow> recordsTable;
    @FXML private TableColumn<DisposalCandidateRow, Boolean> selectColumn;
    @FXML private TableColumn<DisposalCandidateRow, String> recordIdColumn;
    @FXML private TableColumn<DisposalCandidateRow, String> dateColumn;
    @FXML private TableColumn<DisposalCandidateRow, String> descriptionColumn;
    @FXML private TableColumn<DisposalCandidateRow, String> statusColumn;
    @FXML private TableColumn<DisposalCandidateRow, String> amountColumn;
    @FXML private TableColumn<DisposalCandidateRow, String> eligibilityColumn;
    @FXML private TextArea resultArea;
    @FXML private Button removeButton;
    @FXML private Button restoreButton;
    @FXML private Button purgeButton;
    @FXML private VBox inlineActionPane;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final PrivilegedActionService privilegedActionService = PrivilegedActionService.getInstance();
    private final ObservableList<DisposalCandidateRow> candidates = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configureFilters();
        configureTable();
        searchRecords();
    }

    @FXML
    public void refresh() {
        searchRecords();
    }

    @FXML
    private void searchRecords() {
        if (!UserSession.isSuperAdmin()) {
            UiAlerts.info("Record Disposal is restricted to Super Administrators.");
            return;
        }
        try {
            String searchText = searchField.getText() == null ? "" : searchField.getText().trim();
            String exactRecordId = searchText.matches("\\d+") ? searchText : "";
            List<DatabaseHandler.RecordDisposalCandidateData> found = database.searchRecordDisposalCandidates(
                    recordTypeBox.getValue(),
                    exactRecordId,
                    normalizedStatus(),
                    null,
                    null,
                    searchText,
                    false
            );
            candidates.setAll(found.stream().map(DisposalCandidateRow::new).toList());
            updateButtonState();
            updateSummary();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to search records", exception);
        }
    }

    @FXML
    private void removeSelectedRecords() {
        List<DatabaseHandler.RecordDisposalCandidateData> selected = selectedData();
        if (selected.isEmpty()) {
            UiAlerts.info("Select at least one record first.");
            return;
        }
        DatabaseHandler.RecordDisposalImpact impact = database.previewRecordDisposalImpact(selected);
        if (impact.eligibleRecords() == 0) {
            resultArea.setText(lines(
                    "Selected: " + impact.selectedRecords(),
                    "Can be removed: 0",
                    "Protected: " + impact.blockedRecords(),
                    "",
                    "No selected record can be removed. Use the recommended action shown in the table."
            ));
            return;
        }
        showDeleteConfirmation(selected, impact);
    }

    private void executeRemoveSelectedRecords(List<DatabaseHandler.RecordDisposalCandidateData> selected, TextArea reasonArea) {
        try {
            List<DatabaseHandler.RecordDisposalCandidateData> removable = selected.stream()
                    .filter(candidate -> "ELIGIBLE".equals(candidate.eligibility()))
                    .toList();
            BackupRecord backup = database.createBackup(DatabaseHandler.defaultBackupDirectory(), "record-disposal-automatic-backup");
            database.validateBackup(Path.of(backup.getBackupFile()));
            DatabaseHandler.RecordDisposalExecutionResult result = database.executeRecordDisposal(
                    removable,
                    reasonOrDefault(reasonArea, "Selected record removal"),
                    "Inline Super Administrator confirmation",
                    backup.getBackupFile(),
                    backup.getChecksum()
            );
            resultArea.setText(lines(
                "Operation completed.",
                "Backup created",
                "Database verified",
                "Audit recorded",
                "",
                "Records selected: " + result.recordsRequested(),
                "Records deleted: " + result.recordsDisposed(),
                "Records skipped: " + result.recordsSkipped(),
                "Backup reference: " + result.backupReference(),
                "Completed by: " + result.executedBy(),
                "Completion time: " + result.executionTime()
            ));
            DataRefreshBus.notifyDataChanged();
            clearInlineAction();
            searchRecords();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to remove selected records", exception);
        }
    }

    @FXML
    private void restoreSelectedRecords() {
        List<DatabaseHandler.RecordDisposalCandidateData> selected = selectedData();
        if (selected.isEmpty()) {
            UiAlerts.info("Select deleted records to restore.");
            return;
        }
        showRestoreConfirmation(selected);
    }

    private void executeRestoreSelectedRecords(List<DatabaseHandler.RecordDisposalCandidateData> selected, TextArea reasonArea) {
        try {
            int restored = 0;
            String reason = reasonOrDefault(reasonArea, "Super Administrator restored from Deleted Records register.");
            for (DatabaseHandler.RecordDisposalCandidateData candidate : selected) {
                database.restoreDeletedRecord(candidate.recordType(), candidate.recordId(), reason);
                restored++;
            }
            DataRefreshBus.notifyDataChanged();
            clearInlineAction();
            searchRecords();
            resultArea.setText(restored + " deleted record(s) restored. Dependencies were revalidated and the action was audited.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to restore selected records", exception);
        }
    }

    @FXML
    private void permanentlyPurgeSelectedRecords() {
        List<DatabaseHandler.RecordDisposalCandidateData> selected = selectedData();
        if (selected.isEmpty()) {
            UiAlerts.info("Select soft-deleted records to purge.");
            return;
        }
        showPurgeConfirmation(selected);
    }

    private void executePermanentPurge(
            List<DatabaseHandler.RecordDisposalCandidateData> selected,
            SecurityVerificationPane securityPane
    ) {
        if (!securityPane.validateInput()) {
            return;
        }
        try {
            PrivilegedActionService.VerificationResult verification = privilegedActionService.verifyCurrentUser(
                    securityPane.passwordChars(),
                    RiskLevel.CRITICAL,
                    true
            );
            securityPane.markVerified(verification.statusText());
            BackupRecord backup = database.createBackup(DatabaseHandler.defaultBackupDirectory(), "permanent-purge-automatic-backup");
            database.validateBackup(Path.of(backup.getBackupFile()));
            int purged = 0;
            for (DatabaseHandler.RecordDisposalCandidateData candidate : selected) {
                database.permanentlyPurgeDeletedRecord(
                        candidate.recordType(),
                        candidate.recordId(),
                        securityPane.reason(),
                        "DELETE PERMANENTLY",
                        backup.getBackupFile()
                );
                purged++;
            }
            DataRefreshBus.notifyDataChanged();
            clearInlineAction();
            searchRecords();
            resultArea.setText(lines(
                    "Permanent purge completed.",
                    "Records purged: " + purged,
                    "Backup reference: " + backup.getBackupFile(),
                    "Audit recorded."
            ));
        } catch (RuntimeException exception) {
            securityPane.setError(rootMessage(exception));
        } finally {
            securityPane.clearPassword();
        }
    }

    @FXML
    private void cancel() {
        candidates.forEach(row -> row.setSelected(false));
        clearInlineAction();
        updateSummary();
    }

    private void configureFilters() {
        SystemUser workspace = UserSession.getWorkspaceUser();
        workspaceBox.setItems(FXCollections.observableArrayList(workspace.getDisplayName() + " (" + workspace.getUsername() + ")"));
        workspaceBox.getSelectionModel().selectFirst();
        workspaceBox.setDisable(true);
        recordTypeBox.setItems(FXCollections.observableArrayList(
                "Transaction",
                "Account",
                "Budget",
                "Project",
                "Project Activity",
                "Goal",
                "Goal Step",
                "Loan",
                "Repayment",
                "Community Savings",
                "Community Member",
                "Community Contribution",
                "Community Loan",
                "Community Repayment",
                "Community Payout Order",
                "Community Payout",
                "Community Share-out",
                "Category",
                "Payment Method",
                "Currency",
                "Import Record",
                "Report Input",
                "Other"
        ));
        recordTypeBox.setValue("Transaction");
        statusBox.setItems(FXCollections.observableArrayList("Draft / Archived / All eligible", "DRAFT", "ARCHIVED", "Deleted only", "All"));
        statusBox.setValue("Draft / Archived / All eligible");
        recordTypeBox.valueProperty().addListener((observable, oldValue, newValue) -> searchRecords());
        statusBox.valueProperty().addListener((observable, oldValue, newValue) -> searchRecords());
    }

    private void configureTable() {
        recordsTable.setEditable(true);
        selectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
        recordIdColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRecordId()));
        dateColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDate()));
        descriptionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDescription()));
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));
        amountColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAmount()));
        eligibilityColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRemovalStatus()));
        recordsTable.setItems(candidates);
    }

    private void updateSummary() {
        int found = candidates.size();
        long removable = candidates.stream().filter(row -> "Can be removed".equals(row.getRemovalStatus())).count();
        long protectedRecords = Math.max(0, found - removable);
        resultArea.setText(lines(
                "Records found: " + found,
                "Can be deleted or purged: " + removable,
                "Protected or safer action needed: " + protectedRecords
        ));
    }

    private void updateButtonState() {
        boolean deletedMode = "Deleted".equals(normalizedStatus());
        if (removeButton != null) {
            removeButton.setDisable(deletedMode);
        }
        if (restoreButton != null) {
            restoreButton.setDisable(!deletedMode);
        }
        if (purgeButton != null) {
            purgeButton.setDisable(!deletedMode);
        }
    }

    private List<DatabaseHandler.RecordDisposalCandidateData> selectedData() {
        return candidates.stream()
                .filter(DisposalCandidateRow::isSelected)
                .map(DisposalCandidateRow::data)
                .toList();
    }

    private void showDeleteConfirmation(
            List<DatabaseHandler.RecordDisposalCandidateData> selected,
            DatabaseHandler.RecordDisposalImpact impact
    ) {
        TextArea reasonArea = reasonArea("Reason for deleting these records from active views");
        CheckBox confirmation = confirmation("I understand these records will move to Deleted Records for audit and restoration.");
        Button delete = new Button("Delete Selected Records");
        delete.getStyleClass().add("maintenance-danger-button");
        delete.setOnAction(event -> {
            if (!confirmation.isSelected()) {
                resultArea.setText("Review the impact and tick the confirmation box before deleting records.");
                return;
            }
            executeRemoveSelectedRecords(selected, reasonArea);
        });
        renderInlineAction(
                "Delete Selected Records",
                lines(
                        "Selected: " + impact.selectedRecords(),
                        "Can be removed: " + impact.eligibleRecords(),
                        "Protected: " + impact.blockedRecords(),
                        "Account balance change: " + MoneyUtil.mwk(impact.balanceDifference()),
                        "Backup: Will be created and verified"
                ),
                reasonArea,
                confirmation,
                delete
        );
    }

    private void showRestoreConfirmation(List<DatabaseHandler.RecordDisposalCandidateData> selected) {
        TextArea reasonArea = reasonArea("Reason or note for restoring these records");
        CheckBox confirmation = confirmation("I understand dependencies will be revalidated before restore.");
        Button restore = new Button("Restore Selected");
        restore.getStyleClass().add("primary-button");
        restore.setOnAction(event -> {
            if (!confirmation.isSelected()) {
                resultArea.setText("Review the restore details and tick the confirmation box before continuing.");
                return;
            }
            executeRestoreSelectedRecords(selected, reasonArea);
        });
        renderInlineAction(
                "Restore Item",
                lines(
                        "Records selected: " + selected.size(),
                        "Restore destination: Original workspace",
                        "Dependency check: Will run before the record is restored",
                        "Audit: Restore event will be recorded"
                ),
                reasonArea,
                confirmation,
                restore
        );
    }

    private void showPurgeConfirmation(List<DatabaseHandler.RecordDisposalCandidateData> selected) {
        boolean passwordRequired = !privilegedActionService.hasValidSession(RiskLevel.CRITICAL, true);
        SecurityVerificationPane securityPane = new SecurityVerificationPane(
                "Purge deleted records permanently",
                lines(
                        "This item cannot be restored after purge.",
                        "Records selected: " + selected.size(),
                        "Backup: Will be created and verified before purge."
                ),
                RiskLevel.CRITICAL,
                "DELETE PERMANENTLY",
                passwordRequired,
                true,
                ""
        );
        Button purge = new Button("Purge Permanently");
        purge.getStyleClass().add("maintenance-danger-button");
        purge.setOnAction(event -> executePermanentPurge(selected, securityPane));
        renderInlineAction("Purge Item Permanently", "", securityPane, null, purge);
    }

    private void renderInlineAction(String title, String details, Node body, CheckBox confirmation, Button primaryButton) {
        clearInlineAction();
        Label heading = new Label(title);
        heading.getStyleClass().add("maintenance-step-title");
        Label detailLabel = new Label(details == null ? "" : details);
        detailLabel.setWrapText(true);
        detailLabel.getStyleClass().add("settings-status-text");
        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("secondary-button");
        cancelButton.setOnAction(event -> clearInlineAction());
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox actions = new HBox(8, cancelButton, spacer, primaryButton);
        actions.getStyleClass().add("maintenance-simple-actions");
        inlineActionPane.getChildren().setAll(heading);
        if (!detailLabel.getText().isBlank()) {
            inlineActionPane.getChildren().add(detailLabel);
        }
        inlineActionPane.getChildren().add(body);
        if (confirmation != null) {
            inlineActionPane.getChildren().add(confirmation);
        }
        inlineActionPane.getChildren().add(actions);
        inlineActionPane.setVisible(true);
        inlineActionPane.setManaged(true);
        resultArea.setText("Review the inline action panel before continuing.");
    }

    private TextArea reasonArea(String prompt) {
        TextArea area = new TextArea();
        area.setPromptText(prompt);
        area.setPrefRowCount(3);
        area.setWrapText(true);
        area.getStyleClass().add("maintenance-text-area");
        return area;
    }

    private CheckBox confirmation(String text) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.getStyleClass().add("maintenance-checkbox");
        return checkBox;
    }

    private void clearInlineAction() {
        if (inlineActionPane != null) {
            inlineActionPane.getChildren().clear();
            inlineActionPane.setVisible(false);
            inlineActionPane.setManaged(false);
        }
    }

    private String reasonOrDefault(TextArea reasonArea, String fallback) {
        if (reasonArea == null || reasonArea.getText().trim().isBlank()) {
            return fallback;
        }
        return reasonArea.getText().trim();
    }

    private String displayStatus(String eligibility, String recommendation) {
        if ("ELIGIBLE".equals(eligibility)) {
            return "Can be removed";
        }
        String lower = recommendation == null ? "" : recommendation.toLowerCase();
        if (lower.contains("cancel")) {
            return "Cancel instead";
        }
        if (lower.contains("archive")) {
            return "Archive instead";
        }
        return "Cannot be removed";
    }

    private String normalizedStatus() {
        String value = statusBox.getValue();
        if (value == null || value.startsWith("Draft /")) {
            return "All";
        }
        if ("Deleted only".equals(value)) {
            return "Deleted";
        }
        return value;
    }

    private String lines(String... values) {
        return String.join(System.lineSeparator(), values);
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

    public final class DisposalCandidateRow {
        private final DatabaseHandler.RecordDisposalCandidateData data;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

        private DisposalCandidateRow(DatabaseHandler.RecordDisposalCandidateData data) {
            this.data = data;
            selected.addListener((observable, oldValue, newValue) -> updateSummary());
        }

        public BooleanProperty selectedProperty() {
            return selected;
        }

        public boolean isSelected() {
            return selected.get();
        }

        public void setSelected(boolean value) {
            selected.set(value);
        }

        public DatabaseHandler.RecordDisposalCandidateData data() {
            return data;
        }

        public String getRecordId() {
            return String.valueOf(data.recordId());
        }

        public String getDate() {
            return data.recordDate();
        }

        public String getDescription() {
            return data.description();
        }

        public String getStatus() {
            return data.status();
        }

        public String getAmount() {
            return MoneyUtil.mwk(data.amount());
        }

        public String getRemovalStatus() {
            return displayStatus(data.eligibility(), data.recommendation());
        }
    }
}
