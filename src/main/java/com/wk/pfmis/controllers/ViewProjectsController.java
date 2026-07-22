package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.FinanceTransaction;
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
    @FXML private TableColumn<ProjectActivity, String> activityStatusColumn;
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

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Project> projects = List.of();
    private List<ProjectActivity> activities = List.of();
    private List<FinanceTransaction> transactions = List.of();

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

        activityDateColumn.setCellValueFactory(new PropertyValueFactory<>("activityDate"));
        activityNameColumn.setCellValueFactory(new PropertyValueFactory<>("activityName"));
        activityStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getStatus())));
        activityNotesColumn.setCellValueFactory(cell -> new SimpleStringProperty(reasonOrDescription(cell.getValue())));
        configureContextMenus();
        refresh();
    }

    @FXML
    private void refresh() {
        Project selectedProject = summaryProjectBox.getValue();
        projects = database.listProjects();
        activities = database.listProjectActivities();
        transactions = database.listRecentTransactions(1000);
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
        List<FinanceTransaction> projectExpenses = projectExpenses(project);
        double totalSpent = project.getAmountSpent();
        double averageCost = projectActivities.isEmpty() ? 0 : totalSpent / projectActivities.size();
        projectNameLabel.setText(project.getProjectName());
        statusLabel.setText(project.getStatus());
        startDateLabel.setText(blankToDash(project.getStartDate()));
        endDateLabel.setText(blankToDash(project.getEndDate()));
        totalActivitiesLabel.setText(String.valueOf(projectActivities.size()));
        totalSpentLabel.setText(MoneyUtil.mwk(totalSpent));
        averageActivityCostLabel.setText(MoneyUtil.mwk(averageCost));
        lastActivityLabel.setText(lastActivityText(projectActivities));
        mostUsedCategoryLabel.setText(mostUsedExpenseCategory(projectExpenses));
        accountsUsedLabel.setText(accountsUsedText(projectExpenses));
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

    private List<FinanceTransaction> projectExpenses(Project project) {
        if (project == null) {
            return List.of();
        }
        return transactions.stream()
                .filter(transaction -> "EXPENSE".equals(transaction.getTransactionType()))
                .filter(transaction -> project.getProjectName().equals(transaction.getProjectName()))
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

    private String mostUsedExpenseCategory(List<FinanceTransaction> projectExpenses) {
        return projectExpenses.stream()
                .map(FinanceTransaction::getCategoryName)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");
    }

    private String accountsUsedText(List<FinanceTransaction> projectExpenses) {
        String text = projectExpenses.stream()
                .map(FinanceTransaction::getAccountName)
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

    private void configureContextMenus() {
        TableActions.installRowContextMenu(projectsTable, this::projectMenuItems);
        TableActions.installRowContextMenu(recentActivitiesTable, this::activityMenuItems);
    }

    private List<javafx.scene.control.MenuItem> projectMenuItems(Project project) {
        return List.of(
                TableActions.menuItem("Show Project Summary", () -> showProjectFromMenu(project)),
                TableActions.menuItem("View Project Details", () -> viewProjectDetails(project)),
                TableActions.menuItem("Record Project Expense", () -> recordProjectExpense(project)),
                TableActions.separator(),
                TableActions.copyRowItem(projectsTable, project),
                TableActions.exportTableItem(projectsTable, "Projects Summary"),
                TableActions.printTableItem(projectsTable, "Projects Summary"),
                TableActions.refreshItem(this::refresh)
        );
    }

    private List<javafx.scene.control.MenuItem> activityMenuItems(ProjectActivity activity) {
        return List.of(
                TableActions.menuItem("View Activity Details", () -> viewActivityDetails(activity)),
                TableActions.menuItem("Show Activity Project", () -> showActivityProject(activity)),
                TableActions.menuItem("Record Activity Expense", () -> recordActivityExpense(activity)),
                TableActions.separator(),
                TableActions.copyRowItem(recentActivitiesTable, activity),
                TableActions.exportTableItem(recentActivitiesTable, selectedProjectActivitiesTitle()),
                TableActions.printTableItem(recentActivitiesTable, selectedProjectActivitiesTitle()),
                TableActions.refreshItem(this::refresh)
        );
    }

    private void showProjectFromMenu(Project project) {
        if (project == null) {
            return;
        }
        summaryProjectBox.getSelectionModel().select(project);
        syncTableSelection(project);
        showProjectSummary(project);
    }

    private void viewProjectDetails(Project project) {
        if (project == null) {
            return;
        }
        UiAlerts.info(
                "Project: " + project.getProjectName()
                        + "\nStatus: " + blankToDash(project.getStatus())
                        + "\nActivities: " + projectActivities(project).size()
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

    private void showActivityProject(ProjectActivity activity) {
        if (activity == null) {
            return;
        }
        projects.stream()
                .filter(project -> project.getId() == activity.getProjectId())
                .findFirst()
                .ifPresent(this::showProjectFromMenu);
    }

    private void recordProjectExpense(Project project) {
        showProjectFromMenu(project);
        NavigationBus.requestTransaction("EXPENSE", "PROJECT_EXPENSE", null);
        NavigationBus.showTransactionEntry("Record Project Expense");
    }

    private void recordActivityExpense(ProjectActivity activity) {
        showActivityProject(activity);
        NavigationBus.requestTransaction("EXPENSE", "PROJECT_EXPENSE", null);
        NavigationBus.showTransactionEntry("Record Project Expense");
    }

    private String selectedProjectActivitiesTitle() {
        Project project = summaryProjectBox.getValue();
        return project == null ? "Recent Project Activities" : project.getProjectName() + " Activities";
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }
}
