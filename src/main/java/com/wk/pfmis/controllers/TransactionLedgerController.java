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
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;

import java.time.LocalDate;
import java.util.List;

public class TransactionLedgerController {
    @FXML private ComboBox<String> typeFilterBox;
    @FXML private ComboBox<Account> accountFilterBox;
    @FXML private ComboBox<String> statusFilterBox;
    @FXML private DatePicker dateFilterPicker;
    @FXML private TableView<FinanceTransaction> transactionsTable;
    @FXML private TableColumn<FinanceTransaction, String> dateColumn;
    @FXML private TableColumn<FinanceTransaction, String> typeColumn;
    @FXML private TableColumn<FinanceTransaction, String> purposeColumn;
    @FXML private TableColumn<FinanceTransaction, String> accountColumn;
    @FXML private TableColumn<FinanceTransaction, String> categoryColumn;
    @FXML private TableColumn<FinanceTransaction, String> statusColumn;
    @FXML private TableColumn<FinanceTransaction, Double> amountColumn;
    @FXML private TitledPane editPane;
    @FXML private ComboBox<Account> editAccountBox;
    @FXML private ComboBox<Category> editCategoryBox;
    @FXML private ComboBox<Project> editProjectBox;
    @FXML private ComboBox<Person> editPersonBox;
    @FXML private ComboBox<String> editTypeBox;
    @FXML private ComboBox<String> editPurposeBox;
    @FXML private ComboBox<String> editStatusBox;
    @FXML private TextField editAmountField;
    @FXML private DatePicker editDatePicker;
    @FXML private TextArea editDescriptionArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private FinanceTransaction editingTransaction;

    @FXML
    public void initialize() {
        typeFilterBox.setItems(FXCollections.observableArrayList("All Types", "INCOME", "EXPENSE", "TRANSFER"));
        statusFilterBox.setItems(FXCollections.observableArrayList("All Statuses", "COMPLETED", "OPEN", "PARTIALLY_CLEARED", "CLEARED", "CANCELLED"));
        typeFilterBox.getSelectionModel().select("All Types");
        statusFilterBox.getSelectionModel().select("All Statuses");

        editTypeBox.setItems(FXCollections.observableArrayList("INCOME", "EXPENSE", "TRANSFER"));
        editPurposeBox.setItems(FXCollections.observableArrayList(
                "NORMAL", "PROJECT_EXPENSE", "MONEY_LENT", "MONEY_BORROWED",
                "LENT_REPAID", "BORROWED_REPAID", "SUPPORT_GIVEN", "SAVINGS", "GOAL_CONTRIBUTION"));
        editStatusBox.setItems(FXCollections.observableArrayList("COMPLETED", "OPEN", "PARTIALLY_CLEARED", "CLEARED", "CANCELLED"));
        CategoryInput.configure(editCategoryBox);
        editTypeBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshEditCategories());

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionType"));
        purposeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionPurpose"));
        accountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("transactionStatus"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));

        hideEditPane();
        configureTransactionRowActions();
        refresh();
    }

    @FXML
    private void refresh() {
        refreshAccounts();
        editProjectBox.setItems(FXCollections.observableArrayList(database.listProjects()));
        editPersonBox.setItems(FXCollections.observableArrayList(database.listPeople()));
        refreshEditCategories();

        List<FinanceTransaction> transactions = database.listRecentTransactions(500).stream()
                .filter(this::matchesFilters)
                .toList();
        transactionsTable.setItems(FXCollections.observableArrayList(transactions));
    }

    @FXML
    private void clearFilters() {
        typeFilterBox.getSelectionModel().select("All Types");
        accountFilterBox.setValue(null);
        statusFilterBox.getSelectionModel().select("All Statuses");
        dateFilterPicker.setValue(null);
        refresh();
    }

    @FXML
    private void saveEdit() {
        if (editingTransaction == null) {
            UiAlerts.info("Select a transaction to edit.");
            return;
        }
        try {
            Account account = editAccountBox.getValue();
            if (account == null) {
                UiAlerts.info("Select an account.");
                return;
            }
            double amount = Double.parseDouble(editAmountField.getText().replace(",", "").trim());
            Integer categoryId = CategoryInput.resolveCategoryId(database, editCategoryBox, editTypeBox.getValue());
            database.updateTransaction(
                    editingTransaction.getId(),
                    account.getId(),
                    categoryId,
                    editProjectBox.getValue() == null ? null : editProjectBox.getValue().getId(),
                    editPersonBox.getValue() == null ? null : editPersonBox.getValue().getId(),
                    editTypeBox.getValue(),
                    editPurposeBox.getValue(),
                    editStatusBox.getValue(),
                    amount,
                    editDatePicker.getValue(),
                    editDescriptionArea.getText() == null ? "" : editDescriptionArea.getText().trim(),
                    editingTransaction.getPaymentMethod(),
                    editingTransaction.getReferenceNumber()
            );
            cancelEdit();
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update transaction", exception);
        }
    }

    @FXML
    private void cancelEdit() {
        editingTransaction = null;
        editAmountField.clear();
        editDescriptionArea.clear();
        hideEditPane();
    }

    @FXML
    private void exportExcel() {
        UiAlerts.info("Excel export is not implemented yet.");
    }

    @FXML
    private void printLedger() {
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
                if (!row.isEmpty() && isLeftSingleClick(event)) {
                    transactionsTable.getSelectionModel().select(row.getItem());
                    menu.show(row, event.getScreenX() + 8, event.getScreenY() + 8);
                    event.consume();
                }
            });

            return row;
        });
    }

    private boolean isLeftSingleClick(javafx.scene.input.MouseEvent event) {
        return event.getButton() == MouseButton.PRIMARY
                && event.getClickCount() == 1
                && event.isStillSincePress();
    }

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

    private void editSelected() {
        FinanceTransaction selected = transactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a transaction to edit.");
            return;
        }
        if ("TRANSFER".equals(selected.getTransactionType())) {
            UiAlerts.info("Transfers are linked two-account records. Delete and re-record the transfer from Transfer Money if it needs correction.");
            return;
        }
        editingTransaction = selected;
        showEditPane();
        selectAccountByName(editAccountBox, selected.getAccountName());
        editTypeBox.getSelectionModel().select(selected.getTransactionType());
        refreshEditCategories();
        CategoryInput.selectByName(editCategoryBox, selected.getCategoryName());
        selectProjectByName(selected.getProjectName());
        selectPersonByName(selected.getPersonName());
        editPurposeBox.getSelectionModel().select(selected.getTransactionPurpose());
        editStatusBox.getSelectionModel().select(selected.getTransactionStatus());
        editAmountField.setText(String.valueOf(selected.getAmount()));
        editDatePicker.setValue(selected.getTransactionDate() == null || selected.getTransactionDate().isBlank()
                ? LocalDate.now()
                : LocalDate.parse(selected.getTransactionDate()));
        editDescriptionArea.setText(selected.getDescription() == null ? "" : selected.getDescription());
    }

    private void deleteSelected() {
        FinanceTransaction selected = transactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a transaction to delete.");
            return;
        }
        try {
            database.deleteTransaction(selected.getId());
            if (editingTransaction != null && editingTransaction.getId() == selected.getId()) {
                cancelEdit();
            }
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to delete transaction", exception);
        }
    }

    private void refreshAccounts() {
        Account selectedFilterAccount = accountFilterBox.getValue();
        Account selectedEditAccount = editAccountBox.getValue();
        List<Account> accounts = database.listAccounts();
        accountFilterBox.setItems(FXCollections.observableArrayList(accounts));
        editAccountBox.setItems(FXCollections.observableArrayList(accounts));
        if (selectedFilterAccount != null) {
            selectAccountByName(accountFilterBox, selectedFilterAccount.getAccountName());
        }
        if (selectedEditAccount != null) {
            selectAccountByName(editAccountBox, selectedEditAccount.getAccountName());
        }
    }

    private void refreshEditCategories() {
        CategoryInput.setItemsForType(editCategoryBox, database.listCategories(), editTypeBox.getValue());
    }

    private boolean matchesFilters(FinanceTransaction transaction) {
        String typeFilter = typeFilterBox.getValue();
        if (typeFilter != null && !"All Types".equals(typeFilter) && !typeFilter.equals(transaction.getTransactionType())) {
            return false;
        }
        Account accountFilter = accountFilterBox.getValue();
        if (accountFilter != null && !accountFilter.getAccountName().equals(transaction.getAccountName())) {
            return false;
        }
        String statusFilter = statusFilterBox.getValue();
        if (statusFilter != null && !"All Statuses".equals(statusFilter) && !statusFilter.equals(transaction.getTransactionStatus())) {
            return false;
        }
        LocalDate dateFilter = dateFilterPicker.getValue();
        return dateFilter == null || dateFilter.toString().equals(transaction.getTransactionDate());
    }

    private void selectAccountByName(ComboBox<Account> box, String accountName) {
        box.getItems().stream()
                .filter(account -> accountName != null && account.getAccountName().equals(accountName))
                .findFirst()
                .ifPresent(box::setValue);
    }

    private void selectProjectByName(String projectName) {
        editProjectBox.setValue(null);
        editProjectBox.getItems().stream()
                .filter(project -> projectName != null && project.getProjectName().equals(projectName))
                .findFirst()
                .ifPresent(editProjectBox::setValue);
    }

    private void selectPersonByName(String personName) {
        editPersonBox.setValue(null);
        editPersonBox.getItems().stream()
                .filter(person -> personName != null && person.getFullName().equals(personName))
                .findFirst()
                .ifPresent(editPersonBox::setValue);
    }

    private void showEditPane() {
        editPane.setVisible(true);
        editPane.setManaged(true);
        editPane.setExpanded(true);
    }

    private void hideEditPane() {
        editPane.setVisible(false);
        editPane.setManaged(false);
        editPane.setExpanded(false);
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
