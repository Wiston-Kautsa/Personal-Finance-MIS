package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.GoalContribution;
import com.wk.pfmis.models.GoalStep;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class GoalRecordsController {
    private static final String ALL_STATUSES = "All statuses";
    private static final String ALL_PRIORITIES = "All priorities";
    private static final String ALL_TYPES = "All types";
    private static final String ALL_PERIODS = "All periods";

    @FXML private ComboBox<String> statusFilterBox;
    @FXML private ComboBox<String> priorityFilterBox;
    @FXML private ComboBox<String> typeFilterBox;
    @FXML private ComboBox<String> targetDateFilterBox;
    @FXML private TextField searchField;
    @FXML private TableView<GoalRow> goalTable;
    @FXML private TableColumn<GoalRow, String> goalColumn;
    @FXML private TableColumn<GoalRow, String> targetColumn;
    @FXML private TableColumn<GoalRow, String> allocatedColumn;
    @FXML private TableColumn<GoalRow, String> remainingColumn;
    @FXML private TableColumn<GoalRow, String> targetDateColumn;
    @FXML private TableColumn<GoalRow, String> progressColumn;
    @FXML private TableColumn<GoalRow, String> statusColumn;
    @FXML private TextArea detailsArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Goal> goals = List.of();
    private Goal openedGoal;

    @FXML
    public void initialize() {
        configureFilters();
        configureTable();
        refresh();
    }

    @FXML
    private void openGoal() {
        Goal goal = selectedGoal();
        if (goal == null) {
            UiAlerts.info("Select a goal to open.");
            return;
        }
        openedGoal = goal;
        detailsArea.setText(goalProfile(goal));
    }

    @FXML
    private void recordContribution() {
        Goal goal = selectedGoal();
        if (goal == null) {
            UiAlerts.info("Select a goal first.");
            return;
        }
        NavigationBus.requestGoalContribution(goal.getId());
    }

    @FXML
    private void moreActions() {
        Goal goal = openedGoal == null ? selectedGoal() : openedGoal;
        if (goal == null) {
            UiAlerts.info("Open a goal first.");
            return;
        }
        List<String> actions = actionsFor(goal);
        ChoiceDialog<String> dialog = new ChoiceDialog<>(actions.get(0), actions);
        dialog.setTitle("Goal Actions");
        dialog.setHeaderText(goal.getGoalName());
        dialog.setContentText("Action");
        Optional<String> selected = dialog.showAndWait();
        selected.ifPresent(action -> handleAction(goal, action));
    }

    private void configureFilters() {
        statusFilterBox.setItems(FXCollections.observableArrayList(
                ALL_STATUSES, "Draft", "Active", "Paused", "At Risk", "Overdue", "Achieved", "Converted to Project", "Cancelled", "Archived"
        ));
        statusFilterBox.getSelectionModel().select(ALL_STATUSES);
        priorityFilterBox.setItems(FXCollections.observableArrayList(ALL_PRIORITIES, "Essential", "High", "Medium", "Optional"));
        priorityFilterBox.getSelectionModel().select(ALL_PRIORITIES);
        typeFilterBox.setItems(FXCollections.observableArrayList(ALL_TYPES, "Savings", "Purchase", "Emergency Fund", "Debt Repayment", "Education", "Business", "Investment", "Project", "Other"));
        typeFilterBox.getSelectionModel().select(ALL_TYPES);
        targetDateFilterBox.setItems(FXCollections.observableArrayList(ALL_PERIODS, "Next 30 days", "This quarter", "Overdue", "No target date"));
        targetDateFilterBox.getSelectionModel().select(ALL_PERIODS);
        statusFilterBox.setOnAction(event -> applyFilters());
        priorityFilterBox.setOnAction(event -> applyFilters());
        typeFilterBox.setOnAction(event -> applyFilters());
        targetDateFilterBox.setOnAction(event -> applyFilters());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
    }

    private void configureTable() {
        goalColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().goal().getGoalName()));
        targetColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().goal().getTargetAmount())));
        allocatedColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().goal().getCurrentAmount())));
        remainingColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().goal().getRemainingAmount())));
        targetDateColumn.setCellValueFactory(cell -> new SimpleStringProperty(dash(cell.getValue().goal().getTargetDate())));
        progressColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.format(Locale.ENGLISH, "%.1f%%", progress(cell.getValue().goal()))));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayStatus(cell.getValue().goal().getStatus())));
        goalTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                detailsArea.setText(summaryText(newValue.goal()));
            }
        });
    }

    private void refresh() {
        goals = database.listGoals();
        applyFilters();
        detailsArea.setText("Select a goal, then Open Goal to review overview, contributions, steps and history.");
    }

    private void applyFilters() {
        if (goalTable == null) {
            return;
        }
        List<GoalRow> rows = goals.stream()
                .filter(this::matchesStatus)
                .filter(this::matchesPriority)
                .filter(this::matchesType)
                .filter(this::matchesTargetDate)
                .filter(this::matchesSearch)
                .map(GoalRow::new)
                .toList();
        goalTable.setItems(FXCollections.observableArrayList(rows));
    }

    private boolean matchesStatus(Goal goal) {
        String selected = statusFilterBox.getValue();
        return selected == null || ALL_STATUSES.equals(selected) || selected.equalsIgnoreCase(displayStatus(goal.getStatus()));
    }

    private boolean matchesPriority(Goal goal) {
        String selected = priorityFilterBox.getValue();
        return selected == null || ALL_PRIORITIES.equals(selected) || selected.equalsIgnoreCase(safe(goal.getPriority()));
    }

    private boolean matchesType(Goal goal) {
        String selected = typeFilterBox.getValue();
        return selected == null || ALL_TYPES.equals(selected) || selected.equalsIgnoreCase(safe(goal.getGoalType()));
    }

    private boolean matchesTargetDate(Goal goal) {
        String selected = targetDateFilterBox.getValue();
        if (selected == null || ALL_PERIODS.equals(selected)) {
            return true;
        }
        LocalDate targetDate = parseDate(goal.getTargetDate());
        LocalDate today = LocalDate.now();
        return switch (selected) {
            case "Next 30 days" -> targetDate != null && !targetDate.isBefore(today) && !targetDate.isAfter(today.plusDays(30));
            case "This quarter" -> targetDate != null && !targetDate.isBefore(today) && !targetDate.isAfter(today.plusMonths(3));
            case "Overdue" -> targetDate != null && targetDate.isBefore(today) && goal.getRemainingAmount() > 0.005;
            case "No target date" -> targetDate == null;
            default -> true;
        };
    }

    private boolean matchesSearch(Goal goal) {
        String query = safe(searchField.getText()).toLowerCase(Locale.ENGLISH);
        if (query.isBlank()) {
            return true;
        }
        String haystack = (goal.getGoalName() + " " + safe(goal.getGoalType()) + " " + safe(goal.getPriority()) + " "
                + safe(goal.getFundingAccountName()) + " " + safe(goal.getDescription())).toLowerCase(Locale.ENGLISH);
        return haystack.contains(query);
    }

    private String goalProfile(Goal goal) {
        StringBuilder builder = new StringBuilder();
        builder.append(summaryText(goal)).append(System.lineSeparator());
        builder.append("Overview").append(System.lineSeparator());
        builder.append("Goal type: ").append(dash(goal.getGoalType())).append(System.lineSeparator());
        builder.append("Priority: ").append(dash(goal.getPriority())).append(System.lineSeparator());
        builder.append("Funding account: ").append(dash(goal.getFundingAccountName())).append(System.lineSeparator());
        builder.append("Start date: ").append(dash(goal.getStartDate())).append(System.lineSeparator());
        builder.append("Planned contribution: ").append(MoneyUtil.mwk(goal.getMonthlyContribution()))
                .append(" / ").append(dash(goal.getContributionFrequency())).append(System.lineSeparator());
        builder.append("Required monthly contribution: ").append(MoneyUtil.mwk(requiredMonthly(goal))).append(System.lineSeparator());
        builder.append("Forecast completion: ").append(forecastCompletion(goal)).append(System.lineSeparator()).append(System.lineSeparator());

        builder.append("Contributions").append(System.lineSeparator());
        List<GoalContribution> contributions = database.listGoalContributions(goal.getId());
        if (contributions.isEmpty()) {
            builder.append("- No contribution ledger entries yet.").append(System.lineSeparator());
        } else {
            for (GoalContribution contribution : contributions) {
                builder.append(contribution.getContributionDate())
                        .append(" | ")
                        .append(MoneyUtil.mwk(contribution.getAmount()))
                        .append(" | ")
                        .append(dash(contribution.getSourceAccountName()))
                        .append(" -> ")
                        .append(dash(contribution.getDestinationAccountName()))
                        .append(" | ")
                        .append(dash(contribution.getContributionType()))
                        .append(" | ")
                        .append(dash(contribution.getAllocationReference()))
                        .append(System.lineSeparator());
            }
        }

        builder.append(System.lineSeparator()).append("Steps").append(System.lineSeparator());
        List<GoalStep> steps = database.listGoalSteps(goal.getId());
        if (steps.isEmpty()) {
            builder.append("- No steps recorded.").append(System.lineSeparator());
        } else {
            for (GoalStep step : steps) {
                builder.append(step.getStepName())
                        .append(" | target ")
                        .append(dash(step.getTargetDate()))
                        .append(" | ")
                        .append(MoneyUtil.mwk(step.getEstimatedCost()))
                        .append(" | ")
                        .append(dash(step.getStatus()))
                        .append(System.lineSeparator());
            }
        }

        builder.append(System.lineSeparator()).append("History").append(System.lineSeparator());
        builder.append("- Goal created").append(System.lineSeparator());
        if (!"DRAFT".equalsIgnoreCase(safe(goal.getStatus()))) {
            builder.append("- Goal activated or monitored").append(System.lineSeparator());
        }
        if (!contributions.isEmpty()) {
            builder.append("- Contributions recorded: ").append(contributions.size()).append(System.lineSeparator());
        }
        if (!steps.isEmpty()) {
            builder.append("- Steps recorded: ").append(steps.size()).append(System.lineSeparator());
        }
        builder.append("- Status: ").append(displayStatus(goal.getStatus())).append(System.lineSeparator());
        if (!safe(goal.getDescription()).isBlank()) {
            builder.append(System.lineSeparator()).append("Notes").append(System.lineSeparator()).append(goal.getDescription());
        }
        return builder.toString();
    }

    private String summaryText(Goal goal) {
        return """
                Goal: %s
                Target: %s
                Allocated: %s
                Remaining: %s
                Progress: %.1f%%
                Target date: %s
                Status: %s

                Smart check: %s
                """.formatted(
                goal.getGoalName(),
                MoneyUtil.mwk(goal.getTargetAmount()),
                MoneyUtil.mwk(goal.getCurrentAmount()),
                MoneyUtil.mwk(goal.getRemainingAmount()),
                progress(goal),
                dash(goal.getTargetDate()),
                displayStatus(goal.getStatus()),
                smartCheck(goal)
        );
    }

    private List<String> actionsFor(Goal goal) {
        String status = safe(goal.getStatus()).toUpperCase(Locale.ENGLISH);
        List<String> actions = new ArrayList<>();
        actions.add("Record Contribution");
        actions.add("Update Goal Steps");
        actions.add("Turn Into Project");
        if ("PAUSED".equals(status)) {
            actions.add("Resume Goal");
        } else if (!List.of("DRAFT", "ACHIEVED", "CANCELLED", "ARCHIVED", "CONVERTED_TO_PROJECT").contains(status)) {
            actions.add("Pause Goal");
        }
        if (!"ACHIEVED".equals(status)) {
            actions.add("Mark as Achieved");
        }
        if (!List.of("CANCELLED", "ARCHIVED").contains(status)) {
            actions.add("Cancel Goal");
        }
        actions.add("Archive Goal");
        return actions;
    }

    private void handleAction(Goal goal, String action) {
        switch (action) {
            case "Record Contribution" -> NavigationBus.requestGoalContribution(goal.getId());
            case "Update Goal Steps" -> NavigationBus.requestGoalSteps(goal.getId());
            case "Turn Into Project" -> NavigationBus.requestGoalProject(goal.getId());
            case "Pause Goal" -> updateStatus(goal, "PAUSED", "Goal paused from Goal Records.");
            case "Resume Goal" -> updateStatus(goal, "ACTIVE", "Goal resumed from Goal Records.");
            case "Mark as Achieved" -> updateStatus(goal, "ACHIEVED", "Goal marked as achieved from Goal Records.");
            case "Cancel Goal" -> updateStatus(goal, "CANCELLED", "Goal cancelled from Goal Records.");
            case "Archive Goal" -> updateStatus(goal, "ARCHIVED", "Goal archived from Goal Records.");
            default -> UiAlerts.info("Action is not available.");
        }
    }

    private void updateStatus(Goal goal, String status, String note) {
        database.updateGoalStatus(goal.getId(), status, note);
        refresh();
        openedGoal = database.listGoals().stream()
                .filter(updatedGoal -> updatedGoal.getId() == goal.getId())
                .findFirst()
                .orElse(null);
        if (openedGoal != null) {
            detailsArea.setText(goalProfile(openedGoal));
        }
        DataRefreshBus.notifyDataChanged();
    }

    private Goal selectedGoal() {
        GoalRow row = goalTable.getSelectionModel().getSelectedItem();
        return row == null ? null : row.goal();
    }

    private double progress(Goal goal) {
        return goal.getTargetAmount() <= 0 ? 0 : Math.min(100, goal.getCurrentAmount() / goal.getTargetAmount() * 100);
    }

    private double requiredMonthly(Goal goal) {
        LocalDate targetDate = parseDate(goal.getTargetDate());
        if (targetDate == null || !targetDate.isAfter(LocalDate.now())) {
            return 0;
        }
        long months = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(targetDate)) + 1);
        return goal.getRemainingAmount() / months;
    }

    private String forecastCompletion(Goal goal) {
        if (goal.getRemainingAmount() <= 0.005) {
            return "Achieved";
        }
        if (goal.getMonthlyContribution() <= 0.005) {
            return "Set a planned contribution";
        }
        long months = (long) Math.ceil(goal.getRemainingAmount() / goal.getMonthlyContribution());
        return YearMonth.now().plusMonths(months).toString();
    }

    private String smartCheck(Goal goal) {
        LocalDate targetDate = parseDate(goal.getTargetDate());
        if (goal.getRemainingAmount() <= 0.005) {
            return "The target amount has been reached.";
        }
        if (targetDate != null && targetDate.isBefore(LocalDate.now())) {
            return "The target date has passed and the goal is incomplete.";
        }
        double required = requiredMonthly(goal);
        if (required > 0 && goal.getMonthlyContribution() > 0 && goal.getMonthlyContribution() < required) {
            return "The planned contribution is below the required monthly contribution.";
        }
        return "Progress is trackable. Review contributions and steps regularly.";
    }

    private LocalDate parseDate(String value) {
        try {
            return safe(value).isBlank() ? null : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String displayStatus(String status) {
        String cleanStatus = safe(status).toUpperCase(Locale.ENGLISH);
        return switch (cleanStatus) {
            case "AT_RISK" -> "At Risk";
            case "CONVERTED_TO_PROJECT" -> "Converted to Project";
            default -> cleanStatus.isBlank()
                    ? "Active"
                    : cleanStatus.substring(0, 1).toUpperCase(Locale.ENGLISH) + cleanStatus.substring(1).toLowerCase(Locale.ENGLISH);
        };
    }

    private String dash(String value) {
        return safe(value).isBlank() ? "-" : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record GoalRow(Goal goal) {
    }
}
