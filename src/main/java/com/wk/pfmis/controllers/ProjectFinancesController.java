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
import java.util.Locale;

public class ProjectFinancesController {
    @FXML private ComboBox<Project> projectBox;
    @FXML private Label plannedBudgetLabel;
    @FXML private Label actualSpendLabel;
    @FXML private Label remainingBudgetLabel;
    @FXML private Label varianceLabel;
    @FXML private Label transactionStateLabel;
    @FXML private Label activityCostStateLabel;
    @FXML private TextArea financeArea;
    @FXML private TableView<FinanceTransaction> transactionsTable;
    @FXML private TableColumn<FinanceTransaction, String> transactionDateColumn;
    @FXML private TableColumn<FinanceTransaction, String> transactionDescriptionColumn;
    @FXML private TableColumn<FinanceTransaction, String> transactionTypeColumn;
    @FXML private TableColumn<FinanceTransaction, String> transactionAccountColumn;
    @FXML private TableColumn<FinanceTransaction, String> transactionAmountColumn;
    @FXML private TableColumn<FinanceTransaction, String> transactionActivityColumn;
    @FXML private TableView<ProjectActivity> activityCostTable;
    @FXML private TableColumn<ProjectActivity, String> activityNameColumn;
    @FXML private TableColumn<ProjectActivity, String> activityPlannedColumn;
    @FXML private TableColumn<ProjectActivity, String> activityActualColumn;
    @FXML private TableColumn<ProjectActivity, String> activityProgressColumn;
    @FXML private TableColumn<ProjectActivity, String> activityStatusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Project> projects = List.of();

    @FXML
    public void initialize() {
        configureTables();
        projectBox.valueProperty().addListener((observable, oldValue, newValue) -> showProject(newValue));
        refresh();
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
    private void recordProjectExpense() {
        Project project = projectBox.getValue();
        if (project == null) {
            UiAlerts.info("Select a project first.");
            return;
        }
        NavigationBus.requestProjectExpense(project.getId(), null);
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.RECORD_EXPENSE);
    }

    @FXML
    private void openActivities() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.PROJECT_ACTIVITIES);
    }

    @FXML
    private void openMilestones() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.PROJECT_MILESTONES_STATUS);
    }

    private void configureTables() {
        CoreWorkspaceSupport.bind(transactionDateColumn, FinanceTransaction::getTransactionDate);
        CoreWorkspaceSupport.bind(transactionDescriptionColumn, FinanceTransaction::getDescription);
        CoreWorkspaceSupport.bind(transactionTypeColumn, transaction -> CoreWorkspaceSupport.dash(transaction.getTransactionType()));
        CoreWorkspaceSupport.bind(transactionAccountColumn, transaction -> CoreWorkspaceSupport.dash(transaction.getAccountName()));
        CoreWorkspaceSupport.bind(transactionAmountColumn, transaction -> CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), transaction.getAmount()));
        CoreWorkspaceSupport.bind(transactionActivityColumn, transaction -> CoreWorkspaceSupport.dash(transaction.getProjectActivityName()));
        CoreWorkspaceSupport.bind(activityNameColumn, ProjectActivity::getActivityName);
        CoreWorkspaceSupport.bind(activityPlannedColumn, activity -> CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), activity.getPlannedCost()));
        CoreWorkspaceSupport.bind(activityActualColumn, activity -> CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), activity.getAmountUsed()));
        CoreWorkspaceSupport.bind(activityProgressColumn, activity -> CoreWorkspaceSupport.percent(activity.getProgress()));
        CoreWorkspaceSupport.bind(activityStatusColumn, ProjectActivity::getStatus);
        TableActions.configureScrollableTable(transactionsTable);
        TableActions.configureScrollableTable(activityCostTable);
    }

    private void showProject(Project project) {
        if (project == null) {
            plannedBudgetLabel.setText("-");
            actualSpendLabel.setText("-");
            remainingBudgetLabel.setText("-");
            varianceLabel.setText("-");
            financeArea.setText("No projects are recorded yet.");
            CoreWorkspaceSupport.setItems(transactionsTable, List.of(), transactionStateLabel, "No project transactions.");
            CoreWorkspaceSupport.setItems(activityCostTable, List.of(), activityCostStateLabel, "No project activities.");
            return;
        }
        List<FinanceTransaction> transactions = database.listProjectTransactions(project.getId());
        List<ProjectActivity> activities = database.listProjectActivities().stream()
                .filter(activity -> activity.getProjectId() == project.getId())
                .toList();
        double planned = project.getPlannedBudget();
        double actual = transactions.stream()
                .filter(transaction -> "EXPENSE".equalsIgnoreCase(CoreWorkspaceSupport.safe(transaction.getTransactionType())))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();
        double remaining = planned - actual;
        plannedBudgetLabel.setText(CoreWorkspaceSupport.money(project.getCurrency(), planned));
        actualSpendLabel.setText(CoreWorkspaceSupport.money(project.getCurrency(), actual));
        remainingBudgetLabel.setText(CoreWorkspaceSupport.money(project.getCurrency(), remaining));
        varianceLabel.setText(planned <= 0 ? "-" : String.format(Locale.ENGLISH, "%.1f%% used", actual / planned * 100));
        CoreWorkspaceSupport.setItems(transactionsTable, transactions, transactionStateLabel, "No project transactions.");
        CoreWorkspaceSupport.setItems(activityCostTable, activities, activityCostStateLabel, "No project activities.");
        financeArea.setText("""
                Project: %s
                Funding source: %s
                Funding account: %s
                Planned budget: %s
                Posted project spending: %s
                Remaining budget: %s

                Project finances are read from posted transactions linked to this project. To move money, record a project expense through the central transaction workflow.
                """.formatted(
                project.getProjectName(),
                CoreWorkspaceSupport.dash(project.getFundingSource()),
                CoreWorkspaceSupport.dash(project.getFundingAccountName()),
                CoreWorkspaceSupport.money(project.getCurrency(), planned),
                CoreWorkspaceSupport.money(project.getCurrency(), actual),
                CoreWorkspaceSupport.money(project.getCurrency(), remaining)
        ));
    }
}
