package com.wk.pfmis.controllers;

import com.wk.pfmis.services.OverviewWorkspaceService;
import com.wk.pfmis.services.OverviewWorkspaceService.OverviewRow;
import com.wk.pfmis.services.OverviewWorkspaceService.TransactionsOverviewData;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class TransactionsOverviewController {
    @FXML private Label inflowsValueLabel;
    @FXML private Label outflowsValueLabel;
    @FXML private Label transfersValueLabel;
    @FXML private Label exceptionsValueLabel;
    @FXML private Label emptyStateLabel;
    @FXML private Label recentStateLabel;
    @FXML private Label scheduledStateLabel;
    @FXML private Label correctionsStateLabel;
    @FXML private TableView<OverviewRow> recentMovementTable;
    @FXML private TableColumn<OverviewRow, String> recentDateColumn;
    @FXML private TableColumn<OverviewRow, String> recentTypeColumn;
    @FXML private TableColumn<OverviewRow, String> recentAccountColumn;
    @FXML private TableColumn<OverviewRow, String> recentAmountColumn;
    @FXML private TableColumn<OverviewRow, String> recentReferenceColumn;
    @FXML private TableColumn<OverviewRow, String> recentStatusColumn;
    @FXML private TableView<OverviewRow> scheduledTable;
    @FXML private TableColumn<OverviewRow, String> scheduledDueColumn;
    @FXML private TableColumn<OverviewRow, String> scheduledNameColumn;
    @FXML private TableColumn<OverviewRow, String> scheduledRouteColumn;
    @FXML private TableColumn<OverviewRow, String> scheduledAmountColumn;
    @FXML private TableColumn<OverviewRow, String> scheduledFrequencyColumn;
    @FXML private TableColumn<OverviewRow, String> scheduledStatusColumn;
    @FXML private TableView<OverviewRow> correctionsTable;
    @FXML private TableColumn<OverviewRow, String> correctionIdColumn;
    @FXML private TableColumn<OverviewRow, String> correctionOriginalColumn;
    @FXML private TableColumn<OverviewRow, String> correctionAccountColumn;
    @FXML private TableColumn<OverviewRow, String> correctionAmountColumn;
    @FXML private TableColumn<OverviewRow, String> correctionDateColumn;
    @FXML private TableColumn<OverviewRow, String> correctionStatusColumn;

    private final OverviewWorkspaceService service = new OverviewWorkspaceService();

    @FXML
    public void initialize() {
        OverviewScreenSupport.configureTable(recentMovementTable, recentDateColumn, recentTypeColumn, recentAccountColumn,
                recentAmountColumn, recentReferenceColumn, recentStatusColumn, null);
        OverviewScreenSupport.configureTable(scheduledTable, scheduledDueColumn, scheduledNameColumn, scheduledRouteColumn,
                scheduledAmountColumn, scheduledFrequencyColumn, scheduledStatusColumn, null);
        OverviewScreenSupport.configureTable(correctionsTable, correctionIdColumn, correctionOriginalColumn, correctionAccountColumn,
                correctionAmountColumn, correctionDateColumn, correctionStatusColumn, null);
        refresh();
    }

    @FXML
    private void refresh() {
        try {
            TransactionsOverviewData data = service.transactionsOverview();
            inflowsValueLabel.setText(data.inflowsMonthToDate());
            outflowsValueLabel.setText(data.outflowsMonthToDate());
            transfersValueLabel.setText(data.transfersMonthToDate());
            exceptionsValueLabel.setText(data.exceptions());
            OverviewScreenSupport.setRows(recentMovementTable, data.recentMovement(), recentStateLabel, "No recent posted movement.");
            OverviewScreenSupport.setRows(scheduledTable, data.scheduledPending(), scheduledStateLabel, "No pending drafts or scheduled transfers.");
            OverviewScreenSupport.setRows(correctionsTable, data.corrections(), correctionsStateLabel, "No correction drafts.");
            OverviewScreenSupport.setEmptyState(emptyStateLabel, data.empty(),
                    "No transaction activity is recorded yet. Use Transfer Money or a module entry screen to start.",
                    "Transaction overview separates inflows, outflows, transfers and correction work.");
        } catch (RuntimeException exception) {
            emptyStateLabel.setText("Transactions overview could not refresh: " + exception.getMessage());
        }
    }

    @FXML private void openTransferMoney() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.TRANSFER_MONEY); }
    @FXML private void openTransactionLedger() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.TRANSACTION_LEDGER); }
    @FXML private void openScheduledTransfers() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.SCHEDULED_TRANSFERS); }
    @FXML private void openCorrectionsReversals() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.CORRECTIONS_REVERSALS); }
}
