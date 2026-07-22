package com.wk.pfmis.controllers;

import com.wk.pfmis.ai.AiRecommendationService;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.models.BudgetProgress;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.HouseholdMonthMember;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class BudgetsController {
    @FXML private TextField budgetNameField;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private DatePicker budgetMonthPicker;
    @FXML private TextField amountLimitField;
    @FXML private ComboBox<String> statusBox;
    @FXML private CheckBox rolloverBox;
    @FXML private TextArea notesArea;
    @FXML private Label summaryLabel;
    @FXML private Label plannedCountLabel;
    @FXML private Label onBudgetCountLabel;
    @FXML private Label fulfilledCountLabel;
    @FXML private Label notMetCountLabel;
    @FXML private TableView<BudgetProgress> budgetTable;
    @FXML private TableColumn<BudgetProgress, String> nameColumn;
    @FXML private TableColumn<BudgetProgress, String> categoryColumn;
    @FXML private TableColumn<BudgetProgress, String> monthColumn;
    @FXML private TableColumn<BudgetProgress, String> limitColumn;
    @FXML private TableColumn<BudgetProgress, String> spentColumn;
    @FXML private TableColumn<BudgetProgress, String> remainingColumn;
    @FXML private TableColumn<BudgetProgress, String> usedColumn;
    @FXML private TableColumn<BudgetProgress, String> peopleColumn;
    @FXML private TableColumn<BudgetProgress, String> limitPerPersonColumn;
    @FXML private TableColumn<BudgetProgress, String> spentPerPersonColumn;
    @FXML private TableColumn<BudgetProgress, String> planStatusColumn;
    @FXML private TableColumn<BudgetProgress, String> monthResultColumn;
    @FXML private TableColumn<BudgetProgress, String> actionColumn;
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
    @FXML private Label recommendationStatusLabel;
    @FXML private TextArea budgetRecommendationArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final AiRecommendationService aiService = new AiRecommendationService();
    private BudgetProgress selectedBudget;
    private HouseholdMonthMember selectedHouseholdMember;

    @FXML
    public void initialize() {
        configureBudgetMonthPicker();
        statusBox.setItems(FXCollections.observableArrayList("PLANNED", "ON BUDGET", "FULFILLED", "NOT MET", "PAUSED", "CLOSED"));
        statusBox.getSelectionModel().select("PLANNED");
        presenceStatusBox.setItems(FXCollections.observableArrayList("IN HOUSE", "JOINED", "LEFT", "AWAY"));
        presenceStatusBox.getSelectionModel().select("IN HOUSE");
        durationScopeBox.setItems(FXCollections.observableArrayList("THIS MONTH ONLY", "ONGOING"));
        durationScopeBox.getSelectionModel().select("THIS MONTH ONLY");
        joinedDatePicker.setValue(monthStartDate(selectedBudgetMonth()));
        categoryBox.setItems(FXCollections.observableArrayList(expenseCategories()));
        budgetTable.setPlaceholder(new Label("No budgets registered for this period."));
        TableActions.configureScrollableTable(budgetTable);
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getBudgetName()));
        categoryColumn.setCellValueFactory(cell -> new SimpleStringProperty(categoryText(cell.getValue())));
        monthColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getBudgetMonth()));
        limitColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountLimit())));
        spentColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getSpent())));
        remainingColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getRemaining())));
        usedColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.format("%.1f%%", cell.getValue().getPercentUsed())));
        peopleColumn.setCellValueFactory(cell -> new SimpleStringProperty(householdUnitsText(cell.getValue().getHouseholdUnits())));
        limitPerPersonColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getLimitPerPerson())));
        spentPerPersonColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getSpentPerPerson())));
        planStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPlanStatus()));
        monthResultColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMonthResult()));
        actionColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getActionNeeded()));
        budgetTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            selectedBudget = selected;
            if (selected != null) {
                fillForm(selected);
            }
        });
        householdTable.setPlaceholder(new Label("The budget owner is added automatically. Add other people for this month or ongoing."));
        householdNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPersonName()));
        householdRelationshipColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getRelationship(), "-")));
        householdStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayHouseholdStatus(cell.getValue().getPresenceStatus())));
        householdScopeColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayDurationScope(cell.getValue().getDurationScope())));
        householdJoinedColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getJoinedDate(), "-")));
        householdLeftColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getLeftDate(), "-")));
        householdShareColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.format(Locale.ENGLISH, "%.2f", cell.getValue().getShareWeight())));
        householdNotesColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getNotes(), "")));
        householdTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            selectedHouseholdMember = selected;
            if (selected != null) {
                fillHouseholdForm(selected);
            } else {
                setHouseholdFormEditable(true);
            }
        });
        presenceStatusBox.valueProperty().addListener((observable, oldValue, selected) -> updateDefaultShareForStatus(oldValue, selected));
        budgetMonthPicker.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selectedHouseholdMember == null) {
                joinedDatePicker.setValue(monthStartDate(selectedBudgetMonth()));
            }
            refresh();
        });
        configureContextMenus();
        refresh();
    }

    @FXML
    private void saveBudget() {
        try {
            database.addBudget(
                    textValue(budgetNameField),
                    selectedCategoryId(),
                    selectedBudgetMonth(),
                    parseAmount(amountLimitField),
                    rolloverBox.isSelected(),
                    statusBox.getValue(),
                    textValue(notesArea)
            );
            clearForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Budget registered.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save budget", exception);
        }
    }

    @FXML
    private void updateBudget() {
        if (selectedBudget == null) {
            UiAlerts.info("Select a budget first.");
            return;
        }
        try {
            database.updateBudget(
                    selectedBudget.getId(),
                    textValue(budgetNameField),
                    selectedCategoryId(),
                    selectedBudgetMonth(),
                    parseAmount(amountLimitField),
                    rolloverBox.isSelected(),
                    statusBox.getValue(),
                    textValue(notesArea)
            );
            clearForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Budget updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update budget", exception);
        }
    }

    @FXML
    private void deleteBudget() {
        if (selectedBudget == null) {
            UiAlerts.info("Select a budget first.");
            return;
        }
        try {
            database.deleteBudget(selectedBudget.getId());
            clearForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Budget deleted.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to delete budget", exception);
        }
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
        householdTable.getSelectionModel().clearSelection();
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

    @FXML
    private void generateBudgetRecommendation() {
        if (recommendationStatusLabel == null || budgetRecommendationArea == null) {
            UiAlerts.info("Open Smart Analysis under Administration for budget recommendations.");
            return;
        }
        String month = selectedBudgetMonth();
        YearMonth currentMonth = YearMonth.parse(month);
        String nextMonth = currentMonth.plusMonths(1).toString();
        List<BudgetProgress> budgets = database.listBudgetProgress(month);
        List<HouseholdMonthMember> household = database.listHouseholdMonthMembers(month);
        double currentUnits = database.householdUnitsForMonth(month);
        double nextUnits = database.householdUnitsForMonth(nextMonth);
        String deterministic = deterministicBudgetRecommendation(month, nextMonth, budgets, household, currentUnits, nextUnits);
        AiSettings settings = database.getAiSettings();
        if (settings == null || !settings.canGenerateRecommendations()) {
            recommendationStatusLabel.setText("Smart Analysis is not ready. Showing rule-based recommendation.");
            budgetRecommendationArea.setText(deterministic);
            return;
        }

        recommendationStatusLabel.setText("Smart Analysis is preparing next-month budget advice...");
        budgetRecommendationArea.setText("Working...");
        String prompt = budgetRecommendationPrompt(month, nextMonth, budgets, household, currentUnits, nextUnits, deterministic);
        CompletableFuture.supplyAsync(() -> aiService.generateGoalRecommendation(settings, prompt))
                .whenComplete((answer, throwable) -> Platform.runLater(() -> {
                    if (throwable == null) {
                        budgetRecommendationArea.setText(answer);
                        recommendationStatusLabel.setText("Completed by " + settings.getDisplayName() + ".");
                        database.recordAiInteraction("Budgets", "Next-month budget recommendation", settings.getDisplayName(), "SUCCESS");
                    } else {
                        budgetRecommendationArea.setText(deterministic + "\n\nSmart Analysis request failed: " + rootMessage(throwable));
                        recommendationStatusLabel.setText("Smart Analysis request failed. Rule-based recommendation is shown.");
                        database.recordAiInteraction("Budgets", "Next-month budget recommendation", settings.getDisplayName(), "FAILED");
                    }
                }));
    }

    @FXML
    private void clearForm() {
        selectedBudget = null;
        budgetTable.getSelectionModel().clearSelection();
        budgetNameField.clear();
        categoryBox.getSelectionModel().clearSelection();
        budgetMonthPicker.setValue(LocalDate.now());
        amountLimitField.clear();
        rolloverBox.setSelected(false);
        notesArea.clear();
        statusBox.getSelectionModel().select("PLANNED");
    }

    @FXML
    private void refresh() {
        String month = selectedBudgetMonth();
        List<BudgetProgress> budgets = database.listBudgetProgress(month);
        List<HouseholdMonthMember> householdMembers = database.listHouseholdMonthMembers(month);
        budgetTable.setItems(FXCollections.observableArrayList(budgets));
        householdTable.setItems(FXCollections.observableArrayList(householdMembers));
        double limit = budgets.stream().mapToDouble(BudgetProgress::getAmountLimit).sum();
        double spent = budgets.stream().mapToDouble(BudgetProgress::getSpent).sum();
        double householdUnits = database.householdUnitsForMonth(month);
        double effectiveUnits = householdUnits > 0 ? householdUnits : 1;
        long ownerCount = householdMembers.stream().filter(HouseholdMonthMember::isBudgetOwner).count();
        long addedPeople = Math.max(0, householdMembers.size() - ownerCount);
        long planned = budgets.stream().filter(budget -> "Planned".equals(budget.getMonthResult())).count();
        long onBudget = budgets.stream().filter(BudgetProgress::isOnBudget).count();
        long fulfilled = budgets.stream().filter(BudgetProgress::isFulfilled).count();
        long notMet = budgets.stream().filter(BudgetProgress::isNotMet).count();
        summaryLabel.setText("Month " + month + ": limit " + MoneyUtil.mwk(limit)
                + ", spent " + MoneyUtil.mwk(spent)
                + ", remaining " + MoneyUtil.mwk(limit - spent)
                + ", household units " + householdUnitsText(householdUnits)
                + ", spent per unit " + MoneyUtil.mwk(spent / effectiveUnits)
                + ". Some budgets may stay on budget, some may be fulfilled, and some may not be met.");
        householdSummaryLabel.setText("Owner is counted automatically. " + addedPeople + " added person record(s) for " + month
                + " | budget units " + householdUnitsText(householdUnits)
                + " | total spent per unit " + MoneyUtil.mwk(spent / effectiveUnits)
                + " | use This Month Only or Ongoing when adding people.");
        plannedCountLabel.setText(String.valueOf(planned));
        onBudgetCountLabel.setText(String.valueOf(onBudget));
        fulfilledCountLabel.setText(String.valueOf(fulfilled));
        notMetCountLabel.setText(String.valueOf(notMet));
    }

    private String deterministicBudgetRecommendation(
            String month,
            String nextMonth,
            List<BudgetProgress> budgets,
            List<HouseholdMonthMember> household,
            double currentUnits,
            double nextUnits
    ) {
        if (budgets.isEmpty()) {
            return "Register monthly budgets for " + month
                    + " before asking for next-month recommendations. Add household members first so PFMIS can calculate per-person cost.";
        }
        double effectiveCurrentUnits = currentUnits > 0 ? currentUnits : 1;
        double forecastUnits = nextUnits > 0 ? nextUnits : effectiveCurrentUnits;
        double totalLimit = budgets.stream().mapToDouble(BudgetProgress::getAmountLimit).sum();
        double totalSpent = budgets.stream().mapToDouble(BudgetProgress::getSpent).sum();
        StringBuilder builder = new StringBuilder();
        builder.append("Rule-based next-month budget recommendation\n");
        builder.append("Current month: ").append(month).append('\n');
        builder.append("Next month: ").append(nextMonth).append('\n');
        builder.append("Household records: ").append(household.size())
                .append(" | current units: ").append(householdUnitsText(currentUnits))
                .append(" | forecast units: ").append(householdUnitsText(forecastUnits)).append('\n');
        builder.append("Total limit: ").append(MoneyUtil.mwk(totalLimit))
                .append(" | total spent: ").append(MoneyUtil.mwk(totalSpent))
                .append(" | spent per unit: ").append(MoneyUtil.mwk(totalSpent / effectiveCurrentUnits)).append("\n\n");

        for (BudgetProgress budget : budgets) {
            double perUnitSpend = budget.getSpent() / effectiveCurrentUnits;
            double recommendedLimit = Math.max(perUnitSpend * forecastUnits, budget.getAmountLimit());
            if (budget.getSpent() <= budget.getAmountLimit() * 0.75 && budget.getAmountLimit() > 0) {
                recommendedLimit = Math.max(budget.getSpent() * 1.10, budget.getAmountLimit() * 0.85);
            }
            builder.append("- ").append(budget.getBudgetName())
                    .append(": ").append(budget.getMonthResult())
                    .append(". Current limit ").append(MoneyUtil.mwk(budget.getAmountLimit()))
                    .append(", spent ").append(MoneyUtil.mwk(budget.getSpent()))
                    .append(", spent per unit ").append(MoneyUtil.mwk(perUnitSpend))
                    .append(". Suggested next limit: ").append(MoneyUtil.mwk(recommendedLimit)).append(". ");
            if (budget.isNotMet()) {
                builder.append("Review causes before increasing the budget.");
            } else if (budget.isFulfilled()) {
                builder.append("Keep evidence and decide whether unused money should roll forward.");
            } else {
                builder.append("Keep tracking until month close.");
            }
            builder.append('\n');
        }
        if (currentUnits <= 0) {
            builder.append("\nHousehold warning: no budget units are registered, so per-person figures assume one unit.");
        }
        if (nextUnits <= 0) {
            builder.append("\nNext-month warning: no roster is registered for ").append(nextMonth)
                    .append(", so forecast uses the current month household units.");
        }
        return builder.toString();
    }

    private String budgetRecommendationPrompt(
            String month,
            String nextMonth,
            List<BudgetProgress> budgets,
            List<HouseholdMonthMember> household,
            double currentUnits,
            double nextUnits,
            String ruleBasedRecommendation
    ) {
        return """
                PFMIS BUDGET RECOMMENDATION REQUEST
                Current budget month: %s
                Next month: %s
                Household units this month: %s
                Household units registered for next month: %s

                HOUSEHOLD RECORDS
                %s

                BUDGETS
                %s

                RULE-BASED BASELINE
                %s

                TASK
                Recommend practical next-month budgets and actions. Explain whether spending is a true picture after considering household size.
                Use only the figures supplied above. Do not invent income, balances, prices, or exchange rates.
                Include: SUMMARY, HOUSEHOLD IMPACT, NEXT MONTH LIMITS, ACTIONS.
                """.formatted(
                month,
                nextMonth,
                householdUnitsText(currentUnits),
                nextUnits > 0 ? householdUnitsText(nextUnits) : "not registered",
                householdLines(household),
                budgetLines(budgets),
                ruleBasedRecommendation
        );
    }

    private String householdLines(List<HouseholdMonthMember> household) {
        if (household.isEmpty()) {
            return "- No household members registered for this month.";
        }
        StringBuilder builder = new StringBuilder();
        for (HouseholdMonthMember member : household) {
            builder.append("- ").append(member.getPersonName())
                    .append(" | relationship=").append(blank(member.getRelationship(), "not set"))
                    .append(" | status=").append(displayHouseholdStatus(member.getPresenceStatus()))
                    .append(" | duration=").append(displayDurationScope(member.getDurationScope()))
                    .append(" | share=").append(String.format(Locale.ENGLISH, "%.2f", member.getShareWeight()))
                    .append(" | joined=").append(blank(member.getJoinedDate(), "not set"))
                    .append(" | left=").append(blank(member.getLeftDate(), "not set"))
                    .append('\n');
        }
        return builder.toString();
    }

    private String budgetLines(List<BudgetProgress> budgets) {
        if (budgets.isEmpty()) {
            return "- No budgets registered.";
        }
        StringBuilder builder = new StringBuilder();
        for (BudgetProgress budget : budgets) {
            builder.append("- ").append(budget.getBudgetName())
                    .append(" | category=").append(categoryText(budget))
                    .append(" | limit=").append(budget.getAmountLimit())
                    .append(" | spent=").append(budget.getSpent())
                    .append(" | remaining=").append(budget.getRemaining())
                    .append(" | percent=").append(String.format(Locale.ENGLISH, "%.1f", budget.getPercentUsed()))
                    .append(" | result=").append(budget.getMonthResult())
                    .append('\n');
        }
        return builder.toString();
    }

    private void configureContextMenus() {
        TableActions.installRowContextMenu(budgetTable, this::budgetMenuItems);
        TableActions.installRowContextMenu(householdTable, this::householdMenuItems);
    }

    private List<javafx.scene.control.MenuItem> budgetMenuItems(BudgetProgress budget) {
        return List.of(
                TableActions.menuItem("Edit Budget", () -> editBudgetRow(budget)),
                TableActions.menuItem("Delete Budget", () -> deleteBudgetRow(budget)),
                TableActions.menuItem("Budget Recommendation", this::generateBudgetRecommendation),
                TableActions.separator(),
                TableActions.copyRowItem(budgetTable, budget),
                TableActions.exportTableItem(budgetTable, selectedBudgetMonth() + " Budgets"),
                TableActions.printTableItem(budgetTable, selectedBudgetMonth() + " Budgets"),
                TableActions.refreshItem(this::refresh)
        );
    }

    private List<javafx.scene.control.MenuItem> householdMenuItems(HouseholdMonthMember member) {
        List<javafx.scene.control.MenuItem> items = new ArrayList<>();
        items.add(TableActions.menuItem(member != null && member.isBudgetOwner() ? "View Budget Owner" : "Edit Household Member",
                () -> editHouseholdRow(member)));
        if (member == null || !member.isBudgetOwner()) {
            items.add(TableActions.menuItem("Remove Household Member", () -> deleteHouseholdRow(member)));
        }
        items.add(TableActions.separator());
        items.add(TableActions.copyRowItem(householdTable, member));
        items.add(TableActions.exportTableItem(householdTable, selectedBudgetMonth() + " Household Budget Members"));
        items.add(TableActions.printTableItem(householdTable, selectedBudgetMonth() + " Household Budget Members"));
        items.add(TableActions.refreshItem(this::refresh));
        return items;
    }

    private void editBudgetRow(BudgetProgress budget) {
        if (budget == null) {
            return;
        }
        selectedBudget = budget;
        fillForm(budget);
    }

    private void deleteBudgetRow(BudgetProgress budget) {
        if (budget == null) {
            return;
        }
        selectedBudget = budget;
        deleteBudget();
    }

    private void editHouseholdRow(HouseholdMonthMember member) {
        if (member == null) {
            return;
        }
        selectedHouseholdMember = member;
        fillHouseholdForm(member);
    }

    private void deleteHouseholdRow(HouseholdMonthMember member) {
        if (member == null) {
            return;
        }
        selectedHouseholdMember = member;
        deleteHouseholdMember();
    }

    private List<Category> expenseCategories() {
        return database.listCategories().stream()
                .filter(category -> "EXPENSE".equals(category.getCategoryType()) || "BOTH".equals(category.getCategoryType()))
                .toList();
    }

    private void fillForm(BudgetProgress budget) {
        budgetNameField.setText(budget.getBudgetName());
        budgetMonthPicker.setValue(monthStartDate(budget.getBudgetMonth()));
        amountLimitField.setText(String.format("%.2f", budget.getAmountLimit()));
        rolloverBox.setSelected(budget.isRollover());
        notesArea.setText(budget.getNotes() == null ? "" : budget.getNotes());
        statusBox.getSelectionModel().select(displayStatusForForm(budget.getStatus()));
        categoryBox.getSelectionModel().clearSelection();
        if (budget.getCategoryId() != null) {
            categoryBox.getItems().stream()
                    .filter(category -> category.getId() == budget.getCategoryId())
                    .findFirst()
                    .ifPresent(category -> categoryBox.getSelectionModel().select(category));
        }
    }

    private void fillHouseholdForm(HouseholdMonthMember member) {
        personNameField.setText(member.getPersonName());
        relationshipField.setText(member.getRelationship() == null ? "" : member.getRelationship());
        presenceStatusBox.getSelectionModel().select(displayHouseholdStatus(member.getPresenceStatus()));
        durationScopeBox.getSelectionModel().select(displayDurationScope(member.getDurationScope()));
        joinedDatePicker.setValue(parseDate(member.getJoinedDate()));
        leftDatePicker.setValue(parseDate(member.getLeftDate()));
        shareWeightField.setText(String.format(Locale.ENGLISH, "%.2f", member.getShareWeight()));
        householdNotesArea.setText(member.getNotes() == null ? "" : member.getNotes());
        setHouseholdFormEditable(!member.isBudgetOwner());
    }

    private Integer selectedCategoryId() {
        Category category = categoryBox.getValue();
        return category == null ? null : category.getId();
    }

    private double parseAmount(TextField field) {
        String value = textValue(field).replace(",", "");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Budget limit is required.");
        }
        return Double.parseDouble(value);
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

    private void configureBudgetMonthPicker() {
        budgetMonthPicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : YearMonth.from(date).toString();
            }

            @Override
            public LocalDate fromString(String value) {
                if (value == null || value.isBlank()) {
                    return LocalDate.now();
                }
                return monthStartDate(value.trim());
            }
        });
        budgetMonthPicker.setValue(LocalDate.now());
    }

    private String selectedBudgetMonth() {
        LocalDate selectedDate = budgetMonthPicker.getValue();
        if (selectedDate == null) {
            selectedDate = LocalDate.now();
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

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String textValue(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String textValue(TextArea area) {
        return area.getText() == null ? "" : area.getText().trim();
    }

    private String categoryText(BudgetProgress budget) {
        return budget.getCategoryName() == null || budget.getCategoryName().isBlank()
                ? "All expense categories"
                : budget.getCategoryName();
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
        if (shareWeightField == null) {
            return;
        }
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

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private String displayStatusForForm(String status) {
        if (status == null || status.isBlank()) {
            return "PLANNED";
        }
        return switch (status.trim().toUpperCase().replace('_', ' ')) {
            case "ACTIVE", "ON BUDGET" -> "ON BUDGET";
            case "FULFILLED" -> "FULFILLED";
            case "NOT MET" -> "NOT MET";
            case "PAUSED" -> "PAUSED";
            case "CLOSED" -> "CLOSED";
            default -> "PLANNED";
        };
    }
}
