package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.AccountReconciliationRecord;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class QualityReconciliationTaskController {
    private static final int PAGE_LIMIT = 100;
    private static final DateTimeFormatter CHECKED_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH);

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private VBox contentContainer;
    @FXML private TextArea resultArea;
    @FXML private Button supportingActionButton;
    @FXML private Button mainActionButton;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    private String currentArea = "Data Health Overview";
    private List<Account> accounts = List.of();
    private List<FinanceTransaction> transactions = List.of();
    private List<AccountReconciliationRecord> latestReconciliations = List.of();
    private String qualitySummary = "";

    private ComboBox<String> typeBox;
    private ComboBox<String> fieldBox;
    private ComboBox<String> errorTypeBox;
    private ComboBox<String> exceptionTypeBox;
    private TextField searchField;
    private ComboBox<Account> accountBox;
    private DatePicker reconciliationDatePicker;
    private TextField actualBalanceField;
    private Label overviewLastCheckedLabel;
    private Label systemBalanceValueLabel;
    private Label actualBalanceValueLabel;
    private Label differenceValueLabel;
    private TableView<QualityRow> table;
    private List<QualityRow> currentRows = List.of();
    private LocalDateTime lastCheckedAt = LocalDateTime.now();

    @FXML
    public void initialize() {
        selectArea(currentArea);
    }

    public void selectArea(String area) {
        currentArea = area == null || area.isBlank() ? "Data Health Overview" : area.trim();
        render();
    }

    public void refresh() {
        search();
    }

    @FXML
    private void runSupportingAction() {
        switch (currentArea) {
            case "Data Health Overview" -> refreshChecks();
            case "Duplicate Records" -> compareDuplicate();
            case "Account Reconciliation" -> viewEntries();
            case "Exceptions" -> viewDetails();
            default -> openRecord();
        }
    }

    @FXML
    private void runMainAction() {
        switch (currentArea) {
            case "Data Health Overview" -> openSelectedIssue();
            case "Missing Information" -> markFixed();
            case "Duplicate Records" -> resolveDuplicate();
            case "Account Reconciliation" -> reconcileAccount();
            case "Relationship Errors" -> fixSafeErrors();
            case "Exceptions" -> resolveException();
            default -> viewDetails();
        }
    }

    private void render() {
        loadWorkspaceData();
        contentContainer.getChildren().clear();
        table = null;
        currentRows = List.of();
        configureTitleAndButtons();
        switch (currentArea) {
            case "Missing Information" -> renderMissingInformation();
            case "Duplicate Records" -> renderDuplicateRecords();
            case "Account Reconciliation" -> renderAccountReconciliation();
            case "Relationship Errors" -> renderRelationshipErrors();
            case "Exceptions" -> renderExceptions();
            default -> renderOverview();
        }
        search();
    }

    private void configureTitleAndButtons() {
        switch (currentArea) {
            case "Missing Information" -> {
                titleLabel.setText("Missing Information");
                subtitleLabel.setText("Find records that need a required value before reports can rely on them.");
                supportingActionButton.setText("Open Record");
                mainActionButton.setText("Mark Fixed");
            }
            case "Duplicate Records" -> {
                titleLabel.setText("Duplicate Records");
                subtitleLabel.setText("Review records that look like they may have been entered more than once.");
                supportingActionButton.setText("Compare");
                mainActionButton.setText("Resolve Selected");
            }
            case "Account Reconciliation" -> {
                titleLabel.setText("Account Reconciliation");
                subtitleLabel.setText("Check whether the selected account matches its actual balance.");
                supportingActionButton.setText("View Entries");
                mainActionButton.setText("Reconcile");
            }
            case "Relationship Errors" -> {
                titleLabel.setText("Relationship Errors");
                subtitleLabel.setText("Find records with missing or invalid links to related records.");
                supportingActionButton.setText("Open Record");
                mainActionButton.setText("Fix Safe Errors");
            }
            case "Exceptions" -> {
                titleLabel.setText("Exceptions");
                subtitleLabel.setText("Review records that do not fit the normal checks.");
                supportingActionButton.setText("View Details");
                mainActionButton.setText("Resolve Selected");
            }
            default -> {
                titleLabel.setText("Data Health Overview");
                subtitleLabel.setText("Check whether your financial records are complete and reliable.");
                supportingActionButton.setText("Refresh Checks");
                mainActionButton.setText("Open Selected Issue");
            }
        }
        supportingActionButton.getStyleClass().setAll("secondary-button");
        mainActionButton.getStyleClass().setAll("primary-button");
    }

    private void renderOverview() {
        overviewLastCheckedLabel = new Label("Last checked: " + lastCheckedAt.format(CHECKED_FORMAT));
        overviewLastCheckedLabel.getStyleClass().add("form-note");
        table = table("No data quality issues were found.",
                List.of(column("Check", 260), column("Count", 100), column("Status", 180)));
        contentContainer.getChildren().addAll(overviewLastCheckedLabel, table);
    }

    private void renderMissingInformation() {
        typeBox = combo(List.of("All records", "Transactions", "Accounts"), "All records");
        fieldBox = combo(List.of("All missing fields", "Category", "Payment method", "Description", "Date", "Account type", "Currency"), "All missing fields");
        searchField = textField("Record ID or description");
        contentContainer.getChildren().add(filters(
                field("Type", typeBox),
                field("Field", fieldBox),
                wideField("Search", searchField),
                searchButton("Search")
        ));
        table = table("No missing information was found.",
                List.of(column("Record", 170), column("Type", 130), column("Missing field", 200), column("Date", 140), column("Status", 170)));
        contentContainer.getChildren().add(table);
    }

    private void renderDuplicateRecords() {
        typeBox = combo(List.of("All records", "Transactions"), "All records");
        searchField = textField("Record ID or description");
        contentContainer.getChildren().add(filters(
                field("Type", typeBox),
                wideField("Search", searchField),
                searchButton("Search")
        ));
        table = table("No possible duplicate records were found.",
                List.of(column("Record 1", 170), column("Record 2", 170), column("Reason", 330), column("Amount", 150), column("Date", 140)));
        contentContainer.getChildren().add(table);
    }

    private void renderAccountReconciliation() {
        accountBox = new ComboBox<>();
        accountBox.setItems(FXCollections.observableArrayList(accounts));
        accountBox.setMaxWidth(Double.MAX_VALUE);
        accountBox.getStyleClass().add("maintenance-input");
        if (!accounts.isEmpty()) {
            accountBox.getSelectionModel().selectFirst();
        }
        reconciliationDatePicker = datePicker(LocalDate.now());
        actualBalanceField = textField("Enter actual balance");
        actualBalanceField.textProperty().addListener((observable, oldValue, newValue) -> updateReconciliationValues());
        accountBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateReconciliationValues();
            search();
        });
        reconciliationDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateReconciliationValues();
            search();
        });

        systemBalanceValueLabel = valueLabel(MoneyUtil.mwk(0));
        actualBalanceValueLabel = valueLabel("-");
        differenceValueLabel = valueLabel("-");
        HBox summary = new HBox(18,
                valueBlock("System balance", systemBalanceValueLabel),
                valueBlock("Actual balance", actualBalanceValueLabel),
                valueBlock("Difference", differenceValueLabel));
        summary.getStyleClass().add("settings-status-box");

        contentContainer.getChildren().addAll(filters(
                field("Account", accountBox),
                field("Date", reconciliationDatePicker),
                wideField("Actual balance", actualBalanceField)
        ), summary);
        table = table("No entries were found for the selected account.",
                List.of(column("Date", 140), column("Record", 170), column("Type", 130), column("Amount", 150), column("Status", 150)));
        contentContainer.getChildren().add(table);
        updateReconciliationValues();
    }

    private void renderRelationshipErrors() {
        errorTypeBox = combo(List.of("All errors", "Missing account", "Missing loan contact", "Missing loan schedule", "Missing project activity"), "All errors");
        searchField = textField("Record ID or description");
        contentContainer.getChildren().add(filters(
                field("Type", errorTypeBox),
                wideField("Search", searchField),
                searchButton("Search")
        ));
        table = table("No relationship errors were found.",
                List.of(column("Record", 190), column("Error", 280), column("Related record", 240), column("Status", 170)));
        contentContainer.getChildren().add(table);
    }

    private void renderExceptions() {
        exceptionTypeBox = combo(List.of("All exceptions", "Future date", "Zero amount", "Negative amount", "Large amount", "Inactive account"), "All exceptions");
        searchField = textField("Record ID or description");
        contentContainer.getChildren().add(filters(
                field("Type", exceptionTypeBox),
                wideField("Search", searchField),
                searchButton("Search")
        ));
        table = table("No exceptions were found.",
                List.of(column("Record", 170), column("Exception", 280), column("Severity", 130), column("Date", 140), column("Status", 170)));
        contentContainer.getChildren().add(table);
    }

    private void search() {
        if (table == null) {
            return;
        }
        loadWorkspaceData();
        if (overviewLastCheckedLabel != null) {
            overviewLastCheckedLabel.setText("Last checked: " + lastCheckedAt.format(CHECKED_FORMAT));
        }
        currentRows = switch (currentArea) {
            case "Missing Information" -> filteredMissingRows();
            case "Duplicate Records" -> filteredDuplicateRows();
            case "Account Reconciliation" -> reconciliationRows();
            case "Relationship Errors" -> filteredRelationshipRows();
            case "Exceptions" -> filteredExceptionRows();
            default -> overviewRows();
        };
        table.getItems().setAll(currentRows);
        if (!currentRows.isEmpty()) {
            table.getSelectionModel().selectFirst();
        }
        updateButtonState();
        if ("Account Reconciliation".equals(currentArea)) {
            updateReconciliationValues();
        }
        resultArea.setText(summaryMessage());
    }

    private void loadWorkspaceData() {
        accounts = database.listAccounts();
        transactions = database.listRecentTransactions(1000);
        latestReconciliations = database.listLatestAccountReconciliations();
        qualitySummary = database.dataQualitySummary();
        lastCheckedAt = LocalDateTime.now();
    }

    private List<QualityRow> overviewRows() {
        return List.of(
                overviewRow("Missing information", missingRows().size(), "Missing Information"),
                overviewRow("Duplicate records", duplicateRows().size(), "Duplicate Records"),
                overviewRow("Account reconciliation", unreconciledAccounts().size(), "Account Reconciliation"),
                overviewRow("Relationship errors", relationshipRows().size(), "Relationship Errors"),
                overviewRow("Exceptions", exceptionRows().size(), "Exceptions")
        );
    }

    private QualityRow overviewRow(String check, int count, String targetArea) {
        String status = count == 0 ? "Good" : "Needs attention";
        return new QualityRow(row(check, String.valueOf(count), status),
                lines(check, "Count: " + count, "Status: " + status),
                targetArea,
                "Overview",
                null);
    }

    private List<QualityRow> filteredMissingRows() {
        return missingRows().stream()
                .filter(row -> matchesCombo(row.value(1), selected(typeBox).replace("Transactions", "Transaction").replace("Accounts", "Account")))
                .filter(row -> matchesCombo(row.value(2), selected(fieldBox)))
                .filter(this::matchesSearch)
                .limit(PAGE_LIMIT)
                .toList();
    }

    private List<QualityRow> missingRows() {
        List<QualityRow> rows = new ArrayList<>();
        for (FinanceTransaction transaction : transactions) {
            if (isCancelled(transaction)) {
                continue;
            }
            if (isIncomeOrExpense(transaction) && isBlank(transaction.getCategoryName())) {
                rows.add(missingTransactionRow(transaction, "Category", "Select a category before relying on spending or income reports."));
            }
            if (isBlank(transaction.getPaymentMethod())) {
                rows.add(missingTransactionRow(transaction, "Payment method", "Select how the transaction was paid or received."));
            }
            if (isBlank(transaction.getDescription())) {
                rows.add(missingTransactionRow(transaction, "Description", "Add a short description so the record is easy to identify."));
            }
            if (isBlank(transaction.getTransactionDate())) {
                rows.add(missingTransactionRow(transaction, "Date", "Enter the transaction date."));
            }
        }
        for (Account account : accounts) {
            if (isBlank(account.getAccountType())) {
                rows.add(missingAccountRow(account, "Account type", "Select the account type."));
            }
            if (isBlank(account.getCurrency())) {
                rows.add(missingAccountRow(account, "Currency", "Select the account currency."));
            }
        }
        return newest(rows);
    }

    private QualityRow missingTransactionRow(FinanceTransaction transaction, String missingField, String action) {
        String record = "Transaction #" + transaction.getId();
        return new QualityRow(
                row(record, "Transaction", missingField, safe(transaction.getTransactionDate(), "-"), statusText(transaction)),
                lines(record, "Missing field: " + missingField, "Description: " + safe(transaction.getDescription(), "-"), "Recommended action: " + action),
                "Missing Information",
                "Transaction",
                transaction.getId()
        );
    }

    private QualityRow missingAccountRow(Account account, String missingField, String action) {
        String record = "Account #" + account.getId();
        return new QualityRow(
                row(record, "Account", missingField, safe(account.getCreatedAt(), "-"), safe(account.getStatus(), "Active")),
                lines(record, "Account: " + safe(account.getAccountName(), "-"), "Missing field: " + missingField, "Recommended action: " + action),
                "Missing Information",
                "Account",
                account.getId()
        );
    }

    private List<QualityRow> filteredDuplicateRows() {
        return duplicateRows().stream()
                .filter(row -> matchesCombo("Transaction", selected(typeBox).replace("Transactions", "Transaction")))
                .filter(this::matchesSearch)
                .limit(PAGE_LIMIT)
                .toList();
    }

    private List<QualityRow> duplicateRows() {
        Map<String, List<FinanceTransaction>> groups = new LinkedHashMap<>();
        for (FinanceTransaction transaction : transactions) {
            if (isCancelled(transaction)) {
                continue;
            }
            groups.computeIfAbsent(duplicateKey(transaction), ignored -> new ArrayList<>()).add(transaction);
        }
        List<QualityRow> rows = new ArrayList<>();
        for (List<FinanceTransaction> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            group.sort(Comparator.comparing(FinanceTransaction::getId));
            FinanceTransaction first = group.get(0);
            FinanceTransaction second = group.get(1);
            String reason = "Same account, date, type, amount and description";
            String detail = lines(
                    "Record 1: Transaction #" + first.getId(),
                    transactionDetail(first),
                    "",
                    "Record 2: Transaction #" + second.getId(),
                    transactionDetail(second),
                    "",
                    "Reason: " + reason
            );
            rows.add(new QualityRow(
                    row("Transaction #" + first.getId(), "Transaction #" + second.getId(), reason, MoneyUtil.mwk(first.getAmount()), safe(first.getTransactionDate(), "-")),
                    detail,
                    "Duplicate Records",
                    "Transaction",
                    first.getId()
            ));
        }
        return newest(rows);
    }

    private List<QualityRow> reconciliationRows() {
        Account account = selectedAccount();
        if (account == null) {
            return List.of();
        }
        LocalDate date = reconciliationDate();
        return database.listTransactionsForAccount(account.getId()).stream()
                .filter(transaction -> !isCancelled(transaction))
                .filter(transaction -> {
                    LocalDate transactionDate = dateFrom(transaction.getTransactionDate());
                    return date == null || transactionDate == null || !transactionDate.isAfter(date);
                })
                .limit(PAGE_LIMIT)
                .map(transaction -> new QualityRow(
                        row(safe(transaction.getTransactionDate(), "-"), "Transaction #" + transaction.getId(), safe(transaction.getTransactionType(), "-"), MoneyUtil.mwk(signedAmount(transaction)), statusText(transaction)),
                        transactionDetail(transaction),
                        "Account Reconciliation",
                        "Transaction",
                        transaction.getId()
                ))
                .toList();
    }

    private List<QualityRow> filteredRelationshipRows() {
        return relationshipRows().stream()
                .filter(row -> matchesCombo(row.value(1), selected(errorTypeBox)))
                .filter(this::matchesSearch)
                .limit(PAGE_LIMIT)
                .toList();
    }

    private List<QualityRow> relationshipRows() {
        List<QualityRow> rows = new ArrayList<>();
        int missingAccountCount = summaryCount("Transactions linked to missing accounts");
        if (missingAccountCount > 0) {
            rows.add(new QualityRow(
                    row("Workspace check", "Missing account", missingAccountCount + " transaction(s)", "Needs attention"),
                    "Some transactions reference accounts that no longer exist. Open the affected records from the transaction list or restore the missing account.",
                    "Relationship Errors",
                    "Workspace",
                    null
            ));
        }
        int missingLoanScheduleCount = summaryCount("Loan contacts without loan schedule");
        if (missingLoanScheduleCount > 0) {
            rows.add(new QualityRow(
                    row("Workspace check", "Missing loan schedule", missingLoanScheduleCount + " contact(s)", "Needs attention"),
                    "Loan-related contacts have transactions but no loan schedule. Open the contact or loan screen and add the missing schedule.",
                    "Relationship Errors",
                    "Workspace",
                    null
            ));
        }
        for (FinanceTransaction transaction : transactions) {
            if (isLoanTransaction(transaction) && isBlank(transaction.getPersonName())) {
                rows.add(new QualityRow(
                        row("Transaction #" + transaction.getId(), "Missing loan contact", "Person", "Needs attention"),
                        lines(transactionDetail(transaction), "Recommended action: Select the related person for this loan transaction."),
                        "Relationship Errors",
                        "Transaction",
                        transaction.getId()
                ));
            }
            if (transaction.getProjectActivityId() != null && isBlank(transaction.getProjectActivityName())) {
                rows.add(new QualityRow(
                        row("Transaction #" + transaction.getId(), "Missing project activity", "Project activity #" + transaction.getProjectActivityId(), "Needs attention"),
                        lines(transactionDetail(transaction), "Recommended action: Reconnect the project activity or remove the broken link if it is optional."),
                        "Relationship Errors",
                        "Transaction",
                        transaction.getId()
                ));
            }
        }
        return newest(rows);
    }

    private List<QualityRow> filteredExceptionRows() {
        return exceptionRows().stream()
                .filter(row -> matchesCombo(row.value(1), selected(exceptionTypeBox)))
                .filter(this::matchesSearch)
                .limit(PAGE_LIMIT)
                .toList();
    }

    private List<QualityRow> exceptionRows() {
        List<QualityRow> rows = new ArrayList<>();
        Map<String, Account> accountByName = new LinkedHashMap<>();
        for (Account account : accounts) {
            accountByName.put(account.getAccountName(), account);
        }
        double averageAmount = transactions.stream()
                .filter(transaction -> Math.abs(transaction.getAmount()) > 0.005)
                .mapToDouble(transaction -> Math.abs(transaction.getAmount()))
                .average()
                .orElse(0);
        double largeAmountThreshold = Math.max(1_000_000, averageAmount * 3);
        for (FinanceTransaction transaction : transactions) {
            if (isCancelled(transaction)) {
                continue;
            }
            LocalDate transactionDate = dateFrom(transaction.getTransactionDate());
            if (transactionDate != null && transactionDate.isAfter(LocalDate.now())) {
                rows.add(exceptionRow(transaction, "Future date", "Medium", "Confirm this should be scheduled or recorded as an obligation."));
            }
            if (Math.abs(transaction.getAmount()) < 0.005) {
                rows.add(exceptionRow(transaction, "Zero amount", "Medium", "Enter the correct amount or cancel the record."));
            }
            if (transaction.getAmount() < 0) {
                rows.add(exceptionRow(transaction, "Negative amount", "High", "Use the correct transaction type instead of a negative amount."));
            }
            if (averageAmount > 0 && Math.abs(transaction.getAmount()) >= largeAmountThreshold) {
                rows.add(exceptionRow(transaction, "Large amount", "Medium", "Review the amount before relying on summaries."));
            }
            Account account = accountByName.get(transaction.getAccountName());
            if (account != null && "INACTIVE".equalsIgnoreCase(safe(account.getStatus(), ""))) {
                rows.add(exceptionRow(transaction, "Inactive account", "High", "Reactivate the account only if new entries are valid."));
            }
        }
        return newest(rows);
    }

    private QualityRow exceptionRow(FinanceTransaction transaction, String exception, String severity, String action) {
        String record = "Transaction #" + transaction.getId();
        return new QualityRow(
                row(record, exception, severity, safe(transaction.getTransactionDate(), "-"), statusText(transaction)),
                lines(record, "Exception: " + exception, transactionDetail(transaction), "Recommended action: " + action),
                "Exceptions",
                "Transaction",
                transaction.getId()
        );
    }

    private List<Account> unreconciledAccounts() {
        Map<Integer, AccountReconciliationRecord> reconciled = latestReconciliationMap();
        List<Account> rows = new ArrayList<>();
        for (Account account : accounts) {
            if (!"ACTIVE".equalsIgnoreCase(safe(account.getStatus(), "ACTIVE"))) {
                continue;
            }
            AccountReconciliationRecord latest = reconciled.get(account.getId());
            if (latest == null || "DIFFERENCE".equalsIgnoreCase(safe(latest.getStatus(), ""))) {
                rows.add(account);
            }
        }
        return rows;
    }

    private Map<Integer, AccountReconciliationRecord> latestReconciliationMap() {
        Map<Integer, AccountReconciliationRecord> records = new LinkedHashMap<>();
        for (AccountReconciliationRecord record : latestReconciliations) {
            records.putIfAbsent(record.getAccountId(), record);
        }
        return records;
    }

    private void refreshChecks() {
        search();
        resultArea.setText("Checks refreshed.\n\n" + summaryMessage());
    }

    private void openSelectedIssue() {
        QualityRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select an issue first.");
            return;
        }
        String targetArea = row.targetArea();
        if (targetArea == null || targetArea.isBlank() || "Data Health Overview".equals(targetArea)) {
            resultArea.setText("Select a row that points to a quality issue.");
            return;
        }
        boolean opened = selectQualityTab(targetArea);
        if (!opened) {
            resultArea.setText("Open the " + targetArea + " tab to review this issue.");
        }
    }

    private void openRecord() {
        QualityRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select a record first.");
            return;
        }
        resultArea.setText(lines(
                "Open the source screen for " + row.recordLabel() + ".",
                "",
                row.detail()
        ));
    }

    private void markFixed() {
        QualityRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select a record first.");
            return;
        }
        String key = row.key();
        search();
        boolean stillListed = currentRows.stream().anyMatch(current -> current.key().equals(key));
        resultArea.setText(stillListed
                ? "The selected issue is still listed. Open the record and complete the missing information."
                : "The selected issue no longer appears in the list.");
    }

    private void compareDuplicate() {
        QualityRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select a possible duplicate first.");
            return;
        }
        resultArea.setText(row.detail());
    }

    private void resolveDuplicate() {
        QualityRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select a possible duplicate first.");
            return;
        }
        Optional<String> choice = choiceDialog("What do you want to do?", List.of(
                "Keep first record",
                "Keep second record",
                "Keep both",
                "Merge if allowed"
        ));
        choice.ifPresent(selected -> resultArea.setText(lines(
                "Selected action: " + selected,
                "",
                "No automatic record change was made. Open the records and apply the correction from the transaction screen or Data Maintenance when removal is required."
        )));
    }

    private void viewEntries() {
        search();
        Account account = selectedAccount();
        resultArea.setText(account == null
                ? "Select an account first."
                : "Showing entries for " + account.getAccountName() + ".");
    }

    private void reconcileAccount() {
        Account account = selectedAccount();
        if (account == null) {
            resultArea.setText("Select an account first.");
            return;
        }
        LocalDate date = reconciliationDate();
        if (date == null) {
            resultArea.setText("Select a reconciliation date.");
            return;
        }
        double actualBalance;
        try {
            actualBalance = parseAmount(actualBalanceField.getText());
        } catch (NumberFormatException exception) {
            resultArea.setText("Enter a valid actual balance.");
            return;
        }
        double systemBalance = accountBalanceOnDate(account, date);
        double difference = actualBalance - systemBalance;
        database.saveAccountReconciliation(null, account.getId(), date.toString(), actualBalance, "Recorded from Data Quality and Reconciliation.");
        loadWorkspaceData();
        updateReconciliationValues();
        currentRows = reconciliationRows();
        table.getItems().setAll(currentRows);
        resultArea.setText(lines(
                "Reconciliation completed.",
                "",
                "System balance: " + MoneyUtil.mwk(systemBalance),
                "Actual balance: " + MoneyUtil.mwk(actualBalance),
                "Difference: " + MoneyUtil.mwk(difference),
                Math.abs(difference) < 0.005 ? "Result: Reconciled" : "Result: Difference found"
        ));
    }

    private void fixSafeErrors() {
        QualityRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select a relationship error first.");
            return;
        }
        resultArea.setText(lines(
                "No safe automatic fix was applied.",
                "",
                "Open the affected record and correct the relationship manually.",
                "",
                row.detail()
        ));
    }

    private void viewDetails() {
        QualityRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select an exception first.");
            return;
        }
        resultArea.setText(row.detail());
    }

    private void resolveException() {
        QualityRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select an exception first.");
            return;
        }
        Optional<String> choice = choiceDialog("How do you want to resolve this exception?", List.of(
                "Open record and correct it",
                "Mark as valid",
                "Ignore with reason"
        ));
        if (choice.isEmpty()) {
            return;
        }
        if ("Ignore with reason".equals(choice.get())) {
            TextInputDialog reasonDialog = new TextInputDialog();
            reasonDialog.setTitle("PFMIS");
            reasonDialog.setHeaderText("Reason required");
            reasonDialog.setContentText("Reason:");
            Optional<String> reason = reasonDialog.showAndWait().map(String::trim).filter(value -> !value.isBlank());
            if (reason.isEmpty()) {
                resultArea.setText("A reason is required before an exception can be ignored.");
                return;
            }
            resultArea.setText("Reason noted for review: " + reason.get() + "\n\nNo automatic record change was made.");
            return;
        }
        resultArea.setText(lines(
                "Selected action: " + choice.get(),
                "",
                "No automatic record change was made. Open the record and correct or confirm it from the source screen."
        ));
    }

    private Optional<String> choiceDialog(String header, List<String> choices) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("PFMIS");
        dialog.setHeaderText(header);
        ButtonType applyType = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, applyType);

        ToggleGroup group = new ToggleGroup();
        VBox content = new VBox(8);
        for (String choice : choices) {
            RadioButton button = new RadioButton(choice);
            button.setToggleGroup(group);
            button.setUserData(choice);
            content.getChildren().add(button);
        }
        if (!content.getChildren().isEmpty() && content.getChildren().getFirst() instanceof RadioButton first) {
            first.setSelected(true);
        }
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(buttonType -> {
            if (buttonType != applyType || group.getSelectedToggle() == null) {
                return null;
            }
            return String.valueOf(group.getSelectedToggle().getUserData());
        });
        return dialog.showAndWait();
    }

    private boolean selectQualityTab(String targetArea) {
        String tabText = switch (targetArea) {
            case "Data Health Overview" -> "Overview";
            case "Missing Information" -> "Missing Info";
            case "Duplicate Records" -> "Duplicates";
            case "Account Reconciliation" -> "Reconciliation";
            case "Relationship Errors" -> "Relationship Errors";
            case "Exceptions" -> "Exceptions";
            default -> targetArea;
        };
        String tabKey = switch (targetArea) {
            case "Data Health Overview" -> "dataHealthOverviewTab";
            case "Missing Information" -> "missingInformationTab";
            case "Duplicate Records" -> "duplicateRecordsTab";
            case "Account Reconciliation" -> "accountReconciliationTab";
            case "Relationship Errors" -> "relationshipErrorsTab";
            case "Exceptions" -> "exceptionsTab";
            default -> "";
        };
        DataRecordsSectionController.rememberTab("Data Quality and Reconciliation", tabKey);
        if (contentContainer.getScene() == null || contentContainer.getScene().getRoot() == null) {
            return false;
        }
        Set<Node> tabPanes = contentContainer.getScene().getRoot().lookupAll(".tab-pane");
        for (Node node : tabPanes) {
            if (node instanceof TabPane pane
                    && pane.getTabs().stream().anyMatch(tab -> "Overview".equals(tab.getText()))
                    && pane.getTabs().stream().anyMatch(tab -> tabText.equals(tab.getText()))) {
                pane.getTabs().stream()
                        .filter(tab -> tabText.equals(tab.getText()))
                        .findFirst()
                        .ifPresent(tab -> pane.getSelectionModel().select(tab));
                return true;
            }
        }
        return false;
    }

    private void updateButtonState() {
        if ("Account Reconciliation".equals(currentArea)) {
            supportingActionButton.setDisable(selectedAccount() == null);
            mainActionButton.setDisable(selectedAccount() == null || text(actualBalanceField).isBlank());
            return;
        }
        if ("Data Health Overview".equals(currentArea)) {
            supportingActionButton.setDisable(false);
            mainActionButton.setDisable(currentRows.isEmpty());
            return;
        }
        supportingActionButton.setDisable(currentRows.isEmpty());
        mainActionButton.setDisable(currentRows.isEmpty());
    }

    private String summaryMessage() {
        if ("Account Reconciliation".equals(currentArea)) {
            Account account = selectedAccount();
            return account == null
                    ? "Select an account to view entries."
                    : "Choose account, enter actual balance, then reconcile.";
        }
        if (currentRows.isEmpty()) {
            return switch (currentArea) {
                case "Missing Information" -> "No missing information was found.";
                case "Duplicate Records" -> "No possible duplicate records were found.";
                case "Relationship Errors" -> "No relationship errors were found.";
                case "Exceptions" -> "No exceptions were found.";
                default -> "No data quality issues were found.";
            };
        }
        return "Showing " + currentRows.size() + " matching record(s).";
    }

    private void updateReconciliationValues() {
        if (!"Account Reconciliation".equals(currentArea) || systemBalanceValueLabel == null) {
            return;
        }
        Account account = selectedAccount();
        LocalDate date = reconciliationDate();
        double systemBalance = account == null || date == null ? 0 : accountBalanceOnDate(account, date);
        systemBalanceValueLabel.setText(MoneyUtil.mwk(systemBalance));
        try {
            double actualBalance = parseAmount(actualBalanceField.getText());
            double difference = actualBalance - systemBalance;
            actualBalanceValueLabel.setText(MoneyUtil.mwk(actualBalance));
            differenceValueLabel.setText(MoneyUtil.mwk(difference));
        } catch (NumberFormatException exception) {
            actualBalanceValueLabel.setText("-");
            differenceValueLabel.setText("-");
        }
        updateButtonState();
    }

    private Account selectedAccount() {
        return accountBox == null ? null : accountBox.getValue();
    }

    private LocalDate reconciliationDate() {
        return reconciliationDatePicker == null ? null : reconciliationDatePicker.getValue();
    }

    private double accountBalanceOnDate(Account account, LocalDate date) {
        double balance = account.getOpeningBalance();
        for (FinanceTransaction transaction : database.listTransactionsForAccount(account.getId())) {
            if (isCancelled(transaction)) {
                continue;
            }
            LocalDate transactionDate = dateFrom(transaction.getTransactionDate());
            if (transactionDate == null || !transactionDate.isAfter(date)) {
                balance += signedAmount(transaction);
            }
        }
        return balance;
    }

    private double signedAmount(FinanceTransaction transaction) {
        String type = safe(transaction.getTransactionType(), "").toUpperCase(Locale.ENGLISH);
        String purpose = safe(transaction.getTransactionPurpose(), "").toUpperCase(Locale.ENGLISH);
        return switch (type) {
            case "INCOME" -> transaction.getAmount();
            case "EXPENSE" -> -transaction.getAmount();
            case "TRANSFER" -> {
                if ("TRANSFER_IN".equals(purpose)) {
                    yield transaction.getAmount();
                }
                if ("TRANSFER_OUT".equals(purpose)) {
                    yield -transaction.getAmount();
                }
                yield 0;
            }
            default -> transaction.getAmount();
        };
    }

    private double parseAmount(String value) {
        String clean = value == null ? "" : value.replaceAll("(?i)mwk", "").replace(",", "").trim();
        if (clean.isBlank()) {
            throw new NumberFormatException("Amount is required");
        }
        return Double.parseDouble(clean);
    }

    private String duplicateKey(FinanceTransaction transaction) {
        return String.join("|",
                safe(transaction.getAccountName(), ""),
                safe(transaction.getTransactionDate(), ""),
                safe(transaction.getTransactionType(), ""),
                String.format(Locale.ENGLISH, "%.2f", transaction.getAmount()),
                safe(transaction.getDescription(), "").trim().toLowerCase(Locale.ENGLISH));
    }

    private String transactionDetail(FinanceTransaction transaction) {
        return lines(
                "Account: " + safe(transaction.getAccountName(), "-"),
                "Type: " + safe(transaction.getTransactionType(), "-"),
                "Purpose: " + safe(transaction.getTransactionPurpose(), "-"),
                "Amount: " + MoneyUtil.mwk(transaction.getAmount()),
                "Date: " + safe(transaction.getTransactionDate(), "-"),
                "Status: " + statusText(transaction),
                "Category: " + safe(transaction.getCategoryName(), "-"),
                "Payment method: " + safe(transaction.getPaymentMethod(), "-"),
                "Reference: " + safe(transaction.getReferenceNumber(), "-"),
                "Description: " + safe(transaction.getDescription(), "-")
        );
    }

    private int summaryCount(String label) {
        String prefix = label + ":";
        for (String line : qualitySummary.split("\\R")) {
            if (!line.startsWith(prefix)) {
                continue;
            }
            String value = line.substring(prefix.length()).trim();
            StringBuilder digits = new StringBuilder();
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (Character.isDigit(character)) {
                    digits.append(character);
                } else if (!digits.isEmpty()) {
                    break;
                }
            }
            return digits.isEmpty() ? 0 : Integer.parseInt(digits.toString());
        }
        return 0;
    }

    private boolean matchesSearch(QualityRow row) {
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
        return value == null || value.isBlank() || value.toLowerCase(Locale.ENGLISH).startsWith("all ");
    }

    private boolean isIncomeOrExpense(FinanceTransaction transaction) {
        String type = safe(transaction.getTransactionType(), "").toUpperCase(Locale.ENGLISH);
        return "INCOME".equals(type) || "EXPENSE".equals(type);
    }

    private boolean isLoanTransaction(FinanceTransaction transaction) {
        String purpose = safe(transaction.getTransactionPurpose(), "").toUpperCase(Locale.ENGLISH);
        return purpose.contains("LENT") || purpose.contains("BORROWED") || purpose.contains("REPAID");
    }

    private boolean isCancelled(FinanceTransaction transaction) {
        return "CANCELLED".equalsIgnoreCase(safe(transaction.getTransactionStatus(), ""));
    }

    private String statusText(FinanceTransaction transaction) {
        return safe(transaction.getTransactionStatus(), "Completed");
    }

    private LocalDate dateFrom(String value) {
        if (value == null || value.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private List<QualityRow> newest(List<QualityRow> rows) {
        return rows.stream()
                .sorted(Comparator.comparing((QualityRow row) -> row.value(row.values().size() > 3 ? 3 : 0)).reversed())
                .limit(PAGE_LIMIT)
                .toList();
    }

    private QualityRow selectedRow() {
        if (table == null) {
            return null;
        }
        QualityRow row = table.getSelectionModel().getSelectedItem();
        return row == null && !currentRows.isEmpty() ? currentRows.getFirst() : row;
    }

    private TableView<QualityRow> table(String emptyMessage, List<ColumnSpec> columns) {
        TableView<QualityRow> view = new TableView<>();
        view.setPlaceholder(new Label(emptyMessage));
        view.setPrefHeight(360);
        for (int index = 0; index < columns.size(); index++) {
            ColumnSpec spec = columns.get(index);
            int valueIndex = index;
            TableColumn<QualityRow, String> column = new TableColumn<>(spec.title());
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

    private DatePicker datePicker(LocalDate value) {
        DatePicker picker = new DatePicker(value);
        picker.getStyleClass().add("maintenance-input");
        return picker;
    }

    private VBox valueBlock(String label, Label valueLabel) {
        VBox box = new VBox(4);
        Label title = new Label(label);
        title.getStyleClass().add("field-label");
        box.getChildren().addAll(title, valueLabel);
        return box;
    }

    private Label valueLabel(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("section-heading");
        return label;
    }

    private String selected(ComboBox<String> comboBox) {
        return comboBox == null || comboBox.getValue() == null ? "" : comboBox.getValue();
    }

    private String text(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    private record QualityRow(
            List<String> values,
            String detail,
            String targetArea,
            String recordType,
            Integer recordId
    ) {
        private String value(int index) {
            return index >= 0 && index < values.size() ? values.get(index) : "";
        }

        private String searchText() {
            return String.join(" | ", values) + " | " + detail;
        }

        private String recordLabel() {
            return recordId == null ? recordType : recordType + " #" + recordId;
        }

        private String key() {
            return targetArea + "|" + recordType + "|" + recordId + "|" + String.join("|", values);
        }
    }
}
