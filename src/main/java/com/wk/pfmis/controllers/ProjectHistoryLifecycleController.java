package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectActivity;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.util.List;

public class ProjectHistoryLifecycleController {
    @FXML private ComboBox<Project> projectBox;
    @FXML private Label budgetLabel;
    @FXML private Label spentLabel;
    @FXML private Label statusLabel;
    @FXML private Label typeOwnerLabel;
    @FXML private Label activityStateLabel;
    @FXML private Label transactionStateLabel;
    @FXML private TextArea historyArea;
    @FXML private TableView<ProjectActivity> activityTable;
    @FXML private TableColumn<ProjectActivity, String> activityDateColumn;
    @FXML private TableColumn<ProjectActivity, String> activityNameColumn;
    @FXML private TableColumn<ProjectActivity, String> activityProgressColumn;
    @FXML private TableColumn<ProjectActivity, String> activityStatusColumn;
    @FXML private TableView<FinanceTransaction> transactionTable;
    @FXML private TableColumn<FinanceTransaction, String> transactionDateColumn;
    @FXML private TableColumn<FinanceTransaction, String> transactionDescriptionColumn;
    @FXML private TableColumn<FinanceTransaction, String> transactionAmountColumn;
    @FXML private TableColumn<FinanceTransaction, String> transactionStatusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        configureTables();
        projectBox.valueProperty().addListener((observable, oldValue, newValue) -> showProject(newValue));
        refresh();
    }

    @FXML
    private void refresh() {
        Integer selectedId = projectBox.getValue() == null ? null : projectBox.getValue().getId();
        List<Project> projects = database.listProjects();
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
    private void markActive() {
        updateProjectStatus("Active");
    }

    @FXML
    private void markPaused() {
        updateProjectStatus("Paused");
    }

    @FXML
    private void markCompleted() {
        updateProjectStatus("Completed");
    }

    @FXML
    private void archiveProject() {
        updateProjectStatus("Archived");
    }

    @FXML
    private void openFinances() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.PROJECT_FINANCES);
    }

    @FXML
    private void openMilestones() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.PROJECT_MILESTONES_STATUS);
    }

    @FXML
    private void openActivities() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.PROJECT_ACTIVITIES);
    }

    private void configureTables() {
        CoreWorkspaceSupport.bind(activityDateColumn, ProjectActivity::getActivityDate);
        CoreWorkspaceSupport.bind(activityNameColumn, ProjectActivity::getActivityName);
        CoreWorkspaceSupport.bind(activityProgressColumn, activity -> CoreWorkspaceSupport.percent(activity.getProgress()));
        CoreWorkspaceSupport.bind(activityStatusColumn, ProjectActivity::getStatus);
        CoreWorkspaceSupport.bind(transactionDateColumn, FinanceTransaction::getTransactionDate);
        CoreWorkspaceSupport.bind(transactionDescriptionColumn, FinanceTransaction::getDescription);
        CoreWorkspaceSupport.bind(transactionAmountColumn, transaction -> CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), transaction.getAmount()));
        CoreWorkspaceSupport.bind(transactionStatusColumn, FinanceTransaction::getTransactionStatus);
        TableActions.configureScrollableTable(activityTable);
        TableActions.configureScrollableTable(transactionTable);
    }

    private void showProject(Project project) {
        if (project == null) {
            budgetLabel.setText("-");
            spentLabel.setText("-");
            statusLabel.setText("-");
            typeOwnerLabel.setText("-");
            historyArea.setText("No projects are recorded yet.");
            CoreWorkspaceSupport.setItems(activityTable, List.of(), activityStateLabel, "No project activities.");
            CoreWorkspaceSupport.setItems(transactionTable, List.of(), transactionStateLabel, "No project transactions.");
            return;
        }
        budgetLabel.setText(CoreWorkspaceSupport.money(project.getCurrency(), project.getPlannedBudget()));
        spentLabel.setText(CoreWorkspaceSupport.money(project.getCurrency(), project.getAmountSpent()));
        statusLabel.setText(CoreWorkspaceSupport.dash(project.getStatus()));
        typeOwnerLabel.setText(CoreWorkspaceSupport.dash(project.getProjectType()) + " / " + CoreWorkspaceSupport.dash(project.getProjectOwner()));
        List<ProjectActivity> activities = database.listProjectActivities().stream()
                .filter(activity -> activity.getProjectId() == project.getId())
                .toList();
        List<FinanceTransaction> transactions = database.listProjectTransactions(project.getId());
        CoreWorkspaceSupport.setItems(activityTable, activities, activityStateLabel, "No project activities.");
        CoreWorkspaceSupport.setItems(transactionTable, transactions, transactionStateLabel, "No project transactions.");
        historyArea.setText(historyText(project, database.listProjectHistory(project.getId())));
    }

    private void updateProjectStatus(String status) {
        Project project = projectBox.getValue();
        if (project == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        try {
            database.updateProjectStatus(project.getId(), status);
            DataRefreshBus.notifyDataChanged();
            refresh();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update project lifecycle", exception);
        }
    }

    private String historyText(Project project, List<String> history) {
        StringBuilder builder = new StringBuilder();
        builder.append("Project: ").append(project.getProjectName()).append(System.lineSeparator());
        builder.append("Type: ").append(CoreWorkspaceSupport.dash(project.getProjectType())).append(System.lineSeparator());
        builder.append("Owner: ").append(CoreWorkspaceSupport.dash(project.getProjectOwner())).append(System.lineSeparator());
        builder.append("Priority: ").append(CoreWorkspaceSupport.dash(project.getPriority())).append(System.lineSeparator());
        builder.append("Start date: ").append(CoreWorkspaceSupport.dash(project.getStartDate())).append(System.lineSeparator());
        builder.append("End date: ").append(CoreWorkspaceSupport.dash(project.getEndDate())).append(System.lineSeparator());
        builder.append("Status: ").append(CoreWorkspaceSupport.dash(project.getStatus())).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("History").append(System.lineSeparator());
        if (history.isEmpty()) {
            builder.append("- No lifecycle history events recorded yet.").append(System.lineSeparator());
        } else {
            history.forEach(item -> builder.append("- ").append(item).append(System.lineSeparator()));
        }
        if (!CoreWorkspaceSupport.safe(project.getNotes()).isBlank()) {
            builder.append(System.lineSeparator()).append("Notes").append(System.lineSeparator()).append(project.getNotes());
        }
        return builder.toString();
    }
}
