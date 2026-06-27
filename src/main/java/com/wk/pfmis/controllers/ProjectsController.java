package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class ProjectsController {
    @FXML private TextField projectNameField;
    @FXML private TextField plannedBudgetField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea descriptionArea;
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
        statusBox.getSelectionModel().select("ACTIVE");
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
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to add project", exception);
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

    @FXML
    private void refresh() {
        projectsTable.setItems(FXCollections.observableArrayList(database.listProjects()));
    }

    @FXML
    private void viewDetails() {
        Project selected = selectedProject("view details");
        if (selected != null) {
            UiAlerts.info("Open Project Details from the sidebar to view " + selected.getProjectName() + ".");
        }
    }

    @FXML
    private void addActivity() {
        Project selected = selectedProject("add an activity");
        if (selected != null) {
            UiAlerts.info("Open Project Activities from the sidebar to add spending for " + selected.getProjectName() + ".");
        }
    }

    @FXML
    private void changeStatus() {
        Project selected = selectedProject("change status");
        if (selected != null) {
            UiAlerts.info("Open Project Status from the sidebar to update " + selected.getProjectName() + ".");
        }
    }

    @FXML
    private void closeProject() {
        Project selected = selectedProject("close");
        if (selected != null) {
            UiAlerts.info("Project closing is handled from Project Status.");
        }
    }

    private Project selectedProject(String action) {
        Project selected = projectsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a project to " + action + ".");
        }
        return selected;
    }

    private double plannedBudgetValue() {
        String value = plannedBudgetField.getText() == null ? "" : plannedBudgetField.getText().replace(",", "").trim();
        if (value.isEmpty()) {
            return 0;
        }
        return Double.parseDouble(value);
    }
}
