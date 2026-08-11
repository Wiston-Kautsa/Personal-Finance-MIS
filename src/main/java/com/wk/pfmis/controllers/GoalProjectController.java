package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class GoalProjectController {
    @FXML private ComboBox<Goal> projectGoalBox;
    @FXML private TextField projectNameField;
    @FXML private TextField projectBudgetField;
    @FXML private DatePicker projectStartDatePicker;
    @FXML private DatePicker projectEndDatePicker;
    @FXML private TextArea projectDescriptionArea;
    @FXML private CheckBox markGoalCompletedBox;
    @FXML private Label projectReadinessLabel;
    @FXML private Label projectPreviewLabel;
    @FXML private Button createProjectButton;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private Integer lastDefaultGoalId;
    private String lastDefaultProjectName = "";
    private String lastDefaultProjectBudget = "";
    private LocalDate lastDefaultStartDate;
    private LocalDate lastDefaultEndDate;
    private String lastDefaultDescription = "";
    private boolean lastDefaultMarkCompleted = true;
    private boolean refreshingGoalChoices;

    @FXML
    public void initialize() {
        markGoalCompletedBox.setSelected(true);
        projectGoalBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (refreshingGoalChoices) {
                return;
            }
            applyGoalDefaults(oldValue, newValue);
            refreshProjectConversion();
        });
        projectNameField.textProperty().addListener((observable, oldValue, newValue) -> refreshProjectConversion());
        projectBudgetField.textProperty().addListener((observable, oldValue, newValue) -> refreshProjectConversion());
        projectStartDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> refreshProjectConversion());
        projectEndDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> refreshProjectConversion());
        projectDescriptionArea.textProperty().addListener((observable, oldValue, newValue) -> refreshProjectConversion());
        markGoalCompletedBox.selectedProperty().addListener((observable, oldValue, newValue) -> refreshProjectConversion());
        refreshProjectGoalChoices();
    }

    @FXML
    private void createProjectFromGoal() {
        try {
            Goal goal = projectGoalBox.getValue();
            if (goal == null) {
                UiAlerts.info("Select a goal first.");
                return;
            }
            String projectName = textValue(projectNameField);
            if (projectName.isEmpty()) {
                UiAlerts.info("Enter a project name.");
                return;
            }
            double projectBudget = requiredPositiveAmount(projectBudgetField.getText(), "Enter a project budget greater than zero.");
            if (projectBudget > goal.getTargetAmount()) {
                UiAlerts.info("Project budget cannot be greater than the goal target amount.");
                return;
            }
            if (database.projectExistsByName(projectName)) {
                UiAlerts.info("A project with this project name already exists.");
                return;
            }
            boolean projectCanBeActive = goalReadyForProject(goal) || markGoalCompletedBox.isSelected();
            database.addProject(
                    projectName,
                    projectDescription(goal, projectName, projectBudget),
                    projectBudget,
                    dateValue(projectStartDatePicker, LocalDate.now()).toString(),
                    projectEndDatePicker.getValue() == null ? goal.getTargetDate() : projectEndDatePicker.getValue().toString(),
                    projectCanBeActive ? "ACTIVE" : "PLANNED"
            );
            if (projectCanBeActive) {
                database.updateGoalStatus(goal.getId(), "CONVERTED_TO_PROJECT", "Goal converted to project: " + projectName + ".");
            }
            refreshProjectGoalChoices();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info(projectCanBeActive
                    ? "Goal turned into an active project."
                    : "Goal turned into a planned project. It can become active when the goal is fully funded.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to turn goal into project", exception);
        }
    }

    @FXML
    private void refreshProjectGoalChoices() {
        Integer requestedGoalId = NavigationBus.consumeRequestedGoalId();
        Integer selectedId = requestedGoalId == null
                ? projectGoalBox.getValue() == null ? null : projectGoalBox.getValue().getId()
                : requestedGoalId;
        List<Goal> goals = database.listGoals();
        refreshingGoalChoices = true;
        try {
            projectGoalBox.setItems(FXCollections.observableArrayList(goals));
            if (selectedId != null) {
                goals.stream()
                        .filter(goal -> goal.getId() == selectedId)
                        .findFirst()
                        .ifPresent(goal -> projectGoalBox.getSelectionModel().select(goal));
            }
            if (projectGoalBox.getValue() == null && !goals.isEmpty()) {
                goals.stream()
                        .filter(this::goalReadyForProject)
                        .findFirst()
                        .ifPresentOrElse(
                                goal -> projectGoalBox.getSelectionModel().select(goal),
                                () -> projectGoalBox.getSelectionModel().selectFirst()
                        );
            }
        } finally {
            refreshingGoalChoices = false;
        }
        applyGoalDefaults(null, projectGoalBox.getValue());
        refreshProjectConversion();
    }

    private void applyGoalDefaults(Goal previousGoal, Goal goal) {
        if (goal == null) {
            projectNameField.clear();
            projectBudgetField.clear();
            projectStartDatePicker.setValue(null);
            projectEndDatePicker.setValue(null);
            projectDescriptionArea.clear();
            markGoalCompletedBox.setSelected(true);
            clearRememberedDefaults();
            return;
        }
        boolean selectedDifferentGoal = lastDefaultGoalId == null || lastDefaultGoalId != goal.getId();
        String defaultName = goal.getGoalName();
        String defaultBudget = amountText(goal.getTargetAmount());
        LocalDate defaultStartDate = LocalDate.now();
        LocalDate defaultEndDate = parseDate(goal.getTargetDate());
        boolean defaultMarkCompleted = goalReadyForProject(goal) || List.of("ACHIEVED", "COMPLETED").contains(normalizedStatus(goal.getStatus()));
        String defaultDescription = defaultProjectDescription(goal, defaultName, goal.getTargetAmount());

        applyTextDefault(projectNameField, defaultName, lastDefaultProjectName, selectedDifferentGoal);
        applyTextDefault(projectBudgetField, defaultBudget, lastDefaultProjectBudget, selectedDifferentGoal);
        applyDateDefault(projectStartDatePicker, defaultStartDate, lastDefaultStartDate, selectedDifferentGoal);
        applyDateDefault(projectEndDatePicker, defaultEndDate, lastDefaultEndDate, selectedDifferentGoal);
        applyTextAreaDefault(projectDescriptionArea, defaultDescription, lastDefaultDescription, selectedDifferentGoal);
        if (selectedDifferentGoal || markGoalCompletedBox.isSelected() == lastDefaultMarkCompleted) {
            markGoalCompletedBox.setSelected(defaultMarkCompleted);
        }

        rememberDefaults(goal, defaultName, defaultBudget, defaultStartDate, defaultEndDate, defaultDescription, defaultMarkCompleted);
    }

    private void refreshProjectConversion() {
        Goal goal = projectGoalBox.getValue();
        if (goal == null) {
            projectReadinessLabel.setText("Register a goal first. A goal can become a project when its saved amount reaches the target.");
            projectPreviewLabel.setText("No project preview available.");
            createProjectButton.setDisable(true);
            return;
        }
        String projectName = textValue(projectNameField);
        double projectBudget = parsePreviewAmount(projectBudgetField.getText());
        if (projectName.isEmpty()) {
            projectReadinessLabel.setText("Enter a project name before creating the project.");
            projectPreviewLabel.setText(projectPreview(goal, projectName, projectBudget));
            createProjectButton.setDisable(true);
            return;
        }
        if (database.projectExistsByName(projectName)) {
            projectReadinessLabel.setText("A project already exists with this project name.");
            projectPreviewLabel.setText(projectPreview(goal, projectName, projectBudget));
            createProjectButton.setDisable(true);
            return;
        }
        if (projectBudget <= 0) {
            projectReadinessLabel.setText("Enter a project budget greater than zero.");
            projectPreviewLabel.setText(projectPreview(goal, projectName, projectBudget));
            createProjectButton.setDisable(true);
            return;
        }
        if (projectBudget > goal.getTargetAmount()) {
            projectReadinessLabel.setText("Reduce the project budget to the goal target amount or below.");
            projectPreviewLabel.setText(projectPreview(goal, projectName, projectBudget));
            createProjectButton.setDisable(true);
            return;
        }
        boolean projectCanBeActive = goalReadyForProject(goal) || markGoalCompletedBox.isSelected();
        if (projectCanBeActive) {
            projectReadinessLabel.setText(goalReadyForProject(goal)
                    ? "Condition favorable: saved amount reaches the goal target. This will create an active project."
                    : "Manual completion selected. This will create an active project and mark the goal completed.");
            projectPreviewLabel.setText(projectPreview(goal, projectName, projectBudget));
            createProjectButton.setDisable(false);
            return;
        }
        double gap = Math.max(0, goal.getTargetAmount() - goal.getCurrentAmount());
        projectReadinessLabel.setText("Funding not complete. Save " + MoneyUtil.mwk(gap)
                + " more to make it active. You can create it now as a planned project.");
        projectPreviewLabel.setText(projectPreview(goal, projectName, projectBudget));
        createProjectButton.setDisable(false);
    }

    private boolean goalReadyForProject(Goal goal) {
        return goal != null
                && goal.getTargetAmount() > 0
                && (goal.getCurrentAmount() >= goal.getTargetAmount()
                || List.of("ACHIEVED", "COMPLETED").contains(normalizedStatus(goal.getStatus())));
    }

    private String normalizedStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase(Locale.ENGLISH);
    }

    private String projectPreview(Goal goal, String projectName, double projectBudget) {
        LocalDate startDate = dateValue(projectStartDatePicker, LocalDate.now());
        LocalDate endDate = projectEndDatePicker.getValue();
        return "Project name: " + (projectName == null || projectName.isBlank() ? "not set" : projectName)
                + ". Planned budget: " + MoneyUtil.mwk(projectBudget)
                + ". Project status: " + (goalReadyForProject(goal) || markGoalCompletedBox.isSelected() ? "ACTIVE" : "PLANNED")
                + ". Source goal: " + goal.getGoalName()
                + ". Goal saved amount: " + MoneyUtil.mwk(goal.getCurrentAmount())
                + ". Goal monthly contribution: " + MoneyUtil.mwk(goal.getMonthlyContribution())
                + ". Goal status: " + normalizedStatus(goal.getStatus())
                + ". Start date: " + startDate
                + ". End date: " + (endDate == null ? "not set" : endDate) + ".";
    }

    private String projectDescription(Goal goal, String projectName, double projectBudget) {
        String description = projectDescriptionArea.getText() == null ? "" : projectDescriptionArea.getText().trim();
        if (!description.isEmpty()) {
            return description;
        }
        return defaultProjectDescription(goal, projectName, projectBudget);
    }

    private String defaultProjectDescription(Goal goal, String projectName, double projectBudget) {
        return "Converted from financial goal into project " + projectName + ". Target: " + MoneyUtil.mwk(goal.getTargetAmount())
                + ". Project budget: " + MoneyUtil.mwk(projectBudget)
                + ". Saved: " + MoneyUtil.mwk(goal.getCurrentAmount())
                + ". Monthly contribution: " + MoneyUtil.mwk(goal.getMonthlyContribution())
                + ". Original target date: " + (goal.getTargetDate() == null || goal.getTargetDate().isBlank() ? "not set" : goal.getTargetDate()) + ".";
    }

    private void applyTextDefault(TextField field, String defaultValue, String previousDefault, boolean force) {
        String currentValue = field.getText() == null ? "" : field.getText().trim();
        if (force || currentValue.isBlank() || currentValue.equals(previousDefault)) {
            field.setText(defaultValue == null ? "" : defaultValue);
        }
    }

    private void applyTextAreaDefault(TextArea area, String defaultValue, String previousDefault, boolean force) {
        String currentValue = area.getText() == null ? "" : area.getText().trim();
        if (force || currentValue.isBlank() || currentValue.equals(previousDefault)) {
            area.setText(defaultValue == null ? "" : defaultValue);
        }
    }

    private void applyDateDefault(DatePicker picker, LocalDate defaultValue, LocalDate previousDefault, boolean force) {
        LocalDate currentValue = picker.getValue();
        if (force || currentValue == null || currentValue.equals(previousDefault)) {
            picker.setValue(defaultValue);
        }
    }

    private void rememberDefaults(
            Goal goal,
            String defaultName,
            String defaultBudget,
            LocalDate defaultStartDate,
            LocalDate defaultEndDate,
            String defaultDescription,
            boolean defaultMarkCompleted
    ) {
        lastDefaultGoalId = goal.getId();
        lastDefaultProjectName = defaultName == null ? "" : defaultName;
        lastDefaultProjectBudget = defaultBudget == null ? "" : defaultBudget;
        lastDefaultStartDate = defaultStartDate;
        lastDefaultEndDate = defaultEndDate;
        lastDefaultDescription = defaultDescription == null ? "" : defaultDescription;
        lastDefaultMarkCompleted = defaultMarkCompleted;
    }

    private void clearRememberedDefaults() {
        lastDefaultGoalId = null;
        lastDefaultProjectName = "";
        lastDefaultProjectBudget = "";
        lastDefaultStartDate = null;
        lastDefaultEndDate = null;
        lastDefaultDescription = "";
        lastDefaultMarkCompleted = true;
    }

    private String amountText(double amount) {
        return String.format(Locale.ENGLISH, "%.2f", amount);
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

    private double parsePreviewAmount(String value) {
        try {
            return parseOptionalAmount(value);
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private String textValue(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private LocalDate dateValue(DatePicker picker, LocalDate fallback) {
        return picker.getValue() == null ? fallback : picker.getValue();
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
