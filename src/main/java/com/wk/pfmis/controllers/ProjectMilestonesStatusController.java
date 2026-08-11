package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectMilestone;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.List;

public class ProjectMilestonesStatusController {
    @FXML private ComboBox<Project> projectBox;
    @FXML private ComboBox<String> projectStatusBox;
    @FXML private Label currentStatusLabel;
    @FXML private Label resultLabel;
    @FXML private TextField milestoneNameField;
    @FXML private DatePicker milestoneTargetDatePicker;
    @FXML private DatePicker milestoneCompletionDatePicker;
    @FXML private ComboBox<String> milestoneStatusBox;
    @FXML private TextArea milestoneNotesArea;
    @FXML private Label milestoneStateLabel;
    @FXML private TableView<ProjectMilestone> milestonesTable;
    @FXML private TableColumn<ProjectMilestone, String> milestoneNameColumn;
    @FXML private TableColumn<ProjectMilestone, String> milestoneTargetColumn;
    @FXML private TableColumn<ProjectMilestone, String> milestoneCompletionColumn;
    @FXML private TableColumn<ProjectMilestone, String> milestoneStatusColumn;
    @FXML private TableColumn<ProjectMilestone, String> milestoneNotesColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Project> projects = List.of();
    private ProjectMilestone selectedMilestone;

    @FXML
    public void initialize() {
        CoreWorkspaceSupport.setComboItems(projectStatusBox, "Active", "Draft", "Planned", "Active", "At Risk", "Delayed", "Paused", "Completed", "Cancelled", "Archived");
        CoreWorkspaceSupport.setComboItems(milestoneStatusBox, "Not Started", "Not Started", "In Progress", "Completed", "Delayed", "Skipped");
        milestoneTargetDatePicker.setValue(LocalDate.now());
        configureTable();
        projectBox.valueProperty().addListener((observable, oldValue, newValue) -> showProject(newValue));
        milestonesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> applySelectedMilestone(newValue));
        refresh();
    }

    @FXML
    private void saveProjectStatus() {
        Project project = projectBox.getValue();
        if (project == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        try {
            database.updateProjectStatus(project.getId(), projectStatusBox.getValue());
            DataRefreshBus.notifyDataChanged();
            resultLabel.setText("Project status updated.");
            refresh();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update project status", exception);
        }
    }

    @FXML
    private void saveMilestone() {
        try {
            Project project = projectBox.getValue();
            if (project == null) {
                throw new IllegalArgumentException("Select a project first.");
            }
            String name = CoreWorkspaceSupport.required(milestoneNameField, "Milestone name");
            LocalDate target = CoreWorkspaceSupport.requiredDate(milestoneTargetDatePicker, "Target date");
            LocalDate completion = milestoneCompletionDatePicker.getValue();
            if (completion != null && completion.isBefore(target)) {
                throw new IllegalArgumentException("Completion date cannot be before target date.");
            }
            database.saveProjectMilestone(
                    selectedMilestone == null ? null : selectedMilestone.getId(),
                    project.getId(),
                    name,
                    target.toString(),
                    completion == null ? "" : completion.toString(),
                    CoreWorkspaceSupport.selected(milestoneStatusBox, "Not Started"),
                    milestoneNotesArea.getText()
            );
            resultLabel.setText("Project milestone saved.");
            DataRefreshBus.notifyDataChanged();
            clearMilestone();
            refresh();
        } catch (IllegalArgumentException exception) {
            resultLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save project milestone", exception);
        }
    }

    @FXML
    private void clearMilestone() {
        selectedMilestone = null;
        milestoneNameField.clear();
        milestoneTargetDatePicker.setValue(LocalDate.now());
        milestoneCompletionDatePicker.setValue(null);
        milestoneStatusBox.getSelectionModel().select("Not Started");
        milestoneNotesArea.clear();
    }

    @FXML
    private void refresh() {
        Integer selectedId = projectBox.getValue() == null ? null : projectBox.getValue().getId();
        projects = database.listProjects();
        projectBox.setItems(FXCollections.observableArrayList(projects));
        Project selected = selectedId == null ? null : projects.stream()
                .filter(project -> project.getId() == selectedId)
                .findFirst()
                .orElse(null);
        if (selected == null && !projects.isEmpty()) {
            selected = projects.get(0);
        }
        projectBox.getSelectionModel().select(selected);
        showProject(selected);
    }

    @FXML
    private void openHistory() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.PROJECT_HISTORY);
    }

    private void configureTable() {
        CoreWorkspaceSupport.bind(milestoneNameColumn, ProjectMilestone::getMilestoneName);
        CoreWorkspaceSupport.bind(milestoneTargetColumn, ProjectMilestone::getTargetDate);
        CoreWorkspaceSupport.bind(milestoneCompletionColumn, milestone -> CoreWorkspaceSupport.dash(milestone.getCompletionDate()));
        CoreWorkspaceSupport.bind(milestoneStatusColumn, ProjectMilestone::getStatus);
        CoreWorkspaceSupport.bind(milestoneNotesColumn, milestone -> CoreWorkspaceSupport.dash(milestone.getNotes()));
        TableActions.configureScrollableTable(milestonesTable);
    }

    private void showProject(Project project) {
        if (project == null) {
            currentStatusLabel.setText("-");
            CoreWorkspaceSupport.setItems(milestonesTable, List.of(), milestoneStateLabel, "No project selected.");
            return;
        }
        currentStatusLabel.setText(CoreWorkspaceSupport.dash(project.getStatus()));
        projectStatusBox.getSelectionModel().select(CoreWorkspaceSupport.blank(project.getStatus(), "Active"));
        CoreWorkspaceSupport.setItems(milestonesTable, database.listProjectMilestones(project.getId()), milestoneStateLabel, "No milestones recorded.");
    }

    private void applySelectedMilestone(ProjectMilestone milestone) {
        selectedMilestone = milestone;
        if (milestone == null) {
            return;
        }
        milestoneNameField.setText(milestone.getMilestoneName());
        milestoneTargetDatePicker.setValue(parseDate(milestone.getTargetDate()));
        milestoneCompletionDatePicker.setValue(parseDateOrNull(milestone.getCompletionDate()));
        milestoneStatusBox.getSelectionModel().select(CoreWorkspaceSupport.blank(milestone.getStatus(), "Not Started"));
        milestoneNotesArea.setText(CoreWorkspaceSupport.safe(milestone.getNotes()));
        resultLabel.setText("Editing milestone #" + milestone.getId() + ".");
    }

    private LocalDate parseDate(String value) {
        LocalDate parsed = parseDateOrNull(value);
        return parsed == null ? LocalDate.now() : parsed;
    }

    private LocalDate parseDateOrNull(String value) {
        try {
            return CoreWorkspaceSupport.safe(value).isBlank() ? null : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
