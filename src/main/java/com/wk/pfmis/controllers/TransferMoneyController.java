package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.FinanceTransaction;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class TransferMoneyController {
    @FXML private ComboBox<Account> fromAccountBox;
    @FXML private ComboBox<Account> toAccountBox;
    @FXML private Label fromBalanceLabel;
    @FXML private Label toBalanceLabel;
    @FXML private TextField amountSentField;
    @FXML private TextField amountReceivedField;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> paymentMethodBox;
    @FXML private TextField referenceField;
    @FXML private TextArea descriptionArea;
    @FXML private TableView<FinanceTransaction> transfersTable;
    @FXML private TableColumn<FinanceTransaction, String> dateColumn;
    @FXML private TableColumn<FinanceTransaction, String> accountColumn;
    @FXML private TableColumn<FinanceTransaction, String> purposeColumn;
    @FXML private TableColumn<FinanceTransaction, Double> amountColumn;
    @FXML private TableColumn<FinanceTransaction, String> methodColumn;
    @FXML private TableColumn<FinanceTransaction, String> referenceColumn;

    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        MONEY_FORMAT.setMinimumFractionDigits(2);
        MONEY_FORMAT.setMaximumFractionDigits(2);

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        accountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        purposeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionPurpose"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        methodColumn.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        referenceColumn.setCellValueFactory(new PropertyValueFactory<>("referenceNumber"));

        datePicker.setValue(LocalDate.now());
        fromAccountBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshBalanceLabels());
        toAccountBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshBalanceLabels());
        configureTransferRowActions();
        refresh();
    }

    @FXML
    private void saveTransfer() {
        Account fromAccount = fromAccountBox.getValue();
        Account toAccount = toAccountBox.getValue();
        if (fromAccount == null) {
            UiAlerts.info("Select the account money is leaving.");
            return;
        }
        if (toAccount == null) {
            UiAlerts.info("Select the account money is going to.");
            return;
        }
        if (fromAccount.getId() == toAccount.getId()) {
            UiAlerts.info("Choose two different accounts.");
            return;
        }
        try {
            double amountSent = parseAmount(amountSentField.getText(), "Enter the amount sent.");
            double amountReceived = amountReceivedField.getText() == null || amountReceivedField.getText().isBlank()
                    ? amountSent
                    : parseAmount(amountReceivedField.getText(), "Enter the amount received.");
            if (!sameCurrency(fromAccount, toAccount)
                    && (amountReceivedField.getText() == null || amountReceivedField.getText().isBlank())) {
                UiAlerts.info("Enter the amount received for transfers between different currencies.");
                return;
            }

            database.recordTransfer(
                    fromAccount.getId(),
                    toAccount.getId(),
                    amountSent,
                    amountReceived,
                    datePicker.getValue(),
                    clean(descriptionArea.getText()),
                    clean(paymentMethodBox.getEditor().getText()),
                    clean(referenceField.getText())
            );
            clearForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Transfer saved and both account balances updated.");
        } catch (IllegalArgumentException exception) {
            UiAlerts.info(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save transfer", exception);
        }
    }

    @FXML
    private void clearForm() {
        fromAccountBox.setValue(null);
        toAccountBox.setValue(null);
        amountSentField.clear();
        amountReceivedField.clear();
        datePicker.setValue(LocalDate.now());
        paymentMethodBox.getEditor().clear();
        referenceField.clear();
        descriptionArea.clear();
        refreshBalanceLabels();
    }

    @FXML
    private void refresh() {
        String selectedFrom = fromAccountBox.getValue() == null ? null : fromAccountBox.getValue().getAccountName();
        String selectedTo = toAccountBox.getValue() == null ? null : toAccountBox.getValue().getAccountName();
        List<Account> activeAccounts = database.listAccounts().stream()
                .filter(account -> "ACTIVE".equals(account.getStatus()))
                .toList();
        fromAccountBox.setItems(FXCollections.observableArrayList(activeAccounts));
        toAccountBox.setItems(FXCollections.observableArrayList(activeAccounts));
        selectAccountByName(fromAccountBox, selectedFrom);
        selectAccountByName(toAccountBox, selectedTo);
        paymentMethodBox.setItems(FXCollections.observableArrayList(database.listPaymentMethodSuggestions()));

        List<FinanceTransaction> outgoingTransfers = database.listRecentTransactions(200).stream()
                .filter(transaction -> "TRANSFER".equals(transaction.getTransactionType()))
                .filter(transaction -> "TRANSFER_OUT".equals(transaction.getTransactionPurpose()))
                .toList();
        transfersTable.setItems(FXCollections.observableArrayList(outgoingTransfers));
        refreshBalanceLabels();
    }

    private void configureTransferRowActions() {
        transfersTable.setRowFactory(table -> {
            TableRow<FinanceTransaction> row = new TableRow<>();
            MenuItem viewItem = new MenuItem("View");
            MenuItem deleteItem = new MenuItem("Delete");
            MenuItem refreshItem = new MenuItem("Refresh");
            ContextMenu menu = new ContextMenu(viewItem, deleteItem, refreshItem);

            viewItem.setOnAction(event -> {
                transfersTable.getSelectionModel().select(row.getItem());
                viewSelected();
            });
            deleteItem.setOnAction(event -> {
                transfersTable.getSelectionModel().select(row.getItem());
                deleteSelected();
            });
            refreshItem.setOnAction(event -> refresh());

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && isLeftSingleClick(event)) {
                    transfersTable.getSelectionModel().select(row.getItem());
                    menu.show(row, event.getScreenX() + 8, event.getScreenY() + 8);
                    event.consume();
                }
            });
            return row;
        });
    }

    private void viewSelected() {
        FinanceTransaction selected = transfersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a transfer to view.");
            return;
        }
        UiAlerts.info(
                "Date: " + selected.getTransactionDate()
                        + "\nFrom account: " + selected.getAccountName()
                        + "\nAmount sent: " + MONEY_FORMAT.format(selected.getAmount())
                        + "\nMethod: " + blankToDash(selected.getPaymentMethod())
                        + "\nReference: " + blankToDash(selected.getReferenceNumber())
                        + "\nDescription: " + blankToDash(selected.getDescription())
        );
    }

    private void deleteSelected() {
        FinanceTransaction selected = transfersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a transfer to delete.");
            return;
        }
        try {
            database.deleteTransaction(selected.getId());
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to delete transfer", exception);
        }
    }

    private void refreshBalanceLabels() {
        fromBalanceLabel.setText(balanceText(fromAccountBox.getValue()));
        toBalanceLabel.setText(balanceText(toAccountBox.getValue()));
    }

    private String balanceText(Account account) {
        if (account == null) {
            return "Balance: -";
        }
        return "Balance: " + account.getCurrency() + " " + MONEY_FORMAT.format(account.getCurrentBalance());
    }

    private boolean sameCurrency(Account first, Account second) {
        return clean(first.getCurrency()).equalsIgnoreCase(clean(second.getCurrency()));
    }

    private double parseAmount(String value, String emptyMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        double amount;
        try {
            amount = Double.parseDouble(value.replace(",", "").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter amounts using numbers only.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return amount;
    }

    private void selectAccountByName(ComboBox<Account> box, String accountName) {
        if (accountName == null || accountName.isBlank()) {
            return;
        }
        box.getItems().stream()
                .filter(account -> account.getAccountName().equals(accountName))
                .findFirst()
                .ifPresent(box::setValue);
    }

    private boolean isLeftSingleClick(javafx.scene.input.MouseEvent event) {
        return event.getButton() == MouseButton.PRIMARY
                && event.getClickCount() == 1
                && event.isStillSincePress();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
