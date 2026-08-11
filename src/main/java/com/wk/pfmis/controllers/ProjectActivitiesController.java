package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectActivity;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public class ProjectActivitiesController {
    private static final List<String> PROJECT_STATUSES = List.of("Draft", "Planned", "Active", "At Risk", "Delayed", "Paused", "Completed", "Cancelled", "Archived");
    private static final List<String> ACTIVITY_TYPES = List.of("Planning", "Procurement", "Implementation", "Training", "Monitoring", "Construction", "Purchase", "Administrative", "Other");
    private static final List<String> ACTIVITY_STATUSES = List.of("Not Started", "In Progress", "Completed", "Delayed", "Paused", "Cancelled");
    private static final List<String> PRIORITIES = List.of("Critical", "High", "Medium", "Optional");

    @FXML private ComboBox<Project> projectBox;
    @FXML private ComboBox<Project> activityListProjectBox;
    @FXML private ComboBox<String> projectStatusBox;
    @FXML private TextField activityNameField;
    @FXML private ComboBox<String> activityTypeBox;
    @FXML private DatePicker activityDatePicker;
    @FXML private DatePicker plannedStartDatePicker;
    @FXML private DatePicker plannedCompletionDatePicker;
    @FXML private DatePicker actualCompletionDatePicker;
    @FXML private TextField plannedCostField;
    @FXML private TextField responsiblePersonField;
    @FXML private ComboBox<String> priorityBox;
    @FXML private ComboBox<String> progressBox;
    @FXML private TextField evidenceField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea descriptionArea;
    @FXML private TextArea reasonArea;
    @FXML private TableView<ProjectActivity> activitiesTable;
    @FXML private TableColumn<ProjectActivity, String> dateColumn;
    @FXML private TableColumn<ProjectActivity, String> projectColumn;
    @FXML private TableColumn<ProjectActivity, String> activityColumn;
    @FXML private TableColumn<ProjectActivity, String> plannedCostColumn;
    @FXML private TableColumn<ProjectActivity, String> actualCostColumn;
    @FXML private TableColumn<ProjectActivity, String> progressColumn;
    @FXML private TableColumn<ProjectActivity, String> statusColumn;
    @FXML private TableColumn<ProjectActivity, String> reasonColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<ProjectActivity> activities = List.of();
    private ProjectActivity selectedActivity;

    @FXML
    public void initialize() {
        activityDatePicker.setValue(LocalDate.now());
        plannedStartDatePicker.setValue(LocalDate.now());
        projectStatusBox.setItems(FXCollections.observableArrayList(PROJECT_STATUSES));
        activityTypeBox.setItems(FXCollections.observableArrayList(ACTIVITY_TYPES));
        activityTypeBox.getSelectionModel().select("Other");
        statusBox.setItems(FXCollections.observableArrayList(ACTIVITY_STATUSES));
        statusBox.getSelectionModel().select("Not Started");
        priorityBox.setItems(FXCollections.observableArrayList(PRIORITIES));
        priorityBox.getSelectionModel().select("Medium");
        progressBox.setItems(FXCollections.observableArrayList("0", "25", "50", "75", "100"));
        progressBox.getSelectionModel().select("0");
        activitiesTable.setPlaceholder(new Label("Choose a project to view its activities."));
        projectBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            projectStatusBox.getSelectionModel().select(newValue == null ? "ACTIVE" : newValue.getStatus());
        });
        activityListProjectBox.valueProperty().addListener((observable, oldValue, newValue) -> showActivities(newValue));

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("activityDate"));
        projectColumn.setCellValueFactory(new PropertyValueFactory<>("projectName"));
        activityColumn.setCellValueFactory(new PropertyValueFactory<>("activityName"));
        plannedCostColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getPlannedCost())));
        actualCostColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountUsed())));
        progressColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.format("%.0f%%", cell.getValue().getProgress())));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getStatus())));
        reasonColumn.setCellValueFactory(cell -> new SimpleStringProperty(reasonOrDescription(cell.getValue())));
        activitiesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            selectedActivity = selected;
            if (selected != null) {
                fillForm(selected);
            }
        });
        configureContextMenu();
        refresh();
    }

    @FXML
    private void saveActivity() {
        try {
            Project project = projectBox.getValue();
            if (project == null) {
                UiAlerts.info("Select a project first.");
                return;
            }
            String activityName = activityNameField.getText() == null ? "" : activityNameField.getText().trim();
            if (activityName.isEmpty()) {
                UiAlerts.info("Enter an activity name.");
                return;
            }
            if (selectedActivity == null) {
                database.addProjectActivity(
                        project.getId(),
                        activityName,
                        activityTypeBox.getValue(),
                        textValue(descriptionArea),
                        activityDatePicker.getValue() == null ? LocalDate.now() : activityDatePicker.getValue(),
                        plannedStartDatePicker.getValue(),
                        plannedCompletionDatePicker.getValue(),
                        actualCompletionDatePicker.getValue(),
                        plannedCostValue(),
                        textValue(responsiblePersonField),
                        priorityBox.getValue(),
                        progressValue(),
                        statusBox.getValue(),
                        textValue(reasonArea),
                        textValue(evidenceField)
                );
            } else {
                database.updateProjectActivity(
                        selectedActivity.getId(),
                        project.getId(),
                        activityName,
                        activityTypeBox.getValue(),
                        textValue(descriptionArea),
                        activityDatePicker.getValue() == null ? LocalDate.now() : activityDatePicker.getValue(),
                        plannedStartDatePicker.getValue(),
                        plannedCompletionDatePicker.getValue(),
                        actualCompletionDatePicker.getValue(),
                        plannedCostValue(),
                        textValue(responsiblePersonField),
                        priorityBox.getValue(),
                        progressValue(),
                        statusBox.getValue(),
                        textValue(reasonArea),
                        textValue(evidenceField)
                );
            }
            database.updateProjectStatus(project.getId(), projectStatusBox.getValue());
            clearForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Activity saved. Actual cost will be calculated from project expense transactions.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save activity", exception);
        }
    }

    @FXML
    private void recordExpense() {
        Project project = projectBox.getValue();
        if (project == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        NavigationBus.requestProjectExpense(project.getId(), selectedActivity == null ? null : selectedActivity.getId());
        NavigationBus.showTransactionEntry("Record Project Expense");
    }

    @FXML
    private void clearForm() {
        selectedActivity = null;
        activitiesTable.getSelectionModel().clearSelection();
        activityNameField.clear();
        activityTypeBox.getSelectionModel().select("Other");
        activityDatePicker.setValue(LocalDate.now());
        plannedStartDatePicker.setValue(LocalDate.now());
        plannedCompletionDatePicker.setValue(null);
        actualCompletionDatePicker.setValue(null);
        plannedCostField.clear();
        responsiblePersonField.clear();
        priorityBox.getSelectionModel().select("Medium");
        progressBox.getSelectionModel().select("0");
        evidenceField.clear();
        descriptionArea.clear();
        reasonArea.clear();
        statusBox.getSelectionModel().select("Not Started");
    }

    @FXML
    private void refresh() {
        Project selectedProject = projectBox.getValue();
        Project selectedListProject = activityListProjectBox.getValue();
        List<Project> projects = database.listProjects();
        projectBox.setItems(FXCollections.observableArrayList(projects));
        activityListProjectBox.setItems(FXCollections.observableArrayList(projects));
        activities = database.listProjectActivities();
        restoreProjectSelection(projectBox, selectedProject);
        restoreProjectSelection(activityListProjectBox, selectedListProject);
        if (projectBox.getValue() != null) {
            projectStatusBox.getSelectionModel().select(projectBox.getValue().getStatus());
        } else {
            projectStatusBox.getSelectionModel().select("ACTIVE");
        }

        showActivities(activityListProjectBox.getValue());
    }

    private void restoreProjectSelection(ComboBox<Project> comboBox, Project selectedProject) {
        if (selectedProject == null) {
            comboBox.getSelectionModel().clearSelection();
            return;
        }
        comboBox.getItems().stream()
                .filter(project -> project.getId() == selectedProject.getId())
                .findFirst()
                .ifPresentOrElse(
                        project -> comboBox.getSelectionModel().select(project),
                        () -> comboBox.getSelectionModel().clearSelection()
                );
    }

    private void showActivities(Project project) {
        if (project == null) {
            activitiesTable.setItems(FXCollections.observableArrayList());
            return;
        }
        List<ProjectActivity> projectActivities = activities.stream()
                .filter(activity -> activity.getProjectId() == project.getId())
                .sorted(Comparator.comparing(ProjectActivitiesController::activityDateSortValue).reversed()
                        .thenComparing(ProjectActivity::getActivityName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        activitiesTable.setItems(FXCollections.observableArrayList(projectActivities));
    }

    private static String activityDateSortValue(ProjectActivity activity) {
        String activityDate = activity.getActivityDate();
        return activityDate == null ? "" : activityDate;
    }

    private void configureContextMenu() {
        TableActions.installRowContextMenu(activitiesTable, this::activityMenuItems);
    }

    private List<javafx.scene.control.MenuItem> activityMenuItems(ProjectActivity activity) {
        return List.of(
                TableActions.menuItem("View Activity Details", () -> viewActivityDetails(activity)),
                TableActions.menuItem("Select Activity Project", () -> selectActivityProject(activity)),
                TableActions.menuItem("Record Expense For Activity", () -> recordExpenseForActivity(activity)),
                TableActions.separator(),
                TableActions.copyRowItem(activitiesTable, activity),
                TableActions.exportTableItem(activitiesTable, selectedActivitiesTitle()),
                TableActions.printTableItem(activitiesTable, selectedActivitiesTitle()),
                TableActions.refreshItem(this::refresh)
        );
    }

    private void viewActivityDetails(ProjectActivity activity) {
        if (activity == null) {
            return;
        }
        UiAlerts.info(
                "Project: " + blankToDash(activity.getProjectName())
                        + "\nActivity: " + activity.getActivityName()
                        + "\nDate: " + blankToDash(activity.getActivityDate())
                        + "\nStatus: " + blankToDash(activity.getStatus())
                        + "\nAmount Used: " + MoneyUtil.mwk(activity.getAmountUsed())
                        + "\nReason: " + blankToDash(activity.getReason())
                        + "\nDescription: " + blankToDash(activity.getDescription())
        );
    }

    private void selectActivityProject(ProjectActivity activity) {
        if (activity == null) {
            return;
        }
        selectProjectById(projectBox, activity.getProjectId());
        selectProjectById(activityListProjectBox, activity.getProjectId());
    }

    private void recordExpenseForActivity(ProjectActivity activity) {
        if (activity != null) {
            selectActivityProject(activity);
        }
        NavigationBus.requestTransaction("EXPENSE", "PROJECT_EXPENSE", null);
        NavigationBus.requestProjectExpense(activity == null ? null : activity.getProjectId(), activity == null ? null : activity.getId());
        NavigationBus.showTransactionEntry("Record Project Expense");
    }

    private void selectProjectById(ComboBox<Project> comboBox, int projectId) {
        comboBox.getItems().stream()
                .filter(project -> project.getId() == projectId)
                .findFirst()
                .ifPresent(project -> comboBox.getSelectionModel().select(project));
    }

    private String selectedActivitiesTitle() {
        Project project = activityListProjectBox.getValue();
        return project == null ? "Project Activities" : project.getProjectName() + " Activities";
    }

    private String textValue(TextArea area) {
        return area.getText() == null ? "" : area.getText().trim();
    }

    private String textValue(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private double plannedCostValue() {
        String value = textValue(plannedCostField).replace(",", "");
        return value.isBlank() ? 0 : Double.parseDouble(value);
    }

    private double progressValue() {
        String value = progressBox.getValue();
        return value == null || value.isBlank() ? 0 : Double.parseDouble(value.replace("%", ""));
    }

    private void fillForm(ProjectActivity activity) {
        selectProjectById(projectBox, activity.getProjectId());
        selectProjectById(activityListProjectBox, activity.getProjectId());
        activityNameField.setText(activity.getActivityName());
        activityTypeBox.getSelectionModel().select(blankOrDefault(activity.getActivityType(), "Other"));
        activityDatePicker.setValue(parseDate(activity.getActivityDate(), LocalDate.now()));
        plannedStartDatePicker.setValue(parseDate(activity.getStartDate(), LocalDate.now()));
        plannedCompletionDatePicker.setValue(parseDate(activity.getEndDate(), null));
        actualCompletionDatePicker.setValue(parseDate(activity.getActualCompletionDate(), null));
        plannedCostField.setText(activity.getPlannedCost() <= 0 ? "" : String.format("%.2f", activity.getPlannedCost()));
        responsiblePersonField.setText(blankOrDefault(activity.getResponsiblePerson(), ""));
        priorityBox.getSelectionModel().select(blankOrDefault(activity.getPriority(), "Medium"));
        progressBox.getSelectionModel().select(String.format("%.0f", activity.getProgress()));
        statusBox.getSelectionModel().select(blankOrDefault(activity.getStatus(), "Not Started"));
        evidenceField.setText(blankOrDefault(activity.getEvidenceReference(), ""));
        descriptionArea.setText(blankOrDefault(activity.getDescription(), ""));
        reasonArea.setText(blankOrDefault(activity.getReason(), ""));
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        try {
            return value == null || value.isBlank() ? fallback : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String blankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String reasonOrDescription(ProjectActivity activity) {
        if (activity.getReason() != null && !activity.getReason().isBlank()) {
            return activity.getReason();
        }
        return blankToDash(activity.getDescription());
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
