package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.time.YearMonth;

public class IncomeController {
    @FXML private Label todayIncomeLabel;
    @FXML private Label monthIncomeLabel;
    @FXML private Label selectedAccountLabel;
    @FXML private ComboBox<Account> accountBox;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private ComboBox<String> paymentMethodBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextField amountField;
    @FXML private TextField referenceField;
    @FXML private DatePicker datePicker;
    @FXML private TextArea descriptionArea;
    @FXML private TableView<FinanceTransaction> recentIncomeTable;
    @FXML private TableColumn<FinanceTransaction, String> dateColumn;
    @FXML private TableColumn<FinanceTransaction, String> sourceColumn;
    @FXML private TableColumn<FinanceTransaction, Double> amountColumn;
    @FXML private TableColumn<FinanceTransaction, String> accountColumn;
    @FXML private TableColumn<FinanceTransaction, String> paymentMethodColumn;
    @FXML private TableColumn<FinanceTransaction, String> referenceColumn;
    @FXML private TableColumn<FinanceTransaction, String> statusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        datePicker.setValue(LocalDate.now());
        statusBox.setItems(FXCollections.observableArrayList("Received", "Pending", "Cancelled"));
        statusBox.getSelectionModel().select("Received");
        accountBox.valueProperty().addListener((observable, oldValue, newValue) ->
                selectedAccountLabel.setText(newValue == null ? "None" : newValue.getAccountName()));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        accountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        paymentMethodColumn.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        referenceColumn.setCellValueFactory(new PropertyValueFactory<>("referenceNumber"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("incomeStatusLabel"));
        refresh();
    }

    @FXML
    private void saveIncome() {
        try {
            Account account = accountBox.getValue();
            if (account == null) {
                UiAlerts.info("Select an account.");
                return;
            }
            double amount = Double.parseDouble(amountField.getText().replace(",", "").trim());
            String status = incomeStatus();
            database.recordTransaction(
                    account.getId(),
                    categoryBox.getValue() == null ? null : categoryBox.getValue().getId(),
                    null,
                    null,
                    "INCOME",
                    "NORMAL",
                    status,
                    amount,
                    datePicker.getValue(),
                    descriptionValue(),
                    paymentMethodValue(),
                    referenceValue()
            );
            amountField.clear();
            referenceField.clear();
            descriptionArea.clear();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Income saved and account balance updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save income", exception);
        }
    }

    @FXML
    private void refresh() {
        accountBox.setItems(FXCollections.observableArrayList(database.listAccounts()));
        String selectedPaymentMethod = paymentMethodBox.getEditor().getText();
        paymentMethodBox.setItems(FXCollections.observableArrayList(database.listPaymentMethodSuggestions()));
        paymentMethodBox.setValue(selectedPaymentMethod == null || selectedPaymentMethod.isBlank() ? "Bank Transfer" : selectedPaymentMethod);
        categoryBox.setItems(FXCollections.observableArrayList(
                database.listCategories().stream()
                        .filter(category -> "INCOME".equals(category.getCategoryType()) || "BOTH".equals(category.getCategoryType()))
                        .toList()
        ));
        var incomeTransactions = database.listRecentTransactions(100).stream()
                .filter(transaction -> "INCOME".equals(transaction.getTransactionType()))
                .toList();
        recentIncomeTable.setItems(FXCollections.observableArrayList(incomeTransactions));
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.now();
        double todayIncome = incomeTransactions.stream()
                .filter(transaction -> today.toString().equals(transaction.getTransactionDate()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();
        double monthIncome = incomeTransactions.stream()
                .filter(transaction -> transaction.getTransactionDate() != null
                        && YearMonth.from(LocalDate.parse(transaction.getTransactionDate())).equals(thisMonth))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();
        todayIncomeLabel.setText(MoneyUtil.mwk(todayIncome));
        monthIncomeLabel.setText(MoneyUtil.mwk(monthIncome));
        selectedAccountLabel.setText(accountBox.getValue() == null ? "None" : accountBox.getValue().getAccountName());
    }

    @FXML
    private void clearForm() {
        amountField.clear();
        referenceField.clear();
        descriptionArea.clear();
        paymentMethodBox.getSelectionModel().select("Bank Transfer");
        statusBox.getSelectionModel().select("Received");
        datePicker.setValue(LocalDate.now());
    }

    @FXML
    private void viewIncome() {
        FinanceTransaction selected = selectedIncome("view");
        if (selected != null) {
            UiAlerts.info(selected.getTransactionDate() + " income: " + MoneyUtil.mwk(selected.getAmount()));
        }
    }

    @FXML
    private void editIncome() {
        if (selectedIncome("edit") != null) {
            UiAlerts.info("Income editing is not implemented yet.");
        }
    }

    @FXML
    private void deleteIncome() {
        if (selectedIncome("delete") != null) {
            UiAlerts.info("Income deletion is not implemented yet.");
        }
    }

    private FinanceTransaction selectedIncome(String action) {
        FinanceTransaction selected = recentIncomeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select an income record to " + action + ".");
        }
        return selected;
    }

    private String incomeStatus() {
        return switch (statusBox.getValue()) {
            case "Pending" -> "OPEN";
            case "Cancelled" -> "CANCELLED";
            default -> "COMPLETED";
        };
    }

    private String descriptionValue() {
        return descriptionArea.getText() == null ? "" : descriptionArea.getText().trim();
    }

    private String paymentMethodValue() {
        String value = paymentMethodBox.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = paymentMethodBox.getValue();
        }
        return value == null ? "" : value.trim();
    }

    private String referenceValue() {
        return referenceField.getText() == null ? "" : referenceField.getText().trim();
    }
}
