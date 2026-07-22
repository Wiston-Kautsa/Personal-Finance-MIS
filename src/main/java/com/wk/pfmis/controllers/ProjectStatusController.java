package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Project;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

public class ProjectStatusController {
    @FXML private ComboBox<Project> projectBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private Label currentStatusLabel;
    @FXML private Label selectedProjectLabel;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        statusBox.setItems(FXCollections.observableArrayList("ACTIVE", "PLANNED", "COMPLETED", "ON HOLD", "CANCELLED"));
        projectBox.valueProperty().addListener((observable, oldValue, newValue) -> showSelectedProjectStatus(newValue));
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

    private void refresh() {
        Integer selectedProjectId = projectBox.getValue() == null ? null : projectBox.getValue().getId();
        var projects = database.listProjects();
        projectBox.setItems(FXCollections.observableArrayList(projects));
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
