package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.AccountReconciliationRecord;
import com.wk.pfmis.models.CurrencyRecord;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.time.LocalDate;
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
import java.util.stream.Collectors;

public class AccountsController {
    private static final String ALL_STATUSES = "All statuses";
    private static final String ALL_TYPES = "All types";
    private static final String ALL_CURRENCIES = "All currencies";
    private static final String DEFAULT_ACCOUNT_SETTING = "accounts.default";
    private static final double EPSILON = 0.005;

    @FXML private TitledPane accountRecordsPane;
    @FXML private TitledPane accountFormPane;
    @FXML private Button saveAccountButton;
    @FXML private Label totalAccountsLabel;
    @FXML private Label activeAccountsOverviewLabel;
    @FXML private Label needsReconciliationLabel;
    @FXML private Label defaultCurrencyLabel;
    @FXML private Label totalBalanceLabel;
    @FXML private ComboBox<String> statusFilterBox;
    @FXML private ComboBox<String> accountTypeFilterBox;
    @FXML private ComboBox<String> currencyFilterBox;
    @FXML private TextField searchField;
    @FXML private TableView<Account> accountsTable;
    @FXML private TableColumn<Account, String> nameColumn;
    @FXML private TableColumn<Account, String> defaultColumn;
    @FXML private TableColumn<Account, String> typeColumn;
    @FXML private TableColumn<Account, String> providerColumn;
    @FXML private TableColumn<Account, String> currencyColumn;
    @FXML private TableColumn<Account, String> balanceColumn;
    @FXML private TableColumn<Account, String> reconciliationColumn;
    @FXML private TableColumn<Account, String> statusColumn;
    @FXML private TextArea accountDetailsArea;
    @FXML private TextField accountNameField;
    @FXML private ComboBox<String> accountTypeBox;
    @FXML private ComboBox<String> currencyBox;
    @FXML private Label providerLabel;
    @FXML private TextField bankProviderField;
    @FXML private Label accountNumberLabel;
    @FXML private TextField accountNumberField;
    @FXML private Label branchLabel;
    @FXML private TextField branchField;
    @FXML private TextField openingBalanceField;
    @FXML private DatePicker openingBalanceDatePicker;
    @FXML private TextField minimumBalanceField;
    @FXML private ComboBox<String> accountPurposeBox;
    @FXML private TextArea notesArea;
    @FXML private Button openAccountButton;
    @FXML private Button editAccountButton;
    @FXML private Button viewLedgerButton;
    @FXML private Button reconcileButton;
    @FXML private Button setDefaultButton;
    @FXML private Button freezeToggleButton;
    @FXML private Button closeAccountButton;
    @FXML private Button archiveAccountButton;
    @FXML private VBox lifecycleInlinePane;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final ObservableList<Account> accounts = FXCollections.observableArrayList();
    private final Map<Integer, AccountReconciliationRecord> latestReconciliations = new LinkedHashMap<>();
    private List<String> currencyOptions = List.of();
    private boolean updatingCurrencyBox;
    private Account editingAccount;
    private int defaultAccountId = -1;

    @FXML
    public void initialize() {
        configureTable();
        configureFilters();
        configureFormControls();
        configureAccountContextMenu();
        refresh();
        clearForm();
        applyRequestedMode();
    }

    @FXML
    private void addAccount() {
        boolean creating = editingAccount == null;
        Integer focusAccountId;
        try {
            validateRequiredAccountFields();
            String name = required(accountNameField, "Enter an account name.");
            String accountType = accountType();
            String currency = currencyCode();
            double minimumBalance = parseOptionalAmount(minimumBalanceField, "Minimum balance");
            String provider = providerForType(accountType);
            String accountNumber = textValue(accountNumberField);
            String branch = textValue(branchField);
            String purpose = selectedText(accountPurposeBox);
            String notes = notesValue();

            if (creating) {
                double openingBalance = parseOptionalAmount(openingBalanceField, "Opening balance");
                LocalDate openingDate = openingBalanceDatePicker.getValue() == null
                        ? LocalDate.now()
                        : openingBalanceDatePicker.getValue();
                validateNewAccount(accountType, provider, accountNumber, openingBalance);
                if (database.accountIdentityExists(null, name, accountType, provider, accountNumber)) {
                    UiAlerts.info("An active account with the same account identity already exists. Refresh the account list before trying again.");
                    return;
                }
                if (possibleDuplicate(null, name, accountType, provider, accountNumber)
                        && !UiAlerts.confirm("Possible duplicate account", "An account with similar name, provider, type or ending digits already exists. Create it anyway?")) {
                    return;
                }
                int accountId;
                try {
                    accountId = database.addAccount(
                            name,
                            accountType,
                            currency,
                            provider,
                            accountNumber,
                            openingBalance,
                            openingDate.toString(),
                            minimumBalance,
                            purpose,
                            branch,
                            "ACTIVE",
                            notes
                    );
                } catch (RuntimeException creationException) {
                    UiAlerts.error("Account could not be created", creationException);
                    return;
                }
                focusAccountId = accountId;
                accountDetailsArea.setText("Account #" + accountId + " created. Opening balance evidence was recorded where applicable.");
            } else {
                focusAccountId = editingAccount.getId();
                try {
                    database.updateAccount(
                            editingAccount.getId(),
                            name,
                            editingAccount.getAccountType(),
                            editingAccount.getCurrency(),
                            provider,
                            accountNumber,
                            editingAccount.getOpeningBalance(),
                            editingAccount.getOpeningBalanceDate(),
                            minimumBalance,
                            purpose,
                            branch,
                            editingAccount.getStatus(),
                            notes
                    );
                } catch (RuntimeException updateException) {
                    UiAlerts.error("Account could not be updated", updateException);
                    return;
                }
                accountDetailsArea.setText("Account details updated. Financial fields were preserved.");
            }
        } catch (RuntimeException validationException) {
            UiAlerts.error(creating ? "Account could not be created" : "Account could not be updated", validationException);
            return;
        }

        try {
            clearForm();
            accountFormPane.setExpanded(false);
            accountRecordsPane.setExpanded(true);
            refresh();
            selectAccountById(focusAccountId);
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException refreshException) {
            database.recordSystemLog(
                    "Accounts",
                    creating ? "Account refresh failed after create" : "Account refresh failed after update",
                    "ERROR",
                    UiAlerts.rootMessage(refreshException)
            );
            UiAlerts.error(
                    creating
                            ? "Account was created successfully, but the account list could not be refreshed"
                            : "Account was updated successfully, but the account list could not be refreshed",
                    refreshException
            );
        }
    }

    @FXML
    private void clearForm() {
        accountNameField.clear();
        accountTypeBox.getEditor().clear();
        bankProviderField.clear();
        accountNumberField.clear();
        branchField.clear();
        openingBalanceField.setText("0.00");
        openingBalanceDatePicker.setValue(LocalDate.now());
        minimumBalanceField.setText("0.00");
        accountPurposeBox.getSelectionModel().select("General use");
        notesArea.clear();
        if (!currencyOptions.isEmpty()) {
            setCurrencyItems(currencyOptions);
        }
        setCurrencyValue(database.getDefaultCurrency());
        editingAccount = null;
        setEditMode(false);
        updateTypeSpecificFields();
        accountFormPane.setExpanded(false);
    }

    @FXML
    private void openAccount() {
        Account selected = selectedAccountOrNotify("Select an account to open.");
        if (selected == null) {
            return;
        }
        accountDetailsArea.setText(accountSummary(selected));
        showAccountDetailsDialog(selected);
    }

    @FXML
    private void reconcileAccount() {
        Account selected = selectedAccountOrNotify("Select an account to reconcile.");
        if (selected == null) {
            return;
        }
        if (!List.of("ACTIVE", "FROZEN").contains(normalizedStatus(selected))) {
            UiAlerts.info("Closed and archived accounts keep reconciliation history as read-only records.");
            return;
        }
        openAccountReconciliation(selected);
    }

    @FXML
    private void openNewAccountForm() {
        clearForm();
        accountsTable.getSelectionModel().clearSelection();
        accountDetailsArea.setText("Create a new account. Required fields are marked with *.");
        accountRecordsPane.setExpanded(false);
        accountFormPane.setText("New Account");
        saveAccountButton.setText("Create Account");
        accountFormPane.setExpanded(true);
        updateActionButtons(null);
        Platform.runLater(accountNameField::requestFocus);
    }

    @FXML
    private void editSelectedAccount() {
        Account selected = selectedAccountOrNotify("Select an account to edit.");
        if (selected == null) {
            return;
        }
        editAccount(selected);
    }

    @FXML
    private void viewSelectedAccountLedger() {
        Account selected = selectedAccountOrNotify("Select an account before opening the ledger.");
        if (selected == null) {
            return;
        }
        NavigationBus.showAccountHistory(selected.getId());
    }

    @FXML
    private void setSelectedAsDefault() {
        Account selected = selectedAccountOrNotify("Select an account to set as default.");
        if (selected == null) {
            return;
        }
        setDefaultAccount(selected);
    }

    @FXML
    private void toggleSelectedAccountFreeze() {
        Account selected = selectedAccountOrNotify("Select an account to freeze or unfreeze.");
        if (selected == null) {
            return;
        }
        String status = normalizedStatus(selected);
        if ("ACTIVE".equals(status)) {
            updateLifecycleWithConfirmation(
                    selected,
                    "FROZEN",
                    "Freezing prevents new ordinary transactions while preserving the account and its history."
            );
        } else if ("FROZEN".equals(status)) {
            updateLifecycle(selected, "ACTIVE");
        }
    }

    @FXML
    private void closeSelectedAccount() {
        Account selected = selectedAccountOrNotify("Select an account to close.");
        if (selected == null) {
            return;
        }
        closeAccount(selected);
    }

    @FXML
    private void archiveSelectedAccount() {
        Account selected = selectedAccountOrNotify("Select an account to archive.");
        if (selected == null) {
            return;
        }
        updateLifecycleWithConfirmation(
                selected,
                "ARCHIVED",
                "Archiving removes the account from normal active views but preserves records for reporting and audit."
        );
    }

    @FXML
    private void refresh() {
        defaultAccountId = defaultAccountId();
        accountTypeBox.setItems(FXCollections.observableArrayList(database.listAccountTypeSuggestions()));
        String selectedCurrency = currencyBox.getEditor().getText();
        currencyOptions = database.listCurrencySuggestions();
        setCurrencyItems(currencyOptions);
        setCurrencyValue(selectedCurrency == null || selectedCurrency.isBlank() ? database.getDefaultCurrency() : selectedCurrency);
        accounts.setAll(database.listAccounts());
        latestReconciliations.clear();
        for (AccountReconciliationRecord record : database.listLatestAccountReconciliations()) {
            latestReconciliations.put(record.getAccountId(), record);
        }
        refreshFilterOptions();
        applyFilters();
        refreshSummary();
        updateTypeSpecificFields();
        updateActionButtons(selectedAccount());
    }

    @FXML
    private void newAccount() {
        openNewAccountForm();
    }

    private void applyRequestedMode() {
        String mode = NavigationBus.consumeRequestedAccountsMode();
        if ("ADD".equalsIgnoreCase(mode)) {
            openNewAccountForm();
            return;
        }
        accountRecordsPane.setText("Account Records");
        accountRecordsPane.setExpanded(true);
        accountFormPane.setText("New Account");
        accountFormPane.setExpanded(false);
        accountsTable.getSelectionModel().clearSelection();
        accountDetailsArea.setText("Select an account to open details, edit, view the ledger, reconcile, or manage lifecycle status.");
        updateActionButtons(null);
    }

    private void updateActionButtons(Account account) {
        boolean selected = account != null;
        String status = selected ? normalizedStatus(account) : "";
        boolean active = "ACTIVE".equals(status);
        boolean frozen = "FROZEN".equals(status);
        boolean closed = "CLOSED".equals(status);
        boolean archived = "ARCHIVED".equals(status);
        setDisabled(openAccountButton, !selected);
        setDisabled(editAccountButton, !(active || frozen));
        setDisabled(viewLedgerButton, !selected);
        setDisabled(reconcileButton, !(active || frozen));
        setDisabled(setDefaultButton, !active || account.getId() == defaultAccountId);
        setDisabled(freezeToggleButton, !(active || frozen));
        setDisabled(closeAccountButton, !(active || frozen));
        setDisabled(archiveAccountButton, !selected || archived);
        if (freezeToggleButton != null) {
            freezeToggleButton.setText(frozen ? "Unfreeze Account" : "Freeze Account");
        }
        if (accountDetailsArea != null && selected && (closed || archived)) {
            accountDetailsArea.setText(accountSummary(account));
        }
    }

    private void setDisabled(Button button, boolean disabled) {
        if (button != null) {
            button.setDisable(disabled);
        }
    }

    private int defaultAccountId() {
        try {
            String value = database.getAppSetting(DEFAULT_ACCOUNT_SETTING, "");
            return value == null || value.isBlank() ? -1 : Integer.parseInt(value.trim());
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private void configureTable() {
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAccountName()));
        defaultColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId() == defaultAccountId ? "Default" : ""));
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getAccountType())));
        providerColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getBankProviderName())));
        currencyColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getCurrency())));
        balanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(cell.getValue().getCurrency(), cell.getValue().getCurrentBalance())));
        reconciliationColumn.setCellValueFactory(cell -> new SimpleStringProperty(reconciliationStatus(cell.getValue())));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayStatus(cell.getValue().getStatus())));
        accountsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            NavigationBus.rememberSelectedAccountId(newValue == null ? null : newValue.getId());
            clearLifecycleInlinePane();
            if (newValue != null) {
                accountDetailsArea.setText(accountSummary(newValue));
            } else {
                accountDetailsArea.setText("Select an account to open details, edit, view the ledger, reconcile, or manage lifecycle status.");
            }
            updateActionButtons(newValue);
        });
    }

    private void configureFilters() {
        statusFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        accountTypeFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        currencyFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
    }

    private void configureFormControls() {
        accountTypeBox.setEditable(true);
        currencyBox.setEditable(true);
        accountPurposeBox.setEditable(true);
        accountPurposeBox.setItems(FXCollections.observableArrayList(
                "Salary",
                "Household expenses",
                "Emergency fund",
                "Project funds",
                "Business",
                "Savings",
                "General use",
                "Other"
        ));
        accountTypeBox.valueProperty().addListener((observable, oldValue, newValue) -> updateTypeSpecificFields());
        accountTypeBox.getEditor().textProperty().addListener((observable, oldValue, newValue) -> updateTypeSpecificFields());
        accountNameField.textProperty().addListener((observable, oldValue, newValue) -> clearFieldError(accountNameField));
        accountTypeBox.valueProperty().addListener((observable, oldValue, newValue) -> clearFieldError(accountTypeBox));
        accountTypeBox.getEditor().textProperty().addListener((observable, oldValue, newValue) -> clearFieldError(accountTypeBox));
        currencyBox.valueProperty().addListener((observable, oldValue, newValue) -> clearFieldError(currencyBox));
        currencyBox.getEditor().textProperty().addListener((observable, oldValue, newValue) -> clearFieldError(currencyBox));
        openingBalanceField.textProperty().addListener((observable, oldValue, newValue) -> clearFieldError(openingBalanceField));
        openingBalanceDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> clearFieldError(openingBalanceDatePicker));
        configureCurrencySearch();
    }

    private void configureAccountContextMenu() {
        TableActions.installRowContextMenu(accountsTable, account -> {
            List<javafx.scene.control.MenuItem> items = new ArrayList<>();
            items.add(TableActions.menuItem("Open Account", () -> {
                accountsTable.getSelectionModel().select(account);
                openAccount();
            }));
            if (List.of("ACTIVE", "FROZEN").contains(normalizedStatus(account))) {
                items.add(TableActions.menuItem("Reconcile", () -> {
                    accountsTable.getSelectionModel().select(account);
                    reconcileAccount();
                }));
            }
            items.add(TableActions.separator());
            for (String action : actionsFor(account)) {
                items.add(TableActions.menuItem(action, () -> handleAction(account, action)));
            }
            items.add(TableActions.separator());
            items.add(TableActions.copyRowItem(accountsTable, account));
            items.add(TableActions.exportTableItem(accountsTable, "Accounts"));
            items.add(TableActions.printTableItem(accountsTable, "Accounts"));
            items.add(TableActions.refreshItem(this::refresh));
            return items;
        }, this::showAccountDetailsDialog);
    }

    private void refreshFilterOptions() {
        String selectedStatus = statusFilterBox.getValue();
        String selectedType = accountTypeFilterBox.getValue();
        String selectedCurrency = currencyFilterBox.getValue();

        List<String> statuses = new ArrayList<>(List.of(ALL_STATUSES, "Active", "Frozen", "Closed", "Archived", "Inactive"));
        accounts.stream()
                .map(account -> displayStatus(account.getStatus()))
                .filter(value -> !statuses.contains(value))
                .sorted()
                .forEach(statuses::add);
        statusFilterBox.setItems(FXCollections.observableArrayList(statuses));
        statusFilterBox.setValue(statuses.contains(selectedStatus) ? selectedStatus : "Active");

        List<String> types = new ArrayList<>();
        types.add(ALL_TYPES);
        accounts.stream()
                .map(Account::getAccountType)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .forEach(types::add);
        accountTypeFilterBox.setItems(FXCollections.observableArrayList(types));
        accountTypeFilterBox.setValue(types.contains(selectedType) ? selectedType : ALL_TYPES);

        List<String> currencies = new ArrayList<>();
        currencies.add(ALL_CURRENCIES);
        accounts.stream()
                .map(Account::getCurrency)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .forEach(currencies::add);
        currencyFilterBox.setItems(FXCollections.observableArrayList(currencies));
        currencyFilterBox.setValue(currencies.contains(selectedCurrency) ? selectedCurrency : ALL_CURRENCIES);
    }

    private void applyFilters() {
        if (accountsTable == null) {
            return;
        }
        String status = statusFilterBox.getValue();
        String type = accountTypeFilterBox.getValue();
        String currency = currencyFilterBox.getValue();
        String search = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ENGLISH);
        List<Account> filtered = accounts.stream()
                .filter(account -> status == null || ALL_STATUSES.equals(status) || status.equals(displayStatus(account.getStatus())))
                .filter(account -> type == null || ALL_TYPES.equals(type) || type.equals(account.getAccountType()))
                .filter(account -> currency == null || ALL_CURRENCIES.equals(currency) || currency.equals(account.getCurrency()))
                .filter(account -> search.isBlank()
                        || contains(account.getAccountName(), search)
                        || contains(account.getAccountType(), search)
                        || contains(account.getBankProviderName(), search)
                        || contains(account.getAccountNumber(), search)
                        || contains(account.getCurrency(), search)
                        || contains(account.getAccountPurpose(), search))
                .toList();
        Integer selectedId = selectedAccount() == null ? null : selectedAccount().getId();
        accountsTable.setItems(FXCollections.observableArrayList(filtered));
        selectAccountById(selectedId);
    }

    private void refreshSummary() {
        totalAccountsLabel.setText(String.valueOf(accounts.size()));
        activeAccountsOverviewLabel.setText(String.valueOf(accounts.stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(account.getStatus()))
                .count()));
        needsReconciliationLabel.setText(String.valueOf(accounts.stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(account.getStatus()))
                .filter(this::needsReconciliation)
                .count()));
        defaultCurrencyLabel.setText(database.getDefaultCurrency());
        totalBalanceLabel.setText(totalBalanceText());
    }

    private String accountProfile(Account account) {
        AccountReconciliationRecord latest = latestReconciliations.get(account.getId());
        List<FinanceTransaction> transactions = database.listTransactionsForAccount(account.getId());
        String transactionLines = transactions.stream()
                .limit(8)
                .map(tx -> tx.getTransactionDate()
                        + " | " + tx.getTransactionType()
                        + " | In: " + money(account.getCurrency(), moneyIn(tx))
                        + " | Out: " + money(account.getCurrency(), moneyOut(tx))
                        + " | " + blankToDash(tx.getDescription()))
                .collect(Collectors.joining("\n"));
        if (transactionLines.isBlank()) {
            transactionLines = "No transactions are recorded for this account.";
        }
        String warnings = accountWarnings(account, transactions, latest);
        return """
                Overview
                Account name: %s
                Account type: %s
                Provider: %s
                Account number: %s
                Default account: %s
                Branch: %s
                Currency: %s
                Opening balance: %s
                Opening balance date: %s
                Current balance: %s
                Available balance: %s
                Minimum balance: %s
                Purpose: %s
                Status: %s
                Last transaction date: %s
                Last reconciliation date: %s

                Transactions
                %s

                Reconciliation
                System balance: %s
                Actual balance: %s
                Difference: %s
                Status: %s

                History
                Account created: %s
                Opening balance recorded: %s
                Latest reconciliation: %s
                Lifecycle changes are recorded in the system audit log.

                Smart Checks
                %s
                """.formatted(
                account.getAccountName(),
                blankToDash(account.getAccountType()),
                blankToDash(account.getBankProviderName()),
                maskedAccountNumber(account.getAccountNumber()),
                account.getId() == defaultAccountId ? "Yes" : "No",
                blankToDash(account.getBranchName()),
                blankToDash(account.getCurrency()),
                money(account.getCurrency(), account.getOpeningBalance()),
                blankToDash(account.getOpeningBalanceDate()),
                money(account.getCurrency(), account.getCurrentBalance()),
                money(account.getCurrency(), availableBalance(account)),
                money(account.getCurrency(), account.getMinimumBalance()),
                blankToDash(account.getAccountPurpose()),
                displayStatus(account.getStatus()),
                lastTransactionDate(transactions),
                latest == null ? "-" : latest.getReconciliationDate(),
                transactionLines,
                money(account.getCurrency(), latest == null ? account.getCurrentBalance() : latest.getSystemBalance()),
                latest == null ? "-" : money(account.getCurrency(), latest.getActualBalance()),
                latest == null ? "-" : money(account.getCurrency(), latest.getDifference()),
                latest == null ? "Not reconciled" : displayReconciliationStatus(latest),
                blankToDash(account.getCreatedAt()),
                account.getOpeningBalance() > 0 ? money(account.getCurrency(), account.getOpeningBalance()) : "No opening balance amount recorded",
                latest == null ? "No reconciliation completed" : latest.getReconciliationDate() + " / " + displayReconciliationStatus(latest),
                warnings
        );
    }

    private String accountSummary(Account account) {
        return """
                Select Open Account for the full profile.

                Account: %s
                Type: %s
                Provider: %s
                Default: %s
                Currency: %s
                Balance: %s
                Reconciliation: %s
                Status: %s
                """.formatted(
                account.getAccountName(),
                blankToDash(account.getAccountType()),
                blankToDash(account.getBankProviderName()),
                account.getId() == defaultAccountId ? "Yes" : "No",
                blankToDash(account.getCurrency()),
                money(account.getCurrency(), account.getCurrentBalance()),
                reconciliationStatus(account),
                displayStatus(account.getStatus())
        );
    }

    private void showAccountDetailsDialog(Account account) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Account Details");
        dialog.setHeaderText(account.getAccountName());

        TextArea profileArea = new TextArea(accountProfile(account));
        profileArea.setEditable(false);
        profileArea.setWrapText(true);
        profileArea.setPrefColumnCount(88);
        profileArea.setPrefRowCount(24);

        ButtonType viewLedger = new ButtonType("View Ledger", ButtonBar.ButtonData.OTHER);
        ButtonType reconcile = new ButtonType("Reconcile", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(viewLedger, reconcile, ButtonType.CLOSE);
        dialog.getDialogPane().setContent(profileArea);

        Node reconcileButton = dialog.getDialogPane().lookupButton(reconcile);
        if (reconcileButton != null) {
            reconcileButton.setDisable(!List.of("ACTIVE", "FROZEN").contains(normalizedStatus(account)));
        }

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.filter(viewLedger::equals).isPresent()) {
            NavigationBus.showAccountHistory(account.getId());
        } else if (result.filter(reconcile::equals).isPresent()) {
            openAccountReconciliation(account);
        }
    }

    private void openAccountReconciliation(Account account) {
        NavigationBus.showAccountReconciliation(account.getId());
    }

    private void showReconciliationDialog(Account account) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reconcile Account");
        dialog.setHeaderText(account.getAccountName());
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField actualBalanceField = new TextField();
        actualBalanceField.setPromptText("Actual balance");
        actualBalanceField.setText(formatAmount(account.getCurrentBalance()));
        TextArea notes = new TextArea();
        notes.setPromptText("Reason, statement reference, cash-count note or investigation detail");
        notes.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.add(new Label("Account"), 0, 0);
        grid.add(new Label(account.getAccountName()), 1, 0);
        grid.add(new Label("Date"), 0, 1);
        grid.add(datePicker, 1, 1);
        grid.add(new Label("System balance"), 0, 2);
        grid.add(new Label(money(account.getCurrency(), account.getCurrentBalance())), 1, 2);
        grid.add(new Label("Actual balance"), 0, 3);
        grid.add(actualBalanceField, 1, 3);
        grid.add(new Label("Notes"), 0, 4);
        grid.add(notes, 1, 4);

        ButtonType reconcile = new ButtonType("Reconcile Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(reconcile, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(grid);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.filter(reconcile::equals).isEmpty()) {
            return;
        }
        try {
            double actualBalance = parseRequiredAmount(actualBalanceField.getText(), "Actual balance");
            LocalDate date = datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();
            database.saveAccountReconciliation(null, account.getId(), date.toString(), actualBalance, notes.getText());
            refresh();
            accountDetailsArea.setText(accountProfile(refreshedAccount(account.getId())));
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to reconcile account", exception);
        }
    }

    private List<String> actionsFor(Account account) {
        List<String> actions = new ArrayList<>();
        String status = normalizedStatus(account);
        if ("ACTIVE".equals(status) || "FROZEN".equals(status)) {
            actions.add("Edit Details");
        }
        actions.add("View Ledger");
        if ("ACTIVE".equals(status)) {
            actions.add("Set as Default");
        }
        if ("FROZEN".equals(status)) {
            actions.add("Unfreeze Account");
        } else if ("ACTIVE".equals(status)) {
            actions.add("Freeze Account");
        }
        if ("ACTIVE".equals(status) || "FROZEN".equals(status)) {
            actions.add("Close Account");
        }
        if (!"ARCHIVED".equals(status)) {
            actions.add("Archive Account");
        }
        return actions;
    }

    private void handleAction(Account account, String action) {
        switch (action) {
            case "Edit Details" -> editAccount(account);
            case "View Ledger" -> NavigationBus.showAccountHistory(account.getId());
            case "Set as Default" -> setDefaultAccount(account);
            case "Freeze Account" -> updateLifecycleWithConfirmation(
                    account,
                    "FROZEN",
                    "Freezing prevents new ordinary transactions while preserving the account and its history."
            );
            case "Unfreeze Account" -> updateLifecycle(account, "ACTIVE");
            case "Close Account" -> closeAccount(account);
            case "Archive Account" -> updateLifecycleWithConfirmation(
                    account,
                    "ARCHIVED",
                    "Archiving removes the account from normal active views but preserves records for reporting and audit."
            );
            default -> UiAlerts.info("Unsupported account action.");
        }
    }

    private void closeAccount(Account account) {
        if (Math.abs(account.getCurrentBalance()) >= EPSILON) {
            UiAlerts.info("This account still has " + money(account.getCurrency(), account.getCurrentBalance())
                    + ". Transfer or adjust the remaining balance before closing the account.");
            return;
        }
        if (needsReconciliation(account)) {
            UiAlerts.info("Reconcile this account before closing it.");
            return;
        }
        updateLifecycleWithConfirmation(
                account,
                "CLOSED",
                "Closing permanently blocks ordinary account use while preserving all historical records."
        );
    }

    private void setDefaultAccount(Account account) {
        if (!"ACTIVE".equals(normalizedStatus(account))) {
            UiAlerts.info("Only an active account can be set as the default account.");
            return;
        }
        database.saveAppSetting(DEFAULT_ACCOUNT_SETTING, String.valueOf(account.getId()));
        database.recordSystemLog("Accounts", "Set Default Account", "INFO", "Default preference requested for account " + account.getId() + ".");
        defaultAccountId = account.getId();
        refresh();
        selectAccountById(account.getId());
        accountDetailsArea.setText(accountProfile(refreshedAccount(account.getId())));
        UiAlerts.info(account.getAccountName() + " is now saved as the default account preference.");
    }

    private void updateLifecycle(Account account, String status) {
        database.updateAccountLifecycleStatus(account.getId(), status);
        if (account.getId() == defaultAccountId && !"ACTIVE".equals(status)) {
            database.saveAppSetting(DEFAULT_ACCOUNT_SETTING, "");
            defaultAccountId = -1;
        }
        try {
            refresh();
            accountDetailsArea.setText(accountProfile(refreshedAccount(account.getId())));
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException refreshException) {
            database.recordSystemLog(
                    "Accounts",
                    "Account refresh failed after lifecycle update",
                    "ERROR",
                    UiAlerts.rootMessage(refreshException)
            );
            UiAlerts.error("Account status was updated, but the account list could not be refreshed", refreshException);
        }
    }

    private void updateLifecycleWithConfirmation(Account account, String targetStatus, String consequence) {
        if (account == null) {
            return;
        }
        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Reason for this lifecycle change");
        reasonArea.setPrefRowCount(3);
        reasonArea.setWrapText(true);
        reasonArea.getStyleClass().add("maintenance-text-area");

        CheckBox confirmation = new CheckBox("I understand the effect of this account status change.");
        confirmation.getStyleClass().add("maintenance-checkbox");

        Label heading = new Label(displayStatus(targetStatus) + " Account");
        heading.getStyleClass().add("maintenance-step-title");
        Label details = new Label("""
                Selected Account: %s
                Current Status: %s
                New Status: %s

                Effect:
                %s
                """.formatted(
                account.getAccountName(),
                displayStatus(account.getStatus()),
                displayStatus(targetStatus),
                consequence
        ));
        details.setWrapText(true);
        details.getStyleClass().add("settings-status-text");

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(event -> clearLifecycleInlinePane());

        Button apply = new Button(displayStatus(targetStatus) + " Account");
        apply.getStyleClass().add("CLOSED".equals(targetStatus) || "ARCHIVED".equals(targetStatus)
                ? "account-danger-button"
                : "primary-button");
        apply.setOnAction(event -> {
            if (reasonArea.getText().trim().isBlank()) {
                accountDetailsArea.setText("Enter a reason before changing account lifecycle status.");
                reasonArea.requestFocus();
                return;
            }
            if (!confirmation.isSelected()) {
                accountDetailsArea.setText("Review the lifecycle impact and tick the confirmation box before continuing.");
                return;
            }
            try {
                database.recordSystemLog(
                        "Accounts",
                        displayStatus(targetStatus) + " Account",
                        "WARNING",
                        "Account " + account.getId() + " lifecycle change requested. Reason: " + reasonArea.getText().trim()
                );
                updateLifecycle(account, targetStatus);
                clearLifecycleInlinePane();
            } catch (RuntimeException exception) {
                UiAlerts.error("Account status could not be updated", exception);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, cancel, spacer, apply);
        actions.getStyleClass().add("maintenance-simple-actions");

        lifecycleInlinePane.getChildren().setAll(
                heading,
                details,
                lifecycleField("Reason", reasonArea),
                confirmation,
                actions
        );
        lifecycleInlinePane.setVisible(true);
        lifecycleInlinePane.setManaged(true);
        accountDetailsArea.setText("Review the inline account lifecycle panel before continuing.");
    }

    private VBox lifecycleField(String labelText, Node field) {
        VBox box = new VBox(6);
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        box.getChildren().addAll(label, field);
        return box;
    }

    private void clearLifecycleInlinePane() {
        if (lifecycleInlinePane != null) {
            lifecycleInlinePane.getChildren().clear();
            lifecycleInlinePane.setVisible(false);
            lifecycleInlinePane.setManaged(false);
        }
    }

    private void editAccount(Account account) {
        editingAccount = account;
        setEditMode(true);
        accountNameField.setText(account.getAccountName());
        accountTypeBox.getEditor().setText(account.getAccountType());
        selectCurrency(account.getCurrency());
        bankProviderField.setText(blankAs(account.getBankProviderName(), ""));
        accountNumberField.setText(blankAs(account.getAccountNumber(), ""));
        branchField.setText(blankAs(account.getBranchName(), ""));
        openingBalanceField.setText(formatAmount(account.getOpeningBalance()));
        openingBalanceDatePicker.setValue(parseDate(account.getOpeningBalanceDate(), LocalDate.now()));
        minimumBalanceField.setText(formatAmount(account.getMinimumBalance()));
        accountPurposeBox.getSelectionModel().select(blankAs(account.getAccountPurpose(), "General use"));
        notesArea.setText(blankAs(account.getNotes(), ""));
        accountFormPane.setExpanded(true);
        accountRecordsPane.setExpanded(false);
        accountNameField.requestFocus();
    }

    private void setEditMode(boolean editing) {
        accountFormPane.setText(editing ? "Edit Account Details" : "New Account");
        saveAccountButton.setText(editing ? "Update Details" : "Create Account");
        accountTypeBox.setDisable(editing);
        currencyBox.setDisable(editing);
        openingBalanceField.setDisable(editing);
        openingBalanceDatePicker.setDisable(editing);
    }

    private void updateTypeSpecificFields() {
        String type = currentAccountTypeText();
        if (type.isBlank()) {
            providerLabel.setText("Bank / Provider");
            providerLabel.setVisible(true);
            providerLabel.setManaged(true);
            bankProviderField.setVisible(true);
            bankProviderField.setManaged(true);
            bankProviderField.setPromptText("e.g. NBS Bank, Standard Bank, Airtel Money");

            accountNumberLabel.setText("Account Number");
            accountNumberLabel.setVisible(true);
            accountNumberLabel.setManaged(true);
            accountNumberField.setVisible(true);
            accountNumberField.setManaged(true);
            accountNumberField.setPromptText("Optional");

            branchLabel.setVisible(false);
            branchLabel.setManaged(false);
            branchField.setVisible(false);
            branchField.setManaged(false);
            return;
        }
        String lower = type.toLowerCase(Locale.ENGLISH);
        boolean cash = lower.contains("cash");
        boolean mobile = lower.contains("mobile");
        boolean bank = lower.contains("bank") || lower.contains("savings") || lower.contains("project");
        boolean credit = lower.contains("credit") || lower.contains("loan");

        providerLabel.setText(mobile ? "Provider" : "Bank / Provider");
        providerLabel.setVisible(!cash);
        providerLabel.setManaged(!cash);
        bankProviderField.setVisible(!cash);
        bankProviderField.setManaged(!cash);
        bankProviderField.setPromptText(mobile ? "e.g. Airtel Money, TNM Mpamba" : "e.g. NBS Bank, Standard Bank");

        accountNumberLabel.setText(mobile ? "Mobile Number" : credit ? "Account / Facility Number" : "Account Number");
        accountNumberLabel.setVisible(!cash);
        accountNumberLabel.setManaged(!cash);
        accountNumberField.setVisible(!cash);
        accountNumberField.setManaged(!cash);
        accountNumberField.setPromptText(mobile ? "Optional mobile number" : "Optional");

        branchLabel.setVisible(bank);
        branchLabel.setManaged(bank);
        branchField.setVisible(bank);
        branchField.setManaged(bank);

        if (cash && bankProviderField.getText().isBlank()) {
            bankProviderField.setText("Cash");
        }
    }

    private void validateNewAccount(String accountType, String provider, String accountNumber, double openingBalance) {
        if (currencyCode().isBlank()) {
            throw new IllegalArgumentException("Currency is required.");
        }
        if (openingBalance < 0) {
            throw new IllegalArgumentException("Opening balance cannot be negative.");
        }
        String lower = accountType.toLowerCase(Locale.ENGLISH);
        if (isSavingsGroupType(accountType)) {
            throw new IllegalArgumentException("Bank Nkhonde and other savings groups must be created from the Savings Groups module.");
        }
        if (lower.contains("mobile") && !accountNumber.isBlank() && !accountNumber.matches("[+0-9\\s-]{6,20}")) {
            throw new IllegalArgumentException("Mobile money number format is not valid.");
        }
        if (lower.contains("cash") && provider.isBlank()) {
            bankProviderField.setText("Cash");
        }
        boolean communitySavings = lower.contains("community savings");
        if ((lower.contains("bank") || (lower.contains("savings") && !communitySavings)) && provider.isBlank()) {
            throw new IllegalArgumentException("Bank/provider is required for bank accounts.");
        }
    }

    private boolean isSavingsGroupType(String accountType) {
        String clean = blankAs(accountType, "").trim().toUpperCase(Locale.ENGLISH).replace('_', ' ');
        return clean.equals("COMMUNITY SAVINGS")
                || clean.equals("COMMUNITY SAVINGS INTERNAL")
                || clean.equals("COMMUNITY SAVINGS LOAN")
                || clean.equals("BANK NKHONDE")
                || clean.equals("CHIPELEGANYU")
                || clean.equals("ZIPELEGANYU")
                || clean.equals("VILLAGE SAVINGS")
                || clean.equals("VILLAGE SAVINGS GROUP")
                || clean.contains("SAVINGS GROUP");
    }

    private boolean possibleDuplicate(Integer existingAccountId, String name, String type, String provider, String accountNumber) {
        String endingDigits = endingDigits(accountNumber);
        return accounts.stream()
                .filter(account -> existingAccountId == null || account.getId() != existingAccountId)
                .anyMatch(account -> account.getAccountName().equalsIgnoreCase(name)
                        || (!provider.isBlank()
                        && provider.equalsIgnoreCase(blankAs(account.getBankProviderName(), ""))
                        && type.equalsIgnoreCase(blankAs(account.getAccountType(), ""))
                        && !endingDigits.isBlank()
                        && endingDigits.equals(endingDigits(account.getAccountNumber()))));
    }

    private boolean needsReconciliation(Account account) {
        AccountReconciliationRecord latest = latestReconciliations.get(account.getId());
        return latest == null || Math.abs(latest.getDifference()) >= EPSILON;
    }

    private String reconciliationStatus(Account account) {
        AccountReconciliationRecord latest = latestReconciliations.get(account.getId());
        if (latest == null) {
            return "Not reconciled";
        }
        if (Math.abs(latest.getDifference()) < EPSILON) {
            return "Reconciled";
        }
        return "Difference " + money(account.getCurrency(), latest.getDifference());
    }

    private String displayReconciliationStatus(AccountReconciliationRecord record) {
        return Math.abs(record.getDifference()) < EPSILON ? "Reconciled" : "Difference";
    }

    private String accountWarnings(Account account, List<FinanceTransaction> transactions, AccountReconciliationRecord latest) {
        List<String> warnings = new ArrayList<>();
        if (account.getMinimumBalance() > 0 && account.getCurrentBalance() < account.getMinimumBalance()) {
            warnings.add("Low balance: current balance is below the configured minimum balance.");
        }
        if (latest != null && Math.abs(latest.getDifference()) >= EPSILON) {
            warnings.add("Reconciliation problem: latest actual balance differs from system balance by "
                    + money(account.getCurrency(), latest.getDifference()) + ".");
        }
        LocalDate lastDate = transactions.stream()
                .map(FinanceTransaction::getTransactionDate)
                .map(value -> parseDate(value, null))
                .filter(date -> date != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (lastDate != null && lastDate.isBefore(LocalDate.now().minusDays(120))) {
            warnings.add("Dormant account: no transaction has been recorded for more than 120 days.");
        }
        if (warnings.isEmpty()) {
            warnings.add("No account-level warning was detected from the available records.");
        }
        return warnings.stream().map(warning -> "- " + warning).collect(Collectors.joining("\n"));
    }

    private String totalBalanceText() {
        List<Account> active = accounts.stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(account.getStatus()))
                .toList();
        Set<String> currencies = active.stream()
                .map(Account::getCurrency)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (currencies.isEmpty()) {
            return money(currencyCodeFromDisplay(database.getDefaultCurrency()), 0);
        }
        if (currencies.size() == 1) {
            String currency = currencies.iterator().next();
            return money(currency, active.stream().mapToDouble(Account::getCurrentBalance).sum());
        }

        Map<String, CurrencyRecord> currencyRates = database.listCurrencies().stream()
                .collect(Collectors.toMap(CurrencyRecord::getCurrencyCode, record -> record, (first, second) -> first));
        CurrencyRecord base = currencyRates.values().stream()
                .filter(CurrencyRecord::isBaseCurrency)
                .findFirst()
                .orElse(null);
        if (base == null || currencies.stream().anyMatch(code -> {
            CurrencyRecord record = currencyRates.get(code);
            return record == null || record.getRateToBase() <= 0;
        })) {
            return "Consolidated balance unavailable because some account currencies have no valid exchange rate.";
        }
        double total = active.stream()
                .mapToDouble(account -> {
                    CurrencyRecord rate = currencyRates.get(account.getCurrency());
                    return account.getCurrentBalance() * (rate == null ? 0 : rate.getRateToBase());
                })
                .sum();
        return money(base.getCurrencyCode(), total);
    }

    private void configureCurrencySearch() {
        currencyBox.setEditable(true);
        currencyBox.setPromptText("Select or type currency");
        currencyBox.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingCurrencyBox) {
                return;
            }
            filterCurrencyOptions(newValue);
        });
    }

    private void filterCurrencyOptions(String query) {
        if (currencyOptions.isEmpty()) {
            return;
        }
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ENGLISH);
        List<String> filtered = currencyOptions.stream()
                .filter(currency -> search.isEmpty() || currency.toLowerCase(Locale.ENGLISH).contains(search))
                .toList();
        setCurrencyItems(filtered.isEmpty() ? currencyOptions : filtered);
        if (!search.isEmpty() && currencyBox.isFocused()) {
            Platform.runLater(currencyBox::show);
        }
    }

    private void setCurrencyItems(List<String> currencies) {
        updatingCurrencyBox = true;
        try {
            currencyBox.setItems(FXCollections.observableArrayList(currencies));
        } finally {
            updatingCurrencyBox = false;
        }
    }

    private void setCurrencyValue(String currency) {
        updatingCurrencyBox = true;
        try {
            currencyBox.setValue(currency);
        } finally {
            updatingCurrencyBox = false;
        }
    }

    private void selectCurrency(String currency) {
        if (!currencyOptions.isEmpty()) {
            setCurrencyItems(currencyOptions);
        }
        if (currency == null || currency.isBlank()) {
            setCurrencyValue(database.getDefaultCurrency());
            return;
        }
        List<String> items = currencyOptions.isEmpty() ? currencyBox.getItems() : currencyOptions;
        for (String item : items) {
            if (item.equals(currency) || item.startsWith(currency + " - ")) {
                setCurrencyValue(item);
                return;
            }
        }
        setCurrencyValue(currency);
    }

    private Account selectedAccount() {
        return accountsTable == null ? null : accountsTable.getSelectionModel().getSelectedItem();
    }

    private Account selectedAccountOrNotify(String message) {
        Account selected = selectedAccount();
        if (selected == null) {
            UiAlerts.info(message);
        }
        return selected;
    }

    private Account refreshedAccount(int accountId) {
        return database.listAccounts().stream()
                .filter(account -> account.getId() == accountId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Account no longer exists."));
    }

    private void selectAccountById(Integer accountId) {
        if (accountId == null || accountsTable.getItems().isEmpty()) {
            accountsTable.getSelectionModel().clearSelection();
            return;
        }
        accountsTable.getItems().stream()
                .filter(account -> account.getId() == accountId)
                .findFirst()
                .ifPresentOrElse(
                        account -> accountsTable.getSelectionModel().select(account),
                        () -> accountsTable.getSelectionModel().clearSelection()
                );
    }

    private void validateRequiredAccountFields() {
        clearValidationStyles();
        List<String> errors = new ArrayList<>();
        Node firstInvalid = null;
        if (textValue(accountNameField).isBlank()) {
            firstInvalid = markInvalid(firstInvalid, accountNameField);
            errors.add("Account Name is required.");
        }
        String accountType = accountTypeBox.getEditor().getText();
        if (accountType == null || accountType.isBlank()) {
            accountType = accountTypeBox.getValue();
        }
        if (accountType == null || accountType.isBlank()) {
            firstInvalid = markInvalid(firstInvalid, accountTypeBox);
            errors.add("Select an Account Type.");
        }
        String currency = currencyBox.getEditor().getText();
        if (currency == null || currency.isBlank()) {
            currency = currencyBox.getValue();
        }
        if (currency == null || currency.isBlank()) {
            firstInvalid = markInvalid(firstInvalid, currencyBox);
            errors.add("Currency is required.");
        }
        if (textValue(openingBalanceField).isBlank()) {
            firstInvalid = markInvalid(firstInvalid, openingBalanceField);
            errors.add("Opening Balance is required. Enter 0.00 when there is no opening balance.");
        }
        if (openingBalanceDatePicker.getValue() == null) {
            firstInvalid = markInvalid(firstInvalid, openingBalanceDatePicker);
            errors.add("Opening Balance Date is required.");
        }
        if (!errors.isEmpty()) {
            Node focusTarget = firstInvalid;
            Platform.runLater(focusTarget::requestFocus);
            throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
        }
    }

    private Node markInvalid(Node currentFirstInvalid, Node field) {
        if (!field.getStyleClass().contains("field-error")) {
            field.getStyleClass().add("field-error");
        }
        return currentFirstInvalid == null ? field : currentFirstInvalid;
    }

    private void clearValidationStyles() {
        clearFieldError(accountNameField);
        clearFieldError(accountTypeBox);
        clearFieldError(currencyBox);
        clearFieldError(openingBalanceField);
        clearFieldError(openingBalanceDatePicker);
    }

    private void clearFieldError(Node field) {
        if (field != null) {
            field.getStyleClass().remove("field-error");
        }
    }

    private String required(TextField field, String message) {
        String value = textValue(field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String accountType() {
        String value = currentAccountTypeText();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Select an Account Type.");
        }
        return value;
    }

    private String currentAccountTypeText() {
        String value = accountTypeBox.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = accountTypeBox.getValue();
        }
        return value == null ? "" : value.trim();
    }

    private String providerForType(String accountType) {
        String value = textValue(bankProviderField);
        if (accountType.toLowerCase(Locale.ENGLISH).contains("cash")) {
            return value.isBlank() ? "Cash" : value;
        }
        return value;
    }

    private String currencyCode() {
        String value = currencyBox.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = currencyBox.getValue();
        }
        return currencyCodeFromDisplay(value == null || value.isBlank() ? database.getDefaultCurrency() : value);
    }

    private String currencyCodeFromDisplay(String value) {
        if (value == null || value.isBlank()) {
            return "MWK";
        }
        int separator = value.indexOf(" - ");
        return (separator > 0 ? value.substring(0, separator) : value).trim().toUpperCase(Locale.ENGLISH);
    }

    private String selectedText(ComboBox<String> box) {
        String value = box.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = box.getValue();
        }
        return value == null ? "" : value.trim();
    }

    private String textValue(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String notesValue() {
        return notesArea.getText() == null ? "" : notesArea.getText().trim();
    }

    private double parseOptionalAmount(TextField field, String label) {
        String value = textValue(field);
        if (value.isBlank()) {
            return 0;
        }
        return parseRequiredAmount(value, label);
    }

    private double parseRequiredAmount(String value, String label) {
        try {
            double amount = Double.parseDouble(value.replace(",", "").trim());
            if (amount < 0) {
                throw new IllegalArgumentException(label + " cannot be negative.");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a valid amount.");
        }
    }

    private double availableBalance(Account account) {
        return Math.max(0, account.getCurrentBalance() - Math.max(0, account.getMinimumBalance()));
    }

    private double moneyIn(FinanceTransaction transaction) {
        String type = blankAs(transaction.getTransactionType(), "").toUpperCase(Locale.ENGLISH);
        String purpose = blankAs(transaction.getTransactionPurpose(), "").toUpperCase(Locale.ENGLISH);
        if ("INCOME".equals(type) || ("TRANSFER".equals(type) && "TRANSFER_IN".equals(purpose))) {
            return transaction.getAmount();
        }
        if ("LOAN".equals(type) && List.of("MONEY_BORROWED", "LENT_REPAID").contains(purpose)) {
            return transaction.getAmount();
        }
        return 0;
    }

    private double moneyOut(FinanceTransaction transaction) {
        String type = blankAs(transaction.getTransactionType(), "").toUpperCase(Locale.ENGLISH);
        String purpose = blankAs(transaction.getTransactionPurpose(), "").toUpperCase(Locale.ENGLISH);
        if ("EXPENSE".equals(type) || ("TRANSFER".equals(type) && "TRANSFER_OUT".equals(purpose))) {
            return transaction.getAmount();
        }
        if ("LOAN".equals(type) && List.of("MONEY_LENT", "BORROWED_REPAID").contains(purpose)) {
            return transaction.getAmount();
        }
        return 0;
    }

    private String lastTransactionDate(List<FinanceTransaction> transactions) {
        return transactions.stream()
                .map(FinanceTransaction::getTransactionDate)
                .filter(value -> value != null && !value.isBlank())
                .max(String::compareTo)
                .orElse("-");
    }

    private String maskedAccountNumber(String accountNumber) {
        String clean = accountNumber == null ? "" : accountNumber.replaceAll("\\s+", "");
        if (clean.isBlank()) {
            return "-";
        }
        if (clean.length() <= 4) {
            return "**** " + clean;
        }
        return "**** " + clean.substring(clean.length() - 4);
    }

    private String endingDigits(String accountNumber) {
        String clean = accountNumber == null ? "" : accountNumber.replaceAll("[^0-9A-Za-z]", "");
        if (clean.length() <= 4) {
            return clean;
        }
        return clean.substring(clean.length() - 4);
    }

    private String normalizedStatus(Account account) {
        return account == null ? "" : normalizedStatus(account.getStatus());
    }

    private String normalizedStatus(String value) {
        if (value == null || value.isBlank()) {
            return "ACTIVE";
        }
        return value.trim().toUpperCase(Locale.ENGLISH).replace(' ', '_');
    }

    private String displayStatus(String value) {
        if (value == null || value.isBlank()) {
            return "Active";
        }
        return switch (normalizedStatus(value)) {
            case "FROZEN" -> "Frozen";
            case "CLOSED" -> "Closed";
            case "ARCHIVED" -> "Archived";
            case "INACTIVE" -> "Inactive";
            default -> "Active";
        };
    }

    private String money(String currency, double amount) {
        String code = blankAs(currency, "MWK");
        if ("MWK".equalsIgnoreCase(code)) {
            return MoneyUtil.mwk(amount);
        }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return code + " " + format.format(amount);
    }

    private String formatAmount(double amount) {
        return String.format(Locale.ENGLISH, "%.2f", amount);
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        try {
            return value == null || value.isBlank() ? fallback : LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException exception) {
            return fallback;
        }
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ENGLISH).contains(search);
    }

    private String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToDash(String value) {
        return blankAs(value, "-");
    }
}
