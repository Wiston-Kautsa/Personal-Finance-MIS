package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectActivity;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.Comparator;
import java.util.List;

public class ProjectListController {
    @FXML private TextField projectSearchField;
    @FXML private ComboBox<String> statusFilterBox;
    @FXML private TableView<Project> projectsTable;
    @FXML private TableColumn<Project, String> nameColumn;
    @FXML private TableColumn<Project, String> plannedColumn;
    @FXML private TableColumn<Project, String> spentColumn;
    @FXML private TableColumn<Project, String> remainingColumn;
    @FXML private TableColumn<Project, String> startDateColumn;
    @FXML private TableColumn<Project, String> endDateColumn;
    @FXML private TableColumn<Project, String> statusColumn;
    @FXML private ComboBox<Project> activityProjectFilterBox;
    @FXML private ComboBox<String> activityStatusFilterBox;
    @FXML private TableView<ProjectActivity> activitiesTable;
    @FXML private TableColumn<ProjectActivity, String> activityDateColumn;
    @FXML private TableColumn<ProjectActivity, String> activityProjectColumn;
    @FXML private TableColumn<ProjectActivity, String> activityNameColumn;
    @FXML private TableColumn<ProjectActivity, String> activityStatusColumn;
    @FXML private TableColumn<ProjectActivity, String> activityNotesColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Project> projects = List.of();
    private List<ProjectActivity> activities = List.of();

    @FXML
    public void initialize() {
        statusFilterBox.setItems(FXCollections.observableArrayList("All Statuses", "ACTIVE", "PLANNED", "COMPLETED", "ON HOLD", "CANCELLED"));
        statusFilterBox.getSelectionModel().select("All Statuses");
        activityStatusFilterBox.setItems(FXCollections.observableArrayList("All Activity Statuses", "Pending", "In Progress", "Completed", "Cancelled"));
        activityStatusFilterBox.getSelectionModel().select("All Activity Statuses");
        activitiesTable.setPlaceholder(new Label("Choose a project to view its activities."));
        projectSearchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        statusFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        projectsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> selectActivityProject(newValue));
        activityProjectFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyActivityFilters());
        activityStatusFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyActivityFilters());

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("projectName"));
        plannedColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getPlannedBudget())));
        spentColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountSpent())));
        remainingColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getRemainingBudget())));
        startDateColumn.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        endDateColumn.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        activityDateColumn.setCellValueFactory(new PropertyValueFactory<>("activityDate"));
        activityProjectColumn.setCellValueFactory(new PropertyValueFactory<>("projectName"));
        activityNameColumn.setCellValueFactory(new PropertyValueFactory<>("activityName"));
        activityStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getStatus())));
        activityNotesColumn.setCellValueFactory(cell -> new SimpleStringProperty(reasonOrDescription(cell.getValue())));

        configureContextMenus();
        DataRefreshBus.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        int selectedProjectId = selectedProjectId();
        int selectedActivityProjectId = selectedActivityProjectId();
        projects = database.listProjects();
        activities = database.listProjectActivities();
        activityProjectFilterBox.setItems(FXCollections.observableArrayList(projects));
        applyFilters();
        restoreProjectSelection(selectedProjectId);
        restoreActivityProjectSelection(selectedActivityProjectId);
        applyActivityFilters();
    }

    private void applyFilters() {
        int selectedProjectId = selectedProjectId();
        String search = projectSearchField.getText() == null ? "" : projectSearchField.getText().trim().toLowerCase();
        String status = statusFilterBox.getValue();
        List<Project> filteredProjects = projects.stream()
                .filter(project -> search.isEmpty()
                        || contains(project.getProjectName(), search)
                        || contains(project.getDescription(), search)
                        || contains(project.getStatus(), search))
                .filter(project -> status == null || "All Statuses".equals(status) || status.equals(project.getStatus()))
                .toList();
        projectsTable.setItems(FXCollections.observableArrayList(filteredProjects));
        restoreProjectSelection(selectedProjectId);
    }

    private void applyActivityFilters() {
        Project project = activityProjectFilterBox.getValue();
        if (project == null) {
            activitiesTable.setItems(FXCollections.observableArrayList());
            return;
        }
        String status = activityStatusFilterBox.getValue();
        List<ProjectActivity> projectActivities = activities.stream()
                .filter(activity -> activity.getProjectId() == project.getId())
                .filter(activity -> status == null
                        || "All Activity Statuses".equals(status)
                        || status.equals(activity.getStatus()))
                .sorted(Comparator.comparing(ProjectListController::activityDateSortValue).reversed()
                        .thenComparing(ProjectActivity::getActivityName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        activitiesTable.setItems(FXCollections.observableArrayList(projectActivities));
    }

    private void selectActivityProject(Project project) {
        if (project == null) {
            return;
        }
        activityProjectFilterBox.getItems().stream()
                .filter(item -> item.getId() == project.getId())
                .findFirst()
                .ifPresent(item -> activityProjectFilterBox.getSelectionModel().select(item));
    }

    private int selectedProjectId() {
        Project selectedProject = projectsTable.getSelectionModel().getSelectedItem();
        return selectedProject == null ? -1 : selectedProject.getId();
    }

    private int selectedActivityProjectId() {
        Project selectedProject = activityProjectFilterBox.getValue();
        return selectedProject == null ? -1 : selectedProject.getId();
    }

    private void restoreProjectSelection(int projectId) {
        if (projectId < 0) {
            return;
        }
        projectsTable.getItems().stream()
                .filter(project -> project.getId() == projectId)
                .findFirst()
                .ifPresent(project -> projectsTable.getSelectionModel().select(project));
    }

    private void restoreActivityProjectSelection(int projectId) {
        if (projectId < 0) {
            activityProjectFilterBox.getSelectionModel().clearSelection();
            return;
        }
        activityProjectFilterBox.getItems().stream()
                .filter(project -> project.getId() == projectId)
                .findFirst()
                .ifPresentOrElse(
                        project -> activityProjectFilterBox.getSelectionModel().select(project),
                        () -> activityProjectFilterBox.getSelectionModel().clearSelection()
                );
    }

    private static String activityDateSortValue(ProjectActivity activity) {
        String activityDate = activity.getActivityDate();
        return activityDate == null ? "" : activityDate;
    }

    private void configureContextMenus() {
        TableActions.installRowContextMenu(projectsTable, this::projectMenuItems);
        TableActions.installRowContextMenu(activitiesTable, this::activityMenuItems);
    }

    private List<javafx.scene.control.MenuItem> projectMenuItems(Project project) {
        return List.of(
                TableActions.menuItem("View Project Details", () -> viewProjectDetails(project)),
                TableActions.menuItem("Show Project Activities", () -> selectActivityProject(project)),
                TableActions.menuItem("Record Project Expense", () -> recordProjectExpense(project)),
                TableActions.separator(),
                TableActions.copyRowItem(projectsTable, project),
                TableActions.exportTableItem(projectsTable, "Project List"),
                TableActions.printTableItem(projectsTable, "Project List"),
                TableActions.refreshItem(this::refresh)
        );
    }

    private List<javafx.scene.control.MenuItem> activityMenuItems(ProjectActivity activity) {
        return List.of(
                TableActions.menuItem("View Activity Details", () -> viewActivityDetails(activity)),
                TableActions.menuItem("Show Activity Project", () -> showActivityProject(activity)),
                TableActions.menuItem("Record Activity Expense", () -> recordActivityExpense(activity)),
                TableActions.separator(),
                TableActions.copyRowItem(activitiesTable, activity),
                TableActions.exportTableItem(activitiesTable, selectedProjectActivitiesTitle()),
                TableActions.printTableItem(activitiesTable, selectedProjectActivitiesTitle()),
                TableActions.refreshItem(this::refresh)
        );
    }

    private void viewProjectDetails(Project project) {
        if (project == null) {
            return;
        }
        UiAlerts.info(
                "Project: " + project.getProjectName()
                        + "\nStatus: " + blankToDash(project.getStatus())
                        + "\nPlanned Budget: " + MoneyUtil.mwk(project.getPlannedBudget())
                        + "\nSpent: " + MoneyUtil.mwk(project.getAmountSpent())
                        + "\nRemaining: " + MoneyUtil.mwk(project.getRemainingBudget())
                        + "\nStart Date: " + blankToDash(project.getStartDate())
                        + "\nEnd Date: " + blankToDash(project.getEndDate())
                        + "\nDescription: " + blankToDash(project.getDescription())
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
                        + "\nNotes: " + reasonOrDescription(activity)
        );
    }

    private void recordProjectExpense(Project project) {
        if (project != null) {
            projectsTable.getSelectionModel().select(project);
            selectActivityProject(project);
        }
        NavigationBus.requestTransaction("EXPENSE", "PROJECT_EXPENSE", null);
        NavigationBus.showTransactionEntry("Record Project Expense");
    }

    private void showActivityProject(ProjectActivity activity) {
        if (activity == null) {
            return;
        }
        projects.stream()
                .filter(project -> project.getId() == activity.getProjectId())
                .findFirst()
                .ifPresent(project -> {
                    projectsTable.getSelectionModel().select(project);
                    selectActivityProject(project);
                });
    }

    private void recordActivityExpense(ProjectActivity activity) {
        showActivityProject(activity);
        NavigationBus.requestTransaction("EXPENSE", "PROJECT_EXPENSE", null);
        NavigationBus.showTransactionEntry("Record Project Expense");
    }

    private String selectedProjectActivitiesTitle() {
        Project project = activityProjectFilterBox.getValue();
        return project == null ? "Project Activities" : project.getProjectName() + " Activities";
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

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }
}
