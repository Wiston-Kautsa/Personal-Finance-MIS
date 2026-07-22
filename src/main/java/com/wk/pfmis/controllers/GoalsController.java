package com.wk.pfmis.controllers;

import com.wk.pfmis.ai.AiRecommendationService;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.GoalStep;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GoalsController {
    @FXML private Label monthSpentLabel;
    @FXML private Label averageSpentLabel;
    @FXML private Label topSpendingLabel;
    @FXML private Label goalGapLabel;
    @FXML private TitledPane goalFormPane;
    @FXML private TextField goalNameField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextField targetAmountField;
    @FXML private TextField currentAmountField;
    @FXML private Label availableAccountBalanceLabel;
    @FXML private TextField monthlyContributionField;
    @FXML private DatePicker targetDatePicker;
    @FXML private Button registerGoalStepsButton;
    @FXML private TitledPane goalStepsPane;
    @FXML private ComboBox<Goal> stepGoalBox;
    @FXML private TextField stepNameField;
    @FXML private TextField stepEstimatedCostField;
    @FXML private TextField stepAmountReachedField;
    @FXML private ComboBox<String> stepStatusBox;
    @FXML private DatePicker stepTargetDatePicker;
    @FXML private TextArea stepDescriptionArea;
    @FXML private Label stepSummaryLabel;
    @FXML private Label stepAiStatusLabel;
    @FXML private Label stepAiRecommendationLabel;
    @FXML private TableView<GoalStep> goalStepsTable;
    @FXML private TableColumn<GoalStep, String> stepNameColumn;
    @FXML private TableColumn<GoalStep, String> stepCostColumn;
    @FXML private TableColumn<GoalStep, String> stepReachedColumn;
    @FXML private TableColumn<GoalStep, String> stepMissingColumn;
    @FXML private TableColumn<GoalStep, String> stepProgressColumn;
    @FXML private TableColumn<GoalStep, String> stepTargetDateColumn;
    @FXML private TableColumn<GoalStep, String> stepStatusColumn;
    @FXML private Label planSummaryLabel;
    @FXML private Label spendingInsightLabel;
    @FXML private Label categoryInsightLabel;
    @FXML private Label adviceLabel;
    @FXML private Label aiStatusLabel;
    @FXML private Label aiRecommendationLabel;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final AiRecommendationService aiService = new AiRecommendationService();
    private SpendingSnapshot spendingSnapshot = SpendingSnapshot.empty();
    private String lastAccountBalanceDefault = "";

    @FXML
    public void initialize() {
        if (statusBox != null) {
            statusBox.setItems(FXCollections.observableArrayList("ACTIVE", "COMPLETED", "PAUSED", "CANCELLED"));
            statusBox.getSelectionModel().select("ACTIVE");
        }
        if (stepStatusBox != null) {
            stepStatusBox.setItems(FXCollections.observableArrayList("NEEDED", "IN PROGRESS", "REACHED", "SKIPPED"));
            stepStatusBox.getSelectionModel().select("NEEDED");
        }
        if (goalStepsTable != null) {
            configureGoalStepsTable();
            goalStepsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> applySelectedStep(newValue));
        }
        if (hasGoalInsightFields()) {
            targetAmountField.textProperty().addListener((observable, oldValue, newValue) -> refreshGoalInsight());
            currentAmountField.textProperty().addListener((observable, oldValue, newValue) -> refreshGoalInsight());
            monthlyContributionField.textProperty().addListener((observable, oldValue, newValue) -> refreshGoalInsight());
            targetDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> refreshGoalInsight());
        }
        if (stepGoalBox != null) {
            stepGoalBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshGoalSteps());
        }
        if (registerGoalStepsButton != null) {
            registerGoalStepsButton.setDisable(true);
        }
        DataRefreshBus.addListener(this::refreshGoalChoices);
        refresh();
        refreshAiStatus();
        refreshStepAiStatus();
    }

    @FXML
    private void saveGoal() {
        try {
            String name = textValue(goalNameField);
            if (name.isEmpty()) {
                UiAlerts.info("Enter a goal name.");
                return;
            }
            double targetAmount = requiredPositiveAmount(targetAmountField.getText(), "Enter a target amount greater than zero.");
            double currentAmount = parseOptionalAmount(currentAmountField.getText());
            double monthlyContribution = parseOptionalAmount(monthlyContributionField.getText());
            String targetDate = targetDatePicker.getValue() == null ? null : targetDatePicker.getValue().toString();
            String status = goalStatus(targetAmount, currentAmount);

            database.addGoal(name, targetAmount, currentAmount, monthlyContribution, targetDate, status);
            clearForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Goal registered.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to register goal", exception);
        }
    }

    @FXML
    private void clearForm() {
        goalFormPane.setText("Register Goal");
        goalNameField.clear();
        targetAmountField.clear();
        monthlyContributionField.clear();
        targetDatePicker.setValue(null);
        statusBox.getSelectionModel().select("ACTIVE");
        currentAmountField.clear();
        applyAvailableAccountDefault();
        refreshGoalInsight();
    }

    @FXML
    private void refresh() {
        spendingSnapshot = buildSpendingSnapshot();
        refreshGoalChoices();
        applyAvailableAccountDefault();
        refreshSpendingSummary();
        refreshGoalInsight();
        refreshAiStatus();
    }

    @FXML
    private void generateAiRecommendation() {
        if (aiStatusLabel == null || aiRecommendationLabel == null) {
            UiAlerts.info("Open Smart Analysis under Administration for goal recommendations.");
            return;
        }
        AiSettings settings = database.getAiSettings();
        if (settings == null || !settings.canGenerateRecommendations()) {
            aiStatusLabel.setText("Smart Analysis is not ready.");
            aiRecommendationLabel.setText("Open Smart Analysis Settings, configure a provider, then enable recommendations.");
            return;
        }
        aiStatusLabel.setText("Smart Analysis is reviewing this goal...");
        aiRecommendationLabel.setText("Generating recommendation...");
        planSummaryLabel.setText("Preparing the achievement plan...");
        spendingInsightLabel.setText("Reviewing saved spending records...");
        categoryInsightLabel.setText("Finding where to adjust...");
        adviceLabel.setText("Preparing advice...");
        String prompt = buildAiGoalPrompt(settings);
        CompletableFuture.supplyAsync(() -> aiService.generateGoalRecommendation(settings, prompt))
                .whenComplete((recommendation, throwable) -> Platform.runLater(() -> {
                    if (throwable == null) {
                        aiStatusLabel.setText("Smart Analysis recommendation from " + settings.getDisplayName());
                        applyAiGoalInsight(recommendation);
                    } else {
                        aiStatusLabel.setText("Smart Analysis recommendation failed.");
                        aiRecommendationLabel.setText(rootMessage(throwable));
                        refreshSpendingSummary();
                        refreshGoalInsight();
                    }
                }));
    }

    @FXML
    private void showGoalStepsPanel() {
        goalStepsPane.setExpanded(true);
        if (stepGoalBox.getItems().isEmpty()) {
            UiAlerts.info("Register a goal first, then add the steps needed to reach it.");
            return;
        }
        if (stepGoalBox.getValue() == null) {
            stepGoalBox.getSelectionModel().selectFirst();
        }
    }

    @FXML
    private void saveGoalStep() {
        try {
            Goal goal = stepGoalBox.getValue();
            if (goal == null) {
                UiAlerts.info("Select a goal first.");
                return;
            }
            String stepName = textValue(stepNameField);
            if (stepName.isEmpty()) {
                UiAlerts.info("Enter the goal step name.");
                return;
            }
            double estimatedCost = requiredPositiveAmount(stepEstimatedCostField.getText(), "Enter the price needed for this step.");
            double amountReached = parseOptionalAmount(stepAmountReachedField.getText());
            validateStepProgress(estimatedCost, amountReached);
            database.addGoalStep(
                    goal.getId(),
                    stepName,
                    textAreaValue(stepDescriptionArea),
                    estimatedCost,
                    amountReached,
                    stepTargetDatePicker.getValue() == null ? null : stepTargetDatePicker.getValue().toString(),
                    stepStatus(estimatedCost, amountReached)
            );
            clearStepForm();
            refreshGoalSteps();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Goal step registered.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to register goal step", exception);
        }
    }

    @FXML
    private void updateGoalStep() {
        try {
            GoalStep selectedStep = goalStepsTable.getSelectionModel().getSelectedItem();
            if (selectedStep == null) {
                UiAlerts.info("Select a goal step first.");
                return;
            }
            String stepName = textValue(stepNameField);
            if (stepName.isEmpty()) {
                UiAlerts.info("Enter the goal step name.");
                return;
            }
            double estimatedCost = requiredPositiveAmount(stepEstimatedCostField.getText(), "Enter the price needed for this step.");
            double amountReached = parseOptionalAmount(stepAmountReachedField.getText());
            validateStepProgress(estimatedCost, amountReached);
            database.updateGoalStep(
                    selectedStep.getId(),
                    stepName,
                    textAreaValue(stepDescriptionArea),
                    estimatedCost,
                    amountReached,
                    stepTargetDatePicker.getValue() == null ? null : stepTargetDatePicker.getValue().toString(),
                    stepStatus(estimatedCost, amountReached)
            );
            refreshGoalSteps(selectedStep.getId());
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Goal step updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update goal step", exception);
        }
    }

    @FXML
    private void clearStepForm() {
        stepNameField.clear();
        stepEstimatedCostField.clear();
        stepAmountReachedField.clear();
        stepDescriptionArea.clear();
        stepStatusBox.getSelectionModel().select("NEEDED");
        Goal goal = stepGoalBox.getValue();
        stepTargetDatePicker.setValue(goal == null ? null : parseDate(goal.getTargetDate()));
        goalStepsTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void generateAiGoalSteps() {
        if (stepGoalBox == null) {
            UiAlerts.info("Open Goal Steps to select a goal, or use Smart Analysis under Administration.");
            return;
        }
        Goal goal = stepGoalBox.getValue();
        if (goal == null) {
            UiAlerts.info("Select or register a goal first.");
            return;
        }
        if (stepAiStatusLabel == null || stepAiRecommendationLabel == null) {
            UiAlerts.info("Open Smart Analysis under Administration for step suggestions.");
            return;
        }
        AiSettings settings = database.getAiSettings();
        if (settings == null || !settings.canGenerateRecommendations()) {
            stepAiStatusLabel.setText("Smart Analysis is not ready.");
            stepAiRecommendationLabel.setText("Open Smart Analysis Settings, configure a provider, then enable recommendations.");
            return;
        }
        stepAiStatusLabel.setText("Smart Analysis is preparing goal steps...");
        stepAiRecommendationLabel.setText("Generating step suggestions for " + goal.getGoalName() + "...");
        List<GoalStep> currentSteps = database.listGoalSteps(goal.getId());
        String prompt = buildAiStepPrompt(settings, goal, currentSteps);
        CompletableFuture.supplyAsync(() -> aiService.generateGoalRecommendation(settings, prompt))
                .whenComplete((response, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        stepAiStatusLabel.setText("Step suggestion failed.");
                        stepAiRecommendationLabel.setText(rootMessage(throwable));
                        return;
                    }
                    List<SuggestedGoalStep> suggestions = parseAiStepSuggestions(response);
                    if (suggestions.isEmpty()) {
                        stepAiStatusLabel.setText("Smart Analysis returned advice but no recordable steps.");
                        stepAiRecommendationLabel.setText(response);
                        return;
                    }
                    int addedCount = saveAiStepSuggestions(goal, suggestions);
                    refreshGoalSteps();
                    stepAiStatusLabel.setText("Step suggestions saved.");
                    stepAiRecommendationLabel.setText(addedCount == 0
                            ? "Suggested steps matched steps already recorded. No duplicate steps were added."
                            : "Added " + addedCount + " suggested step(s). Review the prices and update amount reached as you progress.");
                    DataRefreshBus.notifyDataChanged();
                }));
    }

    private void configureGoalStepsTable() {
        goalStepsTable.setPlaceholder(new Label("No steps registered for this goal yet."));
        stepNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStepName()));
        stepCostColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getEstimatedCost())));
        stepReachedColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountReached())));
        stepMissingColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getMissingAmount())));
        stepProgressColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.format(Locale.ENGLISH, "%.1f%%", cell.getValue().getProgressPercent())));
        stepTargetDateColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getTargetDate())));
        stepStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getStatus())));
        TableActions.installRowContextMenu(goalStepsTable, this::goalStepMenuItems);
    }

    private List<javafx.scene.control.MenuItem> goalStepMenuItems(GoalStep step) {
        List<javafx.scene.control.MenuItem> items = new ArrayList<>();
        items.add(TableActions.menuItem("View Step Details", () -> viewGoalStepDetails(step)));
        items.add(TableActions.menuItem("Edit Goal Step", () -> editGoalStepRow(step)));
        for (String status : List.of("NEEDED", "IN PROGRESS", "REACHED", "SKIPPED")) {
            if (!status.equals(normalizedStepStatus(step.getStatus()))) {
                items.add(TableActions.menuItem("Mark " + status, () -> updateGoalStepStatus(step, status)));
            }
        }
        items.add(TableActions.menuItem("Generate AI Goal Steps", this::generateAiGoalSteps));
        items.add(TableActions.separator());
        items.add(TableActions.copyRowItem(goalStepsTable, step));
        items.add(TableActions.exportTableItem(goalStepsTable, selectedGoalStepsTitle()));
        items.add(TableActions.printTableItem(goalStepsTable, selectedGoalStepsTitle()));
        items.add(TableActions.refreshItem(this::refreshGoalSteps));
        return items;
    }

    private void viewGoalStepDetails(GoalStep step) {
        if (step == null) {
            return;
        }
        UiAlerts.info(
                "Goal: " + blankToDash(step.getGoalName())
                        + "\nStep: " + step.getStepName()
                        + "\nEstimated Cost: " + MoneyUtil.mwk(step.getEstimatedCost())
                        + "\nAmount Reached: " + MoneyUtil.mwk(step.getAmountReached())
                        + "\nMissing: " + MoneyUtil.mwk(step.getMissingAmount())
                        + "\nProgress: " + String.format(Locale.ENGLISH, "%.1f%%", step.getProgressPercent())
                        + "\nTarget Date: " + blankToDash(step.getTargetDate())
                        + "\nStatus: " + blankToDash(step.getStatus())
                        + "\nDescription: " + blankToDash(step.getDescription())
        );
    }

    private void editGoalStepRow(GoalStep step) {
        if (step == null) {
            return;
        }
        goalStepsTable.getSelectionModel().select(step);
        applySelectedStep(step);
    }

    private void updateGoalStepStatus(GoalStep step, String status) {
        if (step == null) {
            return;
        }
        try {
            database.updateGoalStep(
                    step.getId(),
                    step.getStepName(),
                    step.getDescription(),
                    step.getEstimatedCost(),
                    step.getAmountReached(),
                    step.getTargetDate(),
                    status
            );
            refreshGoalSteps(step.getId());
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Goal step marked " + status + ".");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update goal step", exception);
        }
    }

    private String selectedGoalStepsTitle() {
        Goal goal = stepGoalBox == null ? null : stepGoalBox.getValue();
        return goal == null ? "Goal Steps" : goal.getGoalName() + " Goal Steps";
    }

    private void refreshGoalChoices() {
        List<Goal> goals = database.listGoals();
        if (registerGoalStepsButton != null) {
            registerGoalStepsButton.setDisable(goals.isEmpty());
        }
        if (stepGoalBox == null) {
            return;
        }
        int selectedGoalId = selectedStepGoalId();
        stepGoalBox.setItems(FXCollections.observableArrayList(goals));
        selectStepGoal(selectedGoalId);
        if (stepGoalBox.getValue() == null && !goals.isEmpty()) {
            stepGoalBox.getSelectionModel().selectFirst();
        }
        refreshGoalSteps();
    }

    private int selectedStepGoalId() {
        if (stepGoalBox == null) {
            return -1;
        }
        Goal goal = stepGoalBox.getValue();
        return goal == null ? -1 : goal.getId();
    }

    private void selectStepGoal(int goalId) {
        if (stepGoalBox == null || goalId < 0) {
            return;
        }
        stepGoalBox.getItems().stream()
                .filter(goal -> goal.getId() == goalId)
                .findFirst()
                .ifPresent(goal -> stepGoalBox.getSelectionModel().select(goal));
    }

    private void refreshGoalSteps() {
        if (stepGoalBox == null || goalStepsTable == null) {
            return;
        }
        int selectedStepId = selectedGoalStepId();
        refreshGoalSteps(selectedStepId);
    }

    private void refreshGoalSteps(int selectedStepId) {
        if (stepGoalBox == null || goalStepsTable == null) {
            return;
        }
        Goal goal = stepGoalBox.getValue();
        if (goal == null) {
            goalStepsTable.setItems(FXCollections.observableArrayList());
            stepSummaryLabel.setText("Register or select a goal to start adding steps.");
            clearStepForm();
            return;
        }
        List<GoalStep> steps = database.listGoalSteps(goal.getId());
        goalStepsTable.setItems(FXCollections.observableArrayList(steps));
        restoreStepSelection(selectedStepId);
        stepSummaryLabel.setText(goalStepSummary(goal, steps));
        if (goalStepsTable.getSelectionModel().getSelectedItem() == null) {
            stepTargetDatePicker.setValue(parseDate(goal.getTargetDate()));
        }
    }

    private int selectedGoalStepId() {
        GoalStep step = goalStepsTable.getSelectionModel().getSelectedItem();
        return step == null ? -1 : step.getId();
    }

    private void restoreStepSelection(int stepId) {
        if (stepId < 0) {
            return;
        }
        goalStepsTable.getItems().stream()
                .filter(step -> step.getId() == stepId)
                .findFirst()
                .ifPresent(step -> goalStepsTable.getSelectionModel().select(step));
    }

    private void applySelectedStep(GoalStep step) {
        if (step == null) {
            return;
        }
        stepNameField.setText(step.getStepName());
        stepEstimatedCostField.setText(amountText(step.getEstimatedCost()));
        stepAmountReachedField.setText(amountText(step.getAmountReached()));
        stepDescriptionArea.setText(step.getDescription() == null ? "" : step.getDescription());
        stepTargetDatePicker.setValue(parseDate(step.getTargetDate()));
        stepStatusBox.getSelectionModel().select(step.getStatus());
    }

    private String goalStepSummary(Goal goal, List<GoalStep> steps) {
        if (steps.isEmpty()) {
            return "No steps yet for " + goal.getGoalName() + ". Add the requirements and prices needed to achieve this goal.";
        }
        double totalNeeded = steps.stream().mapToDouble(GoalStep::getEstimatedCost).sum();
        double totalReached = steps.stream().mapToDouble(GoalStep::getAmountReached).sum();
        double missing = steps.stream().mapToDouble(GoalStep::getMissingAmount).sum();
        long reachedSteps = steps.stream().filter(step -> "REACHED".equals(normalizedStepStatus(step.getStatus()))).count();
        return steps.size() + " step(s). Needed: " + MoneyUtil.mwk(totalNeeded)
                + ". Reached: " + MoneyUtil.mwk(totalReached)
                + ". Missing: " + MoneyUtil.mwk(missing)
                + ". Completed steps: " + reachedSteps + "/" + steps.size() + ".";
    }

    private void validateStepProgress(double estimatedCost, double amountReached) {
        if (amountReached > estimatedCost) {
            throw new IllegalArgumentException("Amount reached cannot be greater than the step price needed.");
        }
    }

    private String stepStatus(double estimatedCost, double amountReached) {
        String selectedStatus = normalizedStepStatus(stepStatusBox.getValue());
        if (amountReached >= estimatedCost) {
            return "REACHED";
        }
        if ("NEEDED".equals(selectedStatus) && amountReached > 0) {
            return "IN PROGRESS";
        }
        return selectedStatus;
    }

    private String normalizedStepStatus(String status) {
        if (status == null || status.isBlank()) {
            return "NEEDED";
        }
        return status.trim().toUpperCase(Locale.ENGLISH);
    }

    private String buildAiStepPrompt(AiSettings settings, Goal goal, List<GoalStep> currentSteps) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("PFMIS goal-step request\n");
        prompt.append("Assistant name: ").append(settings.getDisplayName()).append('\n');
        prompt.append("Goal name: ").append(goal.getGoalName()).append('\n');
        prompt.append("Target amount: ").append(MoneyUtil.mwk(goal.getTargetAmount())).append('\n');
        prompt.append("Already saved: ").append(MoneyUtil.mwk(goal.getCurrentAmount())).append('\n');
        prompt.append("Monthly contribution: ").append(MoneyUtil.mwk(goal.getMonthlyContribution())).append('\n');
        prompt.append("Target date: ").append(goal.getTargetDate() == null || goal.getTargetDate().isBlank() ? "Not set" : goal.getTargetDate()).append('\n');
        prompt.append("Existing steps:\n");
        if (currentSteps.isEmpty()) {
            prompt.append("- None recorded yet.\n");
        } else {
            for (GoalStep step : currentSteps) {
                prompt.append("- ")
                        .append(step.getStepName())
                        .append(": needed ")
                        .append(MoneyUtil.mwk(step.getEstimatedCost()))
                        .append(", reached ")
                        .append(MoneyUtil.mwk(step.getAmountReached()))
                        .append(", status ")
                        .append(step.getStatus())
                        .append('\n');
            }
        }
        prompt.append("Suggest practical goal steps that help the user know what is missing or needed before this goal becomes a project.\n");
        prompt.append("Do not repeat existing steps. Keep total suggested step prices realistic for the target amount.\n");
        prompt.append("Return only lines in this exact format:\n");
        prompt.append("STEP: step name | numeric MWK price | short note\n");
        return prompt.toString();
    }

    private List<SuggestedGoalStep> parseAiStepSuggestions(String response) {
        List<SuggestedGoalStep> suggestions = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return suggestions;
        }
        for (String line : response.split("\\R")) {
            String cleanLine = line == null ? "" : line.trim();
            if (cleanLine.isBlank()) {
                continue;
            }
            cleanLine = cleanLine.replace("**", "");
            cleanLine = cleanLine.replaceFirst("^[\\-\\*\\d\\.\\)\\s]+", "");
            String upperLine = cleanLine.toUpperCase(Locale.ENGLISH);
            if (upperLine.startsWith("STEP:")) {
                cleanLine = cleanLine.substring(5).trim();
            } else if (upperLine.startsWith("STEP ")) {
                cleanLine = cleanLine.substring(5).replaceFirst("^[\\-:\\.\\)\\s]+", "").trim();
            } else if (!cleanLine.contains("|")) {
                continue;
            }
            String[] parts = cleanLine.split("\\|");
            if (parts.length < 2) {
                continue;
            }
            String stepName = parts[0].trim();
            double amount = amountFromText(parts[1]);
            String note = parts.length > 2 ? parts[2].trim() : "";
            if (!stepName.isBlank() && amount > 0) {
                suggestions.add(new SuggestedGoalStep(stepName, amount, note));
            }
        }
        return suggestions;
    }

    private int saveAiStepSuggestions(Goal goal, List<SuggestedGoalStep> suggestions) {
        Set<String> existingNames = new LinkedHashSet<>();
        for (GoalStep step : database.listGoalSteps(goal.getId())) {
            existingNames.add(normalizedStepName(step.getStepName()));
        }
        int addedCount = 0;
        for (SuggestedGoalStep suggestion : suggestions) {
            String normalizedName = normalizedStepName(suggestion.name());
            if (normalizedName.isBlank() || existingNames.contains(normalizedName)) {
                continue;
            }
            database.addGoalStep(
                    goal.getId(),
                    suggestion.name(),
                    suggestion.note(),
                    suggestion.estimatedCost(),
                    0,
                    goal.getTargetDate(),
                    "NEEDED"
            );
            existingNames.add(normalizedName);
            addedCount++;
        }
        return addedCount;
    }

    private String normalizedStepName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private void refreshSpendingSummary() {
        if (!hasGoalSummaryFields()) {
            return;
        }
        monthSpentLabel.setText(MoneyUtil.mwk(spendingSnapshot.currentMonthSpent()));
        averageSpentLabel.setText(MoneyUtil.mwk(spendingSnapshot.averageMonthlySpent()));
        topSpendingLabel.setText(spendingSnapshot.topCategoryText());
        spendingInsightLabel.setText(
                "The system has recorded " + MoneyUtil.mwk(spendingSnapshot.currentMonthSpent())
                        + " in expenses this month. The recent monthly average is "
                        + MoneyUtil.mwk(spendingSnapshot.averageMonthlySpent()) + "."
        );
        categoryInsightLabel.setText(spendingSnapshot.topCategoryAdvice());
    }

    private void refreshGoalInsight() {
        if (!hasGoalInsightFields()) {
            return;
        }
        double targetAmount = parseInsightAmount(targetAmountField.getText());
        double currentAmount = parseInsightAmount(currentAmountField.getText());
        double monthlyContribution = parseInsightAmount(monthlyContributionField.getText());
        double remaining = Math.max(0, targetAmount - currentAmount);
        goalGapLabel.setText(MoneyUtil.mwk(remaining));

        if (targetAmount <= 0) {
            planSummaryLabel.setText("Enter a target amount to calculate the saving plan.");
            adviceLabel.setText("Start with a clear target amount, then choose a monthly contribution that does not depend on leftover money.");
            return;
        }
        if (remaining <= 0) {
            planSummaryLabel.setText("The saved amount already reaches this goal.");
            adviceLabel.setText("Mark this goal as completed or increase the target if it is still ongoing.");
            return;
        }

        LocalDate targetDate = targetDatePicker.getValue();
        long monthsUntilTarget = monthsUntil(targetDate);
        double requiredMonthly = monthsUntilTarget <= 0 ? 0 : remaining / monthsUntilTarget;
        String plan = planText(remaining, monthlyContribution, targetDate, monthsUntilTarget, requiredMonthly);
        planSummaryLabel.setText(plan);
        adviceLabel.setText(adviceText(remaining, monthlyContribution, requiredMonthly));
    }

    private String planText(
            double remaining,
            double monthlyContribution,
            LocalDate targetDate,
            long monthsUntilTarget,
            double requiredMonthly
    ) {
        if (targetDate != null && monthsUntilTarget > 0) {
            return "To reach this goal by " + targetDate + ", save about "
                    + MoneyUtil.mwk(requiredMonthly) + " per month for " + monthsUntilTarget + " months.";
        }
        if (monthlyContribution > 0) {
            double monthsNeeded = Math.ceil(remaining / monthlyContribution);
            LocalDate projectedDate = LocalDate.now().plusMonths((long) monthsNeeded);
            return "At " + MoneyUtil.mwk(monthlyContribution) + " per month, this goal can be reached in about "
                    + String.format("%.0f", monthsNeeded) + " months, around " + projectedDate + ".";
        }
        return "Add a monthly contribution or target date to calculate when this goal can be achieved.";
    }

    private String adviceText(double remaining, double monthlyContribution, double requiredMonthly) {
        double averageSpent = spendingSnapshot.averageMonthlySpent();
        String topCategory = spendingSnapshot.topCategoryName();
        if (requiredMonthly > 0 && monthlyContribution > 0 && monthlyContribution < requiredMonthly) {
            String reviewTarget = topCategory.isBlank() ? "the largest visible spending area" : topCategory;
            return "Increase the monthly contribution by about "
                    + MoneyUtil.mwk(requiredMonthly - monthlyContribution)
                    + " to hit the target date. Review " + reviewTarget + " first because it is the biggest visible spending area.";
        }
        if (requiredMonthly > 0) {
            double share = averageSpent <= 0 ? 0 : (requiredMonthly / averageSpent) * 100;
            return "Reserve " + MoneyUtil.mwk(requiredMonthly)
                    + " at the start of each month. That is about " + String.format("%.1f", share)
                    + "% of recent monthly spending.";
        }
        if (monthlyContribution > 0) {
            double share = averageSpent <= 0 ? 0 : (monthlyContribution / averageSpent) * 100;
            return "Keep " + MoneyUtil.mwk(monthlyContribution)
                    + " separate each month. That contribution is about " + String.format("%.1f", share)
                    + "% of recent monthly spending.";
        }
        return "Set a realistic monthly contribution. A useful starting point is redirecting part of the largest spending area into this goal.";
    }

    private void applyAvailableAccountDefault() {
        if (currentAmountField == null || availableAccountBalanceLabel == null) {
            return;
        }
        double availableBalance = Math.max(0, database.getDashboardStats().getTotalBalance());
        String defaultValue = String.format(Locale.ENGLISH, "%.2f", availableBalance);
        String currentValue = textValue(currentAmountField);
        if (currentValue.isBlank() || currentValue.equals(lastAccountBalanceDefault)) {
            currentAmountField.setText(defaultValue);
        }
        lastAccountBalanceDefault = defaultValue;
        availableAccountBalanceLabel.setText("From active accounts: " + MoneyUtil.mwk(availableBalance));
    }

    private void refreshAiStatus() {
        if (aiStatusLabel == null || aiRecommendationLabel == null) {
            return;
        }
        AiSettings settings = database.getAiSettings();
        if (settings != null && settings.canGenerateRecommendations()) {
            aiStatusLabel.setText("Smart Analysis ready: " + settings.getDisplayName() + " / " + settings.getModel());
            if (aiRecommendationLabel.getText() == null || aiRecommendationLabel.getText().isBlank()) {
                aiRecommendationLabel.setText("Open Smart Analysis under Administration for deeper goal recommendations.");
            }
            return;
        }
        aiStatusLabel.setText("Smart Analysis is not ready.");
        aiRecommendationLabel.setText("Configure Smart Analysis under Settings to generate recommendations from your finance data.");
    }

    private void refreshStepAiStatus() {
        if (stepAiStatusLabel == null || stepAiRecommendationLabel == null) {
            return;
        }
        AiSettings settings = database.getAiSettings();
        if (settings != null && settings.canGenerateRecommendations()) {
            stepAiStatusLabel.setText("Smart Analysis ready: " + settings.getDisplayName() + " / " + settings.getModel());
            stepAiRecommendationLabel.setText("Select a goal, then add steps manually or use Smart Analysis under Administration.");
            return;
        }
        stepAiStatusLabel.setText("Smart Analysis is not ready.");
        stepAiRecommendationLabel.setText("Configure Smart Analysis under Settings to generate step suggestions from your goal details.");
    }

    private boolean hasGoalSummaryFields() {
        return monthSpentLabel != null
                && averageSpentLabel != null
                && topSpendingLabel != null
                && spendingInsightLabel != null
                && categoryInsightLabel != null;
    }

    private boolean hasGoalInsightFields() {
        return targetAmountField != null
                && currentAmountField != null
                && monthlyContributionField != null
                && targetDatePicker != null
                && goalGapLabel != null
                && planSummaryLabel != null
                && adviceLabel != null;
    }

    private void applyAiGoalInsight(String recommendation) {
        AiGoalInsight insight = AiGoalInsight.parse(recommendation);
        refreshSpendingSummary();
        refreshGoalInsight();
        if (!insight.hasContent()) {
            adviceLabel.setText(valueOrFallback(recommendation, adviceLabel.getText()));
            if (aiRecommendationLabel != null) {
                aiRecommendationLabel.setText("Smart Analysis returned advice without section labels; showing it in the Advice card.");
            }
            return;
        }
        planSummaryLabel.setText(valueOrFallback(insight.achievementPlan(), planSummaryLabel.getText()));
        spendingInsightLabel.setText(valueOrFallback(insight.spendingInsight(), spendingInsightLabel.getText()));
        categoryInsightLabel.setText(valueOrFallback(insight.whereToAdjust(), categoryInsightLabel.getText()));
        adviceLabel.setText(valueOrFallback(insight.advice(), adviceLabel.getText()));
        if (aiRecommendationLabel != null) {
            aiRecommendationLabel.setText(valueOrFallback(
                    insight.summary(),
                    "Smart Analysis filled the goal analysis cards above."
            ));
        }
    }

    private String buildAiGoalPrompt(AiSettings settings) {
        double targetAmount = parseInsightAmount(targetAmountField.getText());
        double currentAmount = parseInsightAmount(currentAmountField.getText());
        double monthlyContribution = parseInsightAmount(monthlyContributionField.getText());
        double remaining = Math.max(0, targetAmount - currentAmount);
        LocalDate targetDate = targetDatePicker.getValue();
        long monthsUntilTarget = monthsUntil(targetDate);
        double requiredMonthly = monthsUntilTarget <= 0 ? 0 : remaining / monthsUntilTarget;
        StringBuilder prompt = new StringBuilder();
        prompt.append("PFMIS goal recommendation request\n");
        prompt.append("Assistant name: ").append(settings.getDisplayName()).append('\n');
        prompt.append("Enabled agents: ").append(settings.getAgents()).append('\n');
        prompt.append("Enabled extensions: ").append(settings.getExtensions()).append('\n');
        prompt.append("Goal name: ").append(textValue(goalNameField).isBlank() ? "Not entered" : textValue(goalNameField)).append('\n');
        prompt.append("Target amount: ").append(MoneyUtil.mwk(targetAmount)).append('\n');
        prompt.append("Already saved: ").append(MoneyUtil.mwk(currentAmount)).append('\n');
        prompt.append("Remaining amount: ").append(MoneyUtil.mwk(remaining)).append('\n');
        prompt.append("Monthly contribution: ").append(MoneyUtil.mwk(monthlyContribution)).append('\n');
        prompt.append("Target date: ").append(targetDate == null ? "Not entered" : targetDate).append('\n');
        prompt.append("Months until target: ").append(monthsUntilTarget).append('\n');
        prompt.append("Required monthly saving: ").append(MoneyUtil.mwk(requiredMonthly)).append('\n');
        prompt.append("Current month expenses: ").append(MoneyUtil.mwk(spendingSnapshot.currentMonthSpent())).append('\n');
        prompt.append("Three month average expenses: ").append(MoneyUtil.mwk(spendingSnapshot.averageMonthlySpent())).append('\n');
        prompt.append("Top spending category this month: ").append(spendingSnapshot.topCategoryText()).append('\n');
        prompt.append("Other registered goals:\n");
        for (Goal goal : database.listGoals().stream().limit(8).toList()) {
            prompt.append("- ")
                    .append(goal.getGoalName())
                    .append(": target ")
                    .append(MoneyUtil.mwk(goal.getTargetAmount()))
                    .append(", saved ")
                    .append(MoneyUtil.mwk(goal.getCurrentAmount()))
                    .append(", status ")
                    .append(goal.getStatus())
                    .append('\n');
        }
        prompt.append("Return the recommendation in this exact format, with one concise paragraph after each label:\n");
        prompt.append("ACHIEVEMENT_PLAN: Explain how the goal can be achieved, including monthly amount or missing data.\n");
        prompt.append("SPENDING_INSIGHT: Explain what the current spending records show.\n");
        prompt.append("WHERE_TO_ADJUST: Name the category or behavior to adjust first, or say what data is missing.\n");
        prompt.append("ADVICE: Give direct next action for the user.\n");
        prompt.append("SUMMARY: Give one short sentence for the Smart Analysis result.\n");
        return prompt.toString();
    }

    private SpendingSnapshot buildSpendingSnapshot() {
        YearMonth currentMonth = YearMonth.now();
        List<YearMonth> recentMonths = List.of(
                currentMonth.minusMonths(2),
                currentMonth.minusMonths(1),
                currentMonth
        );
        Map<YearMonth, Double> monthTotals = new LinkedHashMap<>();
        for (YearMonth month : recentMonths) {
            monthTotals.put(month, 0.0);
        }

        Map<String, Double> currentMonthCategories = new LinkedHashMap<>();
        for (FinanceTransaction transaction : database.listRecentTransactions(500)) {
            if (!isSpendingTransaction(transaction)) {
                continue;
            }
            YearMonth transactionMonth = transactionMonth(transaction);
            if (transactionMonth == null || !monthTotals.containsKey(transactionMonth)) {
                continue;
            }
            monthTotals.compute(transactionMonth, (month, amount) -> amount + transaction.getAmount());
            if (currentMonth.equals(transactionMonth)) {
                String category = transaction.getCategoryName() == null || transaction.getCategoryName().isBlank()
                        ? "Uncategorized"
                        : transaction.getCategoryName();
                currentMonthCategories.compute(category, (name, amount) -> amount == null
                        ? transaction.getAmount()
                        : amount + transaction.getAmount());
            }
        }

        double currentMonthSpent = monthTotals.getOrDefault(currentMonth, 0.0);
        double averageMonthlySpent = monthTotals.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        Map.Entry<String, Double> topCategory = currentMonthCategories.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .orElse(null);
        return new SpendingSnapshot(
                currentMonthSpent,
                averageMonthlySpent,
                topCategory == null ? "" : topCategory.getKey(),
                topCategory == null ? 0 : topCategory.getValue()
        );
    }

    private boolean isSpendingTransaction(FinanceTransaction transaction) {
        return "EXPENSE".equals(transaction.getTransactionType())
                && !"CANCELLED".equals(transaction.getTransactionStatus());
    }

    private YearMonth transactionMonth(FinanceTransaction transaction) {
        try {
            String date = transaction.getTransactionDate();
            return date == null || date.isBlank() ? null : YearMonth.from(LocalDate.parse(date));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private long monthsUntil(LocalDate targetDate) {
        if (targetDate == null || !targetDate.isAfter(LocalDate.now())) {
            return 0;
        }
        return Math.max(1, ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(targetDate)) + 1);
    }

    private String goalStatus(double targetAmount, double currentAmount) {
        String selectedStatus = normalizedStatus(statusBox.getValue());
        if ("ACTIVE".equals(selectedStatus) && targetAmount > 0 && currentAmount >= targetAmount) {
            return "COMPLETED";
        }
        return selectedStatus;
    }

    private String normalizedStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase(Locale.ENGLISH);
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String amountText(double amount) {
        return String.format(Locale.ENGLISH, "%.2f", amount);
    }

    private double amountFromText(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String amount = value.replaceAll("[^0-9.,]", "").replace(",", "");
        if (amount.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(amount);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private double requiredPositiveAmount(String value, String message) {
        double amount = parseOptionalAmount(value);
        if (amount <= 0) {
            throw new IllegalArgumentException(message);
        }
        return amount;
    }

    private double parseOptionalAmount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        double amount = Double.parseDouble(value.replace(",", "").trim());
        if (amount < 0) {
            throw new IllegalArgumentException("Amounts cannot be negative.");
        }
        return amount;
    }

    private double parseInsightAmount(String value) {
        try {
            return parseOptionalAmount(value);
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private String textValue(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String textAreaValue(TextArea area) {
        return area.getText() == null ? "" : area.getText().trim();
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record AiGoalInsight(
            String achievementPlan,
            String spendingInsight,
            String whereToAdjust,
            String advice,
            String summary
    ) {
        static AiGoalInsight parse(String response) {
            Map<String, StringBuilder> sections = new LinkedHashMap<>();
            String currentKey = "";
            for (String line : valueOrEmpty(response).split("\\R")) {
                ParsedLine parsedLine = parseSectionLine(line);
                if (!parsedLine.key().isBlank()) {
                    currentKey = parsedLine.key();
                    sections.computeIfAbsent(currentKey, key -> new StringBuilder());
                    appendSectionText(sections.get(currentKey), parsedLine.value());
                } else if (!currentKey.isBlank()) {
                    appendSectionText(sections.get(currentKey), line.trim());
                }
            }
            return new AiGoalInsight(
                    section(sections, "ACHIEVEMENT_PLAN"),
                    section(sections, "SPENDING_INSIGHT"),
                    section(sections, "WHERE_TO_ADJUST"),
                    section(sections, "ADVICE"),
                    section(sections, "SUMMARY")
            );
        }

        boolean hasContent() {
            return !achievementPlan.isBlank()
                    || !spendingInsight.isBlank()
                    || !whereToAdjust.isBlank()
                    || !advice.isBlank()
                    || !summary.isBlank();
        }

        private static ParsedLine parseSectionLine(String line) {
            String cleanLine = line == null ? "" : line.trim();
            cleanLine = cleanLine.replace("**", "");
            cleanLine = cleanLine.replaceFirst("^[\\-\\*\\d\\.\\)\\s]+", "");
            String upperLine = cleanLine.toUpperCase(Locale.ENGLISH);
            for (String key : List.of("ACHIEVEMENT_PLAN", "SPENDING_INSIGHT", "WHERE_TO_ADJUST", "ADVICE", "SUMMARY")) {
                String spacedKey = key.replace('_', ' ');
                for (String label : List.of(key, spacedKey)) {
                    if (upperLine.startsWith(label + ":")) {
                        return new ParsedLine(key, cleanLine.substring(label.length() + 1).trim());
                    }
                }
            }
            return new ParsedLine("", "");
        }

        private static void appendSectionText(StringBuilder builder, String text) {
            if (text == null || text.isBlank()) {
                return;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(text.trim());
        }

        private static String section(Map<String, StringBuilder> sections, String key) {
            StringBuilder value = sections.get(key);
            return value == null ? "" : value.toString().strip();
        }

        private static String valueOrEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    private record ParsedLine(String key, String value) {
    }

    private record SuggestedGoalStep(String name, double estimatedCost, String note) {
    }

    private record SpendingSnapshot(
            double currentMonthSpent,
            double averageMonthlySpent,
            String topCategoryName,
            double topCategoryAmount
    ) {
        static SpendingSnapshot empty() {
            return new SpendingSnapshot(0, 0, "", 0);
        }

        String topCategoryText() {
            return topCategoryName.isBlank() ? "-" : topCategoryName + " - " + MoneyUtil.mwk(topCategoryAmount);
        }

        String topCategoryAdvice() {
            if (topCategoryName.isBlank()) {
                return "No spending category has enough data this month. Add expenses to make this insight useful.";
            }
            return topCategoryName + " is the highest spending area this month at "
                    + MoneyUtil.mwk(topCategoryAmount)
                    + ". Reducing this category can create room for goal contributions.";
        }
    }
}
