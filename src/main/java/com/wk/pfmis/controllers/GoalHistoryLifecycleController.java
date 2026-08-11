package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.GoalContribution;
import com.wk.pfmis.models.GoalStep;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.time.LocalDate;
import java.util.List;

public class GoalHistoryLifecycleController {
    @FXML private ComboBox<Goal> goalBox;
    @FXML private Label targetLabel;
    @FXML private Label allocatedLabel;
    @FXML private Label remainingLabel;
    @FXML private Label statusLabel;
    @FXML private Label contributionStateLabel;
    @FXML private Label stepsStateLabel;
    @FXML private Label historyStateLabel;
    @FXML private TextArea lifecycleArea;
    @FXML private TableView<GoalContribution> contributionTable;
    @FXML private TableColumn<GoalContribution, String> contributionDateColumn;
    @FXML private TableColumn<GoalContribution, String> contributionAmountColumn;
    @FXML private TableColumn<GoalContribution, String> contributionTypeColumn;
    @FXML private TableColumn<GoalContribution, String> contributionSourceColumn;
    @FXML private TableColumn<GoalContribution, String> contributionDestinationColumn;
    @FXML private TableColumn<GoalContribution, String> contributionStatusColumn;
    @FXML private TableView<GoalStep> stepsTable;
    @FXML private TableColumn<GoalStep, String> stepNameColumn;
    @FXML private TableColumn<GoalStep, String> stepCostColumn;
    @FXML private TableColumn<GoalStep, String> stepReachedColumn;
    @FXML private TableColumn<GoalStep, String> stepMissingColumn;
    @FXML private TableColumn<GoalStep, String> stepTargetColumn;
    @FXML private TableColumn<GoalStep, String> stepStatusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Goal> goals = List.of();

    @FXML
    public void initialize() {
        configureTables();
        goalBox.valueProperty().addListener((observable, oldValue, newValue) -> showGoal(newValue));
        refresh();
    }

    @FXML
    private void refresh() {
        Integer selectedId = goalBox.getValue() == null ? null : goalBox.getValue().getId();
        goals = database.listGoals();
        goalBox.setItems(FXCollections.observableArrayList(goals));
        Goal selected = selectedId == null ? null : CoreWorkspaceSupport.goalById(goals, selectedId);
        if (selected == null && !goals.isEmpty()) {
            selected = goals.get(0);
        }
        goalBox.getSelectionModel().select(selected);
        showGoal(selected);
    }

    @FXML
    private void markActive() {
        updateStatus("ACTIVE");
    }

    @FXML
    private void pauseGoal() {
        updateStatus("PAUSED");
    }

    @FXML
    private void markAchieved() {
        updateStatus("ACHIEVED");
    }

    @FXML
    private void archiveGoal() {
        updateStatus("ARCHIVED");
    }

    @FXML
    private void openContributions() {
        Goal goal = goalBox.getValue();
        if (goal != null) {
            NavigationBus.requestGoalContribution(goal.getId());
        } else {
            CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.GOAL_CONTRIBUTIONS);
        }
    }

    @FXML
    private void openSteps() {
        Goal goal = goalBox.getValue();
        if (goal != null) {
            NavigationBus.requestGoalSteps(goal.getId());
        } else {
            CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.GOAL_STEPS);
        }
    }

    private void configureTables() {
        CoreWorkspaceSupport.bind(contributionDateColumn, GoalContribution::getContributionDate);
        CoreWorkspaceSupport.bind(contributionAmountColumn, contribution -> CoreWorkspaceSupport.money(contribution.getCurrency(), contribution.getAmount()));
        CoreWorkspaceSupport.bind(contributionTypeColumn, GoalContribution::getContributionType);
        CoreWorkspaceSupport.bind(contributionSourceColumn, contribution -> CoreWorkspaceSupport.dash(contribution.getSourceAccountName()));
        CoreWorkspaceSupport.bind(contributionDestinationColumn, contribution -> CoreWorkspaceSupport.dash(contribution.getDestinationAccountName()));
        CoreWorkspaceSupport.bind(contributionStatusColumn, GoalContribution::getStatus);
        CoreWorkspaceSupport.bind(stepNameColumn, GoalStep::getStepName);
        CoreWorkspaceSupport.bind(stepCostColumn, step -> CoreWorkspaceSupport.money("MWK", step.getEstimatedCost()));
        CoreWorkspaceSupport.bind(stepReachedColumn, step -> CoreWorkspaceSupport.money("MWK", step.getAmountReached()));
        CoreWorkspaceSupport.bind(stepMissingColumn, step -> CoreWorkspaceSupport.money("MWK", step.getMissingAmount()));
        CoreWorkspaceSupport.bind(stepTargetColumn, GoalStep::getTargetDate);
        CoreWorkspaceSupport.bind(stepStatusColumn, GoalStep::getStatus);
        TableActions.configureScrollableTable(contributionTable);
        TableActions.configureScrollableTable(stepsTable);
    }

    private void showGoal(Goal goal) {
        if (goal == null) {
            targetLabel.setText("-");
            allocatedLabel.setText("-");
            remainingLabel.setText("-");
            statusLabel.setText("-");
            lifecycleArea.setText("No goals are recorded yet.");
            CoreWorkspaceSupport.setItems(contributionTable, List.of(), contributionStateLabel, "No contributions.");
            CoreWorkspaceSupport.setItems(stepsTable, List.of(), stepsStateLabel, "No steps.");
            historyStateLabel.setText("No selected goal.");
            return;
        }
        targetLabel.setText(CoreWorkspaceSupport.money(goal.getCurrency(), goal.getTargetAmount()));
        allocatedLabel.setText(CoreWorkspaceSupport.money(goal.getCurrency(), goal.getCurrentAmount()));
        remainingLabel.setText(CoreWorkspaceSupport.money(goal.getCurrency(), goal.getRemainingAmount()));
        statusLabel.setText(CoreWorkspaceSupport.dash(goal.getStatus()));
        List<GoalContribution> contributions = database.listGoalContributions(goal.getId());
        List<GoalStep> steps = database.listGoalSteps(goal.getId());
        CoreWorkspaceSupport.setItems(contributionTable, contributions, contributionStateLabel, "No contributions.");
        CoreWorkspaceSupport.setItems(stepsTable, steps, stepsStateLabel, "No steps.");
        historyStateLabel.setText("Goal lifecycle generated from goal profile, contribution ledger and steps.");
        lifecycleArea.setText(historyText(goal, contributions, steps));
    }

    private void updateStatus(String status) {
        Goal goal = goalBox.getValue();
        if (goal == null) {
            UiAlerts.info("Select a goal first.");
            return;
        }
        try {
            database.updateGoalStatus(goal.getId(), status, "Lifecycle status changed from Goal History on " + LocalDate.now() + ".");
            DataRefreshBus.notifyDataChanged();
            refresh();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update goal lifecycle", exception);
        }
    }

    private String historyText(Goal goal, List<GoalContribution> contributions, List<GoalStep> steps) {
        double progress = goal.getTargetAmount() <= 0 ? 0 : goal.getCurrentAmount() / goal.getTargetAmount() * 100;
        StringBuilder builder = new StringBuilder();
        builder.append("Goal: ").append(goal.getGoalName()).append(System.lineSeparator());
        builder.append("Type: ").append(CoreWorkspaceSupport.dash(goal.getGoalType())).append(System.lineSeparator());
        builder.append("Priority: ").append(CoreWorkspaceSupport.dash(goal.getPriority())).append(System.lineSeparator());
        builder.append("Start date: ").append(CoreWorkspaceSupport.dash(goal.getStartDate())).append(System.lineSeparator());
        builder.append("Target date: ").append(CoreWorkspaceSupport.dash(goal.getTargetDate())).append(System.lineSeparator());
        builder.append("Funding account: ").append(CoreWorkspaceSupport.dash(goal.getFundingAccountName())).append(System.lineSeparator());
        builder.append("Progress: ").append(CoreWorkspaceSupport.percent(progress)).append(System.lineSeparator());
        builder.append("Lifecycle status: ").append(CoreWorkspaceSupport.dash(goal.getStatus())).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("Ledger events").append(System.lineSeparator());
        builder.append("- Goal record created and currently held as ").append(CoreWorkspaceSupport.dash(goal.getStatus())).append(".").append(System.lineSeparator());
        builder.append("- Contributions recorded: ").append(contributions.size()).append(System.lineSeparator());
        builder.append("- Steps recorded: ").append(steps.size()).append(System.lineSeparator());
        if (goal.getRemainingAmount() <= 0.005) {
            builder.append("- Financial target has been reached.").append(System.lineSeparator());
        }
        if (!CoreWorkspaceSupport.safe(goal.getDescription()).isBlank()) {
            builder.append(System.lineSeparator()).append("Notes").append(System.lineSeparator()).append(goal.getDescription());
        }
        return builder.toString();
    }
}
