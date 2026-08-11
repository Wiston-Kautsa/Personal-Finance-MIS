package com.wk.pfmis.controllers;

import com.wk.pfmis.services.OverviewWorkspaceService;
import com.wk.pfmis.services.OverviewWorkspaceService.ExpenseOverviewData;
import com.wk.pfmis.services.OverviewWorkspaceService.OverviewRow;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class ExpenseOverviewController {
    @FXML private Label spentValueLabel;
    @FXML private Label plannedValueLabel;
    @FXML private Label overdueValueLabel;
    @FXML private Label recurringValueLabel;
    @FXML private Label emptyStateLabel;
    @FXML private Label recentStateLabel;
    @FXML private Label attentionStateLabel;
    @FXML private Label upcomingStateLabel;
    @FXML private TableView<OverviewRow> recentExpenseTable;
    @FXML private TableColumn<OverviewRow, String> recentDateColumn;
    @FXML private TableColumn<OverviewRow, String> recentCategoryColumn;
    @FXML private TableColumn<OverviewRow, String> recentAccountColumn;
    @FXML private TableColumn<OverviewRow, String> recentAmountColumn;
    @FXML private TableColumn<OverviewRow, String> recentProjectColumn;
    @FXML private TableColumn<OverviewRow, String> recentStatusColumn;
    @FXML private TableView<OverviewRow> attentionTable;
    @FXML private TableColumn<OverviewRow, String> attentionItemColumn;
    @FXML private TableColumn<OverviewRow, String> attentionTypeColumn;
    @FXML private TableColumn<OverviewRow, String> attentionAccountColumn;
    @FXML private TableColumn<OverviewRow, String> attentionAmountColumn;
    @FXML private TableColumn<OverviewRow, String> attentionDueColumn;
    @FXML private TableColumn<OverviewRow, String> attentionStatusColumn;
    @FXML private TableView<OverviewRow> upcomingExpenseTable;
    @FXML private TableColumn<OverviewRow, String> upcomingDueColumn;
    @FXML private TableColumn<OverviewRow, String> upcomingDescriptionColumn;
    @FXML private TableColumn<OverviewRow, String> upcomingAccountColumn;
    @FXML private TableColumn<OverviewRow, String> upcomingAmountColumn;
    @FXML private TableColumn<OverviewRow, String> upcomingFrequencyColumn;
    @FXML private TableColumn<OverviewRow, String> upcomingStatusColumn;

    private final OverviewWorkspaceService service = new OverviewWorkspaceService();

    @FXML
    public void initialize() {
        OverviewScreenSupport.configureTable(recentExpenseTable, recentDateColumn, recentCategoryColumn, recentAccountColumn,
                recentAmountColumn, recentProjectColumn, recentStatusColumn, null);
        OverviewScreenSupport.configureTable(attentionTable, attentionItemColumn, attentionTypeColumn, attentionAccountColumn,
                attentionAmountColumn, attentionDueColumn, attentionStatusColumn, null);
        OverviewScreenSupport.configureTable(upcomingExpenseTable, upcomingDueColumn, upcomingDescriptionColumn, upcomingAccountColumn,
                upcomingAmountColumn, upcomingFrequencyColumn, upcomingStatusColumn, null);
        refresh();
    }

    @FXML
    private void refresh() {
        try {
            ExpenseOverviewData data = service.expenseOverview();
            spentValueLabel.setText(data.spentThisMonth());
            plannedValueLabel.setText(data.upcomingPlanned());
            overdueValueLabel.setText(data.overdueOrFailed());
            recurringValueLabel.setText(data.recurringDue());
            OverviewScreenSupport.setRows(recentExpenseTable, data.recentExpenses(), recentStateLabel, "No posted expenses yet.");
            OverviewScreenSupport.setRows(attentionTable, data.attention(), attentionStateLabel, "No expense items need attention.");
            OverviewScreenSupport.setRows(upcomingExpenseTable, data.upcomingExpenses(), upcomingStateLabel, "No upcoming planned or recurring expenses.");
            OverviewScreenSupport.setEmptyState(emptyStateLabel, data.empty(),
                    "No expenses or planned obligations are recorded yet. Record an expense or create a plan.",
                    "Expense position separates posted spending from planned and recurring obligations.");
        } catch (RuntimeException exception) {
            emptyStateLabel.setText("Expense overview could not refresh: " + exception.getMessage());
        }
    }

    @FXML private void openRecordExpense() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.RECORD_EXPENSE); }
    @FXML private void openExpenseRecords() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.EXPENSE_RECORDS); }
    @FXML private void openPlannedRecurringExpenses() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.PLANNED_RECURRING_EXPENSES); }
}
