package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;

public class AccountsController {
    @FXML private TitledPane accountFormPane;
    @FXML private Button saveAccountButton;
    @FXML private Label totalAccountsLabel;
    @FXML private Label activeAccountsOverviewLabel;
    @FXML private Label defaultCurrencyLabel;
    @FXML private TextField accountNameField;
    @FXML private ComboBox<String> accountTypeBox;
    @FXML private ComboBox<String> currencyBox;
    @FXML private TextField bankProviderField;
    @FXML private TextField accountNumberField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea notesArea;
    @FXML private TextField searchField;
    @FXML private TableView<Account> accountsTable;
    @FXML private TableColumn<Account, String> nameColumn;
    @FXML private TableColumn<Account, String> typeColumn;
    @FXML private TableColumn<Account, String> providerColumn;
    @FXML private TableColumn<Account, String> currencyColumn;
    @FXML private TableColumn<Account, Double> balanceColumn;
    @FXML private TableColumn<Account, String> statusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final ObservableList<Account> accounts = FXCollections.observableArrayList();
    private Account editingAccount;

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("accountType"));
        providerColumn.setCellValueFactory(new PropertyValueFactory<>("bankProviderName"));
        currencyColumn.setCellValueFactory(new PropertyValueFactory<>("currency"));
        balanceColumn.setCellValueFactory(new PropertyValueFactory<>("currentBalance"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusBox.setItems(FXCollections.observableArrayList("Active", "Inactive"));
        statusBox.getSelectionModel().select("Active");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applySearch());
        configureAccountContextMenu();
        refresh();
    }

    @FXML
    private void addAccount() {
        try {
            String name = accountNameField.getText().trim();
            if (name.isEmpty()) {
                UiAlerts.info("Enter an account name.");
                return;
            }
            String accountType = accountTypeBox.getEditor().getText().trim();
            if (accountType.isEmpty()) {
                UiAlerts.info("Enter an account type.");
                return;
            }
            if (editingAccount == null) {
                database.addAccount(
                        name,
                        accountType,
                        currencyCode(),
                        textValue(bankProviderField),
                        textValue(accountNumberField),
                        0,
                        statusValue(),
                        notesValue()
                );
            } else {
                database.updateAccount(
                        editingAccount.getId(),
                        name,
                        accountType,
                        currencyCode(),
                        textValue(bankProviderField),
                        textValue(accountNumberField),
                        editingAccount.getOpeningBalance(),
                        statusValue(),
                        notesValue()
                );
            }
            clearForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error(editingAccount == null ? "Failed to add account" : "Failed to update account", exception);
        }
    }

    @FXML
    private void clearForm() {
        accountNameField.clear();
        accountTypeBox.getEditor().clear();
        bankProviderField.clear();
        accountNumberField.clear();
        notesArea.clear();
        currencyBox.setValue(database.getDefaultCurrency());
        statusBox.getSelectionModel().select("Active");
        editingAccount = null;
        setEditMode(false);
    }

    @FXML
    private void deactivateAccount() {
        Account selected = accountsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select an account to deactivate.");
            return;
        }
        database.deactivateAccount(selected.getId());
        refresh();
        DataRefreshBus.notifyDataChanged();
    }

    @FXML
    private void editSelectedAccount() {
        Account selected = accountsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select an account to edit.");
            return;
        }
        editAccount(selected);
    }

    @FXML
    private void viewHistory() {
        Account selected = accountsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select an account to view history.");
            return;
        }
        UiAlerts.info("Open Account History from the sidebar to view transactions for " + selected.getAccountName() + ".");
    }

    @FXML
    private void deleteAccount() {
        Account selected = accountsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select an account to delete.");
            return;
        }
        try {
            database.deleteAccount(selected.getId());
            if (editingAccount != null && editingAccount.getId() == selected.getId()) {
                clearForm();
            }
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to delete account", exception);
        }
    }

    @FXML
    private void activateAccount() {
        Account selected = accountsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select an account to activate.");
            return;
        }
        database.activateAccount(selected.getId());
        refresh();
        DataRefreshBus.notifyDataChanged();
    }

    @FXML
    private void refresh() {
        accountTypeBox.setItems(FXCollections.observableArrayList(database.listAccountTypeSuggestions()));
        String selectedCurrency = currencyBox.getEditor().getText();
        currencyBox.setItems(FXCollections.observableArrayList(database.listCurrencySuggestions()));
        currencyBox.setValue(selectedCurrency == null || selectedCurrency.isBlank() ? database.getDefaultCurrency() : selectedCurrency);
        accounts.setAll(database.listAccounts());
        applySearch();
        totalAccountsLabel.setText(String.valueOf(accounts.size()));
        activeAccountsOverviewLabel.setText(String.valueOf(accounts.stream()
                .filter(account -> "ACTIVE".equals(account.getStatus()))
                .count()));
        defaultCurrencyLabel.setText(database.getDefaultCurrency());
    }

    @FXML
    private void newAccount() {
        clearForm();
        accountNameField.requestFocus();
    }

    private void configureAccountContextMenu() {
        accountsTable.setRowFactory(table -> {
            TableRow<Account> row = new TableRow<>();
            MenuItem activateItem = new MenuItem("Activate");

            activateItem.setOnAction(event -> updateStatus(row.getItem(), true));

            ContextMenu menu = new ContextMenu(activateItem);
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );
            return row;
        });
    }

    private void editAccount(Account account) {
        if (account == null) {
            return;
        }
        editingAccount = account;
        setEditMode(true);
        accountNameField.setText(account.getAccountName());
        accountTypeBox.getEditor().setText(account.getAccountType());
        selectCurrency(account.getCurrency());
        bankProviderField.setText(account.getBankProviderName() == null ? "" : account.getBankProviderName());
        accountNumberField.setText(account.getAccountNumber() == null ? "" : account.getAccountNumber());
        statusBox.getSelectionModel().select("ACTIVE".equals(account.getStatus()) ? "Active" : "Inactive");
        notesArea.setText(account.getNotes() == null ? "" : account.getNotes());
        accountNameField.requestFocus();
    }

    private void setEditMode(boolean editing) {
        accountFormPane.setText(editing ? "Edit Account" : "Open New Account");
        saveAccountButton.setText(editing ? "Update Account" : "Save Account");
        accountFormPane.setExpanded(true);
    }

    private void updateStatus(Account account, boolean active) {
        if (account == null) {
            return;
        }
        if (active) {
            database.activateAccount(account.getId());
        } else {
            database.deactivateAccount(account.getId());
        }
        refresh();
        DataRefreshBus.notifyDataChanged();
    }

    private void applySearch() {
        String search = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        if (search.isEmpty()) {
            accountsTable.setItems(FXCollections.observableArrayList(accounts));
            return;
        }
        accountsTable.setItems(accounts.filtered(account ->
                contains(account.getAccountName(), search)
                        || contains(account.getAccountType(), search)
                        || contains(account.getBankProviderName(), search)
                        || contains(account.getCurrency(), search)
                        || contains(account.getStatus(), search)
        ));
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }

    private String currencyCode() {
        String value = currencyBox.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = currencyBox.getValue();
        }
        if (value == null || value.isBlank()) {
            return "MWK";
        }
        int separator = value.indexOf(" - ");
        return separator > 0 ? value.substring(0, separator) : value;
    }

    private String statusValue() {
        return "Inactive".equals(statusBox.getValue()) ? "INACTIVE" : "ACTIVE";
    }

    private String textValue(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String notesValue() {
        return notesArea.getText() == null ? "" : notesArea.getText().trim();
    }

    private void selectCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            currencyBox.setValue(database.getDefaultCurrency());
            return;
        }
        for (String item : currencyBox.getItems()) {
            if (item.equals(currency) || item.startsWith(currency + " - ")) {
                currencyBox.setValue(item);
                return;
            }
        }
        currencyBox.setValue(currency);
    }
}
