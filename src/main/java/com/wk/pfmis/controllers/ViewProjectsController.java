package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectActivity;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ViewProjectsController {
    @FXML private ComboBox<Project> summaryProjectBox;
    @FXML private Label projectNameLabel;
    @FXML private Label statusLabel;
    @FXML private Label startDateLabel;
    @FXML private Label endDateLabel;
    @FXML private Label totalActivitiesLabel;
    @FXML private Label totalSpentLabel;
    @FXML private Label averageActivityCostLabel;
    @FXML private Label lastActivityLabel;
    @FXML private Label mostUsedCategoryLabel;
    @FXML private Label accountsUsedLabel;
    @FXML private Label descriptionLabel;
    @FXML private TableView<ProjectActivity> recentActivitiesTable;
    @FXML private TableColumn<ProjectActivity, String> activityDateColumn;
    @FXML private TableColumn<ProjectActivity, String> activityNameColumn;
    @FXML private TableColumn<ProjectActivity, String> activityCategoryColumn;
    @FXML private TableColumn<ProjectActivity, String> activityAccountColumn;
    @FXML private TableColumn<ProjectActivity, String> activityCostColumn;
    @FXML private TableColumn<ProjectActivity, String> activityNotesColumn;
    @FXML private TextField projectSearchField;
    @FXML private ComboBox<String> statusFilterBox;
    @FXML private TableView<Project> projectsTable;
    @FXML private TableColumn<Project, String> nameColumn;
    @FXML private TableColumn<Project, String> statusColumn;
    @FXML private TableColumn<Project, String> activityCountColumn;
    @FXML private TableColumn<Project, String> spentColumn;
    @FXML private TableColumn<Project, String> lastActivityColumn;
    @FXML private TableColumn<Project, String> startDateColumn;
    @FXML private TableColumn<Project, String> endDateColumn;
    @FXML private TableColumn<Project, Void> actionsColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Project> projects = List.of();
    private List<ProjectActivity> activities = List.of();

    @FXML
    public void initialize() {
        statusFilterBox.setItems(FXCollections.observableArrayList("All Statuses", "ACTIVE", "PLANNED", "COMPLETED", "ON HOLD", "CANCELLED"));
        statusFilterBox.getSelectionModel().select("All Statuses");
        projectSearchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        statusFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        summaryProjectBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            showProjectSummary(newValue);
            syncTableSelection(newValue);
        });
        projectsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                summaryProjectBox.getSelectionModel().select(newValue);
            }
        });

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("projectName"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        activityCountColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(projectActivities(cell.getValue()).size())));
        spentColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountSpent())));
        lastActivityColumn.setCellValueFactory(cell -> new SimpleStringProperty(lastActivityDate(cell.getValue())));
        startDateColumn.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        endDateColumn.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        actionsColumn.setCellFactory(column -> new ProjectActionsCell());

        activityDateColumn.setCellValueFactory(new PropertyValueFactory<>("activityDate"));
        activityNameColumn.setCellValueFactory(new PropertyValueFactory<>("activityName"));
        activityCategoryColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getCategoryName())));
        activityAccountColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getAccountName())));
        activityCostColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountUsed())));
        activityNotesColumn.setCellValueFactory(cell -> new SimpleStringProperty(reasonOrDescription(cell.getValue())));
        refresh();
    }

    @FXML
    private void refresh() {
        Project selectedProject = summaryProjectBox.getValue();
        projects = database.listProjects();
        activities = database.listProjectActivities();
        summaryProjectBox.setItems(FXCollections.observableArrayList(projects));
        if (statusFilterBox.getValue() == null) {
            statusFilterBox.getSelectionModel().select("All Statuses");
        }
        applyFilters();

        if (selectedProject != null) {
            projects.stream()
                    .filter(project -> project.getId() == selectedProject.getId())
                    .findFirst()
                    .ifPresent(project -> summaryProjectBox.getSelectionModel().select(project));
        }
        if (summaryProjectBox.getValue() == null && !projects.isEmpty()) {
            summaryProjectBox.getSelectionModel().selectFirst();
        }
        showProjectSummary(summaryProjectBox.getValue());
        syncTableSelection(summaryProjectBox.getValue());
    }

    @FXML
    private void viewAllActivities() {
        loadProjectActivities();
    }

    private void applyFilters() {
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
        syncTableSelection(summaryProjectBox.getValue());
    }

    private void showProjectSummary(Project project) {
        if (project == null) {
            projectNameLabel.setText("No Project Selected");
            statusLabel.setText("-");
            startDateLabel.setText("-");
            endDateLabel.setText("-");
            totalActivitiesLabel.setText("0");
            totalSpentLabel.setText(MoneyUtil.mwk(0));
            averageActivityCostLabel.setText(MoneyUtil.mwk(0));
            lastActivityLabel.setText("-");
            mostUsedCategoryLabel.setText("-");
            accountsUsedLabel.setText("-");
            descriptionLabel.setText("-");
            recentActivitiesTable.setItems(FXCollections.observableArrayList());
            return;
        }

        List<ProjectActivity> projectActivities = projectActivities(project);
        double totalSpent = projectActivities.stream().mapToDouble(ProjectActivity::getAmountUsed).sum();
        double averageCost = projectActivities.isEmpty() ? 0 : totalSpent / projectActivities.size();
        projectNameLabel.setText(project.getProjectName());
        statusLabel.setText(project.getStatus());
        startDateLabel.setText(blankToDash(project.getStartDate()));
        endDateLabel.setText(blankToDash(project.getEndDate()));
        totalActivitiesLabel.setText(String.valueOf(projectActivities.size()));
        totalSpentLabel.setText(MoneyUtil.mwk(totalSpent));
        averageActivityCostLabel.setText(MoneyUtil.mwk(averageCost));
        lastActivityLabel.setText(lastActivityText(projectActivities));
        mostUsedCategoryLabel.setText(mostUsedValue(projectActivities, ProjectActivity::getCategoryName));
        accountsUsedLabel.setText(accountsUsedText(projectActivities));
        descriptionLabel.setText(blankToDash(project.getDescription()));
        recentActivitiesTable.setItems(FXCollections.observableArrayList(recentActivities(projectActivities)));
    }

    private List<ProjectActivity> projectActivities(Project project) {
        if (project == null) {
            return List.of();
        }
        return activities.stream()
                .filter(activity -> activity.getProjectId() == project.getId())
                .toList();
    }

    private List<ProjectActivity> recentActivities(List<ProjectActivity> projectActivities) {
        return projectActivities.stream()
                .filter(activity -> activity.getActivityDate() != null && !activity.getActivityDate().isBlank())
                .sorted(Comparator.comparing(ProjectActivity::getActivityDate).reversed())
                .limit(5)
                .toList();
    }

    private String lastActivityDate(Project project) {
        return projectActivities(project).stream()
                .map(ProjectActivity::getActivityDate)
                .filter(value -> value != null && !value.isBlank())
                .max(String::compareTo)
                .orElse("-");
    }

    private String lastActivityText(List<ProjectActivity> projectActivities) {
        return projectActivities.stream()
                .filter(activity -> activity.getActivityDate() != null && !activity.getActivityDate().isBlank())
                .max(Comparator.comparing(ProjectActivity::getActivityDate))
                .map(activity -> activity.getActivityName() + " (" + activity.getActivityDate() + ")")
                .orElse("-");
    }

    private String mostUsedValue(List<ProjectActivity> projectActivities, Function<ProjectActivity, String> valueMapper) {
        return projectActivities.stream()
                .map(valueMapper)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");
    }

    private String accountsUsedText(List<ProjectActivity> projectActivities) {
        String text = projectActivities.stream()
                .map(ProjectActivity::getAccountName)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        return text.isBlank() ? "-" : text;
    }

    private String reasonOrDescription(ProjectActivity activity) {
        if (activity.getReason() != null && !activity.getReason().isBlank()) {
            return activity.getReason();
        }
        if (activity.getDescription() != null && !activity.getDescription().isBlank()) {
            return activity.getDescription();
        }
        return "-";
    }

    private void closeOrReopenProject(Project project) {
        if (project == null) {
            UiAlerts.info("Select a project first.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("PFMIS");
        boolean completed = "COMPLETED".equals(project.getStatus());
        alert.setHeaderText(completed ? "Reopen project?" : "Close project?");
        alert.setContentText(completed ? "The project status will become ACTIVE." : "The project status will become COMPLETED.");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        database.updateProjectStatus(project.getId(), completed ? "ACTIVE" : "COMPLETED");
        refresh();
        DataRefreshBus.notifyDataChanged();
    }

    private void loadProjectActivities() {
        try {
            StackPane container = findContentContainer();
            if (container == null) {
                UiAlerts.info("Open Add Project Activity from the Projects menu.");
                return;
            }
            Parent view = FXMLLoader.load(getClass().getResource("/com/wk/pfmis/views/ProjectActivities.fxml"));
            container.getChildren().setAll(view);
        } catch (IOException exception) {
            UiAlerts.error("Failed to open project activities", exception);
        }
    }

    private StackPane findContentContainer() {
        Parent parent = projectsTable.getParent();
        while (parent != null && !(parent instanceof StackPane)) {
            parent = parent.getParent();
        }
        return parent instanceof StackPane stackPane ? stackPane : null;
    }

    private void syncTableSelection(Project project) {
        if (project == null) {
            projectsTable.getSelectionModel().clearSelection();
            return;
        }
        projectsTable.getItems().stream()
                .filter(rowProject -> rowProject.getId() == project.getId())
                .findFirst()
                .ifPresentOrElse(
                        rowProject -> projectsTable.getSelectionModel().select(rowProject),
                        () -> projectsTable.getSelectionModel().clearSelection()
                );
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }

    private final class ProjectActionsCell extends TableCell<Project, Void> {
        private final Button addActivityButton = new Button("Add Activity");
        private final Button viewActivitiesButton = new Button("View Activities");
        private final Button summaryButton = new Button("Summary");
        private final Button closeButton = new Button("Close");
        private final HBox actions = new HBox(6, addActivityButton, viewActivitiesButton, summaryButton, closeButton);

        private ProjectActionsCell() {
            addActivityButton.setOnAction(event -> loadProjectActivities());
            viewActivitiesButton.setOnAction(event -> loadProjectActivities());
            summaryButton.setOnAction(event -> summaryProjectBox.getSelectionModel().select(getTableView().getItems().get(getIndex())));
            closeButton.setOnAction(event -> closeOrReopenProject(getTableView().getItems().get(getIndex())));
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                setGraphic(null);
                return;
            }
            Project project = getTableView().getItems().get(getIndex());
            addActivityButton.setDisable("COMPLETED".equals(project.getStatus()));
            closeButton.setText("COMPLETED".equals(project.getStatus()) ? "Reopen" : "Close");
            setGraphic(actions);
        }
    }
}
