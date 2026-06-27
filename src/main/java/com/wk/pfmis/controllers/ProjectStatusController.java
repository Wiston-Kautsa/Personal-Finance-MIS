package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class ProjectStatusController {
    @FXML private ComboBox<Project> projectBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private Label currentStatusLabel;
    @FXML private Label selectedProjectLabel;
    @FXML private TableView<Project> projectsTable;
    @FXML private TableColumn<Project, String> nameColumn;
    @FXML private TableColumn<Project, String> plannedColumn;
    @FXML private TableColumn<Project, String> spentColumn;
    @FXML private TableColumn<Project, String> remainingColumn;
    @FXML private TableColumn<Project, String> startDateColumn;
    @FXML private TableColumn<Project, String> endDateColumn;
    @FXML private TableColumn<Project, String> statusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        statusBox.setItems(FXCollections.observableArrayList("ACTIVE", "PLANNED", "COMPLETED", "ON HOLD", "CANCELLED"));
        projectBox.valueProperty().addListener((observable, oldValue, newValue) -> showSelectedProjectStatus(newValue));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("projectName"));
        plannedColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getPlannedBudget())));
        spentColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountSpent())));
        remainingColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getRemainingBudget())));
        startDateColumn.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        endDateColumn.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        refresh();
    }

    @FXML
    private void saveStatus() {
        Project project = projectBox.getValue();
        if (project == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        database.updateProjectStatus(project.getId(), statusBox.getValue());
        refresh();
        DataRefreshBus.notifyDataChanged();
        UiAlerts.info("Project status updated.");
    }

    @FXML
    private void refresh() {
        Integer selectedProjectId = projectBox.getValue() == null ? null : projectBox.getValue().getId();
        var projects = database.listProjects();
        projectBox.setItems(FXCollections.observableArrayList(projects));
        projectsTable.setItems(FXCollections.observableArrayList(projects));
        Project selected = projects.stream()
                .filter(project -> selectedProjectId != null && project.getId() == selectedProjectId)
                .findFirst()
                .orElse(projects.isEmpty() ? null : projects.get(0));
        projectBox.getSelectionModel().select(selected);
        showSelectedProjectStatus(selected);
    }

    private void showSelectedProjectStatus(Project project) {
        selectedProjectLabel.setText(project == null ? "No Project Selected" : project.getProjectName());
        currentStatusLabel.setText(project == null ? "-" : project.getStatus());
        statusBox.getSelectionModel().select(project == null ? "ACTIVE" : project.getStatus());
    }
}
