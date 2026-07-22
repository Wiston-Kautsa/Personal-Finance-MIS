package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Project;
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

import java.util.ArrayList;
import java.util.List;

public class ProjectsController {
    private static final List<String> PROJECT_STATUSES = List.of("ACTIVE", "PLANNED", "COMPLETED", "ON HOLD", "CANCELLED");

    @FXML private TextField projectNameField;
    @FXML private TextField plannedBudgetField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea descriptionArea;
    @FXML private Label projectCountLabel;
    @FXML private ComboBox<String> selectedProjectStatusBox;
    @FXML private TableView<Project> projectsTable;
    @FXML private TableColumn<Project, String> nameColumn;
    @FXML private TableColumn<Project, String> plannedBudgetColumn;
    @FXML private TableColumn<Project, String> spentColumn;
    @FXML private TableColumn<Project, String> remainingColumn;
    @FXML private TableColumn<Project, String> startDateColumn;
    @FXML private TableColumn<Project, String> endDateColumn;
    @FXML private TableColumn<Project, String> statusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        statusBox.setItems(FXCollections.observableArrayList(PROJECT_STATUSES));
        statusBox.getSelectionModel().select("ACTIVE");
        selectedProjectStatusBox.setItems(FXCollections.observableArrayList(PROJECT_STATUSES));

        projectsTable.setPlaceholder(new Label("No projects registered yet."));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("projectName"));
        plannedBudgetColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getPlannedBudget())));
        spentColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountSpent())));
        remainingColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getRemainingBudget())));
        startDateColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getStartDate())));
        endDateColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getEndDate())));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getStatus())));
        projectsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> syncSelectedProjectStatus(newValue));
        configureContextMenu();

        DataRefreshBus.addListener(this::refreshProjects);
        refreshProjects();
    }

    @FXML
    private void addProject() {
        try {
            String name = projectNameField.getText().trim();
            if (name.isEmpty()) {
                UiAlerts.info("Enter a project name.");
                return;
            }
            database.addProject(
                    name,
                    descriptionArea.getText().trim(),
                    plannedBudgetValue(),
                    startDatePicker.getValue() == null ? null : startDatePicker.getValue().toString(),
                    endDatePicker.getValue() == null ? null : endDatePicker.getValue().toString(),
                    statusBox.getValue()
            );
            clearForm();
            refreshProjects();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to add project", exception);
        }
    }

    @FXML
    private void updateSelectedProjectStatus() {
        try {
            Project selectedProject = projectsTable.getSelectionModel().getSelectedItem();
            if (selectedProject == null) {
                UiAlerts.info("Select a project first.");
                return;
            }
            String status = selectedProjectStatusBox.getValue();
            if (status == null || status.isBlank()) {
                UiAlerts.info("Select the new project status.");
                return;
            }
            database.updateProjectStatus(selectedProject.getId(), status);
            refreshProjects(selectedProject.getId());
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Project status updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update project status", exception);
        }
    }

    @FXML
    private void clearForm() {
        projectNameField.clear();
        plannedBudgetField.clear();
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        statusBox.getSelectionModel().select("ACTIVE");
        descriptionArea.clear();
    }

    private void refreshProjects() {
        int selectedProjectId = selectedProjectId();
        refreshProjects(selectedProjectId);
    }

    private void refreshProjects(int selectedProjectId) {
        List<Project> projects = database.listProjects();
        projectsTable.setItems(FXCollections.observableArrayList(projects));
        projectCountLabel.setText(projects.size() == 1 ? "1 project registered" : projects.size() + " projects registered");
        restoreSelection(selectedProjectId);
        if (projectsTable.getSelectionModel().getSelectedItem() == null && !projects.isEmpty()) {
            projectsTable.getSelectionModel().selectFirst();
        }
        syncSelectedProjectStatus(projectsTable.getSelectionModel().getSelectedItem());
    }

    private int selectedProjectId() {
        Project selectedProject = projectsTable.getSelectionModel().getSelectedItem();
        return selectedProject == null ? -1 : selectedProject.getId();
    }

    private void restoreSelection(int projectId) {
        if (projectId < 0) {
            return;
        }
        projectsTable.getItems().stream()
                .filter(project -> project.getId() == projectId)
                .findFirst()
                .ifPresent(project -> projectsTable.getSelectionModel().select(project));
    }

    private void syncSelectedProjectStatus(Project project) {
        if (project == null) {
            selectedProjectStatusBox.getSelectionModel().clearSelection();
            return;
        }
        selectedProjectStatusBox.getSelectionModel().select(project.getStatus());
    }

    private void configureContextMenu() {
        TableActions.installRowContextMenu(projectsTable, this::projectMenuItems);
    }

    private List<javafx.scene.control.MenuItem> projectMenuItems(Project project) {
        List<javafx.scene.control.MenuItem> items = new ArrayList<>();
        items.add(TableActions.menuItem("View Project Details", () -> viewProjectDetails(project)));
        items.add(TableActions.menuItem("Record Project Expense", () -> recordProjectExpense(project)));
        for (String status : PROJECT_STATUSES) {
            if (!status.equals(project.getStatus())) {
                items.add(TableActions.menuItem("Mark " + status, () -> updateProjectStatus(project, status)));
            }
        }
        items.add(TableActions.separator());
        items.add(TableActions.copyRowItem(projectsTable, project));
        items.add(TableActions.exportTableItem(projectsTable, "Projects"));
        items.add(TableActions.printTableItem(projectsTable, "Projects"));
        items.add(TableActions.refreshItem(this::refreshProjects));
        return items;
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

    private void updateProjectStatus(Project project, String status) {
        if (project == null) {
            return;
        }
        try {
            database.updateProjectStatus(project.getId(), status);
            refreshProjects(project.getId());
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Project marked " + status + ".");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update project status", exception);
        }
    }

    private void recordProjectExpense(Project project) {
        if (project != null) {
            projectsTable.getSelectionModel().select(project);
        }
        NavigationBus.requestTransaction("EXPENSE", "PROJECT_EXPENSE", null);
        NavigationBus.showTransactionEntry("Record Project Expense");
    }

    private double plannedBudgetValue() {
        String value = plannedBudgetField.getText() == null ? "" : plannedBudgetField.getText().replace(",", "").trim();
        if (value.isEmpty()) {
            return 0;
        }
        return Double.parseDouble(value);
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
