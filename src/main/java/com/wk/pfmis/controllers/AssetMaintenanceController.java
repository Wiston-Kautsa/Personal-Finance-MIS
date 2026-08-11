package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Asset;
import com.wk.pfmis.models.AssetEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.List;

public class AssetMaintenanceController {
    @FXML private ComboBox<Asset> assetBox;
    @FXML private Label statusLabel;
    @FXML private Label conditionLabel;
    @FXML private Label valueLabel;
    @FXML private Label locationLabel;
    @FXML private DatePicker maintenanceDatePicker;
    @FXML private TextField costField;
    @FXML private TextField providerField;
    @FXML private TextField referenceField;
    @FXML private CheckBox addToAssetValueBox;
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
        maintenanceDatePicker.setValue(LocalDate.now());
        costField.setText("0");
        AssetLifecycleSupport.configureEventTable(eventTable, eventDateColumn, eventTypeColumn, eventAmountColumn,
                eventCounterpartyColumn, eventTransactionColumn, eventStatusColumn, eventNotesColumn);
        assetBox.valueProperty().addListener((observable, oldValue, newValue) -> showAsset(newValue));
        refresh();
    }

    @FXML
    private void recordMaintenance() {
        try {
            Asset asset = selectedAsset();
            database.recordAssetMaintenance(
                    asset.getId(),
                    CoreWorkspaceSupport.requiredDate(maintenanceDatePicker, "Maintenance date").toString(),
                    CoreWorkspaceSupport.amount(costField, "Maintenance cost"),
                    providerField.getText(),
                    referenceField.getText(),
                    notesArea.getText(),
                    addToAssetValueBox.isSelected()
            );
            resultLabel.setText("Maintenance event recorded.");
            DataRefreshBus.notifyDataChanged();
            clearForm();
            refreshAsset(asset.getId());
        } catch (IllegalArgumentException exception) {
            resultLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to record maintenance", exception);
        }
    }

    @FXML
    private void clearForm() {
        maintenanceDatePicker.setValue(LocalDate.now());
        costField.setText("0");
        providerField.clear();
        referenceField.clear();
        addToAssetValueBox.setSelected(false);
        notesArea.clear();
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
