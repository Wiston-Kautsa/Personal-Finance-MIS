package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.TransferDraftRecord;
import com.wk.pfmis.db.DatabaseHandler.TransferPostingResult;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class TransactionLedgerController {
    private static final String PERIOD_TODAY = "Today";
    private static final String PERIOD_THIS_WEEK = "This Week";
    private static final String PERIOD_THIS_MONTH = "This Month";
    private static final String PERIOD_LAST_MONTH = "Last Month";
    private static final String PERIOD_LAST_3_MONTHS = "Last 3 Months";
    private static final String PERIOD_LAST_6_MONTHS = "Last 6 Months";
    private static final String PERIOD_THIS_YEAR = "This Year";
    private static final String PERIOD_LAST_YEAR = "Last Year";
    private static final String PERIOD_CUSTOM = "Custom Date Range";
    private static final String PERIOD_ALL_TRANSACTIONS = "All Transactions";
    private static final String ALL_TYPES = "All Types";
    private static final String ALL_STATUSES = "All Statuses";
    private static final String ALL_CATEGORIES = "All Categories";
    private static final int PAGE_SIZE = 200;
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    @FXML private ComboBox<String> periodBox;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private ComboBox<String> typeFilterBox;
    @FXML private ComboBox<Account> accountFilterBox;
    @FXML private ComboBox<String> statusFilterBox;
    @FXML private ComboBox<String> categoryFilterBox;
    @FXML private TextField searchField;
    @FXML private Label filterStatusLabel;
    @FXML private TableView<LedgerRow> transactionsTable;
    @FXML private TableColumn<LedgerRow, String> dateColumn;
    @FXML private TableColumn<LedgerRow, String> referenceColumn;
    @FXML private TableColumn<LedgerRow, String> descriptionColumn;
    @FXML private TableColumn<LedgerRow, String> typeColumn;
    @FXML private TableColumn<LedgerRow, String> accountColumn;
    @FXML private TableColumn<LedgerRow, String> moneyInColumn;
    @FXML private TableColumn<LedgerRow, String> moneyOutColumn;
    @FXML private TableColumn<LedgerRow, String> balanceColumn;
    @FXML private TableColumn<LedgerRow, String> statusColumn;
    @FXML private TextArea detailsArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Account> accounts = List.of();
    private List<Category> categories = List.of();
    private int currentOffset;
    private int currentTotalRecords;

    @FXML
    public void initialize() {
        MONEY_FORMAT.setMinimumFractionDigits(2);
        MONEY_FORMAT.setMaximumFractionDigits(2);

        periodBox.setItems(FXCollections.observableArrayList(
                PERIOD_TODAY,
                PERIOD_THIS_WEEK,
                PERIOD_THIS_MONTH,
                PERIOD_LAST_MONTH,
                PERIOD_LAST_3_MONTHS,
                PERIOD_LAST_6_MONTHS,
                PERIOD_THIS_YEAR,
                PERIOD_LAST_YEAR,
                PERIOD_CUSTOM,
                PERIOD_ALL_TRANSACTIONS
        ));
        typeFilterBox.setItems(FXCollections.observableArrayList(
                ALL_TYPES,
                "Income",
                "Expense",
                "Transfer",
                "Transfer Fee",
                "Loan Proceeds",
                "Loan Repayment",
                "Savings Contribution",
                "Chipeleganyu Contribution",
                "Bank Nkhonde Contribution",
                "Investment",
                "Asset Purchase",
                "Asset Sale",
                "Opening Balance",
                "Adjustment",
                "Draft"
        ));
        statusFilterBox.setItems(FXCollections.observableArrayList(
                ALL_STATUSES,
                "Pending",
                "Completed",
                "Paid",
                "Scheduled",
                "Failed",
                "Posted",
                "Draft",
                "Open",
                "Frozen",
                "Cancelled",
                "Reversed"
        ));
        periodBox.getSelectionModel().select(PERIOD_THIS_MONTH);
        typeFilterBox.getSelectionModel().select(ALL_TYPES);
        statusFilterBox.getSelectionModel().select(ALL_STATUSES);
        applyRequestedLedgerFilter();

        dateColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDate()));
        referenceColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getReference()));
        descriptionColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDescription()));
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getType()));
        accountColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAccount()));
        moneyInColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMoneyIn()));
        moneyOutColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMoneyOut()));
        balanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getBalance()));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));
        TableActions.configureScrollableTable(transactionsTable);
        TableActions.installRowContextMenu(transactionsTable, row -> List.of(
                TableActions.menuItem("Open Record", this::openRecord),
                TableActions.menuItem("More Actions", this::moreActions)
        ));

        transactionsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                detailsArea.clear();
            } else {
                detailsArea.setText(summaryText(newValue));
            }
        });
        periodBox.setOnAction(event -> {
            currentOffset = 0;
            updateCustomDateControls();
            refresh();
        });
        typeFilterBox.setOnAction(event -> resetAndRefresh());
        accountFilterBox.setOnAction(event -> resetAndRefresh());
        statusFilterBox.setOnAction(event -> resetAndRefresh());
        categoryFilterBox.setOnAction(event -> resetAndRefresh());
        searchField.setOnAction(event -> resetAndRefresh());
        fromDatePicker.setOnAction(event -> resetAndRefresh());
        toDatePicker.setOnAction(event -> resetAndRefresh());
        updateCustomDateControls();
        refresh();
    }

    private void applyRequestedLedgerFilter() {
        String requestedType = NavigationBus.consumeRequestedTransactionLedgerFilter();
        if (requestedType == null || requestedType.isBlank()) {
            return;
        }
        String normalized = requestedType.trim();
        if (typeFilterBox.getItems().contains(normalized)) {
            typeFilterBox.getSelectionModel().select(normalized);
        }
        if ("Expense".equals(normalized) || "Income".equals(normalized)) {
            periodBox.getSelectionModel().select(PERIOD_ALL_TRANSACTIONS);
        }
    }

    @FXML
    private void refresh() {
        try {
            refreshFilters();
            List<LedgerRow> rows = buildRows().stream()
                    .filter(this::matchesFilters)
                    .sorted(Comparator.comparing(LedgerRow::sortDate).reversed().thenComparing(Comparator.comparingInt(LedgerRow::getId).reversed()))
                    .toList();
            transactionsTable.setItems(FXCollections.observableArrayList(rows));
            updateFilterStatus(rows.size());
            detailsArea.setText(rows.isEmpty()
                    ? "No transactions found for the selected period. Use View All Transactions or Clear Filters to widen the search."
                    : "Select a record, then use Open Record or More Actions.");
        } catch (IllegalArgumentException exception) {
            transactionsTable.setItems(FXCollections.observableArrayList());
            filterStatusLabel.setText(exception.getMessage());
            detailsArea.setText(exception.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        periodBox.getSelectionModel().select(PERIOD_THIS_MONTH);
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        typeFilterBox.getSelectionModel().select(ALL_TYPES);
        accountFilterBox.setValue(null);
        statusFilterBox.getSelectionModel().select(ALL_STATUSES);
        categoryFilterBox.getSelectionModel().select(ALL_CATEGORIES);
        searchField.clear();
        currentOffset = 0;
        updateCustomDateControls();
        refresh();
    }

    @FXML
    private void viewAllTransactions() {
        periodBox.getSelectionModel().select(PERIOD_ALL_TRANSACTIONS);
        currentOffset = 0;
        updateCustomDateControls();
        refresh();
    }

    @FXML
    private void previousPage() {
        currentOffset = Math.max(0, currentOffset - PAGE_SIZE);
        refresh();
    }

    @FXML
    private void nextPage() {
        if (currentOffset + PAGE_SIZE < currentTotalRecords) {
            currentOffset += PAGE_SIZE;
            refresh();
        }
    }

    @FXML
    private void openRecord() {
        LedgerRow row = selectedRow();
        if (row == null) {
            UiAlerts.info("Select a ledger record to open.");
            return;
        }
        detailsArea.setText(fullRecordText(row));
    }

    @FXML
    private void moreActions() {
        LedgerRow row = selectedRow();
        if (row == null) {
            UiAlerts.info("Select a ledger record first.");
            return;
        }
        List<String> actions = actionsFor(row);
        if (actions.isEmpty()) {
            UiAlerts.info("No additional actions are available for this record.");
            return;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(actions.get(0), actions);
        dialog.setTitle("PFMIS");
        dialog.setHeaderText("More Actions");
        dialog.setContentText("Action");
        Optional<String> selected = dialog.showAndWait();
        selected.ifPresent(action -> runAction(row, action));
    }

    private void refreshFilters() {
        Account selectedAccount = accountFilterBox.getValue();
        String selectedCategory = categoryFilterBox.getValue();

        accounts = database.listAccounts();
        accountFilterBox.setItems(FXCollections.observableArrayList(accounts));
        if (selectedAccount != null) {
            accounts.stream()
                    .filter(account -> account.getId() == selectedAccount.getId())
                    .findFirst()
                    .ifPresent(accountFilterBox::setValue);
        }

        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        this.categories = database.listCategories();
        for (Category category : this.categories) {
            if (!categories.contains(category.getCategoryName())) {
                categories.add(category.getCategoryName());
            }
        }
        categoryFilterBox.setItems(FXCollections.observableArrayList(categories));
        if (selectedCategory == null || !categories.contains(selectedCategory)) {
            categoryFilterBox.getSelectionModel().select(ALL_CATEGORIES);
        } else {
            categoryFilterBox.getSelectionModel().select(selectedCategory);
        }
    }

    private List<LedgerRow> buildRows() {
        Account selectedAccount = accountFilterBox.getValue();
        PeriodRange range = selectedPeriodRange();
        DatabaseHandler.TransactionHistoryPage page = database.listTransactionHistory(new DatabaseHandler.TransactionHistoryFilter(
                range.startDate(),
                range.endDate(),
                selectedAccount == null ? null : selectedAccount.getId(),
                selectedTypeForQuery(),
                selectedStatusForQuery(),
                selectedCategoryId(),
                safe(searchField.getText()),
                selectedAccount != null,
                PAGE_SIZE,
                currentOffset
        ));
        currentTotalRecords = page.totalRecords();
        List<FinanceTransaction> transactions = page.transactions();
        List<LedgerRow> rows = new ArrayList<>();

        for (FinanceTransaction transaction : transactions) {
            rows.add(rowForTransaction(transaction, ""));
        }

        if (selectedAccount != null) {
            applyRunningBalances(rows, selectedAccount, database.listTransactionsForAccount(selectedAccount.getId()));
        }

        for (TransferDraftRecord draft : database.listTransferDrafts(500)) {
            if (selectedAccount != null
                    && selectedAccount.getId() != draft.fromAccountId()
                    && selectedAccount.getId() != draft.toAccountId()) {
                continue;
            }
            rows.add(rowForDraft(draft));
        }

        return rows;
    }

    private void resetAndRefresh() {
        currentOffset = 0;
        refresh();
    }

    private LedgerRow rowForTransaction(FinanceTransaction transaction, String balance) {
        String type = typeLabel(transaction);
        double moneyIn = moneyInAmount(transaction);
        double moneyOut = moneyOutAmount(transaction);
        return new LedgerRow(
                RowKind.TRANSACTION,
                transaction.getId(),
                safe(transaction.getTransactionDate()),
                blankToDash(transaction.getReferenceNumber()),
                descriptionLabel(transaction),
                type,
                safe(transaction.getAccountName()),
                blankToDash(transaction.getCategoryName()),
                moneyIn > 0 ? money(currencyForAccount(transaction.getAccountName()), moneyIn) : "",
                moneyOut > 0 ? money(currencyForAccount(transaction.getAccountName()), moneyOut) : "",
                balance,
                statusLabel(transaction.getTransactionStatus()),
                safe(transaction.getPaymentMethod()),
                blankToDash(transaction.getPersonName()),
                blankToDash(transaction.getProjectName()),
                safe(transaction.getTransactionPurpose()),
                transaction,
                null
        );
    }

    private LedgerRow rowForDraft(TransferDraftRecord draft) {
        String account = draft.fromAccountName() + " -> " + draft.toAccountName();
        return new LedgerRow(
                RowKind.TRANSFER_DRAFT,
                draft.id(),
                safe(draft.transferDate()),
                blankToDash(draft.referenceNumber()),
                draftDescription(draft),
                "Draft",
                account,
                blankToDash(draft.feeCategoryName()),
                "",
                money(draft.fromCurrency(), draft.amountSent()),
                "",
                statusLabel(draft.status()),
                safe(draft.paymentMethod()),
                "-",
                "-",
                "TRANSFER_DRAFT",
                null,
                draft
        );
    }

    private void applyRunningBalances(List<LedgerRow> visibleRows, Account account, List<FinanceTransaction> allTransactions) {
        List<FinanceTransaction> accountTransactions = allTransactions.stream()
                .filter(transaction -> account.getAccountName().equals(transaction.getAccountName()))
                .sorted(Comparator.comparing(this::transactionDateOrMin).thenComparingInt(FinanceTransaction::getId))
                .toList();
        double balance = account.getOpeningBalance();
        for (FinanceTransaction transaction : accountTransactions) {
            if (affectsBalance(transaction)) {
                balance += balanceEffect(transaction);
            }
            double rowBalance = balance;
            visibleRows.stream()
                    .filter(row -> row.getKind() == RowKind.TRANSACTION && row.getId() == transaction.getId())
                    .findFirst()
                    .ifPresent(row -> row.setBalance(money(account.getCurrency(), rowBalance)));
        }
    }

    private boolean matchesFilters(LedgerRow row) {
        // Posted transactions are filtered in SQL. Draft transfer rows are local and need the same checks.
        String typeFilter = typeFilterBox.getValue();
        if (row.getKind() == RowKind.TRANSACTION) {
            return typeFilter == null || !typeFilter.equals("Draft");
        }
        if (!matchesPeriod(row.getDate())) {
            return false;
        }
        if (typeFilter != null && !ALL_TYPES.equals(typeFilter) && !typeFilter.equals(row.getType())) {
            if (!("Loan Repayment".equals(typeFilter) && row.isLoan())
                    && !("Loan Proceeds".equals(typeFilter) && row.isLoan())) {
                return false;
            }
        }
        String statusFilter = statusFilterBox.getValue();
        if (statusFilter != null && !ALL_STATUSES.equals(statusFilter) && !statusFilter.equals(row.getStatus())) {
            return false;
        }
        String categoryFilter = categoryFilterBox.getValue();
        if (categoryFilter != null && !ALL_CATEGORIES.equals(categoryFilter) && !categoryFilter.equals(row.getCategory())) {
            return false;
        }
        String query = safe(searchField.getText()).toLowerCase(Locale.ENGLISH);
        return query.isBlank() || row.searchText().contains(query);
    }

    private boolean matchesPeriod(String dateText) {
        PeriodRange range = selectedPeriodRange();
        if (range.startDate() == null && range.endDate() == null) {
            return true;
        }
        LocalDate date = parseDate(dateText);
        if (date == null) {
            return false;
        }
        return (range.startDate() == null || !date.isBefore(range.startDate()))
                && (range.endDate() == null || !date.isAfter(range.endDate()));
    }

    private PeriodRange selectedPeriodRange() {
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);
        return switch (safe(periodBox.getValue())) {
            case PERIOD_TODAY -> new PeriodRange(today, today);
            case PERIOD_THIS_WEEK -> {
                LocalDate start = today.minusDays(today.getDayOfWeek().getValue() - 1L);
                yield new PeriodRange(start, start.plusDays(6));
            }
            case PERIOD_LAST_MONTH -> {
                YearMonth previous = thisMonth.minusMonths(1);
                yield new PeriodRange(previous.atDay(1), previous.atEndOfMonth());
            }
            case PERIOD_LAST_3_MONTHS -> new PeriodRange(today.minusMonths(3).plusDays(1), today);
            case PERIOD_LAST_6_MONTHS -> new PeriodRange(today.minusMonths(6).plusDays(1), today);
            case PERIOD_THIS_YEAR -> new PeriodRange(LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31));
            case PERIOD_LAST_YEAR -> new PeriodRange(LocalDate.of(today.getYear() - 1, 1, 1), LocalDate.of(today.getYear() - 1, 12, 31));
            case PERIOD_CUSTOM -> {
                LocalDate from = fromDatePicker.getValue();
                LocalDate to = toDatePicker.getValue();
                if (from == null || to == null) {
                    throw new IllegalArgumentException("From Date and To Date are required for Custom Date Range.");
                }
                if (from.isAfter(to)) {
                    throw new IllegalArgumentException("From Date cannot be after To Date.");
                }
                yield new PeriodRange(from, to);
            }
            case PERIOD_ALL_TRANSACTIONS -> new PeriodRange(null, null);
            default -> new PeriodRange(thisMonth.atDay(1), thisMonth.atEndOfMonth());
        };
    }

    private String selectedTypeForQuery() {
        String selected = safe(typeFilterBox.getValue());
        return ALL_TYPES.equals(selected) || "Draft".equals(selected) ? "" : selected;
    }

    private String selectedStatusForQuery() {
        String selected = safe(statusFilterBox.getValue());
        return ALL_STATUSES.equals(selected) ? "" : selected;
    }

    private Integer selectedCategoryId() {
        String selected = safe(categoryFilterBox.getValue());
        if (selected.isBlank() || ALL_CATEGORIES.equals(selected)) {
            return null;
        }
        return categories.stream()
                .filter(category -> selected.equals(category.getCategoryName()))
                .map(Category::getId)
                .findFirst()
                .orElse(null);
    }

    private void updateCustomDateControls() {
        boolean custom = PERIOD_CUSTOM.equals(periodBox.getValue());
        fromDatePicker.setDisable(!custom);
        toDatePicker.setDisable(!custom);
    }

    private void updateFilterStatus(int visibleRows) {
        int from = currentTotalRecords == 0 ? 0 : currentOffset + 1;
        int to = Math.min(currentOffset + visibleRows, currentTotalRecords);
        String period = safe(periodBox.getValue());
        filterStatusLabel.setText("Showing " + from + "-" + to + " of " + currentTotalRecords
                + " transaction records for " + (period.isBlank() ? PERIOD_THIS_MONTH : period) + ".");
    }

    private String summaryText(LedgerRow row) {
        return row.getDate()
                + " | " + row.getType()
                + " | " + row.getAccount()
                + " | " + row.getStatus()
                + "\nReference: " + row.getReference()
                + "\nDescription: " + row.getDescription();
    }

    private String fullRecordText(LedgerRow row) {
        if (row.getKind() == RowKind.TRANSFER_DRAFT) {
            TransferDraftRecord draft = row.getDraft();
            return """
                    Transfer Draft

                    Draft ID: %d
                    Date: %s
                    From account: %s
                    To account: %s
                    Amount sent: %s
                    Amount received: %s
                    Transfer fee: %s
                    Fee category: %s
                    Exchange rate: %s
                    Payment method: %s
                    Reference: %s
                    Status: %s
                    Created: %s
                    Updated: %s

                    Description:
                    %s
                    """.formatted(
                    draft.id(),
                    draft.transferDate(),
                    draft.fromAccountName(),
                    draft.toAccountName(),
                    money(draft.fromCurrency(), draft.amountSent()),
                    money(draft.toCurrency(), draft.amountReceived()),
                    money(draft.fromCurrency(), draft.transferFee()),
                    blankToDash(draft.feeCategoryName()),
                    draft.exchangeRate() == null ? "-" : draft.exchangeRate().toString(),
                    blankToDash(draft.paymentMethod()),
                    blankToDash(draft.referenceNumber()),
                    statusLabel(draft.status()),
                    blankToDash(draft.createdAt()),
                    blankToDash(draft.updatedAt()),
                    blankToDash(draft.description())
            );
        }

        FinanceTransaction transaction = row.getTransaction();
        if (row.isTransfer()) {
            StringBuilder builder = new StringBuilder();
            builder.append("Transfer Details\n\n");
            builder.append("Opened transaction ID: ").append(transaction.getId()).append("\n");
            builder.append("Reference: ").append(blankToDash(transaction.getReferenceNumber())).append("\n");
            builder.append("Status: ").append(statusLabel(transaction.getTransactionStatus())).append("\n\n");
            for (FinanceTransaction linked : database.listLinkedTransactions(transaction.getId())) {
                builder.append(linked.getTransactionPurpose()).append(": ")
                        .append(directionPrefix(linked))
                        .append(money(currencyForAccount(linked.getAccountName()), linked.getAmount()))
                        .append(" | Account: ").append(linked.getAccountName())
                        .append(" | Status: ").append(statusLabel(linked.getTransactionStatus()))
                        .append("\n");
            }
            builder.append("\nDescription:\n").append(blankToDash(transaction.getDescription()));
            return builder.toString();
        }

        return """
                Transaction Details

                Transaction ID: %d
                Date: %s
                Type: %s
                Purpose: %s
                Amount: %s
                Account: %s
                Category: %s
                Payment method: %s
                Person or organisation: %s
                Project: %s
                Reference: %s
                Status: %s
                Created: %s
                Linked loan: %s
                Linked instalment: %s

                Description:
                %s
                """.formatted(
                transaction.getId(),
                transaction.getTransactionDate(),
                transaction.getTransactionType(),
                transaction.getTransactionPurpose(),
                money(currencyForAccount(transaction.getAccountName()), transaction.getAmount()),
                transaction.getAccountName(),
                blankToDash(transaction.getCategoryName()),
                blankToDash(transaction.getPaymentMethod()),
                blankToDash(transaction.getPersonName()),
                blankToDash(transaction.getProjectName()),
                blankToDash(transaction.getReferenceNumber()),
                statusLabel(transaction.getTransactionStatus()),
                blankToDash(transaction.getCreatedAt()),
                transaction.getLoanId() == null ? "-" : "#" + transaction.getLoanId(),
                transaction.getLoanInstallmentId() == null ? "-" : "#" + transaction.getLoanInstallmentId(),
                blankToDash(transaction.getDescription())
        );
    }

    private List<String> actionsFor(LedgerRow row) {
        if (row.getKind() == RowKind.TRANSFER_DRAFT) {
            if ("Cancelled".equals(row.getStatus())) {
                return List.of("View History", "Create Corrected Record");
            }
            return List.of("Edit Draft", "Post Transaction", "Discard Draft");
        }
        if (row.isTransfer()) {
            if (row.isCancelledOrReversed()) {
                return List.of("View History", "Create Corrected Record");
            }
            return List.of("View Transfer Details", "Request Correction", "Cancel Transfer", "Reverse Transfer", "Copy as New Transfer");
        }
        if (row.isCancelledOrReversed()) {
            return List.of("View History", "Create Corrected Record");
        }
        if ("Draft".equals(row.getStatus())) {
            return List.of("Request Correction", "Cancel Transaction");
        }
        return List.of("Request Correction", "Freeze Record", "Cancel Transaction", "Create Reversal", "Copy as New");
    }

    private void runAction(LedgerRow row, String action) {
        try {
            switch (action) {
                case "View Transfer Details", "View History" -> openRecord();
                case "Edit Draft" -> UiAlerts.info("Open Transfer Money to revise draft values before posting. This ledger screen keeps posted records read-only.");
                case "Post Transaction" -> postDraft(row);
                case "Discard Draft" -> discardDraft(row);
                case "Request Correction" -> requestCorrection(row);
                case "Freeze Record" -> updateTransactionStatus(row, "FROZEN", "freeze record");
                case "Cancel Transaction" -> updateTransactionStatus(row, "CANCELLED", "cancel transaction");
                case "Cancel Transfer" -> updateTransferStatus(row, "CANCELLED", "cancel transfer");
                case "Reverse Transfer" -> reverseTransfer(row);
                case "Create Reversal" -> reverseTransaction(row);
                case "Copy as New Transfer", "Create Corrected Record" -> createTransferDraft(row);
                case "Copy as New" -> requestCorrection(row);
                default -> UiAlerts.info("Action is not available.");
            }
        } catch (IllegalArgumentException | SecurityException exception) {
            UiAlerts.info(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to apply ledger action", exception);
        }
    }

    private void postDraft(LedgerRow row) {
        TransferDraftRecord draft = requireDraft(row);
        if (database.hasSimilarTransfer(draft.fromAccountId(), draft.toAccountId(), draft.amountSent(), LocalDate.parse(draft.transferDate()), draft.referenceNumber())
                && !UiAlerts.confirm("Possible duplicate transfer", "A similar posted transfer already exists. Continue posting this draft?")) {
            return;
        }
        TransferPostingResult result = database.recordTransferWithFee(
                draft.fromAccountId(),
                draft.toAccountId(),
                draft.amountSent(),
                draft.amountReceived(),
                draft.transferFee(),
                draft.feeCategoryId(),
                LocalDate.parse(draft.transferDate()),
                draft.description(),
                draft.paymentMethod(),
                draft.referenceNumber()
        );
        database.markTransferDraftPosted(draft.id(), result.outgoingTransactionId());
        refresh();
        DataRefreshBus.notifyDataChanged();
        UiAlerts.info("Transfer draft posted. Reference: " + result.transferReference());
    }

    private void discardDraft(LedgerRow row) {
        TransferDraftRecord draft = requireDraft(row);
        if (!UiAlerts.confirm("Discard draft", "Discard transfer draft #" + draft.id() + "?")) {
            return;
        }
        database.updateTransferDraftStatus(draft.id(), "Cancelled", "Discarded from View Ledger.");
        refresh();
    }

    private void requestCorrection(LedgerRow row) {
        database.recordSystemLog(
                "Transaction Ledger",
                "Correction Requested",
                "INFO",
                "Correction requested for " + row.getType() + " record " + row.getId() + "."
        );
        UiAlerts.info("Correction request recorded in audit history.");
    }

    private void updateTransactionStatus(LedgerRow row, String status, String actionLabel) {
        if (row.getKind() != RowKind.TRANSACTION) {
            UiAlerts.info("This action applies to posted ledger transactions.");
            return;
        }
        String reason = "Requested from View Ledger to " + actionLabel + ".";
        database.updateRecordLifecycleStatus("transaction", row.getId(), status, reason);
        refresh();
        DataRefreshBus.notifyDataChanged();
    }

    private void updateTransferStatus(LedgerRow row, String status, String actionLabel) {
        if (!row.isTransfer()) {
            UiAlerts.info("Select a transfer record.");
            return;
        }
        database.updateTransferGroupStatus(row.getId(), status, "Requested from View Ledger to " + actionLabel + ".");
        refresh();
        DataRefreshBus.notifyDataChanged();
    }

    private void reverseTransaction(LedgerRow row) {
        if (row.getKind() != RowKind.TRANSACTION || row.isTransfer()) {
            UiAlerts.info("Use Reverse Transfer for linked transfer records.");
            return;
        }
        int reversalId = database.createTransactionReversal(row.getId(), "Requested from View Ledger.");
        refresh();
        DataRefreshBus.notifyDataChanged();
        UiAlerts.info("Reversal transaction created: #" + reversalId);
    }

    private void reverseTransfer(LedgerRow row) {
        if (!row.isTransfer()) {
            UiAlerts.info("Select a transfer record.");
            return;
        }
        TransferPostingResult result = database.createTransferReversal(row.getId(), "Requested from View Ledger.");
        refresh();
        DataRefreshBus.notifyDataChanged();
        UiAlerts.info("Transfer reversal posted. Reference: " + result.transferReference());
    }

    private void createTransferDraft(LedgerRow row) {
        if (row.isTransfer()) {
            int draftId = database.createTransferDraftFromTransaction(row.getId(), "Created from View Ledger.");
            refresh();
            UiAlerts.info("Transfer draft created: #" + draftId);
            return;
        }
        requestCorrection(row);
    }

    private LedgerRow selectedRow() {
        return transactionsTable.getSelectionModel().getSelectedItem();
    }

    private TransferDraftRecord requireDraft(LedgerRow row) {
        if (row == null || row.getKind() != RowKind.TRANSFER_DRAFT || row.getDraft() == null) {
            throw new IllegalArgumentException("Select a transfer draft.");
        }
        return row.getDraft();
    }

    private String typeLabel(FinanceTransaction transaction) {
        String purpose = safe(transaction.getTransactionPurpose());
        String type = safe(transaction.getTransactionType());
        if ("OPENING_BALANCE".equals(type) || "OPENING_BALANCE".equals(purpose)) {
            return "Opening Balance";
        }
        if ("LOAN_PROCEEDS".equals(purpose)) {
            return "Loan Proceeds";
        }
        if (List.of("LOAN_PRINCIPAL_PAYMENT", "LOAN_INTEREST_PAYMENT", "LOAN_FEE", "LOAN_PENALTY", "LOAN_SETTLEMENT", "BORROWED_REPAID", "LENT_REPAID").contains(purpose)) {
            return "Loan Repayment";
        }
        if ("TRANSFER".equals(type)) {
            return "Transfer";
        }
        if ("LOAN".equals(type)) {
            return "Loan";
        }
        if ("EXPENSE".equals(type) && "TRANSFER_FEE".equals(purpose)) {
            return "Transfer Fee";
        }
        if ("ASSET_SALE".equals(type) || "ASSET_SALE_PROCEEDS".equals(purpose)) {
            return "Asset Sale";
        }
        if ("ADJUSTMENT".equals(type)) {
            return switch (purpose) {
                case "BALANCE_INCREASE" -> "Balance Increase";
                case "BALANCE_DECREASE" -> "Balance Decrease";
                default -> "Adjustment";
            };
        }
        if (isLoanPurpose(purpose)) {
            return "Loan";
        }
        return switch (type) {
            case "INCOME" -> "Income";
            case "EXPENSE" -> "Expense";
            default -> type;
        };
    }

    private String statusLabel(String status) {
        String cleanStatus = safe(status);
        return switch (cleanStatus) {
            case "COMPLETED", "CLEARED", "PARTIALLY_CLEARED", "Posted" -> "Posted";
            case "OPEN" -> "Open";
            case "Draft" -> "Draft";
            case "FROZEN" -> "Frozen";
            case "CANCELLED", "Cancelled" -> "Cancelled";
            case "REVERSED", "Reversed" -> "Reversed";
            default -> cleanStatus.isBlank() ? "Posted" : cleanStatus;
        };
    }

    private boolean isLoanPurpose(String purpose) {
        String cleanPurpose = safe(purpose);
        return List.of(
                "MONEY_LENT",
                "MONEY_BORROWED",
                "LENT_REPAID",
                "BORROWED_REPAID",
                "LOAN_PROCEEDS",
                "LOAN_PRINCIPAL_PAYMENT",
                "LOAN_INTEREST_PAYMENT",
                "LOAN_FEE",
                "LOAN_PENALTY",
                "LOAN_SETTLEMENT"
        ).contains(cleanPurpose);
    }

    private boolean isTransferIn(FinanceTransaction transaction) {
        return "TRANSFER".equals(transaction.getTransactionType()) && "TRANSFER_IN".equals(transaction.getTransactionPurpose());
    }

    private boolean isLoanIn(FinanceTransaction transaction) {
        return "LOAN".equals(transaction.getTransactionType())
                && List.of(
                        "MONEY_BORROWED",
                        "LENT_REPAID",
                        "LOAN_PROCEEDS",
                        "COMMUNITY_LOAN_RECEIVABLE_INCREASE",
                        "COMMUNITY_LOAN_LIABILITY_INCREASE"
                ).contains(safe(transaction.getTransactionPurpose()));
    }

    private boolean isLoanOut(FinanceTransaction transaction) {
        return "LOAN".equals(transaction.getTransactionType())
                && List.of(
                        "MONEY_LENT",
                        "BORROWED_REPAID",
                        "LOAN_PRINCIPAL_PAYMENT",
                        "LOAN_SETTLEMENT",
                        "COMMUNITY_LOAN_RECEIVABLE_DECREASE",
                        "COMMUNITY_LOAN_LIABILITY_DECREASE"
                ).contains(safe(transaction.getTransactionPurpose()));
    }

    private boolean isBalanceIncrease(FinanceTransaction transaction) {
        return "ADJUSTMENT".equals(transaction.getTransactionType())
                && "BALANCE_INCREASE".equals(transaction.getTransactionPurpose());
    }

    private boolean isBalanceDecrease(FinanceTransaction transaction) {
        return "ADJUSTMENT".equals(transaction.getTransactionType())
                && "BALANCE_DECREASE".equals(transaction.getTransactionPurpose());
    }

    private double moneyInAmount(FinanceTransaction transaction) {
        if ("INCOME".equals(transaction.getTransactionType())
                || "ASSET_SALE".equals(transaction.getTransactionType())
                || isTransferIn(transaction)
                || isLoanIn(transaction)
                || isBalanceIncrease(transaction)) {
            return transaction.getAmount();
        }
        return 0;
    }

    private double moneyOutAmount(FinanceTransaction transaction) {
        if ("EXPENSE".equals(transaction.getTransactionType())
                || ("TRANSFER".equals(transaction.getTransactionType()) && "TRANSFER_OUT".equals(transaction.getTransactionPurpose()))
                || isLoanOut(transaction)
                || isBalanceDecrease(transaction)) {
            return transaction.getAmount();
        }
        return 0;
    }

    private boolean affectsBalance(FinanceTransaction transaction) {
        if ("LOAN".equals(transaction.getTransactionType())) {
            return !List.of("CANCELLED", "REVERSED", "FROZEN").contains(safe(transaction.getTransactionStatus()));
        }
        return !List.of("OPEN", "CANCELLED", "REVERSED", "FROZEN").contains(safe(transaction.getTransactionStatus()));
    }

    private double balanceEffect(FinanceTransaction transaction) {
        if ("INCOME".equals(transaction.getTransactionType())
                || "ASSET_SALE".equals(transaction.getTransactionType())
                || isTransferIn(transaction)
                || isLoanIn(transaction)
                || isBalanceIncrease(transaction)) {
            return transaction.getAmount();
        }
        if ("EXPENSE".equals(transaction.getTransactionType())
                || ("TRANSFER".equals(transaction.getTransactionType()) && "TRANSFER_OUT".equals(transaction.getTransactionPurpose()))
                || isLoanOut(transaction)
                || isBalanceDecrease(transaction)) {
            return -transaction.getAmount();
        }
        return 0;
    }

    private LocalDate transactionDateOrMin(FinanceTransaction transaction) {
        LocalDate date = parseDate(transaction.getTransactionDate());
        return date == null ? LocalDate.MIN : date;
    }

    private String descriptionLabel(FinanceTransaction transaction) {
        if ("TRANSFER".equals(transaction.getTransactionType())) {
            return blankToDash(transaction.getDescription());
        }
        String description = safe(transaction.getDescription());
        if (!description.isBlank()) {
            return description;
        }
        return typeLabel(transaction) + " transaction";
    }

    private String draftDescription(TransferDraftRecord draft) {
        String description = safe(draft.description());
        if (!description.isBlank()) {
            return description;
        }
        return "Transfer draft from " + draft.fromAccountName() + " to " + draft.toAccountName();
    }

    private String directionPrefix(FinanceTransaction transaction) {
        if ("INCOME".equals(transaction.getTransactionType()) || isTransferIn(transaction) || isLoanIn(transaction)) {
            return "+";
        }
        if ("EXPENSE".equals(transaction.getTransactionType())
                || ("TRANSFER".equals(transaction.getTransactionType()) && "TRANSFER_OUT".equals(transaction.getTransactionPurpose()))
                || isLoanOut(transaction)) {
            return "-";
        }
        return "";
    }

    private String currencyForAccount(String accountName) {
        return accounts.stream()
                .filter(account -> account.getAccountName().equals(accountName))
                .map(Account::getCurrency)
                .findFirst()
                .orElse("MWK");
    }

    private String money(String currency, double amount) {
        String cleanCurrency = safe(currency);
        return (cleanCurrency.isBlank() ? "" : cleanCurrency + " ") + MONEY_FORMAT.format(amount);
    }

    private LocalDate parseDate(String date) {
        try {
            return date == null || date.isBlank() ? null : LocalDate.parse(date);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private enum RowKind {
        TRANSACTION,
        TRANSFER_DRAFT
    }

    record PeriodRange(LocalDate startDate, LocalDate endDate) {
    }

    public static final class LedgerRow {
        private final RowKind kind;
        private final int id;
        private final String date;
        private final String reference;
        private final String description;
        private final String type;
        private final String account;
        private final String category;
        private final String moneyIn;
        private final String moneyOut;
        private String balance;
        private final String status;
        private final String paymentMethod;
        private final String person;
        private final String project;
        private final String purpose;
        private final FinanceTransaction transaction;
        private final TransferDraftRecord draft;

        private LedgerRow(
                RowKind kind,
                int id,
                String date,
                String reference,
                String description,
                String type,
                String account,
                String category,
                String moneyIn,
                String moneyOut,
                String balance,
                String status,
                String paymentMethod,
                String person,
                String project,
                String purpose,
                FinanceTransaction transaction,
                TransferDraftRecord draft
        ) {
            this.kind = kind;
            this.id = id;
            this.date = date;
            this.reference = reference;
            this.description = description;
            this.type = type;
            this.account = account;
            this.category = category;
            this.moneyIn = moneyIn;
            this.moneyOut = moneyOut;
            this.balance = balance;
            this.status = status;
            this.paymentMethod = paymentMethod;
            this.person = person;
            this.project = project;
            this.purpose = purpose;
            this.transaction = transaction;
            this.draft = draft;
        }

        public RowKind getKind() {
            return kind;
        }

        public int getId() {
            return id;
        }

        public String getDate() {
            return date;
        }

        public String getReference() {
            return reference;
        }

        public String getDescription() {
            return description;
        }

        public String getType() {
            return type;
        }

        public String getAccount() {
            return account;
        }

        public String getCategory() {
            return category;
        }

        public String getMoneyIn() {
            return moneyIn;
        }

        public String getMoneyOut() {
            return moneyOut;
        }

        public String getBalance() {
            return balance;
        }

        public String getStatus() {
            return status;
        }

        public FinanceTransaction getTransaction() {
            return transaction;
        }

        public TransferDraftRecord getDraft() {
            return draft;
        }

        private void setBalance(String balance) {
            this.balance = balance;
        }

        private LocalDate sortDate() {
            try {
                return date == null || date.isBlank() ? LocalDate.MIN : LocalDate.parse(date);
            } catch (RuntimeException exception) {
                return LocalDate.MIN;
            }
        }

        private boolean isTransfer() {
            return "Transfer".equals(type) && transaction != null;
        }

        private boolean isLoan() {
            return "Loan".equals(type);
        }

        private boolean isCancelledOrReversed() {
            return "Cancelled".equals(status) || "Reversed".equals(status);
        }

        private String searchText() {
            return String.join(" ",
                    date,
                    reference,
                    description,
                    type,
                    account,
                    category,
                    status,
                    paymentMethod,
                    person,
                    project,
                    purpose
            ).toLowerCase(Locale.ENGLISH);
        }
    }
}
