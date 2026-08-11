package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectActivity;
import com.wk.pfmis.models.ProjectMilestone;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProjectListController {
    private static final List<String> PROJECT_STATUSES = List.of("All Statuses", "Draft", "Planned", "Active", "At Risk", "Delayed", "Paused", "Completed", "Cancelled", "Archived");
    private static final List<String> PROJECT_TYPES = List.of("All Types", "Business", "Construction", "Agriculture", "Education", "Household", "Technology", "Community", "Personal", "Other");
    private static final List<String> PRIORITIES = List.of("All Priorities", "Critical", "High", "Medium", "Optional");

    @FXML private TextField projectSearchField;
    @FXML private ComboBox<String> statusFilterBox;
    @FXML private ComboBox<String> typeFilterBox;
    @FXML private ComboBox<String> priorityFilterBox;
    @FXML private TableView<Project> projectsTable;
    @FXML private TableColumn<Project, String> nameColumn;
    @FXML private TableColumn<Project, String> typeColumn;
    @FXML private TableColumn<Project, String> plannedColumn;
    @FXML private TableColumn<Project, String> spentColumn;
    @FXML private TableColumn<Project, String> remainingColumn;
    @FXML private TableColumn<Project, String> progressColumn;
    @FXML private TableColumn<Project, String> endDateColumn;
    @FXML private TableColumn<Project, String> statusColumn;
    @FXML private Button registerProjectAssetButton;
    @FXML private MenuButton moreActionsButton;

    @FXML private Label openProjectTitleLabel;
    @FXML private TextArea overviewArea;
    @FXML private TableView<ProjectActivity> activitiesTable;
    @FXML private TableColumn<ProjectActivity, String> activityDateColumn;
    @FXML private TableColumn<ProjectActivity, String> activityNameColumn;
    @FXML private TableColumn<ProjectActivity, String> activityPlannedCostColumn;
    @FXML private TableColumn<ProjectActivity, String> activityActualCostColumn;
    @FXML private TableColumn<ProjectActivity, String> activityProgressColumn;
    @FXML private TableColumn<ProjectActivity, String> activityStatusColumn;

    @FXML private TableView<FinanceTransaction> financesTable;
    @FXML private TableColumn<FinanceTransaction, String> financeDateColumn;
    @FXML private TableColumn<FinanceTransaction, String> financeDescriptionColumn;
    @FXML private TableColumn<FinanceTransaction, String> financeTypeColumn;
    @FXML private TableColumn<FinanceTransaction, String> financeAccountColumn;
    @FXML private TableColumn<FinanceTransaction, String> financeAmountColumn;
    @FXML private TableColumn<FinanceTransaction, String> financeActivityColumn;

    @FXML private TextField milestoneNameField;
    @FXML private DatePicker milestoneTargetDatePicker;
    @FXML private DatePicker milestoneCompletionDatePicker;
    @FXML private ComboBox<String> milestoneStatusBox;
    @FXML private TextArea milestoneNotesArea;
    @FXML private TableView<ProjectMilestone> milestonesTable;
    @FXML private TableColumn<ProjectMilestone, String> milestoneNameColumn;
    @FXML private TableColumn<ProjectMilestone, String> milestoneTargetColumn;
    @FXML private TableColumn<ProjectMilestone, String> milestoneCompletionColumn;
    @FXML private TableColumn<ProjectMilestone, String> milestoneStatusColumn;
    @FXML private TextArea historyArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Project> projects = List.of();
    private List<ProjectActivity> activities = List.of();
    private Project selectedProject;
    private ProjectActivity selectedActivity;
    private ProjectMilestone selectedMilestone;

    @FXML
    public void initialize() {
        statusFilterBox.setItems(FXCollections.observableArrayList(PROJECT_STATUSES));
        statusFilterBox.getSelectionModel().select("All Statuses");
        typeFilterBox.setItems(FXCollections.observableArrayList(PROJECT_TYPES));
        typeFilterBox.getSelectionModel().select("All Types");
        priorityFilterBox.setItems(FXCollections.observableArrayList(PRIORITIES));
        priorityFilterBox.getSelectionModel().select("All Priorities");
        milestoneStatusBox.setItems(FXCollections.observableArrayList("Not Started", "In Progress", "Completed", "Delayed", "Skipped"));
        milestoneStatusBox.getSelectionModel().select("Not Started");

        configureTables();
        configureListeners();
        configureContextMenus();
        DataRefreshBus.addListener(this::refresh);
        refresh();
    }

    @FXML
    private void refresh() {
        int selectedProjectId = selectedProject == null ? -1 : selectedProject.getId();
        projects = database.listProjects();
        activities = database.listProjectActivities();
        applyFilters();
        restoreProjectSelection(selectedProjectId);
        if (selectedProject == null && !projectsTable.getItems().isEmpty()) {
            projectsTable.getSelectionModel().selectFirst();
        }
        openSelectedProject();
    }

    @FXML
    private void openSelectedProject() {
        Project project = projectsTable.getSelectionModel().getSelectedItem();
        if (project == null) {
            selectedProject = null;
            selectedActivity = null;
            openProjectTitleLabel.setText("No project selected");
            overviewArea.setText("Select a project from Project Records.");
            activitiesTable.setItems(FXCollections.observableArrayList());
            financesTable.setItems(FXCollections.observableArrayList());
            milestonesTable.setItems(FXCollections.observableArrayList());
            historyArea.clear();
            updateActionState();
            return;
        }
        selectedProject = project;
        openProjectTitleLabel.setText(project.getProjectName() + " - " + projectStatus(project));
        List<ProjectActivity> projectActivities = projectActivities(project);
        List<FinanceTransaction> finances = database.listProjectTransactions(project.getId());
        activitiesTable.setItems(FXCollections.observableArrayList(projectActivities));
        financesTable.setItems(FXCollections.observableArrayList(finances));
        milestonesTable.setItems(FXCollections.observableArrayList(database.listProjectMilestones(project.getId())));
        historyArea.setText(projectHistoryText(project));
        overviewArea.setText(projectOverview(project, projectActivities, finances));
        updateActionState();
    }

    @FXML
    private void recordProjectExpense() {
        if (selectedProject == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        NavigationBus.requestProjectExpense(selectedProject.getId(), selectedActivity == null ? null : selectedActivity.getId());
        NavigationBus.showTransactionEntry("Record Project Expense");
    }

    @FXML
    private void pauseProject() {
        updateSelectedProjectStatus("Paused");
    }

    @FXML
    private void resumeProject() {
        updateSelectedProjectStatus("Active");
    }

    @FXML
    private void closeProject() {
        if (selectedProject == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        String issue = closureIssue(selectedProject);
        if (!issue.isBlank() && !UiAlerts.confirm("Close project with unresolved items?", issue)) {
            return;
        }
        updateSelectedProjectStatus("Completed");
    }

    @FXML
    private void archiveProject() {
        updateSelectedProjectStatus("Archived");
    }

    @FXML
    private void registerSelectedProjectAsAsset() {
        if (selectedProject == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        NavigationBus.requestAssetRegistration(
                "Project",
                selectedProject.getId(),
                selectedProject.getProjectName(),
                "Project status: " + projectStatus(selectedProject)
                        + "\n\nProjects do not automatically become assets. Register an asset only when a qualifying purchase or non-cash acquisition has ownership, cost, date, location and supporting evidence."
        );
    }

    @FXML
    private void copyProject() {
        if (selectedProject == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog(selectedProject.getProjectName() + " Copy");
        dialog.setTitle("PFMIS");
        dialog.setHeaderText("Copy project as draft");
        dialog.setContentText("New project name:");
        dialog.showAndWait().map(String::trim).filter(value -> !value.isBlank()).ifPresent(name -> {
            try {
                int newProjectId = database.copyProject(selectedProject.getId(), name);
                refresh();
                restoreProjectSelection(newProjectId);
                DataRefreshBus.notifyDataChanged();
                UiAlerts.info("Project copied as draft.");
            } catch (RuntimeException exception) {
                UiAlerts.error("Failed to copy project", exception);
            }
        });
    }

    @FXML
    private void viewProjectReport() {
        if (selectedProject == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        NavigationBus.requestReport("Projects and Goals", "Project Report");
        NavigationBus.updateReportTitle("Project Report");
        UiAlerts.info("Project reports belong under Reports -> Projects and Goals -> Project Report.");
    }

    @FXML
    private void saveMilestone() {
        if (selectedProject == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        try {
            database.saveProjectMilestone(
                    selectedMilestone == null ? null : selectedMilestone.getId(),
                    selectedProject.getId(),
                    textValue(milestoneNameField),
                    dateText(milestoneTargetDatePicker),
                    dateText(milestoneCompletionDatePicker),
                    milestoneStatusBox.getValue(),
                    textValue(milestoneNotesArea)
            );
            clearMilestoneForm();
            openSelectedProject();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Milestone saved.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save milestone", exception);
        }
    }

    @FXML
    private void clearMilestoneForm() {
        selectedMilestone = null;
        milestonesTable.getSelectionModel().clearSelection();
        milestoneNameField.clear();
        milestoneTargetDatePicker.setValue(null);
        milestoneCompletionDatePicker.setValue(null);
        milestoneStatusBox.getSelectionModel().select("Not Started");
        milestoneNotesArea.clear();
    }

    private void configureTables() {
        projectsTable.setPlaceholder(new Label("No projects found."));
        activitiesTable.setPlaceholder(new Label("Open a project to view its activities."));
        financesTable.setPlaceholder(new Label("Open a project to view linked financial records."));
        milestonesTable.setPlaceholder(new Label("No milestones recorded for this project."));
        TableActions.configureScrollableTable(projectsTable);
        TableActions.configureScrollableTable(activitiesTable);
        TableActions.configureScrollableTable(financesTable);
        TableActions.configureScrollableTable(milestonesTable);

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("projectName"));
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getProjectType())));
        plannedColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getPlannedBudget())));
        spentColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountSpent())));
        remainingColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getRemainingBudget())));
        progressColumn.setCellValueFactory(cell -> new SimpleStringProperty(progressText(projectActivities(cell.getValue()))));
        endDateColumn.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(projectStatus(cell.getValue())));

        activityDateColumn.setCellValueFactory(new PropertyValueFactory<>("activityDate"));
        activityNameColumn.setCellValueFactory(new PropertyValueFactory<>("activityName"));
        activityPlannedCostColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getPlannedCost())));
        activityActualCostColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountUsed())));
        activityProgressColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.format(Locale.ENGLISH, "%.0f%%", cell.getValue().getProgress())));
        activityStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getStatus())));

        financeDateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        financeDescriptionColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getDescription())));
        financeTypeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTransactionType() + " / " + cell.getValue().getTransactionPurpose()));
        financeAccountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        financeAmountColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmount())));
        financeActivityColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getProjectActivityName())));

        milestoneNameColumn.setCellValueFactory(new PropertyValueFactory<>("milestoneName"));
        milestoneTargetColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getTargetDate())));
        milestoneCompletionColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getCompletionDate())));
        milestoneStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(blank(cell.getValue().getStatus())));
    }

    private void configureListeners() {
        projectSearchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        statusFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        typeFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        priorityFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        projectsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> openSelectedProject());
        activitiesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> selectedActivity = selected);
        milestonesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            selectedMilestone = selected;
            if (selected != null) {
                fillMilestoneForm(selected);
            }
        });
    }

    private void configureContextMenus() {
        TableActions.installRowContextMenu(projectsTable, project -> List.of(
                TableActions.menuItem("Open Project", this::openSelectedProject),
                TableActions.menuItem("Assess for Asset", () -> {
                    if (project != null) {
                        projectsTable.getSelectionModel().select(project);
                        openSelectedProject();
                        registerSelectedProjectAsAsset();
                    }
                }),
                TableActions.menuItem("Record Project Expense", this::recordProjectExpense),
                TableActions.menuItem("View Project Report", this::viewProjectReport),
                TableActions.separator(),
                TableActions.copyRowItem(projectsTable, project),
                TableActions.exportTableItem(projectsTable, "Project Records"),
                TableActions.printTableItem(projectsTable, "Project Records"),
                TableActions.refreshItem(this::refresh)
        ));
        TableActions.installRowContextMenu(activitiesTable, activity -> List.of(
                TableActions.menuItem("Record Activity Expense", () -> {
                    selectedActivity = activity;
                    recordProjectExpense();
                }),
                TableActions.separator(),
                TableActions.copyRowItem(activitiesTable, activity),
                TableActions.exportTableItem(activitiesTable, selectedProject == null ? "Project Activities" : selectedProject.getProjectName() + " Activities"),
                TableActions.printTableItem(activitiesTable, selectedProject == null ? "Project Activities" : selectedProject.getProjectName() + " Activities"),
                TableActions.refreshItem(this::openSelectedProject)
        ));
    }

    private void applyFilters() {
        int selectedProjectId = selectedProject == null ? -1 : selectedProject.getId();
        String search = textValue(projectSearchField).toLowerCase(Locale.ENGLISH);
        String status = statusFilterBox.getValue();
        String type = typeFilterBox.getValue();
        String priority = priorityFilterBox.getValue();
        List<Project> filteredProjects = projects.stream()
                .filter(project -> search.isBlank()
                        || contains(project.getProjectName(), search)
                        || contains(project.getDescription(), search)
                        || contains(project.getProjectOwner(), search)
                        || contains(project.getFundingSource(), search))
                .filter(project -> status == null || "All Statuses".equals(status) || status.equals(project.getStatus()) || status.equals(projectStatus(project)))
                .filter(project -> type == null || "All Types".equals(type) || type.equals(project.getProjectType()))
                .filter(project -> priority == null || "All Priorities".equals(priority) || priority.equals(project.getPriority()))
                .toList();
        projectsTable.setItems(FXCollections.observableArrayList(filteredProjects));
        restoreProjectSelection(selectedProjectId);
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

    private List<ProjectActivity> projectActivities(Project project) {
        if (project == null) {
            return List.of();
        }
        return activities.stream()
                .filter(activity -> activity.getProjectId() == project.getId())
                .sorted(Comparator.comparing(ProjectListController::activitySortDate)
                        .thenComparing(ProjectActivity::getActivityName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private String projectOverview(Project project, List<ProjectActivity> projectActivities, List<FinanceTransaction> finances) {
        double activityProgress = activityProgress(projectActivities);
        double budgetUsed = project.getPlannedBudget() <= 0 ? 0 : project.getAmountSpent() / project.getPlannedBudget() * 100;
        double availableFunding = fundingAccountBalance(project.getFundingAccountId());
        double fundingGap = Math.max(0, project.getPlannedBudget() - availableFunding);
        double forecastCost = activityProgress <= 0 ? project.getAmountSpent() : project.getAmountSpent() / (activityProgress / 100.0);
        long overdueActivities = projectActivities.stream().filter(this::isOverdue).count();
        long unsupportedExpenses = finances.stream()
                .filter(transaction -> "EXPENSE".equals(transaction.getTransactionType()))
                .filter(transaction -> blank(transaction.getReferenceNumber()).equals("-"))
                .count();
        String topCategory = topExpenseCategory(finances);

        return """
                Overview
                Project: %s
                Description: %s
                Status: %s
                Priority: %s
                Type: %s
                Start date: %s
                Expected completion: %s
                Owner: %s
                Linked goal: %s

                Budget and funding
                Planned budget: %s
                Actual cost: %s
                Committed/planned activity cost: %s
                Remaining budget: %s
                Available funding: %s
                Funding gap: %s
                Activities completed: %s
                Budget used: %s
                Forecast final cost: %s

                Smart analysis
                %s
                %s
                %s
                %s
                %s
                """.formatted(
                project.getProjectName(),
                blank(project.getDescription()),
                projectStatus(project),
                blank(project.getPriority()),
                blank(project.getProjectType()),
                blank(project.getStartDate()),
                blank(project.getEndDate()),
                blank(project.getProjectOwner()),
                blank(project.getLinkedGoalName()),
                MoneyUtil.mwk(project.getPlannedBudget()),
                MoneyUtil.mwk(project.getAmountSpent()),
                MoneyUtil.mwk(projectActivities.stream().mapToDouble(ProjectActivity::getPlannedCost).sum()),
                MoneyUtil.mwk(project.getRemainingBudget()),
                MoneyUtil.mwk(availableFunding),
                MoneyUtil.mwk(fundingGap),
                String.format(Locale.ENGLISH, "%.0f%%", activityProgress),
                String.format(Locale.ENGLISH, "%.0f%%", budgetUsed),
                MoneyUtil.mwk(forecastCost),
                budgetUsed > activityProgress + 20 ? "Budget overrun risk: spending is ahead of activity completion." : "Budget use and activity progress are not showing a major mismatch.",
                forecastCost > project.getPlannedBudget() && project.getPlannedBudget() > 0 ? "Forecast final cost exceeds budget by " + MoneyUtil.mwk(forecastCost - project.getPlannedBudget()) + "." : "Forecast final cost is within the current budget baseline.",
                overdueActivities > 0 ? overdueActivities + " activity record(s) are overdue or delayed." : "No overdue activity is visible from current records.",
                fundingGap > 0 ? "Funding gap: " + MoneyUtil.mwk(fundingGap) + "." : "No funding gap is visible from the selected funding account.",
                unsupportedExpenses > 0 ? unsupportedExpenses + " project expense(s) have no reference number. Top expense category: " + topCategory + "." : "Project expenses have supporting references where recorded. Top expense category: " + topCategory + "."
        );
    }

    private String projectHistoryText(Project project) {
        List<String> history = database.listProjectHistory(project.getId());
        if (history.isEmpty()) {
            return "No project history events have been recorded yet.";
        }
        return String.join("\n", history);
    }

    private void updateActionState() {
        boolean noProject = selectedProject == null;
        if (registerProjectAssetButton != null) {
            registerProjectAssetButton.setDisable(noProject);
        }
        if (moreActionsButton != null) {
            moreActionsButton.setDisable(noProject);
        }
    }

    private void updateSelectedProjectStatus(String status) {
        if (selectedProject == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        try {
            database.updateProjectStatus(selectedProject.getId(), status);
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Project marked " + status + ".");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update project status", exception);
        }
    }

    private String closureIssue(Project project) {
        List<ProjectActivity> projectActivities = projectActivities(project);
        long unfinished = projectActivities.stream().filter(activity -> !"Completed".equals(activity.getStatus())).count();
        if (unfinished == 0) {
            return "";
        }
        return unfinished + " activity record(s) are not completed. Closing will preserve final financial totals and mark the project completed.";
    }

    private void fillMilestoneForm(ProjectMilestone milestone) {
        milestoneNameField.setText(milestone.getMilestoneName());
        milestoneTargetDatePicker.setValue(parseDate(milestone.getTargetDate()));
        milestoneCompletionDatePicker.setValue(parseDate(milestone.getCompletionDate()));
        milestoneStatusBox.getSelectionModel().select(blankOrDefault(milestone.getStatus(), "Not Started"));
        milestoneNotesArea.setText(blankOrDefault(milestone.getNotes(), ""));
    }

    private double activityProgress(List<ProjectActivity> projectActivities) {
        if (projectActivities.isEmpty()) {
            return 0;
        }
        return projectActivities.stream().mapToDouble(ProjectActivity::getProgress).average().orElse(0);
    }

    private String progressText(List<ProjectActivity> projectActivities) {
        return String.format(Locale.ENGLISH, "%.0f%%", activityProgress(projectActivities));
    }

    private String projectStatus(Project project) {
        if (project == null) {
            return "-";
        }
        if (List.of("Draft", "Planned", "Paused", "Completed", "Cancelled", "Archived").contains(project.getStatus())) {
            return project.getStatus();
        }
        List<ProjectActivity> projectActivities = projectActivities(project);
        if (project.getPlannedBudget() > 0 && project.getAmountSpent() > project.getPlannedBudget()) {
            return "At Risk";
        }
        if (projectActivities.stream().anyMatch(this::isOverdue)) {
            return "Delayed";
        }
        return blankOrDefault(project.getStatus(), "Active");
    }

    private boolean isOverdue(ProjectActivity activity) {
        if ("Completed".equals(activity.getStatus()) || "Cancelled".equals(activity.getStatus())) {
            return false;
        }
        LocalDate due = parseDate(activity.getEndDate());
        return due != null && due.isBefore(LocalDate.now());
    }

    private double fundingAccountBalance(Integer accountId) {
        if (accountId == null) {
            return 0;
        }
        return database.listAccounts().stream()
                .filter(account -> account.getId() == accountId)
                .findFirst()
                .map(Account::getCurrentBalance)
                .orElse(0.0);
    }

    private String topExpenseCategory(List<FinanceTransaction> finances) {
        return finances.stream()
                .filter(transaction -> "EXPENSE".equals(transaction.getTransactionType()))
                .map(FinanceTransaction::getCategoryName)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.summingDouble(category -> 1)))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");
    }

    private static String activitySortDate(ProjectActivity activity) {
        return activity.getActivityDate() == null ? "" : activity.getActivityDate();
    }

    private String textValue(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String textValue(TextArea area) {
        return area.getText() == null ? "" : area.getText().trim();
    }

    private String dateText(DatePicker picker) {
        return picker.getValue() == null ? "" : picker.getValue().toString();
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String blankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ENGLISH).contains(search);
    }
}
