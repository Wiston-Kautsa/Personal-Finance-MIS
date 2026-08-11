package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Asset;
import com.wk.pfmis.models.AssetEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class AssetValuationController {
    @FXML private ComboBox<Asset> assetBox;
    @FXML private Label statusLabel;
    @FXML private Label conditionLabel;
    @FXML private Label valueLabel;
    @FXML private Label locationLabel;
    @FXML private TextField newValueField;
    @FXML private DatePicker valuationDatePicker;
    @FXML private TextField reasonField;
    @FXML private TextArea notesArea;
    @FXML private Label resultLabel;
    @FXML private Label eventStateLabel;
    @FXML private TableView<AssetEvent> eventTable;
    @FXML private TableColumn<AssetEvent, String> eventDateColumn;
    @FXML private TableColumn<AssetEvent, String> eventTypeColumn;
    @FXML private TableColumn<AssetEvent, String> eventAmountColumn;
    @FXML private TableColumn<AssetEvent, String> eventCounterpartyColumn;
    @FXML private TableColumn<AssetEvent, String> eventTransactionColumn;
    @FXML private TableColumn<AssetEvent, String> eventStatusColumn;
    @FXML private TableColumn<AssetEvent, String> eventNotesColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Asset> assets = List.of();

    @FXML
    public void initialize() {
        valuationDatePicker.setValue(LocalDate.now());
        reasonField.setText("Updated valuation");
        AssetLifecycleSupport.configureEventTable(eventTable, eventDateColumn, eventTypeColumn, eventAmountColumn,
                eventCounterpartyColumn, eventTransactionColumn, eventStatusColumn, eventNotesColumn);
        assetBox.valueProperty().addListener((observable, oldValue, newValue) -> showAsset(newValue));
        refresh();
    }

    @FXML
    private void updateValuation() {
        try {
            Asset asset = selectedAsset();
            double newValue = CoreWorkspaceSupport.amount(newValueField, "New value");
            if (newValue < 0) {
                throw new IllegalArgumentException("New value cannot be negative.");
            }
            database.updateAssetValue(
                    asset.getId(),
                    newValue,
                    CoreWorkspaceSupport.requiredDate(valuationDatePicker, "Valuation date").toString(),
                    CoreWorkspaceSupport.required(reasonField, "Reason"),
                    notesArea.getText()
            );
            resultLabel.setText("Valuation saved.");
            DataRefreshBus.notifyDataChanged();
            refreshAsset(asset.getId());
        } catch (IllegalArgumentException exception) {
            resultLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update asset value", exception);
        }
    }

    @FXML
    private void refresh() {
        refreshAsset(assetBox.getValue() == null ? null : assetBox.getValue().getId());
    }

    private void refreshAsset(Integer selectedId) {
        assets = AssetLifecycleSupport.loadAssets(database, assetBox, selectedId);
        showAsset(assetBox.getValue());
    }

    private void showAsset(Asset asset) {
        AssetLifecycleSupport.setAssetSummary(asset, statusLabel, conditionLabel, valueLabel, locationLabel);
        if (asset != null) {
            newValueField.setText(String.format(Locale.ENGLISH, "%.2f", asset.getCurrentValue()));
        }
        AssetLifecycleSupport.loadEvents(database, asset, eventTable, eventStateLabel);
    }

    private Asset selectedAsset() {
        Asset asset = assetBox.getValue();
        if (asset == null) {
            throw new IllegalArgumentException("Select an asset first.");
        }
        return asset;
    }
}
