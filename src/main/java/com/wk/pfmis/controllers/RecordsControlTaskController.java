package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.DeletionRequestRecord;
import com.wk.pfmis.db.DatabaseHandler.TransactionCorrectionDraftRecord;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Budget;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.PaymentMethodRecord;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.SystemLogRecord;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class RecordsControlTaskController {
    private static final int PAGE_LIMIT = 100;

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private VBox contentContainer;
    @FXML private TextArea resultArea;
    @FXML private Button supportingActionButton;
    @FXML private Button deletionActionButton;
    @FXML private Button mainActionButton;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    private String currentArea = "Active Records";
    private ComboBox<String> recordTypeBox;
    private ComboBox<String> statusBox;
    private ComboBox<String> actionBox;
    private ComboBox<String> userBox;
    private ComboBox<String> requestTypeBox;
    private TextField searchField;
    private TableView<RecordRow> table;
    private List<RecordRow> currentRows = List.of();

    @FXML
    public void initialize() {
        selectArea(currentArea);
    }

    public void selectArea(String area) {
        currentArea = area == null || area.isBlank() ? "Active Records" : area.trim();
        render();
    }

    public void refresh() {
        search();
    }

    @FXML
    private void runSupportingAction() {
        switch (currentArea) {
            case "Draft Records" -> openDraft();
            case "Frozen Records", "Cancelled and Reversed", "Archived Records" -> viewDetails();
            case "Correction Requests" -> viewRequest();
            default -> viewRecord();
        }
    }

    @FXML
    private void runMainAction() {
        switch (currentArea) {
            case "Draft Records" -> postSelected();
            case "Frozen Records" -> unfreezeSelected();
            case "Cancelled and Reversed" -> createCorrectedRecord();
            case "Archived Records" -> restoreSelected();
            case "Correction Requests" -> reviewSelected();
            default -> freezeSelected();
        }
    }

    @FXML
    private void runDeletionAction() {
        RecordRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select a record first.");
            return;
        }
        if ("Correction Request".equals(row.recordType()) || "Deletion Request".equals(row.recordType())) {
            resultArea.setText("Open the request and use Review Selected.");
            return;
        }
        Optional<String> reason = reasonDialog(
                UserSession.isSuperAdmin() ? "Delete selected record?" : "Request deletion?",
                deletionPrompt(row),
                UserSession.isSuperAdmin() ? "Delete Record" : "Submit Request"
        );
        reason.ifPresent(value -> {
            try {
                if (UserSession.isSuperAdmin()) {
                    database.softDeleteRecord(row.recordType(), row.recordId(), value);
                    DataRefreshBus.notifyDataChanged();
                    search();
                    resultArea.setText(lines(
                            "Record deleted.",
                            "",
                            "The record was removed from normal active views and retained in the Deleted Records register."
                    ));
                } else {
                    int requestId = database.requestRecordDeletion(row.recordType(), row.recordId(), value, row.detail());
                    DataRecordsSectionController.rememberTab("Records Control", "correctionRequestsTab");
                    search();
                    resultArea.setText(lines(
                            "Deletion request submitted.",
                            "",
                            "Request #" + requestId + " is pending Super Administrator review."
                    ));
                }
            } catch (RuntimeException exception) {
                UiAlerts.error(UserSession.isSuperAdmin() ? "Failed to delete record" : "Failed to submit deletion request", exception);
            }
        });
    }

    private void render() {
        contentContainer.getChildren().clear();
        table = null;
        currentRows = List.of();
        configureTitleAndButtons();
        switch (currentArea) {
            case "Draft Records" -> renderDrafts();
            case "Frozen Records" -> renderFrozen();
            case "Cancelled and Reversed" -> renderCancelledReversed();
            case "Archived Records" -> renderArchived();
            case "Correction Requests" -> renderCorrections();
            default -> renderActive();
        }
        search();
    }

    private void configureTitleAndButtons() {
        switch (currentArea) {
            case "Draft Records" -> {
                titleLabel.setText("Draft Records");
                subtitleLabel.setText("Open saved records that are not final, or post a selected draft after validation.");
                supportingActionButton.setText("Open Draft");
                mainActionButton.setText("Post Selected");
            }
            case "Frozen Records" -> {
                titleLabel.setText("Frozen Records");
                subtitleLabel.setText("Review valid records that are temporarily locked.");
                supportingActionButton.setText("View Details");
                mainActionButton.setText(UserSession.isSuperAdmin() ? "Unfreeze Selected" : "Request Unfreeze");
            }
            case "Cancelled and Reversed" -> {
                titleLabel.setText("Cancelled and Reversed Records");
                subtitleLabel.setText("Review records whose original financial effect was cancelled or reversed.");
                supportingActionButton.setText("View Details");
                mainActionButton.setText("Create Corrected Record");
            }
            case "Archived Records" -> {
                titleLabel.setText("Archived Records");
                subtitleLabel.setText("Review old records that are hidden from routine use but kept for history.");
                supportingActionButton.setText("View Details");
                mainActionButton.setText("Restore Selected");
            }
            case "Correction Requests" -> {
                titleLabel.setText("Correction Requests");
                subtitleLabel.setText("Review requests to correct, freeze, unfreeze, cancel, reverse, archive or restore protected records.");
                supportingActionButton.setText("View Request");
                mainActionButton.setText("Review Selected");
            }
            default -> {
                titleLabel.setText("Active Records");
                subtitleLabel.setText("Find valid records currently used by the system.");
                supportingActionButton.setText("View Record");
                mainActionButton.setText("Freeze Selected");
            }
        }
        supportingActionButton.getStyleClass().setAll("secondary-button");
        mainActionButton.getStyleClass().setAll("primary-button");
        if (deletionActionButton != null) {
            deletionActionButton.setText(UserSession.isSuperAdmin() ? "Delete Selected" : "Request Deletion");
            deletionActionButton.getStyleClass().setAll(UserSession.isSuperAdmin() ? "danger-button" : "secondary-button");
            deletionActionButton.setVisible(!"Correction Requests".equals(currentArea));
            deletionActionButton.setManaged(!"Correction Requests".equals(currentArea));
        }
    }

    private void renderActive() {
        recordTypeBox = combo(recordTypeOptions(), "All record types");
        statusBox = combo(List.of("Active / Posted", "Active", "Posted", "Completed"), "Active / Posted");
        searchField = textField("ID, name, reference or description");
        contentContainer.getChildren().add(filters(
                field("Record type", recordTypeBox),
                field("Status", statusBox),
                wideField("Search", searchField),
                searchButton("Search")
        ));
        table = table("No active records were found.",
                List.of(column("Record", 220), column("Type", 150), column("Date", 140), column("Status", 150), column("Amount", 150)));
        contentContainer.getChildren().add(table);
    }

    private void renderDrafts() {
        recordTypeBox = combo(recordTypeOptions(), "All record types");
        userBox = combo(List.of("All users", signedInUserText(), "System"), "All users");
        searchField = textField("ID or description");
        contentContainer.getChildren().add(filters(
                field("Record type", recordTypeBox),
                field("Created by", userBox),
                wideField("Search", searchField),
                searchButton("Search")
        ));
        table = table("No draft records were found.",
                List.of(column("Draft", 220), column("Type", 150), column("Created by", 160), column("Created date", 140), column("Status", 150)));
        contentContainer.getChildren().add(table);
    }

    private void renderFrozen() {
        recordTypeBox = combo(recordTypeOptions(), "All types");
        userBox = combo(List.of("All administrators", signedInUserText(), "System"), "All administrators");
        searchField = textField("ID or description");
        contentContainer.getChildren().add(filters(
                field("Record type", recordTypeBox),
                field("Frozen by", userBox),
                wideField("Search", searchField),
                searchButton("Search")
        ));
        table = table("No frozen records were found.",
                List.of(column("Record", 220), column("Type", 150), column("Frozen by", 170), column("Reason", 260), column("Frozen date", 140)));
        contentContainer.getChildren().add(table);
    }

    private void renderCancelledReversed() {
        recordTypeBox = combo(recordTypeOptions(), "All types");
        actionBox = combo(List.of("Cancelled / Reversed", "Cancelled", "Reversed"), "Cancelled / Reversed");
        searchField = textField("ID, reference or description");
        contentContainer.getChildren().add(filters(
                field("Record type", recordTypeBox),
                field("Action", actionBox),
                wideField("Search", searchField),
                searchButton("Search")
        ));
        table = table("No cancelled or reversed records were found.",
                List.of(column("Record", 220), column("Type", 150), column("Original amount", 160), column("Action", 140), column("Reason", 260), column("Action date", 140)));
        contentContainer.getChildren().add(table);
    }

    private void renderArchived() {
        recordTypeBox = combo(recordTypeOptions(), "All types");
        userBox = combo(List.of("All users", signedInUserText(), "System"), "All users");
        searchField = textField("ID or description");
        contentContainer.getChildren().add(filters(
                field("Record type", recordTypeBox),
                field("Archived by", userBox),
                wideField("Search", searchField),
                searchButton("Search")
        ));
        table = table("No archived records were found.",
                List.of(column("Record", 220), column("Type", 150), column("Archived date", 150), column("Archived by", 170), column("Reason", 300)));
        contentContainer.getChildren().add(table);
    }

    private void renderCorrections() {
        requestTypeBox = combo(List.of("All request types", "Delete", "Freeze", "Unfreeze", "Post", "Corrected Draft", "Restore", "Review"), "All request types");
        statusBox = combo(List.of("Pending", "All statuses", "Approved", "Rejected", "Recorded"), "Pending");
        userBox = combo(List.of("All users", signedInUserText(), "System"), "All users");
        searchField = textField("Record ID, action or reason");
        contentContainer.getChildren().add(filters(
                field("Request type", requestTypeBox),
                field("Status", statusBox),
                field("Requested by", userBox),
                wideField("Search", searchField),
                searchButton("Search")
        ));
        table = table("No correction requests were found.",
                List.of(column("Request", 160), column("Record", 220), column("Requested action", 220), column("Requested by", 160), column("Reason", 280), column("Status", 130)));
        contentContainer.getChildren().add(table);
    }

    private void search() {
        if (table == null) {
            return;
        }
        currentRows = switch (currentArea) {
            case "Draft Records" -> draftRows();
            case "Frozen Records" -> frozenRows();
            case "Cancelled and Reversed" -> cancelledReversedRows();
            case "Archived Records" -> archivedRows();
            case "Correction Requests" -> correctionRows();
            default -> activeRows();
        };
        table.getItems().setAll(currentRows);
        if (!currentRows.isEmpty()) {
            table.getSelectionModel().selectFirst();
        }
        supportingActionButton.setDisable(currentRows.isEmpty());
        mainActionButton.setDisable(currentRows.isEmpty());
        if (deletionActionButton != null) {
            deletionActionButton.setDisable(currentRows.isEmpty());
        }
        resultArea.setText(summaryMessage());
    }

    private List<RecordRow> activeRows() {
        return allItems().stream()
                .filter(this::isActiveItem)
                .filter(this::matchesRecordType)
                .filter(item -> matchesActiveStatus(item.status()))
                .filter(this::matchesSearch)
                .sorted(newestItems())
                .limit(PAGE_LIMIT)
                .map(item -> rowFor(item, row(item.name(), item.type(), safe(item.date(), "-"), item.status(), amountText(item.amount()))))
                .toList();
    }

    private List<RecordRow> draftRows() {
        return allItems().stream()
                .filter(this::isDraftItem)
                .filter(this::matchesRecordType)
                .filter(this::matchesSearch)
                .sorted(newestItems())
                .limit(PAGE_LIMIT)
                .map(item -> rowFor(item, row(item.name(), item.type(), "System", safe(item.date(), "-"), item.status())))
                .toList();
    }

    private List<RecordRow> frozenRows() {
        return allItems().stream()
                .filter(item -> "FROZEN".equalsIgnoreCase(item.status()))
                .filter(this::matchesRecordType)
                .filter(this::matchesSearch)
                .sorted(newestItems())
                .limit(PAGE_LIMIT)
                .map(item -> rowFor(item, row(item.name(), item.type(), "System", "See audit history", safe(item.date(), "-"))))
                .toList();
    }

    private List<RecordRow> cancelledReversedRows() {
        return allItems().stream()
                .filter(item -> isStatus(item, "CANCELLED") || isStatus(item, "REVERSED"))
                .filter(this::matchesRecordType)
                .filter(item -> {
                    String selected = selected(actionBox);
                    return isAll(selected) || selected.startsWith("Cancelled /")
                            || item.status().toLowerCase(Locale.ENGLISH).contains(selected.toLowerCase(Locale.ENGLISH));
                })
                .filter(this::matchesSearch)
                .sorted(newestItems())
                .limit(PAGE_LIMIT)
                .map(item -> rowFor(item, row(item.name(), item.type(), amountText(item.amount()), titleCase(item.status()), "See audit history", safe(item.date(), "-"))))
                .toList();
    }

    private List<RecordRow> archivedRows() {
        return allItems().stream()
                .filter(this::isArchivedItem)
                .filter(this::matchesRecordType)
                .filter(this::matchesSearch)
                .sorted(newestItems())
                .limit(PAGE_LIMIT)
                .map(item -> rowFor(item, row(item.name(), item.type(), safe(item.date(), "-"), "System", archiveReason(item))))
                .toList();
    }

    private List<RecordRow> correctionRows() {
        String selectedType = selected(requestTypeBox);
        String selectedStatus = selected(statusBox);
        List<RecordRow> rows = new ArrayList<>(database.listDeletionRequests(selectedStatus, PAGE_LIMIT).stream()
                .filter(request -> isAll(selectedType) || selectedType.toLowerCase(Locale.ENGLISH).contains("delete"))
                .map(this::deletionRequestRow)
                .filter(this::matchesSearch)
                .toList());
        rows.addAll(database.listSystemLogHistory(400).stream()
                .filter(this::isCorrectionLog)
                .filter(log -> isAll(selectedType) || log.getActionName().toLowerCase(Locale.ENGLISH).contains(selectedType.toLowerCase(Locale.ENGLISH)))
                .filter(log -> "Pending".equals(selectedStatus) ? isPendingRequest(log) : matchesCombo(correctionStatus(log), selectedStatus))
                .map(this::correctionRow)
                .filter(this::matchesSearch)
                .limit(PAGE_LIMIT)
                .toList());
        return rows.stream().limit(PAGE_LIMIT).toList();
    }

    private List<RecordItem> allItems() {
        List<RecordItem> items = new ArrayList<>();
        for (FinanceTransaction transaction : database.listRecentTransactions(1000)) {
            items.add(new RecordItem(
                    "Transaction",
                    transaction.getId(),
                    "Transaction #" + transaction.getId(),
                    transaction.getTransactionDate(),
                    safe(transaction.getTransactionStatus(), "COMPLETED"),
                    transaction.getAmount(),
                    transactionDetail(transaction),
                    transaction
            ));
        }
        for (TransactionCorrectionDraftRecord draft : database.listTransactionCorrectionDrafts(300)) {
            items.add(new RecordItem(
                    "Correction Draft",
                    draft.id(),
                    "Correction Draft #" + draft.id(),
                    draft.transactionDate(),
                    draft.status(),
                    draft.amount(),
                    correctionDraftDetail(draft),
                    draft
            ));
        }
        for (Account account : database.listAccounts()) {
            items.add(new RecordItem(
                    "Account",
                    account.getId(),
                    account.getAccountName() + " #" + account.getId(),
                    account.getCreatedAt(),
                    safe(account.getStatus(), "ACTIVE"),
                    account.getCurrentBalance(),
                    accountDetail(account),
                    account
            ));
        }
        for (Budget budget : database.listBudgets()) {
            items.add(new RecordItem(
                    "Budget",
                    budget.getId(),
                    budget.getBudgetName() + " #" + budget.getId(),
                    budget.getBudgetMonth(),
                    safe(budget.getStatus(), "PLANNED"),
                    budget.getAmountLimit(),
                    budgetDetail(budget),
                    budget
            ));
        }
        for (Project project : database.listProjects()) {
            items.add(new RecordItem(
                    "Project",
                    project.getId(),
                    project.getProjectName() + " #" + project.getId(),
                    safe(project.getStartDate(), project.getEndDate()),
                    safe(project.getStatus(), "ACTIVE"),
                    project.getPlannedBudget(),
                    projectDetail(project),
                    project
            ));
        }
        for (Goal goal : database.listGoals()) {
            items.add(new RecordItem(
                    "Goal",
                    goal.getId(),
                    goal.getGoalName() + " #" + goal.getId(),
                    goal.getTargetDate(),
                    safe(goal.getStatus(), "ACTIVE"),
                    goal.getTargetAmount(),
                    goalDetail(goal),
                    goal
            ));
        }
        for (PaymentMethodRecord method : database.listPaymentMethods()) {
            items.add(new RecordItem(
                    "Payment Method",
                    method.getId(),
                    method.getMethodName() + " #" + method.getId(),
                    method.getLastUsed(),
                    safe(method.getStatus(), "ACTIVE"),
                    null,
                    paymentMethodDetail(method),
                    method
            ));
        }
        return items;
    }

    private RecordRow rowFor(RecordItem item, List<String> values) {
        return new RecordRow(values, item.detail(), item.type(), item.id(), item.name(), item.status(), item.amount(), item.source());
    }

    private RecordRow correctionRow(SystemLogRecord log) {
        String record = recordFromDetails(log.getDetails());
        String status = correctionStatus(log);
        String detail = lines(
                "Request ID: " + log.getId(),
                "Requested action: " + log.getActionName(),
                "Record: " + record,
                "Requested by: System",
                "Reason: " + safe(log.getDetails(), "-"),
                "Status: " + status,
                "Date: " + safe(log.getCreatedAt(), "-")
        );
        return new RecordRow(
                row("Request #" + log.getId(), record, log.getActionName(), "System", firstLine(log.getDetails()), status),
                detail,
                "Correction Request",
                log.getId(),
                "Request #" + log.getId(),
                status,
                null,
                log
        );
    }

    private RecordRow deletionRequestRow(DeletionRequestRecord request) {
        String detail = lines(
                "Request ID: " + request.id(),
                "Requested action: Delete",
                "Record: " + request.recordDescription(),
                "Record type: " + request.recordType(),
                "Record ID: " + request.recordId(),
                "Requested by: " + safe(request.requestedBy(), "System"),
                "Current status: " + safe(request.currentStatus(), "-"),
                "Related records: " + request.dependencies(),
                "Reason: " + safe(request.reason(), "-"),
                "Supporting notes: " + safe(request.supportingNotes(), "-"),
                "Approval status: " + safe(request.approvalStatus(), "-"),
                "Review notes: " + safe(request.reviewNotes(), "-")
        );
        return new RecordRow(
                row("Deletion Request #" + request.id(), request.recordDescription(), "Delete", safe(request.requestedBy(), "System"), firstLine(request.reason()), request.approvalStatus()),
                detail,
                "Deletion Request",
                request.id(),
                "Deletion Request #" + request.id(),
                request.approvalStatus(),
                null,
                request
        );
    }

    private void viewRecord() {
        showSelectedDetails("Select a record first.");
    }

    private void openDraft() {
        showSelectedDetails("Select a draft first.");
    }

    private void viewDetails() {
        showSelectedDetails("Select a record first.");
    }

    private void viewRequest() {
        showSelectedDetails("Select a correction request first.");
    }

    private void showSelectedDetails(String emptyMessage) {
        RecordRow row = selectedRow();
        resultArea.setText(row == null ? emptyMessage : row.detail());
    }

    private void freezeSelected() {
        RecordRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select an active record first.");
            return;
        }
        Optional<String> reason = reasonDialog(
                "Freeze this record?",
                "The record will remain in financial reports and balances, but users will not be able to change it.",
                "Freeze Record"
        );
        reason.ifPresent(value -> {
            if (!UserSession.isAdminOrSuperAdmin() || !supportsStatus(row, "FROZEN")) {
                requestLifecycleAction("Freeze", row, value);
                return;
            }
            updateLifecycle(row, "FROZEN", value, "Record frozen.");
        });
    }

    private void postSelected() {
        RecordRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select a draft first.");
            return;
        }
        if ("Correction Draft".equals(row.recordType())) {
            resultArea.setText("Correction drafts are review records. Post a corrected transaction through the transaction workflow after approval.");
            return;
        }
        if (!UserSession.isAdminOrSuperAdmin()) {
            requestLifecycleAction("Post", row, "User requested draft posting.");
            return;
        }
        Optional<String> reason = confirmDialog(
                "Post this record?",
                "After posting, it may affect balances and reports.",
                "Post Record"
        );
        reason.ifPresent(value -> updateLifecycle(row, postedStatus(row), value, "Record posted."));
    }

    private void unfreezeSelected() {
        RecordRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select a frozen record first.");
            return;
        }
        Optional<String> reason = reasonDialog(
                UserSession.isSuperAdmin() ? "Unfreeze this record?" : "Request unfreeze?",
                "A reason is required.",
                UserSession.isSuperAdmin() ? "Unfreeze Record" : "Request Unfreeze"
        );
        reason.ifPresent(value -> {
            if (!UserSession.isSuperAdmin()) {
                requestLifecycleAction("Unfreeze", row, value);
                return;
            }
            updateLifecycle(row, activeStatus(row), value, "Record unfrozen.");
        });
    }

    private void createCorrectedRecord() {
        RecordRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select a cancelled or reversed record first.");
            return;
        }
        Optional<String> reason = reasonDialog(
                "Create corrected record?",
                "A new draft will be created. The original record will not be overwritten.",
                "Create Draft"
        );
        reason.ifPresent(value -> {
            if (!UserSession.isAdminOrSuperAdmin()) {
                requestLifecycleAction("Corrected Draft", row, value);
                return;
            }
            if (row.source() instanceof FinanceTransaction transaction) {
                int draftId = database.createCorrectedTransactionDraft(transaction.getId(), value);
                DataRefreshBus.notifyDataChanged();
                search();
                resultArea.setText("Corrected draft created.\n\nCorrection Draft #" + draftId);
            } else {
                requestLifecycleAction("Corrected Draft", row, value);
            }
        });
    }

    private void restoreSelected() {
        RecordRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select an archived record first.");
            return;
        }
        Optional<String> reason = reasonDialog(
                "Restore this archived record?",
                "It will become available for normal use again.",
                "Restore Record"
        );
        reason.ifPresent(value -> {
            if (!UserSession.isAdminOrSuperAdmin()) {
                requestLifecycleAction("Restore", row, value);
                return;
            }
            updateLifecycle(row, activeStatus(row), value, "Record restored.");
        });
    }

    private void reviewSelected() {
        RecordRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select a correction request first.");
            return;
        }
        if (!UserSession.isAdminOrSuperAdmin()) {
            resultArea.setText("Only Administrators and Super Administrators can review correction requests.");
            return;
        }
        if (row.source() instanceof DeletionRequestRecord request) {
            if (!UserSession.isSuperAdmin()) {
                resultArea.setText("Only a Super Administrator can approve or reject deletion requests.");
                return;
            }
            Optional<ReviewDecision> decision = reviewDialog(row);
            decision.ifPresent(value -> {
                try {
                    database.reviewDeletionRequest(request.id(), "Approved".equalsIgnoreCase(value.decision()) ? "APPROVED" : "REJECTED", value.comment());
                    DataRefreshBus.notifyDataChanged();
                    search();
                    resultArea.setText("Deletion request reviewed.\n\nDecision: " + value.decision());
                } catch (RuntimeException exception) {
                    UiAlerts.error("Failed to review deletion request", exception);
                }
            });
            return;
        }
        Optional<ReviewDecision> decision = reviewDialog(row);
        decision.ifPresent(value -> {
            database.recordSystemLog(
                    "Data And Records",
                    "Correction Request Reviewed",
                    "WARNING",
                    row.name() + " decision: " + value.decision() + ". Comment: " + value.comment()
            );
            search();
            resultArea.setText("Correction request reviewed.\n\nDecision: " + value.decision());
        });
    }

    private void updateLifecycle(RecordRow row, String status, String reason, String successMessage) {
        try {
            database.updateRecordLifecycleStatus(row.recordType(), row.recordId(), status, reason);
            DataRefreshBus.notifyDataChanged();
            search();
            resultArea.setText(lines(
                    successMessage,
                    "",
                    row.recordType() + " " + row.recordId() + " is now " + status + "."
            ));
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update record", exception);
        }
    }

    private void requestLifecycleAction(String action, RecordRow row, String reason) {
        database.recordSystemLog(
                "Data And Records",
                action + " Request",
                "WARNING",
                row.recordType() + " " + row.recordId() + ". Reason: " + reason
        );
        DataRecordsSectionController.rememberTab("Records Control", "correctionRequestsTab");
        resultArea.setText(lines(
                action + " request recorded.",
                "",
                "A Super Administrator can review it under Corrections."
        ));
        search();
    }

    private String deletionPrompt(RecordRow row) {
        return lines(
                UserSession.isSuperAdmin()
                        ? "This Super Administrator action performs soft deletion only. The record remains available for audit and restoration."
                        : "This request will not delete automatically. A Super Administrator must review it.",
                "",
                "Record: " + row.name(),
                "Type: " + row.recordType(),
                "ID: " + row.recordId(),
                "Current status: " + safe(row.status(), "-"),
                "",
                "Enter a reason to continue."
        );
    }

    private Optional<String> confirmDialog(String header, String message, String confirmText) {
        return UiAlerts.confirm(header, message)
                ? Optional.of("Confirmed from Records Control.")
                : Optional.empty();
    }

    private Optional<String> reasonDialog(String header, String message, String confirmText) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("PFMIS");
        dialog.setHeaderText(header);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType confirm = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(cancel, confirm);
        TextField reasonField = new TextField();
        reasonField.setPromptText("Required reason");
        reasonField.getStyleClass().add("maintenance-input");
        VBox content = new VBox(8, new Label(message), new Label("Reason"), reasonField);
        dialog.getDialogPane().setContent(content);
        Node confirmButton = dialog.getDialogPane().lookupButton(confirm);
        confirmButton.setDisable(true);
        reasonField.textProperty().addListener((observable, oldValue, newValue) ->
                confirmButton.setDisable(newValue == null || newValue.trim().isBlank()));
        dialog.setResultConverter(buttonType -> buttonType == confirm ? reasonField.getText().trim() : null);
        return dialog.showAndWait();
    }

    private Optional<ReviewDecision> reviewDialog(RecordRow row) {
        Dialog<ReviewDecision> dialog = new Dialog<>();
        dialog.setTitle("PFMIS");
        dialog.setHeaderText("Review Correction Request");
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType save = new ButtonType("Save Decision", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(cancel, save);

        ToggleGroup group = new ToggleGroup();
        RadioButton approveButton = new RadioButton("Approve");
        RadioButton rejectButton = new RadioButton("Reject");
        approveButton.setToggleGroup(group);
        rejectButton.setToggleGroup(group);
        approveButton.setSelected(true);
        TextArea commentArea = new TextArea();
        commentArea.setPromptText("Required comment");
        commentArea.setPrefRowCount(3);
        commentArea.setWrapText(true);
        VBox content = new VBox(8,
                new Label(row.detail()),
                new Label("Decision"),
                approveButton,
                rejectButton,
                new Label("Comment"),
                commentArea);
        dialog.getDialogPane().setContent(content);
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.setDisable(true);
        commentArea.textProperty().addListener((observable, oldValue, newValue) ->
                saveButton.setDisable(newValue == null || newValue.trim().isBlank()));
        dialog.setResultConverter(buttonType -> {
            if (buttonType != save) {
                return null;
            }
            String decision = approveButton.isSelected() ? "Approved" : "Rejected";
            return new ReviewDecision(decision, commentArea.getText().trim());
        });
        return dialog.showAndWait();
    }

    private String postedStatus(RecordRow row) {
        return switch (row.recordType()) {
            case "Transaction" -> "COMPLETED";
            case "Budget" -> "ON_BUDGET";
            default -> "ACTIVE";
        };
    }

    private String activeStatus(RecordRow row) {
        return switch (row.recordType()) {
            case "Transaction" -> "COMPLETED";
            case "Budget" -> "ON_BUDGET";
            default -> "ACTIVE";
        };
    }

    private boolean supportsStatus(RecordRow row, String status) {
        return !"Payment Method".equals(row.recordType()) || "ACTIVE".equals(status) || "INACTIVE".equals(status);
    }

    private boolean isActiveItem(RecordItem item) {
        if ("Transaction".equals(item.type())) {
            return isStatus(item, "COMPLETED") || isStatus(item, "CLEARED") || isStatus(item, "PARTIALLY_CLEARED");
        }
        if ("Budget".equals(item.type())) {
            return isStatus(item, "ON_BUDGET") || isStatus(item, "ACTIVE");
        }
        return isStatus(item, "ACTIVE");
    }

    private boolean isDraftItem(RecordItem item) {
        return isStatus(item, "OPEN") || isStatus(item, "DRAFT") || isStatus(item, "PLANNED");
    }

    private boolean isArchivedItem(RecordItem item) {
        if ("Transaction".equals(item.type())) {
            return isStatus(item, "ARCHIVED");
        }
        return isStatus(item, "ARCHIVED")
                || isStatus(item, "INACTIVE")
                || isStatus(item, "CLOSED")
                || (("Project".equals(item.type()) || "Goal".equals(item.type())) && isStatus(item, "COMPLETED"));
    }

    private boolean isStatus(RecordItem item, String status) {
        return status.equalsIgnoreCase(safe(item.status(), ""));
    }

    private boolean matchesActiveStatus(String status) {
        String selected = selected(statusBox);
        if (selected == null || selected.isBlank() || selected.startsWith("Active /")) {
            return true;
        }
        String clean = status == null ? "" : titleCase(status);
        return clean.toLowerCase(Locale.ENGLISH).contains(selected.toLowerCase(Locale.ENGLISH))
                || status.toLowerCase(Locale.ENGLISH).contains(selected.toLowerCase(Locale.ENGLISH));
    }

    private boolean matchesRecordType(RecordItem item) {
        return matchesRecordType(item.type());
    }

    private boolean matchesRecordType(String type) {
        String selected = selected(recordTypeBox);
        if (isAll(selected)) {
            return true;
        }
        String singular = selected.endsWith("s") ? selected.substring(0, selected.length() - 1) : selected;
        return type.toLowerCase(Locale.ENGLISH).contains(singular.toLowerCase(Locale.ENGLISH));
    }

    private boolean matchesSearch(RecordItem item) {
        String search = text(searchField).toLowerCase(Locale.ENGLISH);
        return search.isBlank() || (item.name() + " " + item.type() + " " + item.status() + " " + item.detail())
                .toLowerCase(Locale.ENGLISH)
                .contains(search);
    }

    private boolean matchesSearch(RecordRow row) {
        String search = text(searchField).toLowerCase(Locale.ENGLISH);
        return search.isBlank() || row.searchText().toLowerCase(Locale.ENGLISH).contains(search);
    }

    private boolean matchesCombo(String value, String selected) {
        if (isAll(selected)) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ENGLISH).contains(selected.toLowerCase(Locale.ENGLISH));
    }

    private boolean isAll(String value) {
        return value == null
                || value.isBlank()
                || value.toLowerCase(Locale.ENGLISH).startsWith("all ")
                || value.contains("/");
    }

    private Comparator<RecordItem> newestItems() {
        return Comparator.comparing((RecordItem item) -> sortableDate(item.date())).reversed();
    }

    private LocalDate sortableDate(String value) {
        if (value == null || value.length() < 10) {
            return LocalDate.MIN;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException exception) {
            return LocalDate.MIN;
        }
    }

    private boolean isCorrectionLog(SystemLogRecord log) {
        String text = (safe(log.getModuleName(), "") + " " + safe(log.getActionName(), "") + " " + safe(log.getDetails(), ""))
                .toLowerCase(Locale.ENGLISH);
        return text.contains("data and records")
                && (text.contains("request") || text.contains("correction") || text.contains("corrected draft") || text.contains("lifecycle"));
    }

    private boolean isPendingRequest(SystemLogRecord log) {
        String action = safe(log.getActionName(), "").toLowerCase(Locale.ENGLISH);
        return action.contains("request") && !action.contains("reviewed");
    }

    private String correctionStatus(SystemLogRecord log) {
        String text = (safe(log.getActionName(), "") + " " + safe(log.getDetails(), "")).toLowerCase(Locale.ENGLISH);
        if (text.contains("approved")) {
            return "Approved";
        }
        if (text.contains("rejected")) {
            return "Rejected";
        }
        if (isPendingRequest(log)) {
            return "Pending";
        }
        return "Recorded";
    }

    private String recordFromDetails(String details) {
        if (details == null || details.isBlank()) {
            return "Protected record";
        }
        int reasonIndex = details.toLowerCase(Locale.ENGLISH).indexOf(". reason:");
        return reasonIndex > 0 ? details.substring(0, reasonIndex).trim() : firstLine(details);
    }

    private String archiveReason(RecordItem item) {
        if (isStatus(item, "INACTIVE")) {
            return "Inactive";
        }
        if (isStatus(item, "COMPLETED")) {
            return "Completed";
        }
        if (isStatus(item, "CLOSED")) {
            return "Closed";
        }
        return "Archived";
    }

    private RecordRow selectedRow() {
        if (table == null) {
            return null;
        }
        RecordRow row = table.getSelectionModel().getSelectedItem();
        return row == null && !currentRows.isEmpty() ? currentRows.getFirst() : row;
    }

    private String summaryMessage() {
        if (currentRows.isEmpty()) {
            return switch (currentArea) {
                case "Draft Records" -> "No draft records were found.";
                case "Frozen Records" -> "No frozen records were found.";
                case "Cancelled and Reversed" -> "No cancelled or reversed records were found.";
                case "Archived Records" -> "No archived records were found.";
                case "Correction Requests" -> "No correction requests were found.";
                default -> "No active records were found.";
            };
        }
        return "Showing " + currentRows.size() + " matching record(s).";
    }

    private String transactionDetail(FinanceTransaction transaction) {
        return lines(
                "Record: Transaction #" + transaction.getId(),
                "Account: " + safe(transaction.getAccountName(), "-"),
                "Type: " + safe(transaction.getTransactionType(), "-"),
                "Purpose: " + safe(transaction.getTransactionPurpose(), "-"),
                "Status: " + safe(transaction.getTransactionStatus(), "COMPLETED"),
                "Amount: " + MoneyUtil.mwk(transaction.getAmount()),
                "Date: " + safe(transaction.getTransactionDate(), "-"),
                "Category: " + safe(transaction.getCategoryName(), "-"),
                "Payment method: " + safe(transaction.getPaymentMethod(), "-"),
                "Reference: " + safe(transaction.getReferenceNumber(), "-"),
                "Description: " + safe(transaction.getDescription(), "-")
        );
    }

    private String correctionDraftDetail(TransactionCorrectionDraftRecord draft) {
        return lines(
                "Record: Correction Draft #" + draft.id(),
                "Original transaction: #" + draft.originalTransactionId(),
                "Account: " + safe(draft.accountName(), "-"),
                "Type: " + safe(draft.transactionType(), "-"),
                "Purpose: " + safe(draft.transactionPurpose(), "-"),
                "Status: " + safe(draft.status(), "Draft"),
                "Amount: " + MoneyUtil.mwk(draft.amount()),
                "Date: " + safe(draft.transactionDate(), "-"),
                "Payment method: " + safe(draft.paymentMethod(), "-"),
                "Reference: " + safe(draft.referenceNumber(), "-"),
                "Description: " + safe(draft.description(), "-"),
                "Reason: " + safe(draft.reason(), "-")
        );
    }

    private String accountDetail(Account account) {
        return lines(
                "Record: " + account.getAccountName() + " #" + account.getId(),
                "Type: Account",
                "Account type: " + safe(account.getAccountType(), "-"),
                "Currency: " + safe(account.getCurrency(), "-"),
                "Status: " + safe(account.getStatus(), "ACTIVE"),
                "Current balance: " + MoneyUtil.mwk(account.getCurrentBalance()),
                "Created: " + safe(account.getCreatedAt(), "-"),
                "Notes: " + safe(account.getNotes(), "-")
        );
    }

    private String budgetDetail(Budget budget) {
        return lines(
                "Record: " + budget.getBudgetName() + " #" + budget.getId(),
                "Type: Budget",
                "Month: " + safe(budget.getBudgetMonth(), "-"),
                "Status: " + safe(budget.getStatus(), "PLANNED"),
                "Amount limit: " + MoneyUtil.mwk(budget.getAmountLimit()),
                "Category: " + safe(budget.getCategoryName(), "-"),
                "Notes: " + safe(budget.getNotes(), "-")
        );
    }

    private String projectDetail(Project project) {
        return lines(
                "Record: " + project.getProjectName() + " #" + project.getId(),
                "Type: Project",
                "Status: " + safe(project.getStatus(), "ACTIVE"),
                "Planned budget: " + MoneyUtil.mwk(project.getPlannedBudget()),
                "Amount spent: " + MoneyUtil.mwk(project.getAmountSpent()),
                "Start date: " + safe(project.getStartDate(), "-"),
                "End date: " + safe(project.getEndDate(), "-"),
                "Description: " + safe(project.getDescription(), "-")
        );
    }

    private String goalDetail(Goal goal) {
        return lines(
                "Record: " + goal.getGoalName() + " #" + goal.getId(),
                "Type: Goal",
                "Status: " + safe(goal.getStatus(), "ACTIVE"),
                "Target amount: " + MoneyUtil.mwk(goal.getTargetAmount()),
                "Current amount: " + MoneyUtil.mwk(goal.getCurrentAmount()),
                "Target date: " + safe(goal.getTargetDate(), "-")
        );
    }

    private String paymentMethodDetail(PaymentMethodRecord method) {
        return lines(
                "Record: " + method.getMethodName() + " #" + method.getId(),
                "Type: Payment Method",
                "Method type: " + safe(method.getMethodType(), "-"),
                "Provider: " + safe(method.getProvider(), "-"),
                "Default account: " + safe(method.getDefaultAccount(), "-"),
                "Status: " + safe(method.getStatus(), "ACTIVE"),
                "Last used: " + safe(method.getLastUsed(), "-")
        );
    }

    private TableView<RecordRow> table(String emptyMessage, List<ColumnSpec> columns) {
        TableView<RecordRow> view = new TableView<>();
        view.setPlaceholder(new Label(emptyMessage));
        view.setPrefHeight(380);
        for (int index = 0; index < columns.size(); index++) {
            ColumnSpec spec = columns.get(index);
            int valueIndex = index;
            TableColumn<RecordRow, String> column = new TableColumn<>(spec.title());
            column.setPrefWidth(spec.width());
            column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().value(valueIndex)));
            view.getColumns().add(column);
        }
        TableActions.configureScrollableTable(view);
        return view;
    }

    private FlowPane filters(Node... nodes) {
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

    private Node searchButton(String text) {
        Button button = new Button(text);
        button.setMinWidth(120);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(event -> search());
        VBox box = new VBox(5);
        box.getStyleClass().add("maintenance-simple-field");
        Label label = new Label(" ");
        label.getStyleClass().add("field-label");
        box.getChildren().addAll(label, button);
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
        field.setOnAction(event -> search());
        return field;
    }

    private List<String> recordTypeOptions() {
        return List.of("All record types", "Transactions", "Accounts", "Budgets", "Projects", "Goals", "Payment methods");
    }

    private String selected(ComboBox<String> comboBox) {
        return comboBox == null || comboBox.getValue() == null ? "" : comboBox.getValue();
    }

    private String text(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private String signedInUserText() {
        try {
            return UserSession.getAuthenticatedUser().getDisplayName();
        } catch (RuntimeException exception) {
            return "System";
        }
    }

    private String amountText(Double amount) {
        return amount == null ? "-" : MoneyUtil.mwk(amount);
    }

    private String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        int lineBreak = value.indexOf('\n');
        String line = lineBreak >= 0 ? value.substring(0, lineBreak) : value;
        return line.length() > 120 ? line.substring(0, 117) + "..." : line;
    }

    private String titleCase(String value) {
        String clean = safe(value, "").replace('_', ' ').toLowerCase(Locale.ENGLISH);
        if (clean.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(clean.length());
        boolean start = true;
        for (char character : clean.toCharArray()) {
            if (Character.isWhitespace(character)) {
                start = true;
                builder.append(character);
            } else if (start) {
                builder.append(Character.toUpperCase(character));
                start = false;
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String lines(String... values) {
        return String.join(System.lineSeparator(), values);
    }

    private List<String> row(String... values) {
        List<String> row = new ArrayList<>();
        for (String value : values) {
            row.add(value == null ? "" : value);
        }
        return row;
    }

    private ColumnSpec column(String title, double width) {
        return new ColumnSpec(title, width);
    }

    private record ColumnSpec(String title, double width) {
    }

    private record RecordItem(
            String type,
            int id,
            String name,
            String date,
            String status,
            Double amount,
            String detail,
            Object source
    ) {
    }

    private record RecordRow(
            List<String> values,
            String detail,
            String recordType,
            int recordId,
            String name,
            String status,
            Double amount,
            Object source
    ) {
        private String value(int index) {
            return index >= 0 && index < values.size() ? values.get(index) : "";
        }

        private String searchText() {
            return String.join(" | ", values) + " | " + detail;
        }
    }

    private record ReviewDecision(String decision, String comment) {
    }
}
