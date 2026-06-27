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
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    @FXML private TableView<Account> accountsTable;
    @FXML private TableColumn<Account, String> accountNameColumn;
    @FXML private TableColumn<Account, String> accountTypeColumn;
    @FXML private TableColumn<Account, String> providerColumn;
    @FXML private TableColumn<Account, String> accountNumberColumn;
    @FXML private TableColumn<Account, String> currencyColumn;
    @FXML private TableColumn<Account, String> balanceColumn;
    @FXML private TableColumn<Account, String> accountStatusColumn;
    @FXML private TableView<FinanceTransaction> historyTable;
    @FXML private TableColumn<FinanceTransaction, String> dateColumn;
    @FXML private TableColumn<FinanceTransaction, String> typeColumn;
    @FXML private TableColumn<FinanceTransaction, String> categoryColumn;
    @FXML private TableColumn<FinanceTransaction, String> amountColumn;
    @FXML private TableColumn<FinanceTransaction, String> paymentMethodColumn;
    @FXML private TableColumn<FinanceTransaction, String> referenceColumn;
    @FXML private TableColumn<FinanceTransaction, String> statusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final ObservableList<Account> allAccounts = FXCollections.observableArrayList();
    private final ObservableList<FinanceTransaction> selectedAccountTransactions = FXCollections.observableArrayList();
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
        balanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getCurrentBalance())));
        accountStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionType"));
        categoryColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayCategory(cell.getValue())));
        amountColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmount())));
        paymentMethodColumn.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        referenceColumn.setCellValueFactory(new PropertyValueFactory<>("referenceNumber"));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayStatus(cell.getValue().getTransactionStatus())));

        filteredAccounts = new FilteredList<>(allAccounts);
        accountsTable.setItems(filteredAccounts);
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
        refresh();
    }

    @FXML
    private void refresh() {
        Integer requestedId = NavigationBus.consumeRequestedAccountHistoryId();
        Integer selectedId = requestedId != null
                ? requestedId
                : selectedAccountId();
        List<Account> accounts = database.listAccounts();
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
            historyTable.getItems().clear();
            return;
        }

        List<FinanceTransaction> transactions = database.listTransactionsForAccount(account.getId());
        selectedAccountTransactions.setAll(transactions);
        refreshSelectedAccountTransactionDates();
        refreshHistoryYearOptions();
        applyHistoryFilters();
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
        List<FinanceTransaction> filtered = selectedAccountTransactions.stream()
                .filter(transaction -> matchesHistoryPeriod(transaction, period))
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
        if ("CANCELLED".equals(status)) {
            return "Cancelled";
        }
        return "Completed";
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
