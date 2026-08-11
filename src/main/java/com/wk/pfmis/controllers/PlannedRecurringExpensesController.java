package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.RecurringTransactionPlan;
import com.wk.pfmis.models.ScheduledObligation;
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

public class PlannedRecurringExpensesController {
    @FXML private TextField obligationNameField;
    @FXML private TextField obligationAmountField;
    @FXML private DatePicker obligationDueDatePicker;
    @FXML private ComboBox<String> obligationTypeBox;
    @FXML private ComboBox<String> obligationFrequencyBox;
    @FXML private ComboBox<Account> obligationAccountBox;
    @FXML private ComboBox<Category> obligationCategoryBox;
    @FXML private ComboBox<Project> obligationProjectBox;
    @FXML private ComboBox<String> obligationStatusBox;
    @FXML private TextArea obligationNotesArea;
    @FXML private Label obligationResultLabel;
    @FXML private Label obligationStateLabel;
    @FXML private TableView<ScheduledObligation> obligationsTable;
    @FXML private TableColumn<ScheduledObligation, String> obligationNameColumn;
    @FXML private TableColumn<ScheduledObligation, String> obligationDueColumn;
    @FXML private TableColumn<ScheduledObligation, String> obligationAccountColumn;
    @FXML private TableColumn<ScheduledObligation, String> obligationCategoryColumn;
    @FXML private TableColumn<ScheduledObligation, String> obligationAmountColumn;
    @FXML private TableColumn<ScheduledObligation, String> obligationFrequencyColumn;
    @FXML private TableColumn<ScheduledObligation, String> obligationStatusColumn;

    @FXML private TextField planNameField;
    @FXML private TextField planAmountField;
    @FXML private DatePicker planNextDueDatePicker;
    @FXML private ComboBox<String> planFrequencyBox;
    @FXML private ComboBox<Account> planAccountBox;
    @FXML private ComboBox<Category> planCategoryBox;
    @FXML private ComboBox<Project> planProjectBox;
    @FXML private ComboBox<String> planStatusBox;
    @FXML private TextArea planNotesArea;
    @FXML private Label planResultLabel;
    @FXML private Label planStateLabel;
    @FXML private TableView<RecurringTransactionPlan> recurringExpenseTable;
    @FXML private TableColumn<RecurringTransactionPlan, String> planNameColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> planDueColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> planAccountColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> planCategoryColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> planAmountColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> planFrequencyColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> planStatusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Account> accounts = List.of();
    private List<Category> categories = List.of();
    private List<Project> projects = List.of();
    private ScheduledObligation selectedObligation;
    private RecurringTransactionPlan selectedPlan;

    @FXML
    public void initialize() {
        CoreWorkspaceSupport.setComboItems(obligationTypeBox, "Bill", "Bill", "Rent", "School Fees", "Utilities", "Insurance", "Household", "Other");
        CoreWorkspaceSupport.setComboItems(obligationFrequencyBox, "One-time", "One-time", "Weekly", "Monthly", "Quarterly", "Yearly", "Custom");
        CoreWorkspaceSupport.setComboItems(obligationStatusBox, "ACTIVE", "ACTIVE", "PLANNED", "PAUSED", "CANCELLED", "COMPLETED");
        CoreWorkspaceSupport.setComboItems(planFrequencyBox, "Monthly", "Weekly", "Monthly", "Quarterly", "Yearly", "Custom");
        CoreWorkspaceSupport.setComboItems(planStatusBox, "ACTIVE", "ACTIVE", "PAUSED", "DRAFT", "CANCELLED");
        obligationDueDatePicker.setValue(LocalDate.now());
        planNextDueDatePicker.setValue(LocalDate.now());
        configureTables();
        obligationsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> applySelectedObligation(newValue));
        recurringExpenseTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> applySelectedPlan(newValue));
        refresh();
    }

    @FXML
    private void saveObligation() {
        try {
            String name = CoreWorkspaceSupport.required(obligationNameField, "Obligation name");
            double amount = CoreWorkspaceSupport.positiveAmount(obligationAmountField, "Obligation amount");
            LocalDate dueDate = CoreWorkspaceSupport.requiredDate(obligationDueDatePicker, "Due date");
            Account account = obligationAccountBox.getValue();
            if (account == null) {
                throw new IllegalArgumentException("Choose the account planned to pay this obligation.");
            }
            database.saveScheduledObligation(
                    selectedObligation == null ? null : selectedObligation.getId(),
                    name,
                    CoreWorkspaceSupport.selected(obligationTypeBox, "Other"),
                    amount,
                    dueDate.toString(),
                    CoreWorkspaceSupport.selected(obligationFrequencyBox, "One-time"),
                    CoreWorkspaceSupport.id(account),
                    CoreWorkspaceSupport.id(obligationCategoryBox.getValue()),
                    CoreWorkspaceSupport.id(obligationProjectBox.getValue()),
                    CoreWorkspaceSupport.selected(obligationStatusBox, "ACTIVE"),
                    obligationNotesArea.getText()
            );
            obligationResultLabel.setText("Planned expense saved.");
            DataRefreshBus.notifyDataChanged();
            clearObligationForm();
            refresh();
        } catch (IllegalArgumentException exception) {
            obligationResultLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save planned expense", exception);
        }
    }

    @FXML
    private void saveRecurringExpense() {
        try {
            String name = CoreWorkspaceSupport.required(planNameField, "Plan name");
            double amount = CoreWorkspaceSupport.positiveAmount(planAmountField, "Recurring amount");
            LocalDate dueDate = CoreWorkspaceSupport.requiredDate(planNextDueDatePicker, "Next due date");
            Account account = planAccountBox.getValue();
            if (account == null) {
                throw new IllegalArgumentException("Choose the account planned to pay this recurring expense.");
            }
            database.saveRecurringTransactionPlan(
                    selectedPlan == null ? null : selectedPlan.getId(),
                    name,
                    "EXPENSE",
                    amount,
                    CoreWorkspaceSupport.selected(planFrequencyBox, "Monthly"),
                    dueDate.toString(),
                    CoreWorkspaceSupport.id(account),
                    CoreWorkspaceSupport.id(planCategoryBox.getValue()),
                    CoreWorkspaceSupport.id(planProjectBox.getValue()),
                    CoreWorkspaceSupport.selected(planStatusBox, "ACTIVE"),
                    planNotesArea.getText()
            );
            planResultLabel.setText("Recurring expense plan saved.");
            DataRefreshBus.notifyDataChanged();
            clearRecurringForm();
            refresh();
        } catch (IllegalArgumentException exception) {
            planResultLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save recurring expense", exception);
        }
    }

    @FXML
    private void clearObligationForm() {
        selectedObligation = null;
        obligationNameField.clear();
        obligationAmountField.clear();
        obligationDueDatePicker.setValue(LocalDate.now());
        obligationTypeBox.getSelectionModel().select("Bill");
        obligationFrequencyBox.getSelectionModel().select("One-time");
        obligationStatusBox.getSelectionModel().select("ACTIVE");
        obligationAccountBox.getSelectionModel().clearSelection();
        obligationCategoryBox.getSelectionModel().clearSelection();
        obligationProjectBox.getSelectionModel().clearSelection();
        obligationNotesArea.clear();
    }

    @FXML
    private void clearRecurringForm() {
        selectedPlan = null;
        planNameField.clear();
        planAmountField.clear();
        planNextDueDatePicker.setValue(LocalDate.now());
        planFrequencyBox.getSelectionModel().select("Monthly");
        planStatusBox.getSelectionModel().select("ACTIVE");
        planAccountBox.getSelectionModel().clearSelection();
        planCategoryBox.getSelectionModel().clearSelection();
        planProjectBox.getSelectionModel().clearSelection();
        planNotesArea.clear();
    }

    @FXML
    private void refresh() {
        accounts = database.listAccounts().stream()
                .filter(account -> !"INACTIVE".equalsIgnoreCase(CoreWorkspaceSupport.safe(account.getStatus())))
                .toList();
        categories = database.listCategories().stream()
                .filter(category -> isExpenseCategory(category.getCategoryType()))
                .toList();
        projects = database.listProjects();
        obligationAccountBox.setItems(FXCollections.observableArrayList(accounts));
        planAccountBox.setItems(FXCollections.observableArrayList(accounts));
        obligationCategoryBox.setItems(FXCollections.observableArrayList(categories));
        planCategoryBox.setItems(FXCollections.observableArrayList(categories));
        obligationProjectBox.setItems(FXCollections.observableArrayList(projects));
        planProjectBox.setItems(FXCollections.observableArrayList(projects));
        CoreWorkspaceSupport.setItems(obligationsTable, database.listScheduledObligations(), obligationStateLabel, "No planned expense obligations.");
        List<RecurringTransactionPlan> rows = database.listRecurringTransactionPlans().stream()
                .filter(plan -> "EXPENSE".equalsIgnoreCase(CoreWorkspaceSupport.safe(plan.getTransactionType())))
                .toList();
        CoreWorkspaceSupport.setItems(recurringExpenseTable, rows, planStateLabel, "No recurring expense plans.");
    }

    @FXML
    private void openRecordExpense() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.RECORD_EXPENSE);
    }

    private void configureTables() {
        CoreWorkspaceSupport.bind(obligationNameColumn, ScheduledObligation::getObligationName);
        CoreWorkspaceSupport.bind(obligationDueColumn, ScheduledObligation::getDueDate);
        CoreWorkspaceSupport.bind(obligationAccountColumn, item -> CoreWorkspaceSupport.dash(item.getAccountName()));
        CoreWorkspaceSupport.bind(obligationCategoryColumn, item -> CoreWorkspaceSupport.dash(item.getCategoryName()));
        CoreWorkspaceSupport.bind(obligationAmountColumn, item -> CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), item.getAmount()));
        CoreWorkspaceSupport.bind(obligationFrequencyColumn, ScheduledObligation::getFrequency);
        CoreWorkspaceSupport.bind(obligationStatusColumn, ScheduledObligation::getStatus);
        CoreWorkspaceSupport.bind(planNameColumn, RecurringTransactionPlan::getPlanName);
        CoreWorkspaceSupport.bind(planDueColumn, RecurringTransactionPlan::getNextDueDate);
        CoreWorkspaceSupport.bind(planAccountColumn, item -> CoreWorkspaceSupport.dash(item.getAccountName()));
        CoreWorkspaceSupport.bind(planCategoryColumn, item -> CoreWorkspaceSupport.dash(item.getCategoryName()));
        CoreWorkspaceSupport.bind(planAmountColumn, item -> CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), item.getAmount()));
        CoreWorkspaceSupport.bind(planFrequencyColumn, RecurringTransactionPlan::getFrequency);
        CoreWorkspaceSupport.bind(planStatusColumn, RecurringTransactionPlan::getStatus);
        TableActions.configureScrollableTable(obligationsTable);
        TableActions.configureScrollableTable(recurringExpenseTable);
    }

    private void applySelectedObligation(ScheduledObligation obligation) {
        selectedObligation = obligation;
        if (obligation == null) {
            return;
        }
        obligationNameField.setText(obligation.getObligationName());
        obligationAmountField.setText(String.format(Locale.ENGLISH, "%.2f", obligation.getAmount()));
        obligationDueDatePicker.setValue(parseDate(obligation.getDueDate()));
        obligationTypeBox.getSelectionModel().select(CoreWorkspaceSupport.blank(obligation.getObligationType(), "Other"));
        obligationFrequencyBox.getSelectionModel().select(CoreWorkspaceSupport.blank(obligation.getFrequency(), "One-time"));
        obligationStatusBox.getSelectionModel().select(CoreWorkspaceSupport.blank(obligation.getStatus(), "ACTIVE"));
        obligationAccountBox.setValue(CoreWorkspaceSupport.accountByName(accounts, obligation.getAccountName()));
        obligationCategoryBox.setValue(CoreWorkspaceSupport.categoryByName(categories, obligation.getCategoryName()));
        obligationProjectBox.setValue(CoreWorkspaceSupport.projectByName(projects, obligation.getProjectName()));
        obligationNotesArea.setText(CoreWorkspaceSupport.safe(obligation.getNotes()));
        obligationResultLabel.setText("Editing planned expense #" + obligation.getId() + ".");
    }

    private void applySelectedPlan(RecurringTransactionPlan plan) {
        selectedPlan = plan;
        if (plan == null) {
            return;
        }
        planNameField.setText(plan.getPlanName());
        planAmountField.setText(String.format(Locale.ENGLISH, "%.2f", plan.getAmount()));
        planNextDueDatePicker.setValue(parseDate(plan.getNextDueDate()));
        planFrequencyBox.getSelectionModel().select(CoreWorkspaceSupport.blank(plan.getFrequency(), "Monthly"));
        planStatusBox.getSelectionModel().select(CoreWorkspaceSupport.blank(plan.getStatus(), "ACTIVE"));
        planAccountBox.setValue(CoreWorkspaceSupport.accountByName(accounts, plan.getAccountName()));
        planCategoryBox.setValue(CoreWorkspaceSupport.categoryByName(categories, plan.getCategoryName()));
        planProjectBox.setValue(CoreWorkspaceSupport.projectByName(projects, plan.getProjectName()));
        planNotesArea.setText(CoreWorkspaceSupport.safe(plan.getNotes()));
        planResultLabel.setText("Editing recurring expense plan #" + plan.getId() + ".");
    }

    private boolean isExpenseCategory(String type) {
        String clean = CoreWorkspaceSupport.safe(type).toUpperCase(Locale.ENGLISH);
        return clean.isBlank() || clean.contains("EXPENSE") || clean.contains("BOTH");
    }

    private LocalDate parseDate(String value) {
        try {
            return CoreWorkspaceSupport.safe(value).isBlank() ? LocalDate.now() : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return LocalDate.now();
        }
    }
}
