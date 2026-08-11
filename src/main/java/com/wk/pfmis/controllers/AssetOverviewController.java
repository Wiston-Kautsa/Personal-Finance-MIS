package com.wk.pfmis.controllers;

import com.wk.pfmis.services.OverviewWorkspaceService;
import com.wk.pfmis.services.OverviewWorkspaceService.AssetOverviewData;
import com.wk.pfmis.services.OverviewWorkspaceService.OverviewRow;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class AssetOverviewController {
    @FXML private Label activeValueLabel;
    @FXML private Label valueValueLabel;
    @FXML private Label maintenanceValueLabel;
    @FXML private Label recognitionValueLabel;
    @FXML private Label emptyStateLabel;
    @FXML private Label attentionStateLabel;
    @FXML private Label recognitionStateLabel;
    @FXML private Label eventsStateLabel;
    @FXML private TableView<OverviewRow> attentionTable;
    @FXML private TableColumn<OverviewRow, String> attentionAssetColumn;
    @FXML private TableColumn<OverviewRow, String> attentionConditionColumn;
    @FXML private TableColumn<OverviewRow, String> attentionLocationColumn;
    @FXML private TableColumn<OverviewRow, String> attentionValueColumn;
    @FXML private TableColumn<OverviewRow, String> attentionDateColumn;
    @FXML private TableColumn<OverviewRow, String> attentionStatusColumn;
    @FXML private TableView<OverviewRow> recognitionTable;
    @FXML private TableColumn<OverviewRow, String> recognitionDateColumn;
    @FXML private TableColumn<OverviewRow, String> recognitionDescriptionColumn;
    @FXML private TableColumn<OverviewRow, String> recognitionAccountColumn;
    @FXML private TableColumn<OverviewRow, String> recognitionAmountColumn;
    @FXML private TableColumn<OverviewRow, String> recognitionCategoryColumn;
    @FXML private TableColumn<OverviewRow, String> recognitionStatusColumn;
    @FXML private TableView<OverviewRow> eventsTable;
    @FXML private TableColumn<OverviewRow, String> eventDateColumn;
    @FXML private TableColumn<OverviewRow, String> eventAssetColumn;
    @FXML private TableColumn<OverviewRow, String> eventTypeColumn;
    @FXML private TableColumn<OverviewRow, String> eventAmountColumn;
    @FXML private TableColumn<OverviewRow, String> eventCounterpartyColumn;
    @FXML private TableColumn<OverviewRow, String> eventStatusColumn;

    private final OverviewWorkspaceService service = new OverviewWorkspaceService();

    @FXML
    public void initialize() {
        OverviewScreenSupport.configureTable(attentionTable, attentionAssetColumn, attentionConditionColumn, attentionLocationColumn,
                attentionValueColumn, attentionDateColumn, attentionStatusColumn, null);
        OverviewScreenSupport.configureTable(recognitionTable, recognitionDateColumn, recognitionDescriptionColumn, recognitionAccountColumn,
                recognitionAmountColumn, recognitionCategoryColumn, recognitionStatusColumn, null);
        OverviewScreenSupport.configureTable(eventsTable, eventDateColumn, eventAssetColumn, eventTypeColumn,
                eventAmountColumn, eventCounterpartyColumn, eventStatusColumn, null);
        refresh();
    }

    @FXML
    private void refresh() {
        try {
            AssetOverviewData data = service.assetOverview();
            activeValueLabel.setText(data.activeAssets());
            valueValueLabel.setText(data.currentValue());
            maintenanceValueLabel.setText(data.maintenanceDue());
            recognitionValueLabel.setText(data.recognitionQueue());
            OverviewScreenSupport.setRows(attentionTable, data.attention(), attentionStateLabel, "No assets require attention.");
            OverviewScreenSupport.setRows(recognitionTable, data.recognitionCandidates(), recognitionStateLabel, "No obvious recognition candidates.");
            OverviewScreenSupport.setRows(eventsTable, data.recentEvents(), eventsStateLabel, "No asset events yet.");
            OverviewScreenSupport.setEmptyState(emptyStateLabel, data.empty(),
                    "No assets are registered. Register an asset or review purchase candidates for recognition.",
                    "Asset overview is read and triage focused; lifecycle actions remain in their own workspaces.");
        } catch (RuntimeException exception) {
            emptyStateLabel.setText("Asset overview could not refresh: " + exception.getMessage());
        }
    }

    @FXML private void openAssetRegister() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.ASSET_REGISTER); }
    @FXML private void openAssetRecognition() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.ASSET_RECOGNITION); }
    @FXML private void openRegisterAsset() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.REGISTER_ASSET); }
    @FXML private void openMaintenance() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.ASSET_MAINTENANCE); }
    @FXML private void openValuation() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.ASSET_VALUATION); }
    @FXML private void openTransferCustody() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.ASSET_TRANSFER_CUSTODY); }
    @FXML private void openSaleDisposal() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.ASSET_SALE_DISPOSAL); }
    @FXML private void openAssetHistory() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.ASSET_HISTORY); }
}
