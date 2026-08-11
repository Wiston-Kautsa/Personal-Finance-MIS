package com.wk.pfmis.controllers;

import com.wk.pfmis.services.OverviewWorkspaceService;
import com.wk.pfmis.services.OverviewWorkspaceService.IncomeOverviewData;
import com.wk.pfmis.services.OverviewWorkspaceService.OverviewRow;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class IncomeOverviewController {
    @FXML private Label receivedValueLabel;
    @FXML private Label expectedValueLabel;
    @FXML private Label overdueValueLabel;
    @FXML private Label recurringValueLabel;
    @FXML private Label emptyStateLabel;
    @FXML private Label recentStateLabel;
    @FXML private Label attentionStateLabel;
    @FXML private Label upcomingStateLabel;
    @FXML private TableView<OverviewRow> recentIncomeTable;
    @FXML private TableColumn<OverviewRow, String> recentDateColumn;
    @FXML private TableColumn<OverviewRow, String> recentSourceColumn;
    @FXML private TableColumn<OverviewRow, String> recentAccountColumn;
    @FXML private TableColumn<OverviewRow, String> recentAmountColumn;
    @FXML private TableColumn<OverviewRow, String> recentReferenceColumn;
    @FXML private TableColumn<OverviewRow, String> recentStatusColumn;
    @FXML private TableView<OverviewRow> attentionTable;
    @FXML private TableColumn<OverviewRow, String> attentionItemColumn;
    @FXML private TableColumn<OverviewRow, String> attentionReasonColumn;
    @FXML private TableColumn<OverviewRow, String> attentionAccountColumn;
    @FXML private TableColumn<OverviewRow, String> attentionAmountColumn;
    @FXML private TableColumn<OverviewRow, String> attentionDateColumn;
    @FXML private TableColumn<OverviewRow, String> attentionStatusColumn;
    @FXML private TableView<OverviewRow> upcomingIncomeTable;
    @FXML private TableColumn<OverviewRow, String> upcomingDateColumn;
    @FXML private TableColumn<OverviewRow, String> upcomingSourceColumn;
    @FXML private TableColumn<OverviewRow, String> upcomingAccountColumn;
    @FXML private TableColumn<OverviewRow, String> upcomingAmountColumn;
    @FXML private TableColumn<OverviewRow, String> upcomingFrequencyColumn;
    @FXML private TableColumn<OverviewRow, String> upcomingStatusColumn;

    private final OverviewWorkspaceService service = new OverviewWorkspaceService();

    @FXML
    public void initialize() {
        OverviewScreenSupport.configureTable(recentIncomeTable, recentDateColumn, recentSourceColumn, recentAccountColumn,
                recentAmountColumn, recentReferenceColumn, recentStatusColumn, null);
        OverviewScreenSupport.configureTable(attentionTable, attentionItemColumn, attentionReasonColumn, attentionAccountColumn,
                attentionAmountColumn, attentionDateColumn, attentionStatusColumn, null);
        OverviewScreenSupport.configureTable(upcomingIncomeTable, upcomingDateColumn, upcomingSourceColumn, upcomingAccountColumn,
                upcomingAmountColumn, upcomingFrequencyColumn, upcomingStatusColumn, null);
        refresh();
    }

    @FXML
    private void refresh() {
        try {
            IncomeOverviewData data = service.incomeOverview();
            receivedValueLabel.setText(data.receivedThisMonth());
            expectedValueLabel.setText(data.expectedNextThirtyDays());
            overdueValueLabel.setText(data.overdueExpected());
            recurringValueLabel.setText(data.recurringDueSoon());
            OverviewScreenSupport.setRows(recentIncomeTable, data.recentIncome(), recentStateLabel, "No posted income yet.");
            OverviewScreenSupport.setRows(attentionTable, data.attention(), attentionStateLabel, "No income needs attention.");
            OverviewScreenSupport.setRows(upcomingIncomeTable, data.upcomingIncome(), upcomingStateLabel, "No upcoming expected or recurring income.");
            OverviewScreenSupport.setEmptyState(emptyStateLabel, data.empty(),
                    "No income has been recorded yet. Add income or create an expected-income plan.",
                    "Income position is based on posted income, expected income and recurring plans.");
        } catch (RuntimeException exception) {
            emptyStateLabel.setText("Income overview could not refresh: " + exception.getMessage());
        }
    }

    @FXML private void openAddIncome() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.ADD_INCOME); }
    @FXML private void openIncomeRecords() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.INCOME_RECORDS); }
    @FXML private void openExpectedIncome() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.EXPECTED_INCOME); }
    @FXML private void openRecurringIncome() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.RECURRING_INCOME); }
}
