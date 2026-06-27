package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Person;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;

import java.time.LocalDate;
import java.util.List;

public class TransactionsController {
    @FXML private ComboBox<Account> accountBox;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private ComboBox<Project> projectBox;
    @FXML private ComboBox<Person> personBox;
    @FXML private ComboBox<String> typeBox;
    @FXML private ComboBox<String> purposeBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextField amountField;
    @FXML private DatePicker datePicker;
    @FXML private TextArea descriptionArea;
    @FXML private TableView<FinanceTransaction> transactionsTable;
    @FXML private TableColumn<FinanceTransaction, String> dateColumn;
    @FXML private TableColumn<FinanceTransaction, String> typeColumn;
    @FXML private TableColumn<FinanceTransaction, String> purposeColumn;
    @FXML private TableColumn<FinanceTransaction, String> accountColumn;
    @FXML private TableColumn<FinanceTransaction, String> categoryColumn;
    @FXML private TableColumn<FinanceTransaction, String> statusColumn;
    @FXML private TableColumn<FinanceTransaction, Double> amountColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private FinanceTransaction editingTransaction;
    private String requestedTransactionType;

    @FXML
    public void initialize() {
        typeBox.setItems(FXCollections.observableArrayList("INCOME", "EXPENSE", "TRANSFER"));
        purposeBox.setItems(FXCollections.observableArrayList(
                "NORMAL", "PROJECT_EXPENSE", "MONEY_LENT", "MONEY_BORROWED",
                "LENT_REPAID", "BORROWED_REPAID", "SUPPORT_GIVEN", "SAVINGS", "GOAL_CONTRIBUTION"));
        statusBox.setItems(FXCollections.observableArrayList("COMPLETED", "OPEN", "PARTIALLY_CLEARED", "CLEARED", "CANCELLED"));
        requestedTransactionType = NavigationBus.consumeRequestedTransactionType();
        typeBox.getSelectionModel().select(requestedTransactionType == null ? "EXPENSE" : requestedTransactionType);
        purposeBox.getSelectionModel().select("NORMAL");
        statusBox.getSelectionModel().select("COMPLETED");
        datePicker.setValue(LocalDate.now());

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionType"));
        purposeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionPurpose"));
        accountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("transactionStatus"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        CategoryInput.configure(categoryBox);
        configureTransactionRowActions();
        refresh();
    }

    @FXML
    private void saveTransaction() {
        try {
            Account account = accountBox.getValue();
            if (account == null) {
                UiAlerts.info("Select an account.");
                return;
            }
            double amount = Double.parseDouble(amountField.getText().replace(",", "").trim());
            Integer categoryId = CategoryInput.resolveCategoryId(database, categoryBox, transactionCategoryType());
            if (editingTransaction == null) {
                database.recordTransaction(
                        account.getId(),
                        categoryId,
                        projectBox.getValue() == null ? null : projectBox.getValue().getId(),
                        personBox.getValue() == null ? null : personBox.getValue().getId(),
                        typeBox.getValue(),
                        purposeBox.getValue(),
                        statusBox.getValue(),
                        amount,
                        datePicker.getValue(),
                        descriptionArea.getText().trim()
                );
            } else {
                database.updateTransaction(
                        editingTransaction.getId(),
                        account.getId(),
                        categoryId,
                        projectBox.getValue() == null ? null : projectBox.getValue().getId(),
                        personBox.getValue() == null ? null : personBox.getValue().getId(),
                        typeBox.getValue(),
                        purposeBox.getValue(),
                        statusBox.getValue(),
                        amount,
                        datePicker.getValue(),
                        descriptionArea.getText().trim(),
                        editingTransaction.getPaymentMethod(),
                        editingTransaction.getReferenceNumber()
                );
                editingTransaction = null;
            }
            amountField.clear();
            descriptionArea.clear();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Transaction saved and account balance updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save transaction", exception);
        }
    }

    @FXML
    private void refresh() {
        accountBox.setItems(FXCollections.observableArrayList(database.listAccounts()));
        CategoryInput.setItems(categoryBox, database.listCategories());
        projectBox.setItems(FXCollections.observableArrayList(database.listProjects()));
        personBox.setItems(FXCollections.observableArrayList(database.listPeople()));
        List<FinanceTransaction> transactions = database.listRecentTransactions(100);
        if (requestedTransactionType != null) {
            transactions = transactions.stream()
                    .filter(transaction -> requestedTransactionType.equals(transaction.getTransactionType()))
                    .toList();
        }
        transactionsTable.setItems(FXCollections.observableArrayList(transactions));
    }

    @FXML
    private void editSelected() {
        FinanceTransaction selected = transactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a transaction to edit.");
            return;
        }
        editingTransaction = selected;
        selectAccountByName(selected.getAccountName());
        selectCategoryByName(selected.getCategoryName());
        selectProjectByName(selected.getProjectName());
        selectPersonByName(selected.getPersonName());
        typeBox.getSelectionModel().select(selected.getTransactionType());
        purposeBox.getSelectionModel().select(selected.getTransactionPurpose());
        statusBox.getSelectionModel().select(selected.getTransactionStatus());
        amountField.setText(String.valueOf(selected.getAmount()));
        if (selected.getTransactionDate() != null && !selected.getTransactionDate().isBlank()) {
            datePicker.setValue(LocalDate.parse(selected.getTransactionDate()));
        }
        descriptionArea.setText(selected.getDescription() == null ? "" : selected.getDescription());
    }

    @FXML
    private void viewSelected() {
        FinanceTransaction selected = transactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a transaction to view.");
            return;
        }
        UiAlerts.info(
                "Date: " + selected.getTransactionDate()
                        + "\nType: " + selected.getTransactionType()
                        + "\nPurpose: " + selected.getTransactionPurpose()
                        + "\nAccount: " + selected.getAccountName()
                        + "\nCategory: " + blankToDash(selected.getCategoryName())
                        + "\nStatus: " + selected.getTransactionStatus()
                        + "\nAmount: " + MoneyUtil.mwk(selected.getAmount())
                        + "\nDescription: " + blankToDash(selected.getDescription())
        );
    }

    @FXML
    private void deleteSelected() {
        FinanceTransaction selected = transactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a transaction to delete.");
            return;
        }
        try {
            database.deleteTransaction(selected.getId());
            if (editingTransaction != null && editingTransaction.getId() == selected.getId()) {
                editingTransaction = null;
                amountField.clear();
                descriptionArea.clear();
            }
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to delete transaction", exception);
        }
    }

    @FXML
    private void exportExcel() {
        UiAlerts.info("Excel export is not implemented yet.");
    }

    @FXML
    private void printTransactions() {
        UiAlerts.info("Printing is not implemented yet.");
    }

    private void configureTransactionRowActions() {
        transactionsTable.setRowFactory(table -> {
            TableRow<FinanceTransaction> row = new TableRow<>();
            MenuItem viewItem = new MenuItem("View");
            MenuItem editItem = new MenuItem("Edit");
            MenuItem deleteItem = new MenuItem("Delete");
            MenuItem refreshItem = new MenuItem("Refresh");
            ContextMenu menu = new ContextMenu(viewItem, editItem, deleteItem, refreshItem);

            viewItem.setOnAction(event -> {
                transactionsTable.getSelectionModel().select(row.getItem());
                viewSelected();
            });
            editItem.setOnAction(event -> {
                transactionsTable.getSelectionModel().select(row.getItem());
                editSelected();
            });
            deleteItem.setOnAction(event -> {
                transactionsTable.getSelectionModel().select(row.getItem());
                deleteSelected();
            });
            refreshItem.setOnAction(event -> refresh());

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY) {
                    transactionsTable.getSelectionModel().select(row.getItem());
                    menu.show(row, event.getScreenX(), event.getScreenY());
                    event.consume();
                }
            });

            return row;
        });
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

    private void selectProjectByName(String projectName) {
        projectBox.getItems().stream()
                .filter(project -> projectName != null && project.getProjectName().equals(projectName))
                .findFirst()
                .ifPresent(projectBox::setValue);
    }

    private void selectPersonByName(String personName) {
        personBox.getItems().stream()
                .filter(person -> personName != null && person.getFullName().equals(personName))
                .findFirst()
                .ifPresent(personBox::setValue);
    }

    private String transactionCategoryType() {
        return switch (typeBox.getValue()) {
            case "INCOME" -> "INCOME";
            case "EXPENSE" -> "EXPENSE";
            default -> "BOTH";
        };
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
