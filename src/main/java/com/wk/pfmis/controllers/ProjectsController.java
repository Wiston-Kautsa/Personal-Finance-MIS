package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Goal;
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
    private static final List<String> PROJECT_STATUSES = List.of("Draft", "Planned", "Active", "At Risk", "Delayed", "Paused", "Completed", "Cancelled", "Archived");
    private static final List<String> PROJECT_TYPES = List.of("Business", "Construction", "Agriculture", "Education", "Household", "Technology", "Community", "Personal", "Other");
    private static final List<String> PRIORITIES = List.of("Critical", "High", "Medium", "Optional");

    @FXML private TextField projectNameField;
    @FXML private ComboBox<String> projectTypeBox;
    @FXML private TextField plannedBudgetField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextField projectOwnerField;
    @FXML private ComboBox<String> priorityBox;
    @FXML private TextField currencyField;
    @FXML private TextField fundingSourceField;
    @FXML private ComboBox<Account> fundingAccountBox;
    @FXML private ComboBox<Goal> linkedGoalBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea descriptionArea;
    @FXML private TextArea notesArea;
    @FXML private Label projectCountLabel;
    @FXML private Label activationSummaryLabel;
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
        statusBox.getSelectionModel().select("Draft");
        projectTypeBox.setItems(FXCollections.observableArrayList(PROJECT_TYPES));
        projectTypeBox.getSelectionModel().select("Other");
        priorityBox.setItems(FXCollections.observableArrayList(PRIORITIES));
        priorityBox.getSelectionModel().select("Medium");
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
        activateProject();
    }

    @FXML
    private void saveDraft() {
        saveProjectWithStatus("Draft");
    }

    @FXML
    private void activateProject() {
        if (!activationProblem().isBlank()) {
            UiAlerts.info(activationProblem());
            return;
        }
        saveProjectWithStatus("Active");
    }

    private void saveProjectWithStatus(String status) {
        try {
            String name = projectNameField.getText().trim();
            if (name.isEmpty()) {
                UiAlerts.info("Enter a project name.");
                return;
            }
            double plannedBudget = plannedBudgetValue();
            database.addProject(
                    name,
                    projectTypeBox.getValue(),
                    descriptionArea.getText().trim(),
                    startDatePicker.getValue() == null ? null : startDatePicker.getValue().toString(),
                    endDatePicker.getValue() == null ? null : endDatePicker.getValue().toString(),
                    textValue(projectOwnerField),
                    priorityBox.getValue(),
                    textValue(currencyField),
                    plannedBudget,
                    textValue(fundingSourceField),
                    fundingAccountBox.getValue() == null ? null : fundingAccountBox.getValue().getId(),
                    linkedGoalBox.getValue() == null ? null : linkedGoalBox.getValue().getId(),
                    status,
                    textValue(notesArea)
            );
            if ("Active".equals(status)) {
                activationSummaryLabel.setText("Project activated. Planned budget " + MoneyUtil.mwk(plannedBudget)
                        + ", available funding " + MoneyUtil.mwk(availableFunding())
                        + ", funding gap " + MoneyUtil.mwk(Math.max(0, plannedBudget - availableFunding()))
                        + ", expected completion " + blankToDash(endDatePicker.getValue() == null ? null : endDatePicker.getValue().toString()) + ".");
            } else {
                activationSummaryLabel.setText("Project draft saved. It will not generate project monitoring alerts until activation.");
            }
            clearForm();
            refreshProjects();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save project", exception);
        }
    }

    @FXML
    private void updateProject() {
        try {
            Project selectedProject = projectsTable.getSelectionModel().getSelectedItem();
            if (selectedProject == null) {
                UiAlerts.info("Select a project first.");
                return;
            }
            database.updateProject(
                    selectedProject.getId(),
                    textValue(projectNameField),
                    projectTypeBox.getValue(),
                    textValue(descriptionArea),
                    startDatePicker.getValue() == null ? null : startDatePicker.getValue().toString(),
                    endDatePicker.getValue() == null ? null : endDatePicker.getValue().toString(),
                    textValue(projectOwnerField),
                    priorityBox.getValue(),
                    textValue(currencyField),
                    plannedBudgetValue(),
                    textValue(fundingSourceField),
                    fundingAccountBox.getValue() == null ? null : fundingAccountBox.getValue().getId(),
                    linkedGoalBox.getValue() == null ? null : linkedGoalBox.getValue().getId(),
                    statusBox.getValue(),
                    textValue(notesArea)
            );
            clearForm();
            refreshProjects(selectedProject.getId());
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Project updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update project", exception);
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
        projectTypeBox.getSelectionModel().select("Other");
        plannedBudgetField.clear();
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        projectOwnerField.clear();
        priorityBox.getSelectionModel().select("Medium");
        currencyField.setText("MWK");
        fundingSourceField.clear();
        fundingAccountBox.getSelectionModel().clearSelection();
        linkedGoalBox.getSelectionModel().clearSelection();
        statusBox.getSelectionModel().select("Draft");
        descriptionArea.clear();
        notesArea.clear();
    }

    private void refreshProjects() {
        int selectedProjectId = selectedProjectId();
        refreshProjects(selectedProjectId);
    }

    private void refreshProjects(int selectedProjectId) {
        List<Project> projects = database.listProjects();
        fundingAccountBox.setItems(FXCollections.observableArrayList(database.listAccounts()));
        linkedGoalBox.setItems(FXCollections.observableArrayList(database.listGoals()));
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
        fillForm(project);
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
                        + "\nType: " + blankToDash(project.getProjectType())
                        + "\nPriority: " + blankToDash(project.getPriority())
                        + "\nOwner: " + blankToDash(project.getProjectOwner())
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
        NavigationBus.requestProjectExpense(project == null ? null : project.getId(), null);
        NavigationBus.showTransactionEntry("Record Project Expense");
    }

    private void fillForm(Project project) {
        projectNameField.setText(project.getProjectName());
        projectTypeBox.getSelectionModel().select(blankOrDefault(project.getProjectType(), "Other"));
        plannedBudgetField.setText(String.format("%.2f", project.getPlannedBudget()));
        startDatePicker.setValue(parseDate(project.getStartDate()));
        endDatePicker.setValue(parseDate(project.getEndDate()));
        projectOwnerField.setText(blankOrDefault(project.getProjectOwner(), ""));
        priorityBox.getSelectionModel().select(blankOrDefault(project.getPriority(), "Medium"));
        currencyField.setText(blankOrDefault(project.getCurrency(), "MWK"));
        fundingSourceField.setText(blankOrDefault(project.getFundingSource(), ""));
        selectAccount(project.getFundingAccountId());
        selectGoal(project.getLinkedGoalId());
        statusBox.getSelectionModel().select(blankOrDefault(project.getStatus(), "Draft"));
        descriptionArea.setText(blankOrDefault(project.getDescription(), ""));
        notesArea.setText(blankOrDefault(project.getNotes(), ""));
    }

    private String activationProblem() {
        String name = textValue(projectNameField);
        if (name.isBlank()) {
            return "Project name is required.";
        }
        if (database.projectExistsByName(name)) {
            return "A project with this name already exists. Open Project Records to edit it.";
        }
        if (plannedBudgetValue() < 0) {
            return "Planned budget cannot be negative.";
        }
        if (startDatePicker.getValue() != null && endDatePicker.getValue() != null
                && endDatePicker.getValue().isBefore(startDatePicker.getValue())) {
            return "Expected completion date cannot be before the start date.";
        }
        if (textValue(currencyField).isBlank()) {
            return "Currency is required.";
        }
        return "";
    }

    private double availableFunding() {
        Account account = fundingAccountBox.getValue();
        return account == null ? 0 : Math.max(0, account.getCurrentBalance());
    }

    private void selectAccount(Integer accountId) {
        fundingAccountBox.getSelectionModel().clearSelection();
        if (accountId == null) {
            return;
        }
        fundingAccountBox.getItems().stream()
                .filter(account -> account.getId() == accountId)
                .findFirst()
                .ifPresent(account -> fundingAccountBox.getSelectionModel().select(account));
    }

    private void selectGoal(Integer goalId) {
        linkedGoalBox.getSelectionModel().clearSelection();
        if (goalId == null) {
            return;
        }
        linkedGoalBox.getItems().stream()
                .filter(goal -> goal.getId() == goalId)
                .findFirst()
                .ifPresent(goal -> linkedGoalBox.getSelectionModel().select(goal));
    }

    private String textValue(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String textValue(TextArea area) {
        return area.getText() == null ? "" : area.getText().trim();
    }

    private java.time.LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : java.time.LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private String blankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
