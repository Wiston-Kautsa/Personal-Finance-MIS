package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.RecurringTransactionPlan;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class RecurringIncomeController {
    @FXML private TextField planNameField;
    @FXML private TextField amountField;
    @FXML private ComboBox<String> frequencyBox;
    @FXML private DatePicker nextDueDatePicker;
    @FXML private ComboBox<Account> accountBox;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private ComboBox<Project> projectBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea notesArea;
    @FXML private Label resultLabel;
    @FXML private Label plansStateLabel;
    @FXML private TableView<RecurringTransactionPlan> plansTable;
    @FXML private TableColumn<RecurringTransactionPlan, String> nameColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> nextDueColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> accountColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> sourceColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> amountColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> frequencyColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> statusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Account> accounts = List.of();
    private List<Category> categories = List.of();
    private List<Project> projects = List.of();
    private RecurringTransactionPlan selectedPlan;

    @FXML
    public void initialize() {
        CoreWorkspaceSupport.setComboItems(frequencyBox, "Monthly", "Weekly", "Monthly", "Quarterly", "Yearly", "Custom");
        CoreWorkspaceSupport.setComboItems(statusBox, "ACTIVE", "ACTIVE", "PAUSED", "DRAFT", "CANCELLED");
        nextDueDatePicker.setValue(LocalDate.now());
        configureTable();
        plansTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> applySelectedPlan(newValue));
        refresh();
    }

    @FXML
    private void savePlan() {
        try {
            String name = CoreWorkspaceSupport.required(planNameField, "Plan name");
            double amount = CoreWorkspaceSupport.positiveAmount(amountField, "Income amount");
            LocalDate nextDue = CoreWorkspaceSupport.requiredDate(nextDueDatePicker, "Next due date");
            Account account = accountBox.getValue();
            if (account == null) {
                throw new IllegalArgumentException("Choose the account expected to receive this income.");
            }
            database.saveRecurringTransactionPlan(
                    selectedPlan == null ? null : selectedPlan.getId(),
                    name,
                    "INCOME",
                    amount,
                    CoreWorkspaceSupport.selected(frequencyBox, "Monthly"),
                    nextDue.toString(),
                    CoreWorkspaceSupport.id(account),
                    CoreWorkspaceSupport.id(categoryBox.getValue()),
                    CoreWorkspaceSupport.id(projectBox.getValue()),
                    CoreWorkspaceSupport.selected(statusBox, "ACTIVE"),
                    notesArea.getText()
            );
            DataRefreshBus.notifyDataChanged();
            resultLabel.setText("Recurring income plan saved.");
            clearForm();
            refresh();
        } catch (IllegalArgumentException exception) {
            resultLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save recurring income", exception);
        }
    }

    @FXML
    private void clearForm() {
        selectedPlan = null;
        planNameField.clear();
        amountField.clear();
        nextDueDatePicker.setValue(LocalDate.now());
        frequencyBox.getSelectionModel().select("Monthly");
        statusBox.getSelectionModel().select("ACTIVE");
        accountBox.getSelectionModel().clearSelection();
        categoryBox.getSelectionModel().clearSelection();
        projectBox.getSelectionModel().clearSelection();
        notesArea.clear();
    }

    @FXML
    private void refresh() {
        accounts = database.listAccounts().stream()
                .filter(account -> !"INACTIVE".equalsIgnoreCase(CoreWorkspaceSupport.safe(account.getStatus())))
                .toList();
        categories = database.listCategories().stream()
                .filter(category -> isIncomeCategory(category.getCategoryType()))
                .toList();
        projects = database.listProjects();
        accountBox.setItems(FXCollections.observableArrayList(accounts));
        categoryBox.setItems(FXCollections.observableArrayList(categories));
        projectBox.setItems(FXCollections.observableArrayList(projects));
        List<RecurringTransactionPlan> rows = database.listRecurringTransactionPlans().stream()
                .filter(plan -> "INCOME".equalsIgnoreCase(CoreWorkspaceSupport.safe(plan.getTransactionType())))
                .toList();
        CoreWorkspaceSupport.setItems(plansTable, rows, plansStateLabel, "No recurring income plans yet.");
    }

    @FXML
    private void openExpectedIncome() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.EXPECTED_INCOME);
    }

    @FXML
    private void openAddIncome() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.ADD_INCOME);
    }

    private void configureTable() {
        CoreWorkspaceSupport.bind(nameColumn, RecurringTransactionPlan::getPlanName);
        CoreWorkspaceSupport.bind(nextDueColumn, RecurringTransactionPlan::getNextDueDate);
        CoreWorkspaceSupport.bind(accountColumn, plan -> CoreWorkspaceSupport.dash(plan.getAccountName()));
        CoreWorkspaceSupport.bind(sourceColumn, plan -> CoreWorkspaceSupport.dash(plan.getCategoryName()));
        CoreWorkspaceSupport.bind(amountColumn, plan -> CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), plan.getAmount()));
        CoreWorkspaceSupport.bind(frequencyColumn, RecurringTransactionPlan::getFrequency);
        CoreWorkspaceSupport.bind(statusColumn, RecurringTransactionPlan::getStatus);
        TableActions.configureScrollableTable(plansTable);
    }

    private void applySelectedPlan(RecurringTransactionPlan plan) {
        selectedPlan = plan;
        if (plan == null) {
            return;
        }
        planNameField.setText(plan.getPlanName());
        amountField.setText(String.format(Locale.ENGLISH, "%.2f", plan.getAmount()));
        nextDueDatePicker.setValue(parseDate(plan.getNextDueDate()));
        frequencyBox.getSelectionModel().select(CoreWorkspaceSupport.blank(plan.getFrequency(), "Monthly"));
        statusBox.getSelectionModel().select(CoreWorkspaceSupport.blank(plan.getStatus(), "ACTIVE"));
        accountBox.setValue(CoreWorkspaceSupport.accountByName(accounts, plan.getAccountName()));
        categoryBox.setValue(CoreWorkspaceSupport.categoryByName(categories, plan.getCategoryName()));
        projectBox.setValue(CoreWorkspaceSupport.projectByName(projects, plan.getProjectName()));
        notesArea.setText(CoreWorkspaceSupport.safe(plan.getNotes()));
        resultLabel.setText("Editing recurring income plan #" + plan.getId() + ".");
    }

    private boolean isIncomeCategory(String type) {
        String clean = CoreWorkspaceSupport.safe(type).toUpperCase(Locale.ENGLISH);
        return clean.isBlank() || clean.contains("INCOME") || clean.contains("BOTH");
    }

    private LocalDate parseDate(String value) {
        try {
            return CoreWorkspaceSupport.safe(value).isBlank() ? LocalDate.now() : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return LocalDate.now();
        }
    }
}
