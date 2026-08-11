package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Asset;
import com.wk.pfmis.models.AssetEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.util.List;

public class AssetHistoryController {
    @FXML private ComboBox<Asset> assetBox;
    @FXML private Label statusLabel;
    @FXML private Label conditionLabel;
    @FXML private Label valueLabel;
    @FXML private Label locationLabel;
    @FXML private Label eventStateLabel;
    @FXML private TextArea detailArea;
    @FXML private TableView<AssetEvent> eventTable;
    @FXML private TableColumn<AssetEvent, String> eventDateColumn;
    @FXML private TableColumn<AssetEvent, String> eventTypeColumn;
    @FXML private TableColumn<AssetEvent, String> eventAmountColumn;
    @FXML private TableColumn<AssetEvent, String> eventCounterpartyColumn;
    @FXML private TableColumn<AssetEvent, String> eventTransactionColumn;
    @FXML private TableColumn<AssetEvent, String> eventStatusColumn;
    @FXML private TableColumn<AssetEvent, String> eventNotesColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        AssetLifecycleSupport.configureEventTable(eventTable, eventDateColumn, eventTypeColumn, eventAmountColumn,
                eventCounterpartyColumn, eventTransactionColumn, eventStatusColumn, eventNotesColumn);
        assetBox.valueProperty().addListener((observable, oldValue, newValue) -> showAsset(newValue));
        refresh();
    }

    @FXML
    private void refresh() {
        AssetLifecycleSupport.loadAssets(database, assetBox, assetBox.getValue() == null ? null : assetBox.getValue().getId());
        showAsset(assetBox.getValue());
    }

    @FXML
    private void openMaintenance() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.ASSET_MAINTENANCE);
    }

    @FXML
    private void openValuation() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.ASSET_VALUATION);
    }

    @FXML
    private void openSaleDisposal() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.ASSET_SALE_DISPOSAL);
    }

    private void showAsset(Asset asset) {
        AssetLifecycleSupport.setAssetSummary(asset, statusLabel, conditionLabel, valueLabel, locationLabel);
        List<AssetEvent> events = asset == null ? List.of() : database.listAssetEvents(asset.getId());
        CoreWorkspaceSupport.setItems(eventTable, events, eventStateLabel, asset == null ? "No asset selected." : "No asset events yet.");
        detailArea.setText(asset == null ? "No assets are registered yet." : detailText(asset, events));
    }

    private String detailText(Asset asset, List<AssetEvent> events) {
        StringBuilder builder = new StringBuilder();
        builder.append("Asset: ").append(asset.getAssetName()).append(System.lineSeparator());
        builder.append("Category: ").append(CoreWorkspaceSupport.dash(asset.getAssetCategory())).append(System.lineSeparator());
        builder.append("Acquisition: ").append(CoreWorkspaceSupport.dash(asset.getAcquisitionMethod())).append(" on ")
                .append(CoreWorkspaceSupport.dash(asset.getPurchaseDate())).append(System.lineSeparator());
        builder.append("Payment treatment: ").append(AssetLifecycleSupport.displayStatus(asset.getPaymentTreatment())).append(System.lineSeparator());
        builder.append("Purchase transaction: ").append(asset.getPurchaseTransactionId() == null ? "-" : "#" + asset.getPurchaseTransactionId()).append(System.lineSeparator());
        builder.append("Current value: ").append(CoreWorkspaceSupport.money(asset.getCurrency(), asset.getCurrentValue())).append(System.lineSeparator());
        builder.append("Events recorded: ").append(events.size()).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("Notes").append(System.lineSeparator()).append(CoreWorkspaceSupport.dash(asset.getNotes()));
        return builder.toString();
    }
}
