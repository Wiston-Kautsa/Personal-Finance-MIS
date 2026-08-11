package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.DateCell;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Set;

public class AccountHistoryController {
    @FXML private ComboBox<String> accountNameFilter;
    @FXML private ComboBox<String> providerFilter;
    @FXML private ComboBox<String> accountTypeFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private DatePicker accountDateFilter;
    @FXML private ComboBox<String> accountDateModeFilter;
    @FXML private DatePicker historyDatePicker;
    @FXML private ComboBox<String> historyPeriodFilter;
    @FXML private ComboBox<String> historyYearsFilter;
    @FXML private TextField historySearchField;
    @FXML private TableView<Account> accountsTable;
    @FXML private TableColumn<Account, String> accountNameColumn;
    @FXML private TableColumn<Account, String> accountTypeColumn;
    @FXML private TableColumn<Account, String> providerColumn;
    @FXML private TableColumn<Account, String> accountNumberColumn;
    @FXML private TableColumn<Account, String> currencyColumn;
    @FXML private TableColumn<Account, String> balanceColumn;
    @FXML private TableColumn<Account, String> accountStatusColumn;
    @FXML private TableView<LedgerRow> historyTable;
    @FXML private TableColumn<LedgerRow, String> dateColumn;
    @FXML private TableColumn<LedgerRow, String> typeColumn;
    @FXML private TableColumn<LedgerRow, String> categoryColumn;
    @FXML private TableColumn<LedgerRow, String> amountColumn;
    @FXML private TableColumn<LedgerRow, String> paymentMethodColumn;
    @FXML private TableColumn<LedgerRow, String> referenceColumn;
    @FXML private TableColumn<LedgerRow, String> runningBalanceColumn;
    @FXML private TableColumn<LedgerRow, String> statusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final ObservableList<Account> allAccounts = FXCollections.observableArrayList();
    private final ObservableList<FinanceTransaction> selectedAccountTransactions = FXCollections.observableArrayList();
    private final ObservableList<LedgerRow> selectedLedgerRows = FXCollections.observableArrayList();
    private final Map<Integer, Set<LocalDate>> accountTransactionDates = new HashMap<>();
    private final Set<LocalDate> allTransactionDates = new HashSet<>();
    private final Set<LocalDate> selectedAccountTransactionDates = new HashSet<>();
    private FilteredList<Account> filteredAccounts;

    @FXML
    public void initialize() {
        accountNameColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        accountTypeColumn.setCellValueFactory(new PropertyValueFactory<>("accountType"));
        providerColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getBankProviderName())));
        accountNumberColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getAccountNumber())));
        currencyColumn.setCellValueFactory(new PropertyValueFactory<>("currency"));
        balanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(cell.getValue().getCurrency(), cell.getValue().getCurrentBalance())));
        accountStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        dateColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().transaction().getTransactionDate()));
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().transaction().getTransactionType()));
        categoryColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayCategory(cell.getValue().transaction())));
        amountColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(cell.getValue().currency(), cell.getValue().transaction().getAmount())));
        paymentMethodColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().transaction().getPaymentMethod())));
        referenceColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().transaction().getReferenceNumber())));
        runningBalanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(cell.getValue().currency(), cell.getValue().runningBalance())));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayStatus(cell.getValue().transaction().getTransactionStatus())));

        filteredAccounts = new FilteredList<>(allAccounts);
        accountsTable.setItems(filteredAccounts);
        configureContextMenus();
        accountsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> refreshSelectedAccount());
        accountNameFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyAccountFilters());
        providerFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyAccountFilters());
        accountTypeFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyAccountFilters());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyAccountFilters());
        accountDateModeFilter.setItems(FXCollections.observableArrayList("All Dates", "Transaction Day", "Transaction Week", "Transaction Month", "Transaction Year"));
        accountDateModeFilter.getSelectionModel().select("All Dates");
        accountDateFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyAccountFilters());
        accountDateModeFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyAccountFilters());
        historyDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> applyHistoryFilters());
        historyPeriodFilter.setItems(FXCollections.observableArrayList("All Dates", "Selected Day", "Selected Week", "Selected Month", "Selected Year", "Last Number of Years"));
        historyPeriodFilter.getSelectionModel().select("All Dates");
        historyPeriodFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyHistoryFilters());
        historyYearsFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyHistoryFilters());
        historySearchField.textProperty().addListener((observable, oldValue, newValue) -> applyHistoryFilters());
        refresh();
    }

    @FXML
    private void refresh() {
        Integer requestedId = NavigationBus.consumeRequestedAccountHistoryId();
        Integer selectedId = requestedId != null
                ? requestedId
                : selectedAccountId();
        List<Account> accounts = database.listAccounts();
        if (requestedId != null && accounts.stream().noneMatch(account -> account.getId() == requestedId)) {
            try {
                accounts = new ArrayList<>(accounts);
                accounts.add(database.getInternalAccountById(requestedId));
            } catch (RuntimeException ignored) {
                // Normal account history should remain limited to user accounts unless an internal ledger was explicitly requested.
            }
        }
        allAccounts.setAll(accounts);
        refreshDatabaseTransactionDates(accounts);
        refreshFilterOptions(accounts);
        applyAccountFilters();
        selectAccount(selectedId);
    }

    private void refreshSelectedAccount() {
        Account account = accountsTable.getSelectionModel().getSelectedItem();
        if (account == null) {
            selectedAccountTransactions.clear();
            selectedLedgerRows.clear();
            historyTable.getItems().clear();
            return;
        }

        List<FinanceTransaction> transactions = database.listTransactionsForAccount(account.getId());
        selectedAccountTransactions.setAll(transactions);
        selectedLedgerRows.setAll(ledgerRows(account, transactions));
        refreshSelectedAccountTransactionDates();
        refreshHistoryYearOptions();
        applyHistoryFilters();
    }

    private void configureContextMenus() {
        TableActions.installRowContextMenu(accountsTable, this::accountMenuItems);
        TableActions.installRowContextMenu(historyTable, this::historyMenuItems);
    }

    private List<javafx.scene.control.MenuItem> accountMenuItems(Account account) {
        return List.of(
                TableActions.menuItem("View Account Details", () -> viewAccountDetails(account)),
                TableActions.menuItem("Refresh Account History", this::refreshSelectedAccount),
                TableActions.separator(),
                TableActions.copyRowItem(accountsTable, account),
                TableActions.exportTableItem(accountsTable, "Account History Accounts"),
                TableActions.printTableItem(accountsTable, "Account History Accounts"),
                TableActions.refreshItem(this::refresh)
        );
    }

    private List<javafx.scene.control.MenuItem> historyMenuItems(LedgerRow row) {
        return List.of(
                TableActions.menuItem("View Transaction Details", () -> viewTransactionDetails(row.transaction())),
                TableActions.separator(),
                TableActions.copyRowItem(historyTable, row),
                TableActions.exportTableItem(historyTable, selectedAccountTitle()),
                TableActions.printTableItem(historyTable, selectedAccountTitle()),
                TableActions.refreshItem(this::refreshSelectedAccount)
        );
    }

    private void viewAccountDetails(Account account) {
        if (account == null) {
            return;
        }
        UiAlerts.info(
                "Account: " + account.getAccountName()
                        + "\nType: " + account.getAccountType()
                        + "\nProvider: " + blankToDash(account.getBankProviderName())
                        + "\nAccount Number: " + blankToDash(account.getAccountNumber())
                        + "\nCurrency: " + blankToDash(account.getCurrency())
                        + "\nBalance: " + money(account.getCurrency(), account.getCurrentBalance())
                        + "\nStatus: " + blankToDash(account.getStatus())
        );
    }

    private void viewTransactionDetails(FinanceTransaction transaction) {
        if (transaction == null) {
            return;
        }
        UiAlerts.info(
                "Date: " + transaction.getTransactionDate()
                        + "\nType: " + transaction.getTransactionType()
                        + "\nCategory/Project: " + displayCategory(transaction)
                        + "\nAmount: " + money(selectedAccountCurrency(), transaction.getAmount())
                        + "\nMethod: " + blankToDash(transaction.getPaymentMethod())
                        + "\nReference: " + blankToDash(transaction.getReferenceNumber())
                        + "\nStatus: " + displayStatus(transaction.getTransactionStatus())
                        + "\nDescription: " + blankToDash(transaction.getDescription())
        );
    }

    private String selectedAccountTitle() {
        Account account = accountsTable.getSelectionModel().getSelectedItem();
        return account == null ? "Account Transactions" : account.getAccountName() + " Transactions";
    }

    private String selectedAccountCurrency() {
        Account account = accountsTable.getSelectionModel().getSelectedItem();
        return account == null ? "MWK" : account.getCurrency();
    }

    private Integer selectedAccountId() {
        Account selected = accountsTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    private void refreshDatabaseTransactionDates(List<Account> accounts) {
        allTransactionDates.clear();
        database.listTransactionDates().stream()
                .map(this::parseDate)
                .filter(date -> date != null)
                .forEach(allTransactionDates::add);

        accountTransactionDates.clear();
        for (Account account : accounts) {
            Set<LocalDate> dates = new HashSet<>();
            database.listTransactionsForAccount(account.getId()).stream()
                    .map(FinanceTransaction::getTransactionDate)
                    .map(this::parseDate)
                    .filter(date -> date != null)
                    .forEach(dates::add);
            accountTransactionDates.put(account.getId(), dates);
        }
        configureDatePicker(accountDateFilter, allTransactionDates);
    }

    private void refreshFilterOptions(List<Account> accounts) {
        String selectedName = accountNameFilter.getValue();
        String selectedProvider = providerFilter.getValue();
        String selectedType = accountTypeFilter.getValue();
        String selectedStatus = statusFilter.getValue();

        Set<String> names = new LinkedHashSet<>();
        Set<String> providers = new LinkedHashSet<>();
        Set<String> types = new LinkedHashSet<>();
        Set<String> statuses = new LinkedHashSet<>();
        accounts.forEach(account -> {
            if (account.getAccountName() != null && !account.getAccountName().isBlank()) {
                names.add(account.getAccountName());
            }
            if (account.getBankProviderName() != null && !account.getBankProviderName().isBlank()) {
                providers.add(account.getBankProviderName());
            }
            if (account.getAccountType() != null && !account.getAccountType().isBlank()) {
                types.add(account.getAccountType());
            }
            if (account.getStatus() != null && !account.getStatus().isBlank()) {
                statuses.add(account.getStatus());
            }
        });

        List<String> nameOptions = new ArrayList<>();
        nameOptions.add("All Accounts");
        nameOptions.addAll(names);
        accountNameFilter.setItems(FXCollections.observableArrayList(nameOptions));
        accountNameFilter.setValue(nameOptions.contains(selectedName) ? selectedName : "All Accounts");

        List<String> providerOptions = new ArrayList<>();
        providerOptions.add("All Providers");
        providerOptions.addAll(providers);
        providerFilter.setItems(FXCollections.observableArrayList(providerOptions));
        providerFilter.setValue(providerOptions.contains(selectedProvider) ? selectedProvider : "All Providers");

        List<String> typeOptions = new ArrayList<>();
        typeOptions.add("All Types");
        typeOptions.addAll(types);
        accountTypeFilter.setItems(FXCollections.observableArrayList(typeOptions));
        accountTypeFilter.setValue(typeOptions.contains(selectedType) ? selectedType : "All Types");

        List<String> statusOptions = new ArrayList<>();
        statusOptions.add("All Statuses");
        statusOptions.addAll(statuses);
        statusFilter.setItems(FXCollections.observableArrayList(statusOptions));
        statusFilter.setValue(statusOptions.contains(selectedStatus) ? selectedStatus : "All Statuses");
    }

    private void applyAccountFilters() {
        if (filteredAccounts == null) {
            return;
        }
        String accountName = accountNameFilter.getValue();
        String provider = providerFilter.getValue();
        String type = accountTypeFilter.getValue();
        String status = statusFilter.getValue();
        String dateMode = accountDateModeFilter.getValue();
        filteredAccounts.setPredicate(account -> {
            boolean matchesName = accountName == null || "All Accounts".equals(accountName) || accountName.equals(account.getAccountName());
            boolean matchesProvider = provider == null || "All Providers".equals(provider) || provider.equals(account.getBankProviderName());
            boolean matchesType = type == null || "All Types".equals(type) || type.equals(account.getAccountType());
            boolean matchesStatus = status == null || "All Statuses".equals(status) || status.equals(account.getStatus());
            boolean matchesDate = matchesAccountDate(account, dateMode);
            return matchesName && matchesProvider && matchesType && matchesStatus && matchesDate;
        });
        selectAccount(accountsTable.getSelectionModel().getSelectedItem() == null
                ? null
                : accountsTable.getSelectionModel().getSelectedItem().getId());
    }

    private void selectAccount(Integer selectedId) {
        Account selected = filteredAccounts.stream()
                .filter(account -> selectedId != null && account.getId() == selectedId)
                .findFirst()
                .orElse(filteredAccounts.isEmpty() ? null : filteredAccounts.get(0));
        if (selected == null) {
            accountsTable.getSelectionModel().clearSelection();
            historyTable.getItems().clear();
        } else {
            accountsTable.getSelectionModel().select(selected);
        }
    }

    private void applyHistoryFilters() {
        String period = historyPeriodFilter.getValue();
        List<LedgerRow> filtered = selectedLedgerRows.stream()
                .filter(row -> matchesHistoryPeriod(row.transaction(), period))
                .filter(this::matchesHistorySearch)
                .toList();
        historyTable.setItems(FXCollections.observableArrayList(filtered));
    }

    private void refreshSelectedAccountTransactionDates() {
        selectedAccountTransactionDates.clear();
        selectedAccountTransactions.stream()
                .map(FinanceTransaction::getTransactionDate)
                .map(this::parseDate)
                .filter(date -> date != null)
                .forEach(selectedAccountTransactionDates::add);
        configureDatePicker(historyDatePicker, selectedAccountTransactionDates);
    }

    private void configureDatePicker(DatePicker datePicker, Set<LocalDate> databaseDates) {
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || !databaseDates.contains(date));
            }
        });
        if (datePicker.getValue() != null && !databaseDates.contains(datePicker.getValue())) {
            datePicker.setValue(null);
        }
    }

    private boolean matchesAccountDate(Account account, String dateMode) {
        if (dateMode == null || "All Dates".equals(dateMode)) {
            return true;
        }
        LocalDate selectedDate = accountDateFilter.getValue();
        if (selectedDate == null) {
            return true;
        }
        Set<LocalDate> dates = accountTransactionDates.getOrDefault(account.getId(), Set.of());
        if (dates.isEmpty()) {
            return false;
        }
        return dates.stream().anyMatch(transactionDate -> switch (dateMode) {
            case "Transaction Day" -> transactionDate.equals(selectedDate);
            case "Transaction Week" -> isSameWeek(transactionDate, selectedDate);
            case "Transaction Month" -> transactionDate.getYear() == selectedDate.getYear()
                    && transactionDate.getMonth() == selectedDate.getMonth();
            case "Transaction Year" -> transactionDate.getYear() == selectedDate.getYear();
            default -> true;
        });
    }

    private void refreshHistoryYearOptions() {
        String selectedYears = historyYearsFilter.getValue();
        int maxYears = selectedAccountTransactions.stream()
                .map(FinanceTransaction::getTransactionDate)
                .map(this::parseDate)
                .filter(date -> date != null)
                .mapToInt(date -> LocalDate.now().getYear() - date.getYear() + 1)
                .max()
                .orElse(1);

        List<String> yearOptions = new ArrayList<>();
        for (int year = 1; year <= maxYears; year++) {
            yearOptions.add(year == 1 ? "1 Year" : year + " Years");
        }
        historyYearsFilter.setItems(FXCollections.observableArrayList(yearOptions));
        historyYearsFilter.setValue(yearOptions.contains(selectedYears) ? selectedYears : yearOptions.get(0));
    }

    private boolean matchesHistoryPeriod(FinanceTransaction transaction, String period) {
        if (period == null || "All Dates".equals(period)) {
            return true;
        }
        LocalDate transactionDate = parseDate(transaction.getTransactionDate());
        if (transactionDate == null) {
            return false;
        }
        LocalDate selectedDate = historyDatePicker.getValue() == null ? LocalDate.now() : historyDatePicker.getValue();
        return switch (period) {
            case "Selected Day" -> transactionDate.equals(selectedDate);
            case "Selected Week" -> isSameWeek(transactionDate, selectedDate);
            case "Selected Month" -> transactionDate.getYear() == selectedDate.getYear()
                    && transactionDate.getMonth() == selectedDate.getMonth();
            case "Selected Year" -> transactionDate.getYear() == selectedDate.getYear();
            case "Last Number of Years" -> !transactionDate.isBefore(LocalDate.now().minusYears(selectedYears()));
            default -> true;
        };
    }

    private List<LedgerRow> ledgerRows(Account account, List<FinanceTransaction> transactions) {
        Map<Integer, Double> runningBalances = new HashMap<>();
        List<FinanceTransaction> chronological = transactions.stream()
                .sorted(Comparator
                        .comparing((FinanceTransaction transaction) -> parseDate(transaction.getTransactionDate()), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(FinanceTransaction::getId))
                .toList();
        double balance = account.getOpeningBalance();
        for (FinanceTransaction transaction : chronological) {
            balance += signedAmount(transaction);
            runningBalances.put(transaction.getId(), balance);
        }
        return transactions.stream()
                .map(transaction -> new LedgerRow(
                        transaction,
                        runningBalances.getOrDefault(transaction.getId(), account.getCurrentBalance()),
                        account.getCurrency()
                ))
                .toList();
    }

    private double signedAmount(FinanceTransaction transaction) {
        String type = blankAs(transaction.getTransactionType(), "").toUpperCase(Locale.ENGLISH);
        String purpose = blankAs(transaction.getTransactionPurpose(), "").toUpperCase(Locale.ENGLISH);
        if ("INCOME".equals(type) || "ASSET_SALE".equals(type) || ("TRANSFER".equals(type) && "TRANSFER_IN".equals(purpose))) {
            return transaction.getAmount();
        }
        if ("EXPENSE".equals(type) || ("TRANSFER".equals(type) && "TRANSFER_OUT".equals(purpose))) {
            return -transaction.getAmount();
        }
        if ("LOAN".equals(type) && List.of("MONEY_BORROWED", "LENT_REPAID").contains(purpose)) {
            return transaction.getAmount();
        }
        if ("LOAN".equals(type) && List.of("MONEY_LENT", "BORROWED_REPAID").contains(purpose)) {
            return -transaction.getAmount();
        }
        return 0;
    }

    private boolean matchesHistorySearch(LedgerRow row) {
        String search = historySearchField.getText() == null ? "" : historySearchField.getText().trim().toLowerCase(Locale.ENGLISH);
        if (search.isBlank()) {
            return true;
        }
        FinanceTransaction transaction = row.transaction();
        return contains(transaction.getTransactionDate(), search)
                || contains(transaction.getTransactionType(), search)
                || contains(transaction.getTransactionPurpose(), search)
                || contains(displayCategory(transaction), search)
                || contains(transaction.getPaymentMethod(), search)
                || contains(transaction.getReferenceNumber(), search)
                || contains(transaction.getDescription(), search)
                || contains(displayStatus(transaction.getTransactionStatus()), search);
    }

    private boolean isSameWeek(LocalDate transactionDate, LocalDate selectedDate) {
        LocalDate weekStart = selectedDate.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = selectedDate.with(DayOfWeek.SUNDAY);
        return !transactionDate.isBefore(weekStart) && !transactionDate.isAfter(weekEnd);
    }

    private int selectedYears() {
        String years = historyYearsFilter.getValue();
        if (years == null || years.isBlank()) {
            return 1;
        }
        return Integer.parseInt(years.split(" ")[0]);
    }

    private LocalDate parseDate(String date) {
        try {
            if (date == null || date.isBlank()) {
                return null;
            }
            return LocalDate.parse(date.length() >= 10 ? date.substring(0, 10) : date);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String displayCategory(FinanceTransaction transaction) {
        if (transaction.getCategoryName() != null && !transaction.getCategoryName().isBlank()) {
            return transaction.getCategoryName();
        }
        if (transaction.getProjectName() != null && !transaction.getProjectName().isBlank()) {
            return transaction.getProjectName();
        }
        return "-";
    }

    private String displayStatus(String status) {
        if ("OPEN".equals(status)) {
            return "Pending";
        }
        if ("PARTIALLY_CLEARED".equals(status)) {
            return "Partially Cleared";
        }
        if ("CLEARED".equals(status)) {
            return "Cleared";
        }
        if ("CANCELLED".equals(status)) {
            return "Cancelled";
        }
        return "Completed";
    }

    private String money(String currency, double amount) {
        String code = currency == null || currency.isBlank() ? "MWK" : currency.trim().toUpperCase(Locale.ENGLISH);
        if ("MWK".equals(code)) {
            return MoneyUtil.mwk(amount);
        }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return code + " " + format.format(amount);
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ENGLISH).contains(search);
    }

    private String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private record LedgerRow(FinanceTransaction transaction, double runningBalance, String currency) {
    }
}
