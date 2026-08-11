package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.BudgetProgress;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.HouseholdMonthMember;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BudgetsController {
    @FXML private Label budgetPageTitleLabel;
    @FXML private Label budgetPageSubtitleLabel;
    @FXML private Button budgetHeaderPrimaryButton;
    @FXML private VBox overviewPane;
    @FXML private VBox createPane;
    @FXML private VBox allocationsPane;
    @FXML private VBox performancePane;
    @FXML private VBox householdPane;
    @FXML private VBox historyPane;
    @FXML private VBox budgetContextPane;
    @FXML private ComboBox<BudgetPlanSummary> budgetPlanSelectorBox;
    @FXML private Label selectedPlanContextLabel;
    @FXML private Label allocationSummaryLabel;
    @FXML private Label createAffordabilityPreviewLabel;
    @FXML private Label historySummaryLabel;
    @FXML private DatePicker budgetMonthPicker;
    @FXML private ComboBox<String> planStatusFilterBox;
    @FXML private ComboBox<String> budgetTypeFilterBox;
    @FXML private TextField budgetSearchField;
    @FXML private Label summaryLabel;
    @FXML private Label plannedCountLabel;
    @FXML private Label onBudgetCountLabel;
    @FXML private Label fulfilledCountLabel;
    @FXML private Label notMetCountLabel;
    @FXML private TableView<BudgetPlanSummary> budgetPlanTable;
    @FXML private TableColumn<BudgetPlanSummary, String> planNameColumn;
    @FXML private TableColumn<BudgetPlanSummary, String> planPeriodColumn;
    @FXML private TableColumn<BudgetPlanSummary, String> planTypeColumn;
    @FXML private TableColumn<BudgetPlanSummary, String> planPlannedColumn;
    @FXML private TableColumn<BudgetPlanSummary, String> planActualColumn;
    @FXML private TableColumn<BudgetPlanSummary, String> planRemainingColumn;
    @FXML private TableColumn<BudgetPlanSummary, String> planUsedColumn;
    @FXML private TableColumn<BudgetPlanSummary, String> planSummaryStatusColumn;
    @FXML private TableColumn<BudgetPlanSummary, String> planCashFlowColumn;

    @FXML private TextField budgetNameField;
    @FXML private ComboBox<String> budgetTypeBox;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextField currencyField;
    @FXML private TextField expectedIncomeField;
    @FXML private TextField plannedSavingsField;
    @FXML private TextField overallSpendingLimitField;
    @FXML private ComboBox<String> statusBox;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private TextField amountLimitField;
    @FXML private CheckBox rolloverBox;
    @FXML private TextArea notesArea;
    @FXML private TextField revisionReasonField;
    @FXML private TableView<BudgetProgress> budgetTable;
    @FXML private TableColumn<BudgetProgress, String> nameColumn;
    @FXML private TableColumn<BudgetProgress, String> categoryColumn;
    @FXML private TableColumn<BudgetProgress, String> monthColumn;
    @FXML private TableColumn<BudgetProgress, String> limitColumn;
    @FXML private TableColumn<BudgetProgress, String> spentColumn;
    @FXML private TableColumn<BudgetProgress, String> remainingColumn;
    @FXML private TableColumn<BudgetProgress, String> usedColumn;
    @FXML private TableColumn<BudgetProgress, String> allocationStatusColumn;
    @FXML private TableColumn<BudgetProgress, String> actionColumn;
    @FXML private TextArea budgetDetailsArea;

    @FXML private Label performanceSummaryLabel;
    @FXML private TableView<BudgetProgress> performanceTable;
    @FXML private TableColumn<BudgetProgress, String> performanceCategoryColumn;
    @FXML private TableColumn<BudgetProgress, String> performanceBudgetColumn;
    @FXML private TableColumn<BudgetProgress, String> performanceActualColumn;
    @FXML private TableColumn<BudgetProgress, String> performanceVarianceColumn;
    @FXML private TableColumn<BudgetProgress, String> performanceForecastColumn;
    @FXML private TableColumn<BudgetProgress, String> performanceStatusColumn;

    @FXML private Label householdSummaryLabel;
    @FXML private TextField personNameField;
    @FXML private TextField relationshipField;
    @FXML private ComboBox<String> presenceStatusBox;
    @FXML private ComboBox<String> durationScopeBox;
    @FXML private DatePicker joinedDatePicker;
    @FXML private DatePicker leftDatePicker;
    @FXML private TextField shareWeightField;
    @FXML private TextArea householdNotesArea;
    @FXML private TableView<HouseholdMonthMember> householdTable;
    @FXML private TableColumn<HouseholdMonthMember, String> householdNameColumn;
    @FXML private TableColumn<HouseholdMonthMember, String> householdRelationshipColumn;
    @FXML private TableColumn<HouseholdMonthMember, String> householdStatusColumn;
    @FXML private TableColumn<HouseholdMonthMember, String> householdScopeColumn;
    @FXML private TableColumn<HouseholdMonthMember, String> householdJoinedColumn;
    @FXML private TableColumn<HouseholdMonthMember, String> householdLeftColumn;
    @FXML private TableColumn<HouseholdMonthMember, String> householdShareColumn;
    @FXML private TableColumn<HouseholdMonthMember, String> householdNotesColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final List<BudgetProgress> budgetRows = new ArrayList<>();
    private BudgetPlanSummary selectedPlan;
    private BudgetProgress selectedBudget;
    private BudgetProgress selectedPerformanceBudget;
    private HouseholdMonthMember selectedHouseholdMember;
    private BudgetMode activeMode = BudgetMode.OVERVIEW;
    private boolean selectingPlan;

    @FXML
    public void initialize() {
        configureBudgetMonthPicker();
        configureChoiceBoxes();
        configureTables();
        configureListeners();
        configureContextMenus();
        clearForm();
        clearHouseholdForm();
        activeMode = NavigationBus.consumeRequestedBudgetMode();
        refresh();
        applyMode();
    }

    @FXML
    private void newBudget() {
        selectedPlan = null;
        selectedBudget = null;
        budgetPlanTable.getSelectionModel().clearSelection();
        if (budgetPlanSelectorBox != null) {
            budgetPlanSelectorBox.getSelectionModel().clearSelection();
        }
        budgetTable.setItems(FXCollections.observableArrayList());
        clearForm();
        updateActionState();
        budgetDetailsArea.setText("Create a budget plan as a draft, add category allocations, then activate it when the amounts are affordable.");
        switchMode(BudgetMode.CREATE);
    }

    @FXML
    private void openBudget() {
        BudgetPlanSummary plan = budgetPlanTable.getSelectionModel().getSelectedItem();
        if (plan == null) {
            UiAlerts.info("Select a budget plan first.");
            return;
        }
        openPlan(plan);
    }

    @FXML
    private void goBudgetOverview() {
        switchMode(BudgetMode.OVERVIEW);
    }

    @FXML
    private void goCreateBudget() {
        newBudget();
    }

    @FXML
    private void continueToBudgetAllocations() {
        BudgetPlanSummary plan = selectedPlan != null ? selectedPlan : budgetPlanTable.getSelectionModel().getSelectedItem();
        if (plan != null) {
            openPlan(plan);
        }
        switchMode(BudgetMode.ALLOCATIONS);
    }

    @FXML
    private void goBudgetPerformance() {
        BudgetPlanSummary plan = selectedPlan != null ? selectedPlan : budgetPlanTable.getSelectionModel().getSelectedItem();
        if (plan != null) {
            openPlan(plan);
        }
        switchMode(BudgetMode.PERFORMANCE);
    }

    @FXML
    private void goHouseholdBudget() {
        switchMode(BudgetMode.HOUSEHOLD);
    }

    @FXML
    private void goBudgetHistory() {
        BudgetPlanSummary plan = selectedPlan != null ? selectedPlan : budgetPlanTable.getSelectionModel().getSelectedItem();
        if (plan != null) {
            openPlan(plan);
        }
        switchMode(BudgetMode.HISTORY);
    }

    @FXML
    private void createDraftBudget() {
        try {
            String budgetName = textValue(budgetNameField);
            String budgetMonth = selectedBudgetMonth();
            if (budgetName.isBlank()) {
                UiAlerts.info("Budget name is required.");
                return;
            }
            validateBudgetDates(startDatePicker.getValue(), endDatePicker.getValue());
            double expectedIncome = parseOptionalAmount(expectedIncomeField);
            double plannedSavings = parseOptionalAmount(plannedSavingsField);
            double overallLimit = parseOptionalAmount(overallSpendingLimitField);
            if (expectedIncome > 0 && plannedSavings > expectedIncome) {
                UiAlerts.info("Planned savings cannot exceed expected income.");
                return;
            }
            if (planExists(budgetName, budgetMonth)) {
                UiAlerts.info("A budget with this name already exists for " + budgetMonth + ". Open it and add category allocations.");
                return;
            }
            database.addBudgetPlanDraft(
                    budgetName,
                    budgetMonth,
                    budgetTypeBox.getValue(),
                    dateText(startDatePicker),
                    dateText(endDatePicker),
                    textValue(currencyField),
                    expectedIncome,
                    plannedSavings,
                    overallLimit,
                    textValue(notesArea)
            );
            refresh();
            selectPlan(budgetName, budgetMonth);
            DataRefreshBus.notifyDataChanged();
            budgetDetailsArea.setText("Budget created as Draft. Add category allocations next; no expense or account transaction was posted.");
            switchMode(BudgetMode.ALLOCATIONS);
            UiAlerts.info("Budget created as a draft plan.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to create budget draft", exception);
        }
    }

    @FXML
    private void saveBudget() {
        try {
            BudgetPlanSummary plan = selectedPlan != null ? selectedPlan : activePlanFromSelectionOrForm();
            String budgetName = plan == null ? textValue(budgetNameField) : plan.budgetName();
            String budgetMonth = plan == null ? selectedBudgetMonth() : plan.budgetMonth();
            if (plan == null || budgetName.isBlank() || !planExists(budgetName, budgetMonth)) {
                UiAlerts.info("Create or select a budget plan before adding category allocations.");
                return;
            }
            Integer categoryId = selectedCategoryId();
            if (categoryId == null) {
                UiAlerts.info("Select a spending category before saving the allocation.");
                return;
            }
            if (duplicateAllocationExists(budgetName, budgetMonth, categoryId, null)) {
                UiAlerts.info("This category already exists in the selected budget plan. Select it and use Edit Budget.");
                return;
            }
            validateBudgetDates(startDatePicker.getValue(), endDatePicker.getValue());
            database.addBudget(
                    budgetName,
                    categoryId,
                    budgetMonth,
                    parseRequiredAmount(amountLimitField, "Category budget amount"),
                    rolloverBox.isSelected(),
                    "DRAFT",
                    textValue(notesArea),
                    budgetTypeBox.getValue(),
                    dateText(startDatePicker),
                    dateText(endDatePicker),
                    textValue(currencyField),
                    parseOptionalAmount(expectedIncomeField),
                    parseOptionalAmount(plannedSavingsField),
                    parseOptionalAmount(overallSpendingLimitField)
            );
            refresh();
            selectPlan(budgetName, budgetMonth);
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Budget draft allocation saved.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save budget draft", exception);
        }
    }

    @FXML
    private void activateBudget() {
        BudgetPlanSummary plan = activePlanFromSelectionOrForm();
        if (plan == null) {
            UiAlerts.info("Save at least one category allocation before activating the budget.");
            return;
        }
        String validation = activationProblem(plan);
        if (!validation.isBlank()) {
            UiAlerts.info(validation);
            return;
        }
        if (plan.expectedIncome() <= 0 && !UiAlerts.confirm(
                "Activate without expected income?",
                "Expected income is not set. The budget can be monitored, but affordability warnings will be limited."
        )) {
            return;
        }
        try {
            database.updateBudgetGroupStatus(plan.budgetName(), plan.budgetMonth(), "ACTIVE");
            refresh();
            selectPlan(plan.budgetName(), plan.budgetMonth());
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Budget activated. Actual spending will now be compared with the category limits.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to activate budget", exception);
        }
    }

    @FXML
    private void updateBudget() {
        if (selectedBudget == null) {
            UiAlerts.info("Select a category allocation from the opened budget first.");
            return;
        }
        try {
            double newAmount = parseRequiredAmount(amountLimitField, "Category budget amount");
            if (selectedCategoryId() == null) {
                UiAlerts.info("Select a spending category before updating the allocation.");
                return;
            }
            validateBudgetDates(startDatePicker.getValue(), endDatePicker.getValue());
            String reason = textValue(revisionReasonField);
            if (requiresRevisionReason(selectedBudget, newAmount) && reason.isBlank()) {
                UiAlerts.info("Enter a revision reason before changing an active budget amount.");
                return;
            }
            database.updateBudget(
                    selectedBudget.getId(),
                    textValue(budgetNameField),
                    selectedCategoryId(),
                    selectedBudgetMonth(),
                    newAmount,
                    rolloverBox.isSelected(),
                    statusBox.getValue(),
                    textValue(notesArea),
                    budgetTypeBox.getValue(),
                    dateText(startDatePicker),
                    dateText(endDatePicker),
                    textValue(currencyField),
                    parseOptionalAmount(expectedIncomeField),
                    parseOptionalAmount(plannedSavingsField),
                    parseOptionalAmount(overallSpendingLimitField),
                    reason
            );
            refresh();
            selectPlan(textValue(budgetNameField), selectedBudgetMonth());
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Budget allocation updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update budget", exception);
        }
    }

    @FXML
    private void deleteBudget() {
        if (selectedBudget == null) {
            UiAlerts.info("Select a category allocation first.");
            return;
        }
        if (!"Draft".equals(selectedBudget.getPlanStatus())) {
            UiAlerts.info("Only draft allocations should be removed. Close or archive active budgets instead.");
            return;
        }
        if (!UiAlerts.confirm("Remove draft allocation?", "This removes the selected draft category allocation.")) {
            return;
        }
        try {
            database.updateBudget(
                    selectedBudget.getId(),
                    selectedBudget.getBudgetName(),
                    selectedBudget.getCategoryId(),
                    selectedBudget.getBudgetMonth(),
                    selectedBudget.getAmountLimit(),
                    selectedBudget.isRollover(),
                    "ARCHIVED",
                    selectedBudget.getNotes(),
                    selectedBudget.getBudgetType(),
                    selectedBudget.getStartDate(),
                    selectedBudget.getEndDate(),
                    selectedBudget.getCurrency(),
                    selectedBudget.getExpectedIncome(),
                    selectedBudget.getPlannedSavings(),
                    selectedBudget.getOverallSpendingLimit(),
                    "Draft allocation removed from active planning."
            );
            selectedBudget = null;
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Draft allocation archived.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to remove budget allocation", exception);
        }
    }

    @FXML
    private void copyBudgetToNewPeriod() {
        BudgetPlanSummary plan = requireSelectedPlan();
        if (plan == null) {
            return;
        }
        YearMonth nextMonth = parseYearMonth(plan.budgetMonth()).plusMonths(1);
        TextInputDialog dialog = new TextInputDialog(nextMonth.toString());
        dialog.setTitle("PFMIS");
        dialog.setHeaderText("Copy budget to new period");
        dialog.setContentText("New period (YYYY-MM):");
        dialog.showAndWait().map(String::trim).filter(value -> !value.isBlank()).ifPresent(destination -> {
            try {
                YearMonth destinationMonth = YearMonth.parse(destination);
                if (planExists(plan.budgetName(), destinationMonth.toString())) {
                    UiAlerts.info("A budget with this name already exists for " + destinationMonth + ".");
                    return;
                }
                database.copyBudgetToNewPeriod(
                        plan.budgetName(),
                        plan.budgetMonth(),
                        destinationMonth.toString(),
                        destinationMonth.atDay(1).toString(),
                        destinationMonth.atEndOfMonth().toString()
                );
                refresh();
                selectPlan(plan.budgetName(), destinationMonth.toString());
                DataRefreshBus.notifyDataChanged();
                UiAlerts.info("Budget copied as a draft for " + destinationMonth + ".");
            } catch (DateTimeParseException exception) {
                UiAlerts.info("Use YYYY-MM format, for example 2026-08.");
            } catch (RuntimeException exception) {
                UiAlerts.error("Failed to copy budget", exception);
            }
        });
    }

    @FXML
    private void pauseBudget() {
        updateSelectedPlanStatus("PAUSED", "Budget paused.");
    }

    @FXML
    private void closeBudget() {
        updateSelectedPlanStatus("CLOSED", "Budget closed. Final totals are preserved.");
    }

    @FXML
    private void archiveBudget() {
        updateSelectedPlanStatus("ARCHIVED", "Budget archived.");
    }

    @FXML
    private void viewChangeHistory() {
        BudgetPlanSummary plan = requireSelectedPlan();
        if (plan == null) {
            return;
        }
        List<String> history = database.listBudgetRevisionHistory(plan.budgetName(), plan.budgetMonth());
        if (history.isEmpty()) {
            budgetDetailsArea.setText("No budget revision history has been recorded for this plan.");
            return;
        }
        budgetDetailsArea.setText("Budget revision history\n\n" + String.join("\n", history));
    }

    @FXML
    private void openCategoryExpenses() {
        BudgetProgress budget = selectedPerformanceBudget != null ? selectedPerformanceBudget : selectedBudget;
        if (budget == null) {
            UiAlerts.info("Select a budget category first.");
            return;
        }
        if (budget.getCategoryId() == null) {
            UiAlerts.info("Select a category allocation. Budget plan headers do not have posted category expenses.");
            return;
        }
        List<FinanceTransaction> expenses = database.listExpenseTransactionsForBudgetCategory(
                budget.getBudgetMonth(),
                budget.getCategoryId()
        );
        if (expenses.isEmpty()) {
            budgetDetailsArea.setText("No posted expense transactions were found for " + categoryText(budget) + " in " + budget.getBudgetMonth() + ".");
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Expense records behind ").append(categoryText(budget)).append(" actual spending\n\n");
        for (FinanceTransaction expense : expenses) {
            builder.append(expense.getTransactionDate())
                    .append(" | ").append(MoneyUtil.mwk(expense.getAmount()))
                    .append(" | ").append(blank(expense.getAccountName(), "Account not set"))
                    .append(" | ").append(blank(expense.getDescription(), "No description"))
                    .append('\n');
        }
        budgetDetailsArea.setText(builder.toString());
    }

    @FXML
    private void clearForm() {
        selectedBudget = null;
        if (budgetTable != null) {
            budgetTable.getSelectionModel().clearSelection();
        }
        budgetNameField.clear();
        budgetTypeBox.getSelectionModel().select("Monthly");
        LocalDate monthStart = monthStartDate(selectedBudgetMonth());
        startDatePicker.setValue(monthStart);
        endDatePicker.setValue(YearMonth.from(monthStart).atEndOfMonth());
        currencyField.setText("MWK");
        expectedIncomeField.clear();
        plannedSavingsField.clear();
        overallSpendingLimitField.clear();
        statusBox.getSelectionModel().select("DRAFT");
        categoryBox.getSelectionModel().clearSelection();
        amountLimitField.clear();
        rolloverBox.setSelected(false);
        notesArea.clear();
        revisionReasonField.clear();
    }

    @FXML
    private void refresh() {
        String month = selectedBudgetMonth();
        budgetRows.clear();
        budgetRows.addAll(database.listBudgetProgress(month));

        List<BudgetPlanSummary> plans = groupedBudgetPlans(budgetRows).stream()
                .filter(this::matchesFilters)
                .toList();
        budgetPlanTable.setItems(FXCollections.observableArrayList(plans));
        refreshSelectedPlan(plans);
        refreshPlanSelector(plans);
        refreshPerformanceTable();
        refreshSummary(plans);
        refreshHousehold(month);
        updateModeContextLabels();
    }

    @FXML
    private void saveHouseholdMember() {
        try {
            database.addHouseholdMonthMember(
                    selectedBudgetMonth(),
                    textValue(personNameField),
                    textValue(relationshipField),
                    presenceStatusBox.getValue(),
                    joinedDatePicker.getValue(),
                    leftDatePicker.getValue(),
                    parseShareWeight(),
                    durationScopeBox.getValue(),
                    textValue(householdNotesArea)
            );
            clearHouseholdForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Household budget member recorded.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to record household member", exception);
        }
    }

    @FXML
    private void updateHouseholdMember() {
        if (selectedHouseholdMember == null) {
            UiAlerts.info("Select a household member first.");
            return;
        }
        if (selectedHouseholdMember.isBudgetOwner()) {
            UiAlerts.info("The budget owner is included automatically and cannot be edited here.");
            return;
        }
        try {
            database.updateHouseholdMonthMember(
                    selectedHouseholdMember.getId(),
                    budgetMonthForHouseholdUpdate(),
                    textValue(personNameField),
                    textValue(relationshipField),
                    presenceStatusBox.getValue(),
                    joinedDatePicker.getValue(),
                    leftDatePicker.getValue(),
                    parseShareWeight(),
                    durationScopeBox.getValue(),
                    textValue(householdNotesArea)
            );
            clearHouseholdForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Household budget member updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update household member", exception);
        }
    }

    @FXML
    private void deleteHouseholdMember() {
        if (selectedHouseholdMember == null) {
            UiAlerts.info("Select a household member first.");
            return;
        }
        if (selectedHouseholdMember.isBudgetOwner()) {
            UiAlerts.info("The budget owner is included automatically and cannot be removed.");
            return;
        }
        try {
            database.deleteHouseholdMonthMember(selectedHouseholdMember.getId());
            clearHouseholdForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Household budget member removed for this month.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to remove household member", exception);
        }
    }

    @FXML
    private void clearHouseholdForm() {
        selectedHouseholdMember = null;
        if (householdTable != null) {
            householdTable.getSelectionModel().clearSelection();
        }
        personNameField.clear();
        relationshipField.clear();
        presenceStatusBox.getSelectionModel().select("IN HOUSE");
        durationScopeBox.getSelectionModel().select("THIS MONTH ONLY");
        joinedDatePicker.setValue(monthStartDate(selectedBudgetMonth()));
        leftDatePicker.setValue(null);
        shareWeightField.setText("1");
        householdNotesArea.clear();
        setHouseholdFormEditable(true);
    }

    private void configureChoiceBoxes() {
        planStatusFilterBox.setItems(FXCollections.observableArrayList("All statuses", "Draft", "Active", "At Risk", "Exceeded", "Paused", "Closed", "Archived"));
        planStatusFilterBox.getSelectionModel().select("All statuses");
        List<String> budgetTypes = List.of("Monthly", "Weekly", "Quarterly", "Annual", "Custom period", "Project budget", "Goal-related budget");
        budgetTypeFilterBox.setItems(FXCollections.observableArrayList("All types", "Monthly", "Weekly", "Quarterly", "Annual", "Custom period", "Project budget", "Goal-related budget"));
        budgetTypeFilterBox.getSelectionModel().select("All types");
        budgetTypeBox.setItems(FXCollections.observableArrayList(budgetTypes));
        budgetTypeBox.getSelectionModel().select("Monthly");
        statusBox.setItems(FXCollections.observableArrayList("DRAFT", "ACTIVE", "AT RISK", "EXCEEDED", "PAUSED", "CLOSED", "ARCHIVED"));
        statusBox.getSelectionModel().select("DRAFT");
        presenceStatusBox.setItems(FXCollections.observableArrayList("IN HOUSE", "JOINED", "LEFT", "AWAY"));
        presenceStatusBox.getSelectionModel().select("IN HOUSE");
        durationScopeBox.setItems(FXCollections.observableArrayList("THIS MONTH ONLY", "ONGOING"));
        durationScopeBox.getSelectionModel().select("THIS MONTH ONLY");
        categoryBox.setItems(FXCollections.observableArrayList(expenseCategories()));
        if (budgetPlanSelectorBox != null) {
            budgetPlanSelectorBox.setConverter(new StringConverter<>() {
                @Override
                public String toString(BudgetPlanSummary plan) {
                    return plan == null ? "" : plan.budgetName() + " - " + plan.budgetMonth() + " (" + plan.status() + ")";
                }

                @Override
                public BudgetPlanSummary fromString(String value) {
                    return null;
                }
            });
        }
    }

    private void configureTables() {
        budgetPlanTable.setPlaceholder(new Label("No budget plans found for the selected period."));
        budgetTable.setPlaceholder(new Label("Open a budget plan to see category allocations."));
        performanceTable.setPlaceholder(new Label("No budget performance is available for the selected period."));
        householdTable.setPlaceholder(new Label("The budget owner is added automatically. Add other people for this month or ongoing."));
        TableActions.configureScrollableTable(budgetPlanTable);
        TableActions.configureScrollableTable(budgetTable);
        TableActions.configureScrollableTable(performanceTable);
        TableActions.configureScrollableTable(householdTable);

        planNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().budgetName()));
        planPeriodColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().budgetMonth()));
        planTypeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().budgetType()));
        planPlannedColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().plannedAmount())));
        planActualColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().actualAmount())));
        planRemainingColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().remainingAmount())));
        planUsedColumn.setCellValueFactory(cell -> new SimpleStringProperty(percentText(cell.getValue().percentUsed())));
        planSummaryStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status()));
        planCashFlowColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().cashFlowText()));

        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getBudgetName()));
        categoryColumn.setCellValueFactory(cell -> new SimpleStringProperty(categoryText(cell.getValue())));
        monthColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getBudgetMonth()));
        limitColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountLimit())));
        spentColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getSpent())));
        remainingColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getRemaining())));
        usedColumn.setCellValueFactory(cell -> new SimpleStringProperty(percentText(cell.getValue().getPercentUsed())));
        allocationStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(performanceStatus(cell.getValue())));
        actionColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getActionNeeded()));

        performanceCategoryColumn.setCellValueFactory(cell -> new SimpleStringProperty(categoryText(cell.getValue())));
        performanceBudgetColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountLimit())));
        performanceActualColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getSpent())));
        performanceVarianceColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getRemaining())));
        performanceForecastColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(projectedSpend(cell.getValue()))));
        performanceStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(performanceStatus(cell.getValue())));

        householdNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPersonName()));
        householdRelationshipColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getRelationship(), "-")));
        householdStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayHouseholdStatus(cell.getValue().getPresenceStatus())));
        householdScopeColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayDurationScope(cell.getValue().getDurationScope())));
        householdJoinedColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getJoinedDate(), "-")));
        householdLeftColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getLeftDate(), "-")));
        householdShareColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.format(Locale.ENGLISH, "%.2f", cell.getValue().getShareWeight())));
        householdNotesColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getNotes(), "")));
    }

    private void configureListeners() {
        budgetMonthPicker.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selectedHouseholdMember == null) {
                joinedDatePicker.setValue(monthStartDate(selectedBudgetMonth()));
            }
            refresh();
        });
        planStatusFilterBox.valueProperty().addListener((observable, oldValue, selected) -> refresh());
        budgetTypeFilterBox.valueProperty().addListener((observable, oldValue, selected) -> refresh());
        budgetSearchField.textProperty().addListener((observable, oldValue, selected) -> refresh());
        expectedIncomeField.textProperty().addListener((observable, oldValue, selected) -> updateModeContextLabels());
        plannedSavingsField.textProperty().addListener((observable, oldValue, selected) -> updateModeContextLabels());
        overallSpendingLimitField.textProperty().addListener((observable, oldValue, selected) -> updateModeContextLabels());
        if (budgetPlanSelectorBox != null) {
            budgetPlanSelectorBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
                if (!selectingPlan && selected != null) {
                    openPlan(selected);
                }
            });
        }
        budgetPlanTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                selectedPlan = selected;
                describePlan(selected);
                syncPlanSelector(selected);
            }
            updateActionState();
        });
        budgetTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            selectedBudget = selected;
            if (selected != null) {
                fillForm(selected);
            }
        });
        performanceTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            selectedPerformanceBudget = selected;
            if (selected != null) {
                budgetDetailsArea.setText(performanceDetails(selected));
            }
        });
        householdTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            selectedHouseholdMember = selected;
            if (selected != null) {
                fillHouseholdForm(selected);
            } else {
                setHouseholdFormEditable(true);
            }
        });
        presenceStatusBox.valueProperty().addListener((observable, oldValue, selected) -> updateDefaultShareForStatus(oldValue, selected));
    }

    private void configureContextMenus() {
        TableActions.installRowContextMenu(budgetPlanTable, plan -> List.of(
                TableActions.menuItem("Open Budget", () -> {
                    if (plan != null) {
                        openPlan(plan);
                    }
                }),
                TableActions.menuItem("Copy to New Period", this::copyBudgetToNewPeriod),
                TableActions.menuItem("View Change History", this::viewChangeHistory),
                TableActions.separator(),
                TableActions.copyRowItem(budgetPlanTable, plan),
                TableActions.exportTableItem(budgetPlanTable, selectedBudgetMonth() + " Budget Plans"),
                TableActions.printTableItem(budgetPlanTable, selectedBudgetMonth() + " Budget Plans"),
                TableActions.refreshItem(this::refresh)
        ));
        TableActions.installRowContextMenu(budgetTable, budget -> List.of(
                TableActions.menuItem("Edit Budget", () -> {
                    if (budget != null) {
                        selectedBudget = budget;
                        fillForm(budget);
                    }
                }),
                TableActions.menuItem("Open Category Expenses", this::openCategoryExpenses),
                TableActions.separator(),
                TableActions.copyRowItem(budgetTable, budget),
                TableActions.exportTableItem(budgetTable, selectedBudgetMonth() + " Budget Allocations"),
                TableActions.printTableItem(budgetTable, selectedBudgetMonth() + " Budget Allocations"),
                TableActions.refreshItem(this::refresh)
        ));
        TableActions.installRowContextMenu(performanceTable, budget -> List.of(
                TableActions.menuItem("Open Category Expenses", () -> {
                    selectedPerformanceBudget = budget;
                    openCategoryExpenses();
                }),
                TableActions.separator(),
                TableActions.copyRowItem(performanceTable, budget),
                TableActions.exportTableItem(performanceTable, selectedBudgetMonth() + " Budget Performance"),
                TableActions.printTableItem(performanceTable, selectedBudgetMonth() + " Budget Performance"),
                TableActions.refreshItem(this::refresh)
        ));
        TableActions.installRowContextMenu(householdTable, member -> {
            List<javafx.scene.control.MenuItem> items = new ArrayList<>();
            items.add(TableActions.menuItem(member != null && member.isBudgetOwner() ? "View Budget Owner" : "Edit Household Member",
                    () -> {
                        if (member != null) {
                            selectedHouseholdMember = member;
                            fillHouseholdForm(member);
                        }
                    }));
            if (member == null || !member.isBudgetOwner()) {
                items.add(TableActions.menuItem("Remove Household Member", () -> {
                    selectedHouseholdMember = member;
                    deleteHouseholdMember();
                }));
            }
            items.add(TableActions.separator());
            items.add(TableActions.copyRowItem(householdTable, member));
            items.add(TableActions.exportTableItem(householdTable, selectedBudgetMonth() + " Household Budget Members"));
            items.add(TableActions.printTableItem(householdTable, selectedBudgetMonth() + " Household Budget Members"));
            items.add(TableActions.refreshItem(this::refresh));
            return items;
        });
    }

    private void openPlan(BudgetPlanSummary plan) {
        selectedPlan = plan;
        budgetNameField.setText(plan.budgetName());
        budgetMonthPicker.setValue(monthStartDate(plan.budgetMonth()));
        budgetTypeBox.getSelectionModel().select(plan.budgetType());
        startDatePicker.setValue(parseDate(plan.startDate(), monthStartDate(plan.budgetMonth())));
        endDatePicker.setValue(parseDate(plan.endDate(), parseYearMonth(plan.budgetMonth()).atEndOfMonth()));
        currencyField.setText(blank(plan.currency(), "MWK"));
        expectedIncomeField.setText(amountForForm(plan.expectedIncome()));
        plannedSavingsField.setText(amountForForm(plan.plannedSavings()));
        overallSpendingLimitField.setText(amountForForm(plan.overallSpendingLimit()));
        statusBox.getSelectionModel().select(statusForForm(plan.status()));
        notesArea.setText(plan.notes());
        revisionReasonField.clear();
        budgetTable.setItems(FXCollections.observableArrayList(plan.allocations()));
        selectedBudget = null;
        budgetTable.getSelectionModel().clearSelection();
        syncPlanSelector(plan);
        describePlan(plan);
        updateActionState();
    }

    private void selectPlan(String budgetName, String budgetMonth) {
        for (BudgetPlanSummary plan : budgetPlanTable.getItems()) {
            if (plan.budgetName().equalsIgnoreCase(budgetName) && plan.budgetMonth().equals(budgetMonth)) {
                budgetPlanTable.getSelectionModel().select(plan);
                openPlan(plan);
                return;
            }
        }
    }

    private void fillForm(BudgetProgress budget) {
        budgetNameField.setText(budget.getBudgetName());
        budgetMonthPicker.setValue(monthStartDate(budget.getBudgetMonth()));
        budgetTypeBox.getSelectionModel().select(blank(budget.getBudgetType(), "Monthly"));
        startDatePicker.setValue(parseDate(budget.getStartDate(), monthStartDate(budget.getBudgetMonth())));
        endDatePicker.setValue(parseDate(budget.getEndDate(), parseYearMonth(budget.getBudgetMonth()).atEndOfMonth()));
        currencyField.setText(blank(budget.getCurrency(), "MWK"));
        expectedIncomeField.setText(amountForForm(budget.getExpectedIncome()));
        plannedSavingsField.setText(amountForForm(budget.getPlannedSavings()));
        overallSpendingLimitField.setText(amountForForm(budget.getOverallSpendingLimit()));
        statusBox.getSelectionModel().select(statusForForm(budget.getPlanStatus()));
        categoryBox.getSelectionModel().clearSelection();
        if (budget.getCategoryId() != null) {
            categoryBox.getItems().stream()
                    .filter(category -> category.getId() == budget.getCategoryId())
                    .findFirst()
                    .ifPresent(category -> categoryBox.getSelectionModel().select(category));
        }
        amountLimitField.setText(amountForForm(budget.getAmountLimit()));
        rolloverBox.setSelected(budget.isRollover());
        notesArea.setText(blank(budget.getNotes(), ""));
        revisionReasonField.clear();
        budgetDetailsArea.setText(performanceDetails(budget));
    }

    private void refreshSelectedPlan(List<BudgetPlanSummary> plans) {
        if (selectedPlan == null) {
            budgetTable.setItems(FXCollections.observableArrayList());
            updateActionState();
            return;
        }
        for (BudgetPlanSummary plan : plans) {
            if (plan.budgetName().equals(selectedPlan.budgetName()) && plan.budgetMonth().equals(selectedPlan.budgetMonth())) {
                selectedPlan = plan;
                budgetTable.setItems(FXCollections.observableArrayList(plan.allocations()));
                describePlan(plan);
                updateActionState();
                return;
            }
        }
        selectedPlan = null;
        selectedBudget = null;
        budgetTable.setItems(FXCollections.observableArrayList());
        updateActionState();
    }

    private void refreshPerformanceTable() {
        List<BudgetProgress> rows = selectedPlan == null
                ? new ArrayList<>(budgetRows)
                : budgetRows.stream()
                .filter(row -> row.getBudgetName().equals(selectedPlan.budgetName()) && row.getBudgetMonth().equals(selectedPlan.budgetMonth()))
                .toList();
        rows = rows.stream().filter(this::isCategoryAllocation).toList();
        performanceTable.setItems(FXCollections.observableArrayList(rows));
        double planned = rows.stream().mapToDouble(BudgetProgress::getAmountLimit).sum();
        double actual = rows.stream().mapToDouble(BudgetProgress::getSpent).sum();
        double forecast = rows.stream().mapToDouble(this::projectedSpend).sum();
        performanceSummaryLabel.setText("Budget performance: planned " + MoneyUtil.mwk(planned)
                + ", actual " + MoneyUtil.mwk(actual)
                + ", variance " + MoneyUtil.mwk(planned - actual)
                + ", forecast " + MoneyUtil.mwk(forecast)
                + ". Actual spending comes from posted expense transactions only.");
    }

    private void refreshSummary(List<BudgetPlanSummary> plans) {
        double planned = plans.stream().mapToDouble(BudgetPlanSummary::plannedAmount).sum();
        double actual = plans.stream().mapToDouble(BudgetPlanSummary::actualAmount).sum();
        long active = plans.stream().filter(plan -> "Active".equals(plan.status())).count();
        long atRisk = plans.stream().filter(plan -> "At Risk".equals(plan.status())).count();
        long exceeded = plans.stream().filter(plan -> "Exceeded".equals(plan.status())).count();
        long draft = plans.stream().filter(plan -> "Draft".equals(plan.status())).count();
        summaryLabel.setText("Period " + selectedBudgetMonth() + ": planned " + MoneyUtil.mwk(planned)
                + ", actual " + MoneyUtil.mwk(actual)
                + ", unused limit " + MoneyUtil.mwk(planned - actual)
                + ". Unused budget is a remaining limit, not saved cash unless account balances confirm it.");
        plannedCountLabel.setText(String.valueOf(plans.size()));
        onBudgetCountLabel.setText(String.valueOf(active));
        fulfilledCountLabel.setText(String.valueOf(atRisk + draft));
        notMetCountLabel.setText(String.valueOf(exceeded));
    }

    private void refreshHousehold(String month) {
        List<HouseholdMonthMember> householdMembers = database.listHouseholdMonthMembers(month);
        householdTable.setItems(FXCollections.observableArrayList(householdMembers));
        double householdUnits = database.householdUnitsForMonth(month);
        double totalSpent = budgetRows.stream()
                .filter(this::isCategoryAllocation)
                .mapToDouble(BudgetProgress::getSpent)
                .sum();
        double effectiveUnits = householdUnits > 0 ? householdUnits : 1;
        long ownerCount = householdMembers.stream().filter(HouseholdMonthMember::isBudgetOwner).count();
        long addedPeople = Math.max(0, householdMembers.size() - ownerCount);
        householdSummaryLabel.setText("Owner is counted automatically. " + addedPeople + " added person record(s) for " + month
                + " | budget units " + householdUnitsText(householdUnits)
                + " | total spent per unit " + MoneyUtil.mwk(totalSpent / effectiveUnits)
                + " | use This Month Only or Ongoing when adding people.");
    }

    private void switchMode(BudgetMode mode) {
        activeMode = mode == null ? BudgetMode.OVERVIEW : mode;
        applyMode();
    }

    private void applyMode() {
        BudgetMode mode = activeMode == null ? BudgetMode.OVERVIEW : activeMode;
        setContentVisible(overviewPane, mode == BudgetMode.OVERVIEW);
        setContentVisible(createPane, mode == BudgetMode.CREATE);
        setContentVisible(allocationsPane, mode == BudgetMode.ALLOCATIONS);
        setContentVisible(performancePane, mode == BudgetMode.PERFORMANCE);
        setContentVisible(householdPane, mode == BudgetMode.HOUSEHOLD);
        setContentVisible(historyPane, mode == BudgetMode.HISTORY);
        setContentVisible(budgetContextPane, mode != BudgetMode.CREATE && mode != BudgetMode.HOUSEHOLD);

        switch (mode) {
            case OVERVIEW -> {
                setPageHeader(
                        "Budget Overview",
                        "Planning dashboard for budget plans, current spend, remaining limits, and attention items."
                );
                configureHeaderPrimaryAction("Create Budget", this::newBudget);
            }
            case CREATE -> {
                setPageHeader(
                        "Create Budget",
                        "Create the draft plan only. Category allocations and actual spending are handled on separate screens."
                );
                configureHeaderPrimaryAction("Create Draft Budget", this::createDraftBudget);
            }
            case ALLOCATIONS -> {
                setPageHeader(
                        "Category Allocations",
                        "Assign category limits to a selected draft or active budget plan."
                );
                configureHeaderPrimaryAction("Save Allocation", this::saveBudget);
            }
            case PERFORMANCE -> {
                setPageHeader(
                        "Performance & Variance",
                        "Compare category limits with posted expense transactions; unused budget remains a limit, not cash."
                );
                configureHeaderPrimaryAction("Refresh Variance", this::refresh);
            }
            case HOUSEHOLD -> {
                setPageHeader(
                        "Household Budget",
                        "Maintain the household context used to understand per-person budget pressure."
                );
                configureHeaderPrimaryAction("Add Household Member", this::saveHouseholdMember);
            }
            case HISTORY -> {
                setPageHeader(
                        "Budget History & Lifecycle",
                        "Review revisions and lifecycle actions without mixing them into day-to-day allocation work."
                );
                configureHeaderPrimaryAction("Copy to New Period", this::copyBudgetToNewPeriod);
            }
        }
        updateModeContextLabels();
    }

    private void setPageHeader(String title, String subtitle) {
        if (budgetPageTitleLabel != null) {
            budgetPageTitleLabel.setText(title);
        }
        if (budgetPageSubtitleLabel != null) {
            budgetPageSubtitleLabel.setText(subtitle);
        }
    }

    private void configureHeaderPrimaryAction(String text, Runnable action) {
        if (budgetHeaderPrimaryButton == null) {
            return;
        }
        budgetHeaderPrimaryButton.setText(text);
        budgetHeaderPrimaryButton.setOnAction(event -> action.run());
    }

    private void setContentVisible(Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private void refreshPlanSelector(List<BudgetPlanSummary> plans) {
        if (budgetPlanSelectorBox == null) {
            return;
        }
        selectingPlan = true;
        try {
            budgetPlanSelectorBox.setItems(FXCollections.observableArrayList(plans));
            if (selectedPlan == null) {
                budgetPlanSelectorBox.getSelectionModel().clearSelection();
                return;
            }
            plans.stream()
                    .filter(plan -> plan.budgetName().equals(selectedPlan.budgetName())
                            && plan.budgetMonth().equals(selectedPlan.budgetMonth()))
                    .findFirst()
                    .ifPresentOrElse(
                            plan -> budgetPlanSelectorBox.getSelectionModel().select(plan),
                            () -> budgetPlanSelectorBox.getSelectionModel().clearSelection()
                    );
        } finally {
            selectingPlan = false;
        }
    }

    private void syncPlanSelector(BudgetPlanSummary plan) {
        if (budgetPlanSelectorBox == null || plan == null) {
            return;
        }
        selectingPlan = true;
        try {
            for (BudgetPlanSummary item : budgetPlanSelectorBox.getItems()) {
                if (item.budgetName().equals(plan.budgetName()) && item.budgetMonth().equals(plan.budgetMonth())) {
                    budgetPlanSelectorBox.getSelectionModel().select(item);
                    return;
                }
            }
        } finally {
            selectingPlan = false;
        }
    }

    private void updateModeContextLabels() {
        if (selectedPlanContextLabel != null) {
            selectedPlanContextLabel.setText(selectedPlan == null
                    ? "No budget plan selected. Choose a plan from the selector or open one from Budget Overview."
                    : selectedPlan.budgetName() + " | " + selectedPlan.budgetMonth() + " | " + selectedPlan.status());
        }
        if (allocationSummaryLabel != null) {
            allocationSummaryLabel.setText(selectedPlan == null
                    ? "Select a budget plan before saving category limits."
                    : selectedPlan.allocations().size() + " allocation(s), planned "
                    + MoneyUtil.mwk(selectedPlan.plannedAmount()) + ", unused limit "
                    + MoneyUtil.mwk(selectedPlan.remainingAmount()) + ".");
        }
        if (historySummaryLabel != null) {
            historySummaryLabel.setText(selectedPlan == null
                    ? "Select a plan to view lifecycle details and revision history."
                    : selectedPlan.budgetName() + " is " + selectedPlan.status() + " for " + selectedPlan.budgetMonth()
                    + ". Use lifecycle actions only for plan state changes.");
        }
        if (createAffordabilityPreviewLabel != null) {
            createAffordabilityPreviewLabel.setText(createAffordabilityPreview());
        }
    }

    private List<BudgetPlanSummary> groupedBudgetPlans(List<BudgetProgress> rows) {
        Map<String, List<BudgetProgress>> grouped = new LinkedHashMap<>();
        for (BudgetProgress row : rows) {
            grouped.computeIfAbsent(row.getBudgetName() + "\u0000" + row.getBudgetMonth(), ignored -> new ArrayList<>()).add(row);
        }
        return grouped.values().stream().map(BudgetPlanSummary::from).toList();
    }

    private boolean matchesFilters(BudgetPlanSummary plan) {
        String status = planStatusFilterBox.getValue();
        String type = budgetTypeFilterBox.getValue();
        String search = textValue(budgetSearchField).toLowerCase(Locale.ENGLISH);
        boolean statusMatches = status == null || status.equals("All statuses") || status.equals(plan.status());
        boolean typeMatches = type == null || type.equals("All types") || type.equals(plan.budgetType());
        boolean searchMatches = search.isBlank() || plan.budgetName().toLowerCase(Locale.ENGLISH).contains(search);
        return statusMatches && typeMatches && searchMatches;
    }

    private BudgetPlanSummary activePlanFromSelectionOrForm() {
        if (selectedPlan != null) {
            return selectedPlan;
        }
        String budgetName = textValue(budgetNameField);
        String month = selectedBudgetMonth();
        return groupedBudgetPlans(budgetRows).stream()
                .filter(plan -> plan.budgetName().equalsIgnoreCase(budgetName) && plan.budgetMonth().equals(month))
                .findFirst()
                .orElse(null);
    }

    private String activationProblem(BudgetPlanSummary plan) {
        if (plan.allocations().isEmpty()) {
            return "Add at least one category allocation before activation.";
        }
        if (plan.allocations().stream().anyMatch(row -> row.getAmountLimit() <= 0)) {
            return "All category amounts must be greater than zero.";
        }
        if (plan.expectedIncome() > 0 && plan.plannedAmount() + plan.plannedSavings() > plan.expectedIncome()) {
            return "The proposed budget exceeds expected income by "
                    + MoneyUtil.mwk(plan.plannedAmount() + plan.plannedSavings() - plan.expectedIncome())
                    + ". Reduce allocations or planned savings before activation.";
        }
        if (plan.overallSpendingLimit() > 0 && plan.plannedAmount() > plan.overallSpendingLimit()) {
            return "Category allocations exceed the overall spending limit by "
                    + MoneyUtil.mwk(plan.plannedAmount() - plan.overallSpendingLimit()) + ".";
        }
        String duplicate = duplicateActiveCoverage(plan);
        if (!duplicate.isBlank()) {
            return duplicate;
        }
        return "";
    }

    private String duplicateActiveCoverage(BudgetPlanSummary plan) {
        for (BudgetProgress candidate : plan.allocations()) {
            for (BudgetProgress row : budgetRows) {
                if (row.getCategoryId() == null || candidate.getCategoryId() == null) {
                    continue;
                }
                if (row.getCategoryId().equals(candidate.getCategoryId())
                        && row.getBudgetMonth().equals(candidate.getBudgetMonth())
                        && !row.getBudgetName().equals(candidate.getBudgetName())
                        && isMonitoringStatus(row.getPlanStatus())) {
                    return "Another active budget already covers " + categoryText(candidate)
                            + " for " + candidate.getBudgetMonth() + ".";
                }
            }
        }
        return "";
    }

    private boolean duplicateAllocationExists(String budgetName, String budgetMonth, Integer categoryId, Integer ignoredBudgetId) {
        return budgetRows.stream().anyMatch(row ->
                row.getBudgetName().equalsIgnoreCase(budgetName)
                        && row.getBudgetMonth().equals(budgetMonth)
                        && categoryId.equals(row.getCategoryId())
                        && (ignoredBudgetId == null || row.getId() != ignoredBudgetId)
        );
    }

    private boolean planExists(String budgetName, String budgetMonth) {
        List<BudgetProgress> rows = budgetMonth.equals(selectedBudgetMonth())
                ? budgetRows
                : database.listBudgetProgress(budgetMonth);
        return rows.stream().anyMatch(row ->
                row.getBudgetName().equalsIgnoreCase(budgetName)
                        && row.getBudgetMonth().equals(budgetMonth)
        );
    }

    private BudgetPlanSummary requireSelectedPlan() {
        BudgetPlanSummary plan = selectedPlan != null ? selectedPlan : budgetPlanTable.getSelectionModel().getSelectedItem();
        if (plan == null) {
            UiAlerts.info("Select a budget plan first.");
            return null;
        }
        selectedPlan = plan;
        return plan;
    }

    private void updateSelectedPlanStatus(String status, String message) {
        BudgetPlanSummary plan = requireSelectedPlan();
        if (plan == null) {
            return;
        }
        try {
            database.updateBudgetGroupStatus(plan.budgetName(), plan.budgetMonth(), status);
            refresh();
            selectPlan(plan.budgetName(), plan.budgetMonth());
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info(message);
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update budget status", exception);
        }
    }

    private void describePlan(BudgetPlanSummary plan) {
        budgetDetailsArea.setText("""
                %s

                Planned expenses: %s
                Actual spending:   %s
                Unused limit:      %s
                Budget used:       %s
                Expected income:   %s
                Planned savings:   %s
                Unallocated:       %s
                Forecast:          %s

                Alerts
                %s

                Note: unused budget is an unused spending limit. It is not automatically saved money unless account and cash-flow records support that conclusion.
                """.formatted(
                plan.budgetName() + " (" + plan.budgetMonth() + ")",
                MoneyUtil.mwk(plan.plannedAmount()),
                MoneyUtil.mwk(plan.actualAmount()),
                MoneyUtil.mwk(plan.remainingAmount()),
                percentText(plan.percentUsed()),
                plan.expectedIncome() > 0 ? MoneyUtil.mwk(plan.expectedIncome()) : "Not set",
                MoneyUtil.mwk(plan.plannedSavings()),
                plan.expectedIncome() > 0 ? MoneyUtil.mwk(plan.expectedIncome() - plan.plannedSavings() - plan.plannedAmount()) : "Not available",
                MoneyUtil.mwk(plan.projectedAmount()),
                plan.alertText()
        ));
    }

    private String performanceDetails(BudgetProgress budget) {
        double projected = projectedSpend(budget);
        LocalDate endDate = parseDate(budget.getEndDate(), parseYearMonth(budget.getBudgetMonth()).atEndOfMonth());
        String exhaustion = expectedExhaustionDate(budget);
        return """
                %s budget performance

                Budget:     %s
                Actual:     %s
                Variance:   %s
                Used:       %s
                Forecast:   %s
                Exhaustion:  %s
                Period end:  %s
                Status:      %s

                Actual spending is recalculated from posted expense transactions. Cancelled transactions are excluded.
                """.formatted(
                categoryText(budget),
                MoneyUtil.mwk(budget.getAmountLimit()),
                MoneyUtil.mwk(budget.getSpent()),
                MoneyUtil.mwk(budget.getRemaining()),
                percentText(budget.getPercentUsed()),
                MoneyUtil.mwk(projected),
                exhaustion,
                endDate,
                performanceStatus(budget)
        );
    }

    private double projectedSpend(BudgetProgress budget) {
        LocalDate start = parseDate(budget.getStartDate(), monthStartDate(budget.getBudgetMonth()));
        LocalDate end = parseDate(budget.getEndDate(), parseYearMonth(budget.getBudgetMonth()).atEndOfMonth());
        LocalDate today = LocalDate.now();
        LocalDate measuredDate = today.isBefore(start) ? start : today.isAfter(end) ? end : today;
        long elapsedDays = Math.max(1, ChronoUnit.DAYS.between(start, measuredDate) + 1);
        long totalDays = Math.max(elapsedDays, ChronoUnit.DAYS.between(start, end) + 1);
        return (budget.getSpent() / elapsedDays) * totalDays;
    }

    private String expectedExhaustionDate(BudgetProgress budget) {
        if (budget.getSpent() <= 0 || budget.getAmountLimit() <= 0) {
            return "Not projected";
        }
        LocalDate start = parseDate(budget.getStartDate(), monthStartDate(budget.getBudgetMonth()));
        LocalDate end = parseDate(budget.getEndDate(), parseYearMonth(budget.getBudgetMonth()).atEndOfMonth());
        LocalDate today = LocalDate.now();
        LocalDate measuredDate = today.isBefore(start) ? start : today.isAfter(end) ? end : today;
        long elapsedDays = Math.max(1, ChronoUnit.DAYS.between(start, measuredDate) + 1);
        double averageDaily = budget.getSpent() / elapsedDays;
        if (averageDaily <= 0) {
            return "Not projected";
        }
        LocalDate exhaustion = start.plusDays(Math.max(0, (long) Math.ceil(budget.getAmountLimit() / averageDaily) - 1));
        return exhaustion.isAfter(end) ? "After period end" : exhaustion.toString();
    }

    private String performanceStatus(BudgetProgress budget) {
        if (!isMonitoringStatus(budget.getPlanStatus())) {
            return budget.getPlanStatus();
        }
        if (budget.getSpent() > budget.getAmountLimit()) {
            return "Exceeded";
        }
        if (budget.getPercentUsed() >= 80 || projectedSpend(budget) > budget.getAmountLimit()) {
            return "At Risk";
        }
        return "On track";
    }

    private boolean isMonitoringStatus(String status) {
        return List.of("Active", "At Risk", "Exceeded", "On Budget").contains(status);
    }

    private boolean requiresRevisionReason(BudgetProgress budget, double newAmount) {
        return isMonitoringStatus(budget.getPlanStatus()) && Math.abs(budget.getAmountLimit() - newAmount) >= 0.005;
    }

    private List<Category> expenseCategories() {
        return database.listCategories().stream()
                .filter(category -> "EXPENSE".equals(category.getCategoryType()) || "BOTH".equals(category.getCategoryType()))
                .toList();
    }

    private Integer selectedCategoryId() {
        Category category = categoryBox.getValue();
        return category == null ? null : category.getId();
    }

    private void configureBudgetMonthPicker() {
        budgetMonthPicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : YearMonth.from(date).toString();
            }

            @Override
            public LocalDate fromString(String value) {
                if (value == null || value.isBlank()) {
                    return LocalDate.now().withDayOfMonth(1);
                }
                return monthStartDate(value.trim());
            }
        });
        budgetMonthPicker.setValue(LocalDate.now().withDayOfMonth(1));
    }

    private String selectedBudgetMonth() {
        LocalDate selectedDate = budgetMonthPicker.getValue();
        if (selectedDate == null) {
            selectedDate = LocalDate.now().withDayOfMonth(1);
            budgetMonthPicker.setValue(selectedDate);
        }
        return YearMonth.from(selectedDate).toString();
    }

    private LocalDate monthStartDate(String value) {
        try {
            return YearMonth.parse(value).atDay(1);
        } catch (DateTimeParseException exception) {
            try {
                return LocalDate.parse(value).withDayOfMonth(1);
            } catch (DateTimeParseException ignored) {
                return LocalDate.now().withDayOfMonth(1);
            }
        }
    }

    private YearMonth parseYearMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            return YearMonth.now();
        }
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        try {
            return value == null || value.isBlank() ? fallback : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return fallback;
        }
    }

    private String dateText(DatePicker picker) {
        return picker.getValue() == null ? "" : picker.getValue().toString();
    }

    private double parseRequiredAmount(TextField field, String label) {
        String value = textValue(field).replace(",", "");
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        double amount = Double.parseDouble(value);
        if (amount <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
        return amount;
    }

    private double parseOptionalAmount(TextField field) {
        String value = textValue(field).replace(",", "");
        if (value.isBlank()) {
            return 0;
        }
        double amount = Double.parseDouble(value);
        if (amount < 0) {
            throw new IllegalArgumentException("Amounts cannot be negative.");
        }
        return amount;
    }

    private double safeOptionalAmount(TextField field) {
        try {
            return parseOptionalAmount(field);
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private void validateBudgetDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }
    }

    private boolean isCategoryAllocation(BudgetProgress row) {
        return row != null && row.getCategoryId() != null;
    }

    private String createAffordabilityPreview() {
        double expectedIncome = safeOptionalAmount(expectedIncomeField);
        double plannedSavings = safeOptionalAmount(plannedSavingsField);
        double overallLimit = safeOptionalAmount(overallSpendingLimitField);
        if (expectedIncome <= 0 && plannedSavings <= 0 && overallLimit <= 0) {
            return "Draft plan only. Add expected income, planned savings, or an overall spending limit to see affordability context.";
        }
        double unreserved = expectedIncome - plannedSavings - overallLimit;
        if (expectedIncome <= 0) {
            return "Expected income is not set. Affordability warnings will be limited until income is entered.";
        }
        if (unreserved < 0) {
            return "Planned savings plus overall limit exceed expected income by " + MoneyUtil.mwk(Math.abs(unreserved)) + ".";
        }
        return "Available after savings and overall limit: " + MoneyUtil.mwk(unreserved)
                + ". No cash movement is posted when this draft is saved.";
    }

    private String textValue(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String textValue(TextArea area) {
        return area.getText() == null ? "" : area.getText().trim();
    }

    private String amountForForm(double amount) {
        return amount <= 0 ? "" : String.format(Locale.ENGLISH, "%.2f", amount);
    }

    private String percentText(double value) {
        return String.format(Locale.ENGLISH, "%.1f%%", value);
    }

    private String categoryText(BudgetProgress budget) {
        if (budget.getCategoryId() == null) {
            return "Plan header";
        }
        return budget.getCategoryName() == null || budget.getCategoryName().isBlank()
                ? "Unassigned category"
                : budget.getCategoryName();
    }

    private String statusForForm(String status) {
        if (status == null || status.isBlank()) {
            return "DRAFT";
        }
        return switch (status.trim().toUpperCase(Locale.ENGLISH).replace('_', ' ')) {
            case "ACTIVE", "ON BUDGET", "ON TRACK" -> "ACTIVE";
            case "AT RISK" -> "AT RISK";
            case "EXCEEDED" -> "EXCEEDED";
            case "PAUSED" -> "PAUSED";
            case "CLOSED" -> "CLOSED";
            case "ARCHIVED" -> "ARCHIVED";
            default -> "DRAFT";
        };
    }

    private void updateActionState() {
        updateModeContextLabels();
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private double parseShareWeight() {
        String value = textValue(shareWeightField).replace(",", "");
        if (value.isBlank()) {
            return defaultShareForStatus(presenceStatusBox.getValue());
        }
        return Double.parseDouble(value);
    }

    private String budgetMonthForHouseholdUpdate() {
        return selectedHouseholdMember != null
                && selectedHouseholdMember.isOngoing()
                && "ONGOING".equals(displayDurationScope(durationScopeBox.getValue()))
                ? selectedHouseholdMember.getBudgetMonth()
                : selectedBudgetMonth();
    }

    private void fillHouseholdForm(HouseholdMonthMember member) {
        personNameField.setText(member.getPersonName());
        relationshipField.setText(blank(member.getRelationship(), ""));
        presenceStatusBox.getSelectionModel().select(displayHouseholdStatus(member.getPresenceStatus()));
        durationScopeBox.getSelectionModel().select(displayDurationScope(member.getDurationScope()));
        joinedDatePicker.setValue(parseDate(member.getJoinedDate(), monthStartDate(selectedBudgetMonth())));
        leftDatePicker.setValue(member.getLeftDate() == null || member.getLeftDate().isBlank() ? null : parseDate(member.getLeftDate(), null));
        shareWeightField.setText(String.format(Locale.ENGLISH, "%.2f", member.getShareWeight()));
        householdNotesArea.setText(blank(member.getNotes(), ""));
        setHouseholdFormEditable(!member.isBudgetOwner());
    }

    private String displayHouseholdStatus(String status) {
        if (status == null || status.isBlank()) {
            return "IN HOUSE";
        }
        return switch (status.trim().toUpperCase(Locale.ENGLISH).replace('_', ' ')) {
            case "JOINED" -> "JOINED";
            case "LEFT" -> "LEFT";
            case "AWAY" -> "AWAY";
            default -> "IN HOUSE";
        };
    }

    private String displayDurationScope(String durationScope) {
        if (durationScope == null || durationScope.isBlank()) {
            return "THIS MONTH ONLY";
        }
        String normalized = durationScope.trim().toUpperCase(Locale.ENGLISH).replace('_', ' ');
        return "FOREVER".equals(normalized) || "ONGOING".equals(normalized) ? "ONGOING" : "THIS MONTH ONLY";
    }

    private void setHouseholdFormEditable(boolean editable) {
        personNameField.setDisable(!editable);
        relationshipField.setDisable(!editable);
        presenceStatusBox.setDisable(!editable);
        durationScopeBox.setDisable(!editable);
        joinedDatePicker.setDisable(!editable);
        leftDatePicker.setDisable(!editable);
        shareWeightField.setDisable(!editable);
        householdNotesArea.setDisable(!editable);
    }

    private void updateDefaultShareForStatus(String oldStatus, String status) {
        String currentValue = textValue(shareWeightField);
        String oldDefault = String.format(Locale.ENGLISH, "%.0f", defaultShareForStatus(oldStatus));
        if (!currentValue.isBlank() && !currentValue.equals(oldDefault)) {
            return;
        }
        shareWeightField.setText(String.format(Locale.ENGLISH, "%.0f", defaultShareForStatus(status)));
    }

    private double defaultShareForStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ENGLISH);
        return normalized.equals("LEFT") || normalized.equals("AWAY") ? 0 : 1;
    }

    private String householdUnitsText(double value) {
        if (value <= 0) {
            return "not set";
        }
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ENGLISH, "%.2f", value);
    }

    private record BudgetPlanSummary(
            String budgetName,
            String budgetMonth,
            String budgetType,
            String currency,
            String startDate,
            String endDate,
            String notes,
            double expectedIncome,
            double plannedSavings,
            double overallSpendingLimit,
            double plannedAmount,
            double actualAmount,
            double projectedAmount,
            String status,
            List<BudgetProgress> allocations
    ) {
        static BudgetPlanSummary from(List<BudgetProgress> allocations) {
            BudgetProgress first = allocations.stream()
                    .filter(row -> row.getCategoryId() == null)
                    .findFirst()
                    .orElse(allocations.get(0));
            List<BudgetProgress> categoryAllocations = allocations.stream()
                    .filter(row -> row.getCategoryId() != null)
                    .toList();
            double planned = categoryAllocations.stream().mapToDouble(BudgetProgress::getAmountLimit).sum();
            double actual = categoryAllocations.stream().mapToDouble(BudgetProgress::getSpent).sum();
            double projected = categoryAllocations.stream().mapToDouble(BudgetPlanSummary::projectedSpendForSummary).sum();
            return new BudgetPlanSummary(
                    first.getBudgetName(),
                    first.getBudgetMonth(),
                    first.getBudgetType() == null || first.getBudgetType().isBlank() ? "Monthly" : first.getBudgetType(),
                    first.getCurrency() == null || first.getCurrency().isBlank() ? "MWK" : first.getCurrency(),
                    first.getStartDate(),
                    first.getEndDate(),
                    first.getNotes() == null ? "" : first.getNotes(),
                    first.getExpectedIncome(),
                    first.getPlannedSavings(),
                    first.getOverallSpendingLimit(),
                    planned,
                    actual,
                    projected,
                    resolveStatus(allocations, planned, actual, projected),
                    List.copyOf(categoryAllocations)
            );
        }

        double remainingAmount() {
            return plannedAmount - actualAmount;
        }

        double percentUsed() {
            return plannedAmount <= 0 ? 0 : (actualAmount / plannedAmount) * 100;
        }

        String cashFlowText() {
            if (expectedIncome <= 0) {
                return "Income not set";
            }
            double unallocated = expectedIncome - plannedSavings - plannedAmount;
            return (unallocated < 0 ? "Short by " : "Unallocated ") + MoneyUtil.mwk(Math.abs(unallocated));
        }

        String alertText() {
            List<String> alerts = new ArrayList<>();
            if (expectedIncome > 0 && plannedAmount + plannedSavings > expectedIncome) {
                alerts.add("Planned expenses plus savings exceed expected income by "
                        + MoneyUtil.mwk(plannedAmount + plannedSavings - expectedIncome) + ".");
            }
            if (overallSpendingLimit > 0 && plannedAmount > overallSpendingLimit) {
                alerts.add("Category allocations exceed the overall spending limit by "
                        + MoneyUtil.mwk(plannedAmount - overallSpendingLimit) + ".");
            }
            if (actualAmount > plannedAmount) {
                alerts.add("Actual spending has exceeded the budget by " + MoneyUtil.mwk(actualAmount - plannedAmount) + ".");
            } else if (percentUsed() >= 80) {
                alerts.add("Spending has reached " + String.format(Locale.ENGLISH, "%.1f%%", percentUsed()) + " of the budget.");
            }
            if (projectedAmount > plannedAmount) {
                alerts.add("Projected spending may exceed the budget by " + MoneyUtil.mwk(projectedAmount - plannedAmount) + ".");
            }
            return alerts.isEmpty() ? "No budget alert for the current data." : String.join("\n", alerts);
        }

        private static String resolveStatus(List<BudgetProgress> allocations, double planned, double actual, double projected) {
            String stored = allocations.stream()
                    .filter(row -> row.getCategoryId() == null)
                    .findFirst()
                    .orElse(allocations.get(0))
                    .getPlanStatus();
            if (List.of("Draft", "Paused", "Closed", "Archived").contains(stored)) {
                return stored;
            }
            if (actual > planned) {
                return "Exceeded";
            }
            if (projected > planned || (planned > 0 && actual / planned >= 0.80)) {
                return "At Risk";
            }
            return "Active";
        }

        private static double projectedSpendForSummary(BudgetProgress budget) {
            LocalDate start = parseStaticDate(budget.getStartDate(), staticMonthStart(budget.getBudgetMonth()));
            LocalDate end = parseStaticDate(budget.getEndDate(), YearMonth.parse(budget.getBudgetMonth()).atEndOfMonth());
            LocalDate today = LocalDate.now();
            LocalDate measuredDate = today.isBefore(start) ? start : today.isAfter(end) ? end : today;
            long elapsedDays = Math.max(1, ChronoUnit.DAYS.between(start, measuredDate) + 1);
            long totalDays = Math.max(elapsedDays, ChronoUnit.DAYS.between(start, end) + 1);
            return (budget.getSpent() / elapsedDays) * totalDays;
        }

        private static LocalDate staticMonthStart(String month) {
            try {
                return YearMonth.parse(month).atDay(1);
            } catch (DateTimeParseException exception) {
                return LocalDate.now().withDayOfMonth(1);
            }
        }

        private static LocalDate parseStaticDate(String value, LocalDate fallback) {
            try {
                return value == null || value.isBlank() ? fallback : LocalDate.parse(value);
            } catch (DateTimeParseException exception) {
                return fallback;
            }
        }
    }
}
