package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Person;
import com.wk.pfmis.models.Project;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;

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

    @FXML
    public void initialize() {
        typeBox.setItems(FXCollections.observableArrayList("INCOME", "EXPENSE", "TRANSFER"));
        purposeBox.setItems(FXCollections.observableArrayList(
                "NORMAL", "PROJECT_EXPENSE", "MONEY_LENT", "MONEY_BORROWED",
                "LENT_REPAID", "BORROWED_REPAID", "SUPPORT_GIVEN", "SAVINGS", "GOAL_CONTRIBUTION"));
        statusBox.setItems(FXCollections.observableArrayList("COMPLETED", "OPEN", "PARTIALLY_CLEARED", "CLEARED", "CANCELLED"));
        typeBox.getSelectionModel().select("EXPENSE");
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
            database.recordTransaction(
                    account.getId(),
                    categoryBox.getValue() == null ? null : categoryBox.getValue().getId(),
                    projectBox.getValue() == null ? null : projectBox.getValue().getId(),
                    personBox.getValue() == null ? null : personBox.getValue().getId(),
                    typeBox.getValue(),
                    purposeBox.getValue(),
                    statusBox.getValue(),
                    amount,
                    datePicker.getValue(),
                    descriptionArea.getText().trim()
            );
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
        categoryBox.setItems(FXCollections.observableArrayList(database.listCategories()));
        projectBox.setItems(FXCollections.observableArrayList(database.listProjects()));
        personBox.setItems(FXCollections.observableArrayList(database.listPeople()));
        transactionsTable.setItems(FXCollections.observableArrayList(database.listRecentTransactions(100)));
    }

    @FXML
    private void editSelected() {
        if (transactionsTable.getSelectionModel().getSelectedItem() == null) {
            UiAlerts.info("Select a transaction to edit.");
            return;
        }
        UiAlerts.info("Transaction editing is not implemented yet.");
    }

    @FXML
    private void deleteSelected() {
        if (transactionsTable.getSelectionModel().getSelectedItem() == null) {
            UiAlerts.info("Select a transaction to delete.");
            return;
        }
        UiAlerts.info("Transaction deletion is not implemented yet.");
    }

    @FXML
    private void exportExcel() {
        UiAlerts.info("Excel export is not implemented yet.");
    }

    @FXML
    private void printTransactions() {
        UiAlerts.info("Printing is not implemented yet.");
    }
}
