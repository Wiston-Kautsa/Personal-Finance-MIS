package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.AccountReconciliationRecord;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.LoanScheduleRecord;
import com.wk.pfmis.models.Person;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.RecurringTransactionPlan;
import com.wk.pfmis.models.ReportPositionItem;
import com.wk.pfmis.models.ScheduledObligation;
import com.wk.pfmis.models.SystemLogRecord;
import com.wk.pfmis.security.UserSession;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportInputsController {
    @FXML private Label inputGovernanceStatusLabel;
    @FXML private FlowPane reportWorkflowStepsPane;
    @FXML private FlowPane reportWorkbenchFieldsPane;
    @FXML private Button deleteReconciliationButton;
    @FXML private Button deleteObligationButton;
    @FXML private Button deleteRecurringButton;
    @FXML private Button deletePositionButton;
    @FXML private Button deleteLoanScheduleButton;

    @FXML private ComboBox<Account> reconciliationAccountBox;
    @FXML private DatePicker reconciliationDatePicker;
    @FXML private TextField actualBalanceField;
    @FXML private TextArea reconciliationNotesArea;
    @FXML private Label reconciliationStatusLabel;
    @FXML private TableView<AccountReconciliationRecord> reconciliationTable;
    @FXML private TableColumn<AccountReconciliationRecord, String> reconciliationAccountColumn;
    @FXML private TableColumn<AccountReconciliationRecord, String> reconciliationDateColumn;
    @FXML private TableColumn<AccountReconciliationRecord, Double> reconciliationSystemBalanceColumn;
    @FXML private TableColumn<AccountReconciliationRecord, Double> reconciliationActualBalanceColumn;
    @FXML private TableColumn<AccountReconciliationRecord, Double> reconciliationDifferenceColumn;
    @FXML private TableColumn<AccountReconciliationRecord, String> reconciliationStatusColumn;
    @FXML private TableColumn<AccountReconciliationRecord, String> reconciliationNotesColumn;

    @FXML private TextField obligationNameField;
    @FXML private ComboBox<String> obligationTypeBox;
    @FXML private TextField obligationAmountField;
    @FXML private DatePicker obligationDueDatePicker;
    @FXML private ComboBox<String> obligationFrequencyBox;
    @FXML private ComboBox<Account> obligationAccountBox;
    @FXML private ComboBox<Category> obligationCategoryBox;
    @FXML private ComboBox<Project> obligationProjectBox;
    @FXML private ComboBox<String> obligationStatusBox;
    @FXML private TextArea obligationNotesArea;
    @FXML private Label obligationStatusLabel;
    @FXML private TableView<ScheduledObligation> obligationsTable;
    @FXML private TableColumn<ScheduledObligation, String> obligationNameColumn;
    @FXML private TableColumn<ScheduledObligation, String> obligationTypeColumn;
    @FXML private TableColumn<ScheduledObligation, Double> obligationAmountColumn;
    @FXML private TableColumn<ScheduledObligation, String> obligationDueDateColumn;
    @FXML private TableColumn<ScheduledObligation, String> obligationFrequencyColumn;
    @FXML private TableColumn<ScheduledObligation, String> obligationStatusColumn;

    @FXML private TextField recurringNameField;
    @FXML private ComboBox<String> recurringTypeBox;
    @FXML private TextField recurringAmountField;
    @FXML private ComboBox<String> recurringFrequencyBox;
    @FXML private DatePicker recurringNextDueDatePicker;
    @FXML private ComboBox<Account> recurringAccountBox;
    @FXML private ComboBox<Category> recurringCategoryBox;
    @FXML private ComboBox<Project> recurringProjectBox;
    @FXML private ComboBox<String> recurringStatusBox;
    @FXML private TextArea recurringNotesArea;
    @FXML private Label recurringStatusLabel;
    @FXML private TableView<RecurringTransactionPlan> recurringTable;
    @FXML private TableColumn<RecurringTransactionPlan, String> recurringNameColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> recurringTypeColumn;
    @FXML private TableColumn<RecurringTransactionPlan, Double> recurringAmountColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> recurringFrequencyColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> recurringNextDueColumn;
    @FXML private TableColumn<RecurringTransactionPlan, String> recurringStatusColumn;

    @FXML private TextField positionNameField;
    @FXML private ComboBox<String> positionTypeBox;
    @FXML private ComboBox<String> positionItemTypeBox;
    @FXML private TextField positionValueField;
    @FXML private DatePicker positionValuationDatePicker;
    @FXML private ComboBox<String> positionStatusBox;
    @FXML private TextArea positionNotesArea;
    @FXML private Label positionStatusLabel;
    @FXML private TableView<ReportPositionItem> positionTable;
    @FXML private TableColumn<ReportPositionItem, String> positionNameColumn;
    @FXML private TableColumn<ReportPositionItem, String> positionTypeColumn;
    @FXML private TableColumn<ReportPositionItem, String> positionItemTypeColumn;
    @FXML private TableColumn<ReportPositionItem, Double> positionValueColumn;
    @FXML private TableColumn<ReportPositionItem, String> positionValuationDateColumn;
    @FXML private TableColumn<ReportPositionItem, String> positionStatusColumn;

    @FXML private ComboBox<Person> loanPersonBox;
    @FXML private ComboBox<String> loanDirectionBox;
    @FXML private TextField loanPrincipalField;
    @FXML private TextField loanOutstandingField;
    @FXML private TextField loanInterestRateField;
    @FXML private TextField loanPaymentAmountField;
    @FXML private DatePicker loanDueDatePicker;
    @FXML private ComboBox<String> loanFrequencyBox;
    @FXML private ComboBox<String> loanStatusBox;
    @FXML private TextArea loanNotesArea;
    @FXML private Label loanStatusLabel;
    @FXML private TableView<LoanScheduleRecord> loanScheduleTable;
    @FXML private TableColumn<LoanScheduleRecord, String> loanPersonColumn;
    @FXML private TableColumn<LoanScheduleRecord, String> loanDirectionColumn;
    @FXML private TableColumn<LoanScheduleRecord, Double> loanPrincipalColumn;
    @FXML private TableColumn<LoanScheduleRecord, Double> loanOutstandingColumn;
    @FXML private TableColumn<LoanScheduleRecord, Double> loanInterestColumn;
    @FXML private TableColumn<LoanScheduleRecord, Double> loanPaymentColumn;
    @FXML private TableColumn<LoanScheduleRecord, String> loanDueDateColumn;
    @FXML private TableColumn<LoanScheduleRecord, String> loanStatusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private int selectedReconciliationId;
    private int selectedObligationId;
    private int selectedRecurringId;
    private int selectedPositionId;
    private int selectedLoanId;

    @FXML
    public void initialize() {
        configureTables();
        configureChoices();
        configureSelections();
        configureFormDefaults();
        configureDeletionControls();
        renderWorkflowDesignCards();
        configureContextMenus();
        refresh();
    }

    private void configureTables() {
        reconciliationAccountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        reconciliationDateColumn.setCellValueFactory(new PropertyValueFactory<>("reconciliationDate"));
        reconciliationSystemBalanceColumn.setCellValueFactory(new PropertyValueFactory<>("systemBalance"));
        reconciliationActualBalanceColumn.setCellValueFactory(new PropertyValueFactory<>("actualBalance"));
        reconciliationDifferenceColumn.setCellValueFactory(new PropertyValueFactory<>("difference"));
        reconciliationStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        reconciliationNotesColumn.setCellValueFactory(new PropertyValueFactory<>("notes"));

        obligationNameColumn.setCellValueFactory(new PropertyValueFactory<>("obligationName"));
        obligationTypeColumn.setCellValueFactory(new PropertyValueFactory<>("obligationType"));
        obligationAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        obligationDueDateColumn.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        obligationFrequencyColumn.setCellValueFactory(new PropertyValueFactory<>("frequency"));
        obligationStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        recurringNameColumn.setCellValueFactory(new PropertyValueFactory<>("planName"));
        recurringTypeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionType"));
        recurringAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        recurringFrequencyColumn.setCellValueFactory(new PropertyValueFactory<>("frequency"));
        recurringNextDueColumn.setCellValueFactory(new PropertyValueFactory<>("nextDueDate"));
        recurringStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        positionNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        positionTypeColumn.setCellValueFactory(new PropertyValueFactory<>("positionType"));
        positionItemTypeColumn.setCellValueFactory(new PropertyValueFactory<>("itemType"));
        positionValueColumn.setCellValueFactory(new PropertyValueFactory<>("currentValue"));
        positionValuationDateColumn.setCellValueFactory(new PropertyValueFactory<>("valuationDate"));
        positionStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        loanPersonColumn.setCellValueFactory(new PropertyValueFactory<>("personName"));
        loanDirectionColumn.setCellValueFactory(new PropertyValueFactory<>("loanDirection"));
        loanPrincipalColumn.setCellValueFactory(new PropertyValueFactory<>("principalAmount"));
        loanOutstandingColumn.setCellValueFactory(new PropertyValueFactory<>("outstandingAmount"));
        loanInterestColumn.setCellValueFactory(new PropertyValueFactory<>("interestRate"));
        loanPaymentColumn.setCellValueFactory(new PropertyValueFactory<>("paymentAmount"));
        loanDueDateColumn.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        loanStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    private void configureChoices() {
        obligationTypeBox.setItems(FXCollections.observableArrayList("Rent", "School Fees", "Utilities", "Subscription", "Project Commitment", "Loan Repayment", "Transfer", "Other"));
        obligationFrequencyBox.setItems(FXCollections.observableArrayList("One-time", "Weekly", "Monthly", "Quarterly", "Annual"));
        obligationStatusBox.setItems(statusChoices());
        recurringTypeBox.setItems(FXCollections.observableArrayList("Income", "Expense"));
        recurringFrequencyBox.setItems(FXCollections.observableArrayList("Weekly", "Monthly", "Quarterly", "Annual"));
        recurringStatusBox.setItems(statusChoices());
        positionTypeBox.setItems(FXCollections.observableArrayList("Asset", "Liability"));
        positionItemTypeBox.setItems(FXCollections.observableArrayList("Cash", "Savings", "Investment", "Receivable", "Credit", "Borrowed Money", "Unpaid Obligation", "Other"));
        positionStatusBox.setItems(statusChoices());
        loanDirectionBox.setItems(FXCollections.observableArrayList("Money Borrowed", "Money Lent"));
        loanFrequencyBox.setItems(FXCollections.observableArrayList("One-time", "Weekly", "Monthly", "Quarterly", "Annual"));
        loanStatusBox.setItems(statusChoices());
        clearAllForms();
    }

    private javafx.collections.ObservableList<String> statusChoices() {
        return FXCollections.observableArrayList("Active", "Completed", "Cancelled", "Inactive");
    }

    private void configureSelections() {
        reconciliationAccountBox.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null && selectedReconciliationId == 0 && text(actualBalanceField).isBlank()) {
                actualBalanceField.setText(String.valueOf(selected.getCurrentBalance()));
            }
        });
        obligationTypeBox.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selectedObligationId == 0) {
                replaceGeneratedName(obligationNameField, defaultObligationName(oldValue), defaultObligationName(selected));
            }
        });
        recurringTypeBox.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selectedRecurringId == 0) {
                replaceGeneratedName(recurringNameField, defaultRecurringName(oldValue), defaultRecurringName(selected));
            }
        });
        positionTypeBox.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selectedPositionId == 0) {
                replaceGeneratedName(positionNameField,
                        defaultPositionName(oldValue, positionItemTypeBox.getValue()),
                        defaultPositionName(selected, positionItemTypeBox.getValue()));
            }
        });
        positionItemTypeBox.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selectedPositionId == 0) {
                replaceGeneratedName(positionNameField,
                        defaultPositionName(positionTypeBox.getValue(), oldValue),
                        defaultPositionName(positionTypeBox.getValue(), selected));
            }
        });
        reconciliationTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                populateReconciliation(selected);
            }
        });
        obligationsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                populateObligation(selected);
            }
        });
        recurringTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                populateRecurring(selected);
            }
        });
        positionTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                populatePosition(selected);
            }
        });
        loanScheduleTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                populateLoan(selected);
            }
        });
    }

    private void configureFormDefaults() {
        actualBalanceField.setText("0.00");
        obligationAmountField.setText("0.00");
        recurringAmountField.setText("0.00");
        positionValueField.setText("0.00");
        loanPrincipalField.setText("0.00");
        loanOutstandingField.setText("0.00");
        loanInterestRateField.setText("0");
        loanPaymentAmountField.setText("0.00");
    }

    @FXML
    private void saveReconciliation() {
        try {
            Account account = reconciliationAccountBox.getValue();
            if (account == null) {
                UiAlerts.info("Choose the account being reconciled.");
                return;
            }
            database.saveAccountReconciliation(
                    selectedId(selectedReconciliationId),
                    account.getId(),
                    dateValue(reconciliationDatePicker),
                    parseAmount(actualBalanceField, "Actual balance"),
                    text(reconciliationNotesArea)
            );
            refresh();
            clearReconciliationForm();
            reconciliationStatusLabel.setText("Account reconciliation saved.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save reconciliation", exception);
        }
    }

    @FXML
    private void clearReconciliationForm() {
        selectedReconciliationId = 0;
        reconciliationTable.getSelectionModel().clearSelection();
        selectFirstIfEmpty(reconciliationAccountBox);
        reconciliationDatePicker.setValue(LocalDate.now());
        Account account = reconciliationAccountBox.getValue();
        actualBalanceField.setText(account == null ? "0.00" : String.valueOf(account.getCurrentBalance()));
        reconciliationNotesArea.clear();
        reconciliationStatusLabel.setText("Ready.");
    }

    @FXML
    private void deleteReconciliation() {
        AccountReconciliationRecord selected = reconciliationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a reconciliation record to delete.");
            return;
        }
        if (governedPhysicalRemoval("account reconciliation", selected.getId(), reconciliationStatusLabel,
                () -> database.deleteAccountReconciliation(selected.getId()))) {
            refresh();
            clearReconciliationForm();
        }
    }

    @FXML
    private void saveObligation() {
        try {
            database.saveScheduledObligation(
                    selectedId(selectedObligationId),
                    defaultedText(obligationNameField, defaultObligationName()),
                    value(obligationTypeBox, "Other"),
                    parseAmount(obligationAmountField, "Obligation amount"),
                    dateValue(obligationDueDatePicker),
                    value(obligationFrequencyBox, "One-time"),
                    selectedAccountId(obligationAccountBox),
                    selectedCategoryId(obligationCategoryBox),
                    selectedProjectId(obligationProjectBox),
                    statusValue(obligationStatusBox),
                    text(obligationNotesArea)
            );
            refresh();
            clearObligationForm();
            obligationStatusLabel.setText("Scheduled obligation saved.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save obligation", exception);
        }
    }

    @FXML
    private void clearObligationForm() {
        selectedObligationId = 0;
        obligationsTable.getSelectionModel().clearSelection();
        obligationAmountField.setText("0.00");
        obligationDueDatePicker.setValue(LocalDate.now());
        obligationAccountBox.getSelectionModel().clearSelection();
        obligationCategoryBox.getSelectionModel().clearSelection();
        obligationProjectBox.getSelectionModel().clearSelection();
        obligationNotesArea.clear();
        obligationTypeBox.getSelectionModel().select("Other");
        obligationFrequencyBox.getSelectionModel().select("Monthly");
        obligationStatusBox.getSelectionModel().select("Active");
        selectFirstIfEmpty(obligationAccountBox);
        selectFirstIfEmpty(obligationCategoryBox);
        selectFirstIfEmpty(obligationProjectBox);
        obligationNameField.setText(defaultObligationName());
        obligationStatusLabel.setText("Ready.");
    }

    @FXML
    private void deleteObligation() {
        ScheduledObligation selected = obligationsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select an obligation to delete.");
            return;
        }
        if (governedPhysicalRemoval("scheduled obligation", selected.getId(), obligationStatusLabel,
                () -> database.deleteScheduledObligation(selected.getId()))) {
            refresh();
            clearObligationForm();
        }
    }

    @FXML
    private void saveRecurring() {
        try {
            database.saveRecurringTransactionPlan(
                    selectedId(selectedRecurringId),
                    defaultedText(recurringNameField, defaultRecurringName()),
                    "Income".equals(recurringTypeBox.getValue()) ? "INCOME" : "EXPENSE",
                    parseAmount(recurringAmountField, "Recurring amount"),
                    value(recurringFrequencyBox, "Monthly"),
                    dateValue(recurringNextDueDatePicker),
                    selectedAccountId(recurringAccountBox),
                    selectedCategoryId(recurringCategoryBox),
                    selectedProjectId(recurringProjectBox),
                    statusValue(recurringStatusBox),
                    text(recurringNotesArea)
            );
            refresh();
            clearRecurringForm();
            recurringStatusLabel.setText("Recurring plan saved.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save recurring plan", exception);
        }
    }

    @FXML
    private void clearRecurringForm() {
        selectedRecurringId = 0;
        recurringTable.getSelectionModel().clearSelection();
        recurringAmountField.setText("0.00");
        recurringNextDueDatePicker.setValue(LocalDate.now());
        recurringAccountBox.getSelectionModel().clearSelection();
        recurringCategoryBox.getSelectionModel().clearSelection();
        recurringProjectBox.getSelectionModel().clearSelection();
        recurringNotesArea.clear();
        recurringTypeBox.getSelectionModel().select("Expense");
        recurringFrequencyBox.getSelectionModel().select("Monthly");
        recurringStatusBox.getSelectionModel().select("Active");
        selectFirstIfEmpty(recurringAccountBox);
        selectFirstIfEmpty(recurringCategoryBox);
        selectFirstIfEmpty(recurringProjectBox);
        recurringNameField.setText(defaultRecurringName());
        recurringStatusLabel.setText("Ready.");
    }

    @FXML
    private void deleteRecurring() {
        RecurringTransactionPlan selected = recurringTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a recurring plan to delete.");
            return;
        }
        if (governedPhysicalRemoval("recurring transaction plan", selected.getId(), recurringStatusLabel,
                () -> database.deleteRecurringTransactionPlan(selected.getId()))) {
            refresh();
            clearRecurringForm();
        }
    }

    @FXML
    private void savePosition() {
        try {
            database.saveReportPositionItem(
                    selectedId(selectedPositionId),
                    defaultedText(positionNameField, defaultPositionName()),
                    "Liability".equals(positionTypeBox.getValue()) ? "LIABILITY" : "ASSET",
                    value(positionItemTypeBox, "Other"),
                    parseAmount(positionValueField, "Current value"),
                    dateValue(positionValuationDatePicker),
                    statusValue(positionStatusBox),
                    text(positionNotesArea)
            );
            refresh();
            clearPositionForm();
            positionStatusLabel.setText("Financial position item saved.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save financial position item", exception);
        }
    }

    @FXML
    private void clearPositionForm() {
        selectedPositionId = 0;
        positionTable.getSelectionModel().clearSelection();
        positionValueField.setText("0.00");
        positionValuationDatePicker.setValue(LocalDate.now());
        positionNotesArea.clear();
        positionTypeBox.getSelectionModel().select("Asset");
        positionItemTypeBox.getSelectionModel().select("Other");
        positionStatusBox.getSelectionModel().select("Active");
        positionNameField.setText(defaultPositionName());
        positionStatusLabel.setText("Ready.");
    }

    @FXML
    private void deletePosition() {
        ReportPositionItem selected = positionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a position item to delete.");
            return;
        }
        if (governedPhysicalRemoval("financial position item", selected.getId(), positionStatusLabel,
                () -> database.deleteReportPositionItem(selected.getId()))) {
            refresh();
            clearPositionForm();
        }
    }

    @FXML
    private void saveLoanSchedule() {
        try {
            database.saveLoanSchedule(
                    selectedId(selectedLoanId),
                    selectedPersonId(loanPersonBox),
                    "Money Lent".equals(loanDirectionBox.getValue()) ? "LENT" : "BORROWED",
                    parseAmount(loanPrincipalField, "Principal amount"),
                    parseAmount(loanOutstandingField, "Outstanding amount"),
                    parseOptionalAmount(loanInterestRateField),
                    parseOptionalAmount(loanPaymentAmountField),
                    dateValue(loanDueDatePicker),
                    value(loanFrequencyBox, "Monthly"),
                    statusValue(loanStatusBox),
                    text(loanNotesArea)
            );
            refresh();
            clearLoanForm();
            loanStatusLabel.setText("Loan schedule saved.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save loan schedule", exception);
        }
    }

    @FXML
    private void clearLoanForm() {
        selectedLoanId = 0;
        loanScheduleTable.getSelectionModel().clearSelection();
        selectFirstIfEmpty(loanPersonBox);
        loanPrincipalField.setText("0.00");
        loanOutstandingField.setText("0.00");
        loanInterestRateField.setText("0");
        loanPaymentAmountField.setText("0.00");
        loanDueDatePicker.setValue(LocalDate.now());
        loanNotesArea.clear();
        loanDirectionBox.getSelectionModel().select("Money Borrowed");
        loanFrequencyBox.getSelectionModel().select("Monthly");
        loanStatusBox.getSelectionModel().select("Active");
        loanStatusLabel.setText("Ready.");
    }

    @FXML
    private void deleteLoanSchedule() {
        LoanScheduleRecord selected = loanScheduleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a loan schedule to delete.");
            return;
        }
        if (governedPhysicalRemoval("loan schedule", selected.getId(), loanStatusLabel,
                () -> database.deleteLoanSchedule(selected.getId()))) {
            refresh();
            clearLoanForm();
        }
    }

    @FXML
    private void refresh() {
        refreshReferenceLists();
        reconciliationTable.setItems(FXCollections.observableArrayList(database.listAccountReconciliations()));
        obligationsTable.setItems(FXCollections.observableArrayList(database.listScheduledObligations()));
        recurringTable.setItems(FXCollections.observableArrayList(database.listRecurringTransactionPlans()));
        positionTable.setItems(FXCollections.observableArrayList(database.listReportPositionItems()));
        loanScheduleTable.setItems(FXCollections.observableArrayList(database.listLoanSchedules()));
    }

    private void refreshReferenceLists() {
        Account reconciliationAccount = reconciliationAccountBox.getValue();
        Account obligationAccount = obligationAccountBox.getValue();
        Account recurringAccount = recurringAccountBox.getValue();
        Category obligationCategory = obligationCategoryBox.getValue();
        Category recurringCategory = recurringCategoryBox.getValue();
        Project obligationProject = obligationProjectBox.getValue();
        Project recurringProject = recurringProjectBox.getValue();
        Person loanPerson = loanPersonBox.getValue();
        List<Account> accounts = database.listAccounts();
        List<Category> categories = database.listCategories();
        List<Project> projects = database.listProjects();
        List<Person> people = database.listPeople();
        reconciliationAccountBox.setItems(FXCollections.observableArrayList(accounts));
        obligationAccountBox.setItems(FXCollections.observableArrayList(accounts));
        recurringAccountBox.setItems(FXCollections.observableArrayList(accounts));
        obligationCategoryBox.setItems(FXCollections.observableArrayList(categories));
        recurringCategoryBox.setItems(FXCollections.observableArrayList(categories));
        obligationProjectBox.setItems(FXCollections.observableArrayList(projects));
        recurringProjectBox.setItems(FXCollections.observableArrayList(projects));
        loanPersonBox.setItems(FXCollections.observableArrayList(people));
        restoreAccountSelection(reconciliationAccountBox, reconciliationAccount);
        restoreAccountSelection(obligationAccountBox, obligationAccount);
        restoreAccountSelection(recurringAccountBox, recurringAccount);
        restoreCategorySelection(obligationCategoryBox, obligationCategory);
        restoreCategorySelection(recurringCategoryBox, recurringCategory);
        restoreProjectSelection(obligationProjectBox, obligationProject);
        restoreProjectSelection(recurringProjectBox, recurringProject);
        restorePersonSelection(loanPersonBox, loanPerson);
        selectFirstIfEmpty(reconciliationAccountBox);
        if (selectedObligationId == 0) {
            selectFirstIfEmpty(obligationAccountBox);
            selectFirstIfEmpty(obligationCategoryBox);
            selectFirstIfEmpty(obligationProjectBox);
        }
        if (selectedRecurringId == 0) {
            selectFirstIfEmpty(recurringAccountBox);
            selectFirstIfEmpty(recurringCategoryBox);
            selectFirstIfEmpty(recurringProjectBox);
        }
        selectFirstIfEmpty(loanPersonBox);
        Account selectedReconciliationAccount = reconciliationAccountBox.getValue();
        if (selectedReconciliationId == 0
                && selectedReconciliationAccount != null
                && (text(actualBalanceField).isBlank() || "0.00".equals(text(actualBalanceField)))) {
            actualBalanceField.setText(String.valueOf(selectedReconciliationAccount.getCurrentBalance()));
        }
        if (reconciliationAccountBox.getValue() == null) {
            reconciliationStatusLabel.setText("Register an account before saving a reconciliation.");
        }
        if (selectedObligationId == 0 && text(obligationNameField).isBlank()) {
            obligationNameField.setText(defaultObligationName());
        }
        if (selectedRecurringId == 0 && text(recurringNameField).isBlank()) {
            recurringNameField.setText(defaultRecurringName());
        }
        if (selectedPositionId == 0 && text(positionNameField).isBlank()) {
            positionNameField.setText(defaultPositionName());
        }
        if (loanPersonBox.getValue() == null) {
            loanStatusLabel.setText("Add a person first if this loan schedule should be linked to someone.");
        }
    }

    private void populateReconciliation(AccountReconciliationRecord selected) {
        selectedReconciliationId = selected.getId();
        selectById(reconciliationAccountBox, selected.getAccountId());
        reconciliationDatePicker.setValue(parseDate(selected.getReconciliationDate()));
        actualBalanceField.setText(String.valueOf(selected.getActualBalance()));
        reconciliationNotesArea.setText(selected.getNotes());
        reconciliationStatusLabel.setText("Editing " + selected.getAccountName() + " reconciliation.");
    }

    private void populateObligation(ScheduledObligation selected) {
        selectedObligationId = selected.getId();
        obligationNameField.setText(selected.getObligationName());
        obligationTypeBox.setValue(selected.getObligationType());
        obligationAmountField.setText(String.valueOf(selected.getAmount()));
        obligationDueDatePicker.setValue(parseDate(selected.getDueDate()));
        obligationFrequencyBox.setValue(selected.getFrequency());
        selectByName(obligationAccountBox, selected.getAccountName());
        selectByName(obligationCategoryBox, selected.getCategoryName());
        selectByName(obligationProjectBox, selected.getProjectName());
        obligationStatusBox.setValue(displayStatus(selected.getStatus()));
        obligationNotesArea.setText(selected.getNotes());
        obligationStatusLabel.setText("Editing " + selected.getObligationName() + ".");
    }

    private void populateRecurring(RecurringTransactionPlan selected) {
        selectedRecurringId = selected.getId();
        recurringNameField.setText(selected.getPlanName());
        recurringTypeBox.setValue("INCOME".equals(selected.getTransactionType()) ? "Income" : "Expense");
        recurringAmountField.setText(String.valueOf(selected.getAmount()));
        recurringFrequencyBox.setValue(selected.getFrequency());
        recurringNextDueDatePicker.setValue(parseDate(selected.getNextDueDate()));
        selectByName(recurringAccountBox, selected.getAccountName());
        selectByName(recurringCategoryBox, selected.getCategoryName());
        selectByName(recurringProjectBox, selected.getProjectName());
        recurringStatusBox.setValue(displayStatus(selected.getStatus()));
        recurringNotesArea.setText(selected.getNotes());
        recurringStatusLabel.setText("Editing " + selected.getPlanName() + ".");
    }

    private void populatePosition(ReportPositionItem selected) {
        selectedPositionId = selected.getId();
        positionNameField.setText(selected.getItemName());
        positionTypeBox.setValue("LIABILITY".equals(selected.getPositionType()) ? "Liability" : "Asset");
        positionItemTypeBox.setValue(selected.getItemType());
        positionValueField.setText(String.valueOf(selected.getCurrentValue()));
        positionValuationDatePicker.setValue(parseDate(selected.getValuationDate()));
        positionStatusBox.setValue(displayStatus(selected.getStatus()));
        positionNotesArea.setText(selected.getNotes());
        positionStatusLabel.setText("Editing " + selected.getItemName() + ".");
    }

    private void populateLoan(LoanScheduleRecord selected) {
        selectedLoanId = selected.getId();
        selectByName(loanPersonBox, selected.getPersonName());
        loanDirectionBox.setValue("LENT".equals(selected.getLoanDirection()) ? "Money Lent" : "Money Borrowed");
        loanPrincipalField.setText(String.valueOf(selected.getPrincipalAmount()));
        loanOutstandingField.setText(String.valueOf(selected.getOutstandingAmount()));
        loanInterestRateField.setText(String.valueOf(selected.getInterestRate()));
        loanPaymentAmountField.setText(String.valueOf(selected.getPaymentAmount()));
        loanDueDatePicker.setValue(parseDate(selected.getDueDate()));
        loanFrequencyBox.setValue(selected.getFrequency());
        loanStatusBox.setValue(displayStatus(selected.getStatus()));
        loanNotesArea.setText(selected.getNotes());
        loanStatusLabel.setText("Editing loan schedule.");
    }

    @FXML
    private void clearAllForms() {
        clearReconciliationForm();
        clearObligationForm();
        clearRecurringForm();
        clearPositionForm();
        clearLoanForm();
    }

    @FXML
    private void validateSelectedInput() {
        if (!requireAdminAuthority("run validation checks")) {
            return;
        }
        recordWorkflowAction("Run Input Check");
    }

    @FXML
    private void submitSelectedInput() {
        recordWorkflowAction("Submit for Approval");
    }

    @FXML
    private void approveSelectedInput() {
        if (!UserSession.isSuperAdmin()) {
            UiAlerts.info("Approval of important supplementary inputs requires an authorised approval role.");
            return;
        }
        recordWorkflowAction("Approval Review");
    }

    @FXML
    private void rejectSelectedInput() {
        if (!requireAdminAuthority("review rejection")) {
            return;
        }
        recordWorkflowAction("Rejection Review");
    }

    @FXML
    private void freezeSelectedInput() {
        if (!requireAdminAuthority("request freeze")) {
            return;
        }
        recordWorkflowAction("Freeze Request");
    }

    @FXML
    private void createRevisedVersion() {
        recordWorkflowAction("Create Revised Version");
    }

    @FXML
    private void viewSource() {
        String selected = selectedInputLabel();
        if (selected == null) {
            UiAlerts.info("Select a supplementary input record first.");
            return;
        }
        UiAlerts.info("Source evidence for " + selected + " should include source, source date, attachment reference, entered by, validation status, approval status, version and remarks.");
        database.recordSystemLog("Data And Records", "View supplementary input source", "INFO", selected);
    }

    @FXML
    private void viewHistory() {
        List<SystemLogRecord> records = database.listSystemLogHistory(20);
        StringBuilder builder = new StringBuilder("Recent supplementary input and data-governance history\n\n");
        for (SystemLogRecord record : records) {
            builder.append(record.getCreatedAt())
                    .append(" | ")
                    .append(record.getModuleName())
                    .append(" | ")
                    .append(record.getActionName())
                    .append(" | ")
                    .append(record.getSeverity())
                    .append('\n');
        }
        UiAlerts.info(builder.toString());
    }

    private Integer selectedId(int id) {
        return id > 0 ? id : null;
    }

    private Integer selectedAccountId(ComboBox<Account> box) {
        Account value = box.getValue();
        return value == null ? null : value.getId();
    }

    private Integer selectedCategoryId(ComboBox<Category> box) {
        Category value = box.getValue();
        return value == null ? null : value.getId();
    }

    private Integer selectedProjectId(ComboBox<Project> box) {
        Project value = box.getValue();
        return value == null ? null : value.getId();
    }

    private Integer selectedPersonId(ComboBox<Person> box) {
        Person value = box.getValue();
        return value == null ? null : value.getId();
    }

    private String value(ComboBox<String> box, String fallback) {
        String value = box.getValue();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String defaultedText(TextField field, String fallback) {
        String value = text(field);
        return value.isBlank() ? fallback : value;
    }

    private String defaultObligationName() {
        return defaultObligationName(obligationTypeBox.getValue());
    }

    private String defaultObligationName(String type) {
        String selectedType = type == null || type.isBlank() ? "Other" : type.trim();
        return selectedType + " obligation";
    }

    private String defaultRecurringName() {
        return defaultRecurringName(recurringTypeBox.getValue());
    }

    private String defaultRecurringName(String type) {
        String selectedType = type == null || type.isBlank() ? "Expense" : type.trim();
        return selectedType + " recurring plan";
    }

    private String defaultPositionName() {
        return defaultPositionName(positionTypeBox.getValue(), positionItemTypeBox.getValue());
    }

    private String defaultPositionName(String positionType, String itemType) {
        String selectedPosition = positionType == null || positionType.isBlank() ? "Asset" : positionType.trim();
        String selectedItem = itemType == null || itemType.isBlank() ? "Other" : itemType.trim();
        return selectedPosition + " - " + selectedItem;
    }

    private void replaceGeneratedName(TextField field, String previousDefault, String nextDefault) {
        String current = text(field);
        if (current.isBlank() || current.equals(previousDefault)) {
            field.setText(nextDefault);
        }
    }

    private String statusValue(ComboBox<String> box) {
        return switch (value(box, "Active")) {
            case "Completed" -> "COMPLETED";
            case "Cancelled" -> "CANCELLED";
            case "Inactive" -> "INACTIVE";
            default -> "ACTIVE";
        };
    }

    private String displayStatus(String status) {
        return switch (status == null ? "" : status) {
            case "COMPLETED" -> "Completed";
            case "CANCELLED" -> "Cancelled";
            case "INACTIVE" -> "Inactive";
            default -> "Active";
        };
    }

    private String dateValue(DatePicker picker) {
        LocalDate value = picker.getValue();
        return value == null ? LocalDate.now().toString() : value.toString();
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? LocalDate.now() : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return LocalDate.now();
        }
    }

    private double parseAmount(TextField field, String label) {
        if (text(field).isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(text(field));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a number.", exception);
        }
    }

    private double parseOptionalAmount(TextField field) {
        String value = text(field);
        if (value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Optional amount fields must be numbers.", exception);
        }
    }

    private String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String text(TextArea area) {
        return area.getText() == null ? "" : area.getText().trim();
    }

    private void selectById(ComboBox<Account> box, int id) {
        box.getItems().stream()
                .filter(account -> account.getId() == id)
                .findFirst()
                .ifPresentOrElse(box::setValue, () -> box.getSelectionModel().clearSelection());
    }

    private void restoreAccountSelection(ComboBox<Account> box, Account selected) {
        if (selected != null) {
            selectById(box, selected.getId());
        }
    }

    private void restoreCategorySelection(ComboBox<Category> box, Category selected) {
        if (selected != null) {
            box.getItems().stream()
                    .filter(category -> category.getId() == selected.getId())
                    .findFirst()
                    .ifPresentOrElse(box::setValue, () -> box.getSelectionModel().clearSelection());
        }
    }

    private void restoreProjectSelection(ComboBox<Project> box, Project selected) {
        if (selected != null) {
            box.getItems().stream()
                    .filter(project -> project.getId() == selected.getId())
                    .findFirst()
                    .ifPresentOrElse(box::setValue, () -> box.getSelectionModel().clearSelection());
        }
    }

    private void restorePersonSelection(ComboBox<Person> box, Person selected) {
        if (selected != null) {
            box.getItems().stream()
                    .filter(person -> person.getId() == selected.getId())
                    .findFirst()
                    .ifPresentOrElse(box::setValue, () -> box.getSelectionModel().clearSelection());
        }
    }

    private <T> void selectFirstIfEmpty(ComboBox<T> box) {
        if (box.getValue() == null && !box.getItems().isEmpty()) {
            box.getSelectionModel().selectFirst();
        }
    }

    private <T> void selectByName(ComboBox<T> box, String name) {
        if (name == null || name.isBlank()) {
            box.getSelectionModel().clearSelection();
            return;
        }
        box.getItems().stream()
                .filter(item -> name.equals(item.toString()))
                .findFirst()
                .ifPresentOrElse(box::setValue, () -> box.getSelectionModel().clearSelection());
    }

    private void configureDeletionControls() {
        boolean superAdmin = UserSession.isSuperAdmin();
        for (Button button : deletionButtons()) {
            if (button == null) {
                continue;
            }
            button.setText(superAdmin ? "Delete Selected" : "Request Removal");
            if (!superAdmin) {
                button.getStyleClass().remove("danger-button");
                if (!button.getStyleClass().contains("secondary-button")) {
                    button.getStyleClass().add("secondary-button");
                }
            }
        }
        if (inputGovernanceStatusLabel != null) {
            inputGovernanceStatusLabel.setText(superAdmin
                    ? "Active workflow: lifecycle controls are enabled. Physical removal requires confirmation and a safety backup."
                    : "Active workflow: request controls are enabled. Physical deletion is restricted to Super Administrator review.");
        }
    }

    private void renderWorkflowDesignCards() {
        renderActiveCards(reportWorkflowStepsPane, List.of(
                card("Draft", "Capture supporting data without affecting reports, balances or Smart Analysis conclusions."),
                card("Validate", "Check period, amount, source, currency, attachment and whether the value can be derived from transactions."),
                card("Review", "Confirm evidence, business reason and expected report use before approval."),
                card("Approve", "Authorised approval role accepts the value and records the approval reference."),
                card("Post", "Make the approved supplementary input available to report generation without overwriting transaction totals."),
                card("Freeze", "Lock the posted value. Frozen values remain valid evidence and remain available to reports."),
                card("Use in Report", "Issued reports reference the frozen version; corrections create a revised version rather than editing history.")
        ), true);

        renderActiveCards(reportWorkbenchFieldsPane, List.of(
                card("Input Type", "Management note, external indicator, target, forecast assumption, opening figure or attachment."),
                card("Reporting Period", "Month, quarter, year or custom report period."),
                card("Description", "Clear business meaning of the supplementary value."),
                card("Value / Unit / Currency", "Amount, count, percentage, text value and approved currency where applicable."),
                card("Source / Source Date", "External source, internal approval, migration record or supporting reference date."),
                card("Entered By / Date Entered", "Actor and timestamp for accountability."),
                card("Evidence or Attachment", "Document, statement, approval note or source link."),
                card("Validation / Approval Status", "Draft, validated, submitted, approved, rejected, posted or frozen."),
                card("Version / Remarks", "Original and revised versions with explanation.")
        ), false);
    }

    private void renderActiveCards(FlowPane pane, List<DesignCard> cards, boolean workflowStep) {
        if (pane == null) {
            return;
        }
        pane.getChildren().clear();
        for (int index = 0; index < cards.size(); index++) {
            DesignCard designCard = cards.get(index);
            Button button = new Button();
            button.getStyleClass().add(workflowStep ? "workflow-step-card" : "workbench-field-card");
            button.getStyleClass().add(workflowStep ? "workflow-step-button" : "workbench-field-button");
            button.setPrefWidth(workflowStep ? 155 : 205);
            button.setMinWidth(workflowStep ? 155 : 205);
            button.setMaxWidth(workflowStep ? 155 : 205);
            button.setTooltip(new Tooltip("Open " + (workflowStep ? "workflow step: " : "required field: ") + designCard.name()));
            button.setAccessibleText((workflowStep ? "Workflow step " + (index + 1) + ": " : "Required field: ") + designCard.name());

            VBox content = new VBox(workflowStep ? 5 : 4);
            content.setPrefWidth(workflowStep ? 132 : 182);
            if (workflowStep) {
                Label number = new Label(String.format("%02d", index + 1));
                number.getStyleClass().add("workflow-step-number");
                content.getChildren().add(number);
            }
            Label title = new Label(designCard.name());
            title.setWrapText(true);
            title.getStyleClass().add(workflowStep ? "workflow-step-label" : "field-label");
            Label detail = new Label(designCard.detail());
            detail.setWrapText(true);
            detail.getStyleClass().add(workflowStep ? "workflow-step-detail" : "muted-label");
            content.getChildren().addAll(title, detail);
            button.setGraphic(content);

            int stepNumber = index + 1;
            button.setOnAction(event -> activateSupplementaryDesignCard(designCard, workflowStep, stepNumber));
            pane.getChildren().add(button);
        }
    }

    private void activateSupplementaryDesignCard(DesignCard designCard, boolean workflowStep, int stepNumber) {
        String kind = workflowStep ? "workflow step" : "required field";
        String prefix = workflowStep ? "Step " + stepNumber + ": " : "";
        database.recordSystemLog("Data And Records", "Activate supplementary " + kind, "INFO", prefix + designCard.name());
        if (inputGovernanceStatusLabel != null) {
            inputGovernanceStatusLabel.setText("Active " + kind + ": " + prefix + designCard.name() + ". " + designCard.detail());
        }
    }

    private List<Button> deletionButtons() {
        return List.of(
                deleteReconciliationButton,
                deleteObligationButton,
                deleteRecurringButton,
                deletePositionButton,
                deleteLoanScheduleButton
        );
    }

    private boolean governedPhysicalRemoval(String label, int id, Label statusLabel, Runnable deleteAction) {
        if (!UserSession.isSuperAdmin()) {
            requestRemoval(label, id, statusLabel);
            return false;
        }
        boolean confirmed = UiAlerts.confirm(
                "Confirm physical removal",
                "This will permanently delete " + label + " #" + id + ". A safety backup will be created first. Continue?"
        );
        if (!confirmed) {
            return false;
        }
        try {
            database.createBackup(DatabaseHandler.defaultBackupDirectory(), "pre-delete-" + label.replace(' ', '-') + "-" + id);
            deleteAction.run();
            database.recordSystemLog("Data And Records", "Super Admin physical removal", "WARNING", label + " #" + id + " removed after safety backup.");
            if (statusLabel != null) {
                statusLabel.setText(label + " removed after safety backup.");
            }
            if (inputGovernanceStatusLabel != null) {
                inputGovernanceStatusLabel.setText("Physical removal completed by Super Administrator and recorded in the audit log.");
            }
            return true;
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to remove " + label, exception);
            return false;
        }
    }

    private void requestRemoval(String label, int id, Label statusLabel) {
        String message = label + " #" + id + " removal requested. No record was deleted.";
        database.recordSystemLog("Data And Records", "Removal request", "WARNING", message);
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
        if (inputGovernanceStatusLabel != null) {
            inputGovernanceStatusLabel.setText(message + " Open Data Maintenance for dependency analysis, backup and approval.");
        }
        UiAlerts.info("Removal request recorded. Only a Super Administrator may complete physical disposal through Data Maintenance.");
    }

    private void recordWorkflowAction(String action) {
        String selected = selectedInputLabel();
        if (selected == null) {
            UiAlerts.info("Select a supplementary input record first.");
            return;
        }
        database.recordSystemLog("Data And Records", action + " supplementary input", "INFO", selected + ". No stored value was overwritten.");
        inputGovernanceStatusLabel.setText("Active workflow: " + action + " recorded for " + selected + ". No stored value was overwritten.");
    }

    private DesignCard card(String name, String detail) {
        return new DesignCard(name, detail);
    }

    private boolean requireAdminAuthority(String action) {
        if (!UserSession.isAdminOrSuperAdmin()) {
            UiAlerts.info("Only an Administrator or Super Administrator may " + action + " for supplementary report inputs.");
            return false;
        }
        return true;
    }

    private String selectedInputLabel() {
        AccountReconciliationRecord reconciliation = reconciliationTable.getSelectionModel().getSelectedItem();
        if (reconciliation != null) {
            return "account reconciliation #" + reconciliation.getId();
        }
        ScheduledObligation obligation = obligationsTable.getSelectionModel().getSelectedItem();
        if (obligation != null) {
            return "scheduled obligation #" + obligation.getId();
        }
        RecurringTransactionPlan recurring = recurringTable.getSelectionModel().getSelectedItem();
        if (recurring != null) {
            return "recurring transaction plan #" + recurring.getId();
        }
        ReportPositionItem position = positionTable.getSelectionModel().getSelectedItem();
        if (position != null) {
            return "financial position item #" + position.getId();
        }
        LoanScheduleRecord loan = loanScheduleTable.getSelectionModel().getSelectedItem();
        if (loan != null) {
            return "loan schedule #" + loan.getId();
        }
        return null;
    }

    private void configureContextMenus() {
        TableActions.installRowContextMenu(reconciliationTable, row -> rowMenu("Edit Reconciliation", () -> populateReconciliation(row), "account reconciliation", row.getId(), () -> database.deleteAccountReconciliation(row.getId()), reconciliationStatusLabel, reconciliationTable, "Account Reconciliations"));
        TableActions.installRowContextMenu(obligationsTable, row -> rowMenu("Edit Obligation", () -> populateObligation(row), "scheduled obligation", row.getId(), () -> database.deleteScheduledObligation(row.getId()), obligationStatusLabel, obligationsTable, "Scheduled Obligations"));
        TableActions.installRowContextMenu(recurringTable, row -> rowMenu("Edit Recurring Plan", () -> populateRecurring(row), "recurring transaction plan", row.getId(), () -> database.deleteRecurringTransactionPlan(row.getId()), recurringStatusLabel, recurringTable, "Recurring Transaction Plans"));
        TableActions.installRowContextMenu(positionTable, row -> rowMenu("Edit Position Item", () -> populatePosition(row), "financial position item", row.getId(), () -> database.deleteReportPositionItem(row.getId()), positionStatusLabel, positionTable, "Financial Position Items"));
        TableActions.installRowContextMenu(loanScheduleTable, row -> rowMenu("Edit Loan Schedule", () -> populateLoan(row), "loan schedule", row.getId(), () -> database.deleteLoanSchedule(row.getId()), loanStatusLabel, loanScheduleTable, "Loan Schedules"));
    }

    private <T> List<MenuItem> rowMenu(
            String editLabel,
            Runnable editAction,
            String deleteLabel,
            int recordId,
            Runnable deleteAction,
            Label statusLabel,
            TableView<T> table,
            String title
    ) {
        List<MenuItem> items = new ArrayList<>();
        items.add(TableActions.menuItem(editLabel, editAction));
        items.add(TableActions.menuItem(UserSession.isSuperAdmin() ? "Delete With Backup" : "Request Removal", () -> {
            if (governedPhysicalRemoval(deleteLabel, recordId, statusLabel, deleteAction)) {
                refresh();
            }
        }));
        items.add(TableActions.separator());
        T selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            items.add(TableActions.copyRowItem(table, selected));
        }
        items.add(TableActions.exportTableItem(table, title));
        items.add(TableActions.printTableItem(table, title));
        items.add(TableActions.refreshItem(this::refresh));
        return items;
    }

    private record DesignCard(String name, String detail) {
    }
}
