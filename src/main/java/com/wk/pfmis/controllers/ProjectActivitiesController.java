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
    @FXML private ComboBox<Project> projectBox;
    @FXML private ComboBox<Project> activityListProjectBox;
    @FXML private ComboBox<String> projectStatusBox;
    @FXML private TextField activityNameField;
    @FXML private DatePicker activityDatePicker;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea descriptionArea;
    @FXML private TextArea reasonArea;
    @FXML private TableView<ProjectActivity> activitiesTable;
    @FXML private TableColumn<ProjectActivity, String> dateColumn;
    @FXML private TableColumn<ProjectActivity, String> projectColumn;
    @FXML private TableColumn<ProjectActivity, String> activityColumn;
    @FXML private TableColumn<ProjectActivity, String> statusColumn;
    @FXML private TableColumn<ProjectActivity, String> reasonColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<ProjectActivity> activities = List.of();

    @FXML
    public void initialize() {
        activityDatePicker.setValue(LocalDate.now());
        projectStatusBox.setItems(FXCollections.observableArrayList("ACTIVE", "PLANNED", "COMPLETED", "ON HOLD", "CANCELLED"));
        statusBox.setItems(FXCollections.observableArrayList("Pending", "In Progress", "Completed", "Cancelled"));
        statusBox.getSelectionModel().select("Pending");
        activitiesTable.setPlaceholder(new Label("Choose a project to view its activities."));
        projectBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            projectStatusBox.getSelectionModel().select(newValue == null ? "ACTIVE" : newValue.getStatus());
        });
        activityListProjectBox.valueProperty().addListener((observable, oldValue, newValue) -> showActivities(newValue));

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("activityDate"));
        projectColumn.setCellValueFactory(new PropertyValueFactory<>("projectName"));
        activityColumn.setCellValueFactory(new PropertyValueFactory<>("activityName"));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getStatus())));
        reasonColumn.setCellValueFactory(cell -> new SimpleStringProperty(reasonOrDescription(cell.getValue())));
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
            database.addProjectActivity(
                    project.getId(),
                    activityName,
                    textValue(descriptionArea),
                    activityDatePicker.getValue() == null ? LocalDate.now() : activityDatePicker.getValue(),
                    textValue(reasonArea),
                    statusBox.getValue()
            );
            database.updateProjectStatus(project.getId(), projectStatusBox.getValue());
            clearForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Activity registered. Record expenses for it from Record Expense.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save activity", exception);
        }
    }

    @FXML
    private void clearForm() {
        activityNameField.clear();
        activityDatePicker.setValue(LocalDate.now());
        descriptionArea.clear();
        reasonArea.clear();
        statusBox.getSelectionModel().select("Pending");
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
