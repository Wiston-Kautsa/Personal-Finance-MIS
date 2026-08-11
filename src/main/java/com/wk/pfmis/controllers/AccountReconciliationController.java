package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.AccountReconciliationRecord;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AccountReconciliationController {
    private static final double EPSILON = 0.005;

    @FXML private Label needsReconciliationCountLabel;
    @FXML private Label selectedSystemBalanceLabel;
    @FXML private Label selectedDifferenceLabel;
    @FXML private Label selectedStatusLabel;
    @FXML private Label messageLabel;
    @FXML private ComboBox<Account> accountBox;
    @FXML private DatePicker reconciliationDatePicker;
    @FXML private TextField systemBalanceField;
    @FXML private TextField actualBalanceField;
    @FXML private TextField differenceField;
    @FXML private TextArea notesArea;
    @FXML private Button saveReconciliationButton;
    @FXML private TableView<Account> needsReconciliationTable;
    @FXML private TableColumn<Account, String> needsAccountColumn;
    @FXML private TableColumn<Account, String> needsBalanceColumn;
    @FXML private TableColumn<Account, String> needsLatestDateColumn;
    @FXML private TableColumn<Account, String> needsDifferenceColumn;
    @FXML private TableColumn<Account, String> needsStatusColumn;
    @FXML private TableView<AccountReconciliationRecord> reconciliationTable;
    @FXML private TableColumn<AccountReconciliationRecord, String> historyAccountColumn;
    @FXML private TableColumn<AccountReconciliationRecord, String> historyDateColumn;
    @FXML private TableColumn<AccountReconciliationRecord, String> historySystemBalanceColumn;
    @FXML private TableColumn<AccountReconciliationRecord, String> historyActualBalanceColumn;
    @FXML private TableColumn<AccountReconciliationRecord, String> historyDifferenceColumn;
    @FXML private TableColumn<AccountReconciliationRecord, String> historyStatusColumn;
    @FXML private TableColumn<AccountReconciliationRecord, String> historyNotesColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final Map<Integer, AccountReconciliationRecord> latestReconciliations = new LinkedHashMap<>();
    private final Map<Integer, String> accountCurrencies = new LinkedHashMap<>();
    private double selectedSystemBalance;

    @FXML
    public void initialize() {
        configureTables();
        reconciliationDatePicker.setValue(LocalDate.now());
        accountBox.valueProperty().addListener((observable, oldValue, newValue) -> accountChanged(newValue));
        reconciliationDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> refreshSelectedBalance(false));
        actualBalanceField.textProperty().addListener((observable, oldValue, newValue) -> refreshDifference());
        needsReconciliationTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                accountBox.getSelectionModel().select(selected);
            }
        });
        reconciliationTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                selectAccount(selected.getAccountId());
            }
        });
        refresh();
    }

    @FXML
    private void refresh() {
        Integer requestedId = NavigationBus.consumeRequestedAccountReconciliationId();
        Integer selectedId = requestedId != null
                ? requestedId
                : selectedAccount() == null ? null : selectedAccount().getId();
        latestReconciliations.clear();
        List<AccountReconciliationRecord> latest = database.listLatestAccountReconciliations();
        latest.forEach(record -> latestReconciliations.put(record.getAccountId(), record));

        List<Account> accounts = database.listAccounts();
        accountCurrencies.clear();
        accounts.forEach(account -> accountCurrencies.put(account.getId(), account.getCurrency()));
        accountBox.setItems(FXCollections.observableArrayList(accounts));
        List<Account> needsReconciliation = accounts.stream()
                .filter(account -> "ACTIVE".equals(normalizedStatus(account)))
                .filter(this::needsReconciliation)
                .sorted(Comparator.comparing(Account::getAccountName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        needsReconciliationTable.setItems(FXCollections.observableArrayList(needsReconciliation));
        reconciliationTable.setItems(FXCollections.observableArrayList(database.listAccountReconciliations()));
        needsReconciliationCountLabel.setText(String.valueOf(needsReconciliation.size()));

        if (selectedId != null && selectAccount(selectedId)) {
            // The requested account can be reconciled even when it is not currently in the "needs" table.
        } else if (!needsReconciliation.isEmpty()) {
            accountBox.getSelectionModel().select(needsReconciliation.getFirst());
        } else {
            accountBox.getSelectionModel().clearSelection();
            accountChanged(null);
        }
        showMessage("");
    }

    @FXML
    private void saveReconciliation() {
        Account account = selectedAccount();
        if (account == null) {
            return;
        }
        if (!canSaveReconciliation(account)) {
            showMessage("Closed and archived accounts keep reconciliation history as read-only records.");
            return;
        }
        try {
            LocalDate date = reconciliationDatePicker.getValue() == null ? LocalDate.now() : reconciliationDatePicker.getValue();
            double actualBalance = parseAmount(actualBalanceField.getText(), "Actual balance");
            database.saveAccountReconciliation(null, account.getId(), date.toString(), actualBalance, notesArea.getText());
            DataRefreshBus.notifyDataChanged();
            showMessage("Account reconciliation saved.");
            refresh();
            selectAccount(account.getId());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save account reconciliation", exception);
        }
    }

    private void configureTables() {
        needsAccountColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAccountName()));
        needsBalanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(cell.getValue().getCurrency(), cell.getValue().getCurrentBalance())));
        needsLatestDateColumn.setCellValueFactory(cell -> {
            AccountReconciliationRecord latest = latestReconciliations.get(cell.getValue().getId());
            return new SimpleStringProperty(latest == null ? "-" : latest.getReconciliationDate());
        });
        needsDifferenceColumn.setCellValueFactory(cell -> {
            AccountReconciliationRecord latest = latestReconciliations.get(cell.getValue().getId());
            return new SimpleStringProperty(latest == null ? "Not reconciled" : money(cell.getValue().getCurrency(), latest.getDifference()));
        });
        needsStatusColumn.setCellValueFactory(cell -> {
            AccountReconciliationRecord latest = latestReconciliations.get(cell.getValue().getId());
            return new SimpleStringProperty(latest == null ? "Required" : displayReconciliationStatus(latest));
        });

        historyAccountColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAccountName()));
        historyDateColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getReconciliationDate()));
        historySystemBalanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(accountCurrencies.get(cell.getValue().getAccountId()), cell.getValue().getSystemBalance())));
        historyActualBalanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(accountCurrencies.get(cell.getValue().getAccountId()), cell.getValue().getActualBalance())));
        historyDifferenceColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(accountCurrencies.get(cell.getValue().getAccountId()), cell.getValue().getDifference())));
        historyStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayReconciliationStatus(cell.getValue())));
        historyNotesColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getNotes())));

        TableActions.configureScrollableTable(needsReconciliationTable);
        TableActions.configureScrollableTable(reconciliationTable);
    }

    private void accountChanged(Account account) {
        NavigationBus.rememberSelectedAccountId(account == null ? null : account.getId());
        if (account == null) {
            selectedSystemBalance = 0;
            systemBalanceField.clear();
            actualBalanceField.clear();
            differenceField.clear();
            notesArea.clear();
            selectedSystemBalanceLabel.setText("-");
            selectedDifferenceLabel.setText("-");
            selectedStatusLabel.setText("Select an account");
            saveReconciliationButton.setDisable(true);
            return;
        }
        notesArea.clear();
        refreshSelectedBalance(true);
        boolean canSave = canSaveReconciliation(account);
        saveReconciliationButton.setDisable(!canSave);
        actualBalanceField.setDisable(!canSave);
        notesArea.setDisable(!canSave);
        selectedStatusLabel.setText(canSave
                ? statusSummary(account)
                : "Read-only history for " + displayStatus(account.getStatus()) + " account");
    }

    private void refreshSelectedBalance(boolean resetActualBalance) {
        Account account = selectedAccount();
        if (account == null) {
            accountChanged(null);
            return;
        }
        LocalDate date = reconciliationDatePicker.getValue() == null ? LocalDate.now() : reconciliationDatePicker.getValue();
        selectedSystemBalance = database.calculateAccountBalanceOnDate(account.getId(), date.toString());
        String balanceText = money(account.getCurrency(), selectedSystemBalance);
        systemBalanceField.setText(balanceText);
        selectedSystemBalanceLabel.setText(balanceText);
        if (resetActualBalance) {
            actualBalanceField.setText(formatAmount(selectedSystemBalance));
        }
        refreshDifference();
    }

    private void refreshDifference() {
        Account account = selectedAccount();
        if (account == null) {
            return;
        }
        try {
            double actualBalance = parseAmount(actualBalanceField.getText(), "Actual balance");
            double difference = actualBalance - selectedSystemBalance;
            String differenceText = money(account.getCurrency(), difference);
            differenceField.setText(differenceText);
            selectedDifferenceLabel.setText(differenceText);
        } catch (RuntimeException exception) {
            differenceField.setText("-");
            selectedDifferenceLabel.setText("-");
        }
    }

    private boolean selectAccount(int accountId) {
        for (Account account : accountBox.getItems()) {
            if (account.getId() == accountId) {
                accountBox.getSelectionModel().select(account);
                return true;
            }
        }
        return false;
    }

    private Account selectedAccount() {
        return accountBox == null ? null : accountBox.getSelectionModel().getSelectedItem();
    }

    private boolean needsReconciliation(Account account) {
        AccountReconciliationRecord latest = latestReconciliations.get(account.getId());
        return latest == null || Math.abs(latest.getDifference()) >= EPSILON;
    }

    private boolean canSaveReconciliation(Account account) {
        return List.of("ACTIVE", "FROZEN").contains(normalizedStatus(account));
    }

    private String statusSummary(Account account) {
        AccountReconciliationRecord latest = latestReconciliations.get(account.getId());
        if (latest == null) {
            return "No previous reconciliation";
        }
        return latest.getReconciliationDate() + " / " + displayReconciliationStatus(latest);
    }

    private String displayReconciliationStatus(AccountReconciliationRecord record) {
        return Math.abs(record.getDifference()) < EPSILON ? "Reconciled" : "Difference";
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
        return switch (normalizedStatus(value)) {
            case "FROZEN" -> "Frozen";
            case "CLOSED" -> "Closed";
            case "ARCHIVED" -> "Archived";
            case "INACTIVE" -> "Inactive";
            default -> "Active";
        };
    }

    private double parseAmount(String value, String label) {
        try {
            String clean = value == null ? "" : value.replace(",", "").trim();
            if (clean.isBlank()) {
                throw new IllegalArgumentException(label + " is required.");
            }
            return Double.parseDouble(clean);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a valid amount.");
        }
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

    private String formatAmount(double amount) {
        return String.format(Locale.ENGLISH, "%.2f", amount);
    }

    private void showMessage(String message) {
        messageLabel.setText(message == null ? "" : message);
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
