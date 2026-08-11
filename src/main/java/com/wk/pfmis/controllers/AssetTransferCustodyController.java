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

public class AssetTransferCustodyController {
    @FXML private ComboBox<Asset> assetBox;
    @FXML private Label statusLabel;
    @FXML private Label conditionLabel;
    @FXML private Label valueLabel;
    @FXML private Label locationLabel;
    @FXML private ComboBox<String> actionBox;
    @FXML private DatePicker eventDatePicker;
    @FXML private TextField counterpartyField;
    @FXML private TextField reasonField;
    @FXML private TextField referenceField;
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

    @FXML
    public void initialize() {
        CoreWorkspaceSupport.setComboItems(actionBox, "Custody Update",
                "Custody Update", "Transfer Out", "Donate", "Mark Damaged", "Freeze / Hold", "Return To Active");
        eventDatePicker.setValue(LocalDate.now());
        AssetLifecycleSupport.configureEventTable(eventTable, eventDateColumn, eventTypeColumn, eventAmountColumn,
                eventCounterpartyColumn, eventTransactionColumn, eventStatusColumn, eventNotesColumn);
        assetBox.valueProperty().addListener((observable, oldValue, newValue) -> showAsset(newValue));
        refresh();
    }

    @FXML
    private void saveCustodyEvent() {
        try {
            Asset asset = selectedAsset();
            StatusAction action = statusAction(CoreWorkspaceSupport.selected(actionBox, "Custody Update"));
            database.updateAssetStatusWithEvent(
                    asset.getId(),
                    action.status(),
                    action.eventType(),
                    CoreWorkspaceSupport.requiredDate(eventDatePicker, "Event date").toString(),
                    counterpartyField.getText(),
                    CoreWorkspaceSupport.required(reasonField, "Reason"),
                    referenceField.getText(),
                    notesArea.getText()
            );
            resultLabel.setText("Asset custody/status event saved.");
            DataRefreshBus.notifyDataChanged();
            clearForm();
            refreshAsset(asset.getId());
        } catch (IllegalArgumentException exception) {
            resultLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update asset custody", exception);
        }
    }

    @FXML
    private void clearForm() {
        actionBox.getSelectionModel().select("Custody Update");
        eventDatePicker.setValue(LocalDate.now());
        counterpartyField.clear();
        reasonField.clear();
        referenceField.clear();
        notesArea.clear();
    }

    @FXML
    private void refresh() {
        refreshAsset(assetBox.getValue() == null ? null : assetBox.getValue().getId());
    }

    private void refreshAsset(Integer selectedId) {
        AssetLifecycleSupport.loadAssets(database, assetBox, selectedId);
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

    private StatusAction statusAction(String action) {
        return switch (action) {
            case "Transfer Out" -> new StatusAction("TRANSFERRED", "TRANSFER");
            case "Donate" -> new StatusAction("DONATED", "DONATION");
            case "Mark Damaged" -> new StatusAction("DAMAGED", "CONDITION");
            case "Freeze / Hold" -> new StatusAction("FROZEN", "CUSTODY_HOLD");
            case "Return To Active" -> new StatusAction("ACTIVE", "CUSTODY_RETURN");
            default -> new StatusAction("ACTIVE", "CUSTODY_UPDATE");
        };
    }

    private record StatusAction(String status, String eventType) {
    }
}
