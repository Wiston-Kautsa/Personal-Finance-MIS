package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ComboBox;
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
    @FXML private TableColumn<FinanceTransaction, String> amountColumn;
    @FXML private TableColumn<FinanceTransaction, String> accountColumn;
    @FXML private TableColumn<FinanceTransaction, String> paymentMethodColumn;
    @FXML private TableColumn<FinanceTransaction, String> referenceColumn;
    @FXML private TableColumn<FinanceTransaction, String> statusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private FinanceTransaction editingIncome;

    @FXML
    public void initialize() {
        datePicker.setValue(LocalDate.now());
        statusBox.setItems(FXCollections.observableArrayList("Received", "Pending", "Cancelled"));
        statusBox.getSelectionModel().select("Received");
        accountBox.valueProperty().addListener((observable, oldValue, newValue) ->
                selectedAccountLabel.setText(newValue == null ? "None" : newValue.getAccountName()));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        amountColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmount())));
        accountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        paymentMethodColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getPaymentMethod())));
        referenceColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getReferenceNumber())));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("incomeStatusLabel"));
        recentIncomeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        CategoryInput.configure(categoryBox);
        configureIncomeRowActions();
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
            Integer categoryId = CategoryInput.resolveCategoryId(database, categoryBox, "INCOME");
            if (editingIncome == null) {
                database.recordTransaction(
                        account.getId(),
                        categoryId,
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
            } else {
                database.updateTransaction(
                        editingIncome.getId(),
                        account.getId(),
                        categoryId,
                        null,
                        null,
                        "INCOME",
                        editingIncome.getTransactionPurpose(),
                        status,
                        amount,
                        datePicker.getValue(),
                        descriptionValue(),
                        paymentMethodValue(),
                        referenceValue()
                );
                editingIncome = null;
            }
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
        CategoryInput.setItems(categoryBox, database.listCategories().stream()
                .filter(category -> "INCOME".equals(category.getCategoryType()) || "BOTH".equals(category.getCategoryType()))
                .toList());
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
        editingIncome = null;
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
        FinanceTransaction selected = selectedIncome("edit");
        if (selected != null) {
            editingIncome = selected;
            selectAccountByName(selected.getAccountName());
            selectCategoryByName(selected.getCategoryName());
            amountField.setText(String.valueOf(selected.getAmount()));
            datePicker.setValue(LocalDate.parse(selected.getTransactionDate()));
            paymentMethodBox.setValue(selected.getPaymentMethod());
            referenceField.setText(selected.getReferenceNumber() == null ? "" : selected.getReferenceNumber());
            descriptionArea.setText(selected.getDescription() == null ? "" : selected.getDescription());
            statusBox.getSelectionModel().select(selected.getIncomeStatusLabel());
        }
    }

    @FXML
    private void deleteIncome() {
        FinanceTransaction selected = selectedIncome("delete");
        if (selected != null) {
            try {
                database.deleteTransaction(selected.getId());
                if (editingIncome != null && editingIncome.getId() == selected.getId()) {
                    clearForm();
                }
                refresh();
                DataRefreshBus.notifyDataChanged();
            } catch (RuntimeException exception) {
                UiAlerts.error("Failed to delete income", exception);
            }
        }
    }

    private void configureIncomeRowActions() {
        recentIncomeTable.setRowFactory(table -> {
            TableRow<FinanceTransaction> row = new TableRow<>();
            MenuItem viewItem = new MenuItem("View");
            MenuItem editItem = new MenuItem("Edit");
            MenuItem deleteItem = new MenuItem("Delete");
            MenuItem refreshItem = new MenuItem("Refresh");
            ContextMenu menu = new ContextMenu(viewItem, editItem, deleteItem, refreshItem);

            viewItem.setOnAction(event -> {
                recentIncomeTable.getSelectionModel().select(row.getItem());
                viewIncome();
            });
            editItem.setOnAction(event -> {
                recentIncomeTable.getSelectionModel().select(row.getItem());
                editIncome();
            });
            deleteItem.setOnAction(event -> {
                recentIncomeTable.getSelectionModel().select(row.getItem());
                deleteIncome();
            });
            refreshItem.setOnAction(event -> refresh());

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY) {
                    recentIncomeTable.getSelectionModel().select(row.getItem());
                    menu.show(row, event.getScreenX(), event.getScreenY());
                    event.consume();
                }
            });

            return row;
        });
    }

    private FinanceTransaction selectedIncome(String action) {
        FinanceTransaction selected = recentIncomeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select an income record to " + action + ".");
        }
        return selected;
    }

    private void selectAccountByName(String accountName) {
        accountBox.getItems().stream()
                .filter(account -> account.getAccountName().equals(accountName))
                .findFirst()
                .ifPresent(accountBox::setValue);
    }

    private void selectCategoryByName(String categoryName) {
        CategoryInput.selectByName(categoryBox, categoryName);
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

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
