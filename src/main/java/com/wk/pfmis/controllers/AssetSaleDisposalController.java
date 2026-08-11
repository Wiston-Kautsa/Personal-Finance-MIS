package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Asset;
import com.wk.pfmis.models.AssetEvent;
import javafx.collections.FXCollections;
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

public class AssetSaleDisposalController {
    @FXML private ComboBox<Asset> assetBox;
    @FXML private Label statusLabel;
    @FXML private Label conditionLabel;
    @FXML private Label valueLabel;
    @FXML private Label locationLabel;
    @FXML private ComboBox<String> saleTypeBox;
    @FXML private DatePicker saleDatePicker;
    @FXML private TextField buyerField;
    @FXML private TextField salePriceField;
    @FXML private TextField sellingCostsField;
    @FXML private ComboBox<Account> receivingAccountBox;
    @FXML private ComboBox<String> paymentMethodBox;
    @FXML private ComboBox<String> paymentOptionBox;
    @FXML private TextField amountReceivedField;
    @FXML private TextField dueDateField;
    @FXML private TextField quantitySoldField;
    @FXML private TextField valueRemovedField;
    @FXML private TextField reasonField;
    @FXML private TextField referenceField;
    @FXML private TextField supportingDocumentField;
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
        CoreWorkspaceSupport.setComboItems(saleTypeBox, "Full Sale", "Full Sale", "Partial Sale");
        CoreWorkspaceSupport.setComboItems(paymentOptionBox, "Full payment received",
                "Full payment received", "Partial payment received", "Payment to be received later", "Asset exchanged for another asset");
        saleDatePicker.setValue(LocalDate.now());
        reasonField.setText("Asset sold");
        paymentMethodBox.setItems(FXCollections.observableArrayList(database.listPaymentMethodSuggestions()));
        if (!paymentMethodBox.getItems().isEmpty()) {
            paymentMethodBox.getSelectionModel().selectFirst();
        }
        receivingAccountBox.setItems(FXCollections.observableArrayList(activeAccounts()));
        AssetLifecycleSupport.configureEventTable(eventTable, eventDateColumn, eventTypeColumn, eventAmountColumn,
                eventCounterpartyColumn, eventTransactionColumn, eventStatusColumn, eventNotesColumn);
        assetBox.valueProperty().addListener((observable, oldValue, newValue) -> showAsset(newValue));
        refresh();
    }

    @FXML
    private void confirmSale() {
        try {
            Asset asset = selectedAsset();
            Account account = receivingAccountBox.getValue();
            if (account == null) {
                throw new IllegalArgumentException("Choose the receiving account for sale proceeds or receivables.");
            }
            database.sellAsset(
                    asset.getId(),
                    "Partial Sale".equals(saleTypeBox.getValue()) ? "PARTIAL_SALE" : "FULL_SALE",
                    CoreWorkspaceSupport.requiredDate(saleDatePicker, "Sale date").toString(),
                    buyerField.getText(),
                    CoreWorkspaceSupport.amount(salePriceField, "Sale price"),
                    asset.getCurrency(),
                    account.getId(),
                    CoreWorkspaceSupport.selected(paymentMethodBox, ""),
                    referenceField.getText(),
                    CoreWorkspaceSupport.amount(sellingCostsField, "Selling costs"),
                    CoreWorkspaceSupport.required(reasonField, "Reason"),
                    supportingDocumentField.getText(),
                    notesArea.getText(),
                    CoreWorkspaceSupport.selected(paymentOptionBox, "Full payment received"),
                    CoreWorkspaceSupport.amount(amountReceivedField, "Amount received"),
                    dueDateField.getText(),
                    CoreWorkspaceSupport.amount(quantitySoldField, "Quantity or portion sold"),
                    CoreWorkspaceSupport.amount(valueRemovedField, "Value removed")
            );
            resultLabel.setText("Asset sale recorded through the existing transaction posting workflow.");
            DataRefreshBus.notifyDataChanged();
            refreshAsset(asset.getId());
        } catch (IllegalArgumentException exception) {
            resultLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to record asset sale", exception);
        }
    }

    @FXML
    private void markDisposed() {
        closeWithoutSale("DISPOSED", "DISPOSAL");
    }

    @FXML
    private void writeOffAsset() {
        closeWithoutSale("WRITTEN_OFF", "WRITE_OFF");
    }

    @FXML
    private void markLost() {
        closeWithoutSale("LOST", "LOST");
    }

    @FXML
    private void refresh() {
        receivingAccountBox.setItems(FXCollections.observableArrayList(activeAccounts()));
        refreshAsset(assetBox.getValue() == null ? null : assetBox.getValue().getId());
    }

    private void closeWithoutSale(String status, String eventType) {
        try {
            Asset asset = selectedAsset();
            database.updateAssetStatusWithEvent(
                    asset.getId(),
                    status,
                    eventType,
                    CoreWorkspaceSupport.requiredDate(saleDatePicker, "Event date").toString(),
                    buyerField.getText(),
                    CoreWorkspaceSupport.required(reasonField, "Reason"),
                    referenceField.getText(),
                    notesArea.getText()
            );
            resultLabel.setText("Asset status changed to " + AssetLifecycleSupport.displayStatus(status) + ".");
            DataRefreshBus.notifyDataChanged();
            refreshAsset(asset.getId());
        } catch (IllegalArgumentException exception) {
            resultLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update asset disposal status", exception);
        }
    }

    private void refreshAsset(Integer selectedId) {
        AssetLifecycleSupport.loadAssets(database, assetBox, selectedId);
        showAsset(assetBox.getValue());
    }

    private void showAsset(Asset asset) {
        AssetLifecycleSupport.setAssetSummary(asset, statusLabel, conditionLabel, valueLabel, locationLabel);
        if (asset != null) {
            salePriceField.setText(String.format(Locale.ENGLISH, "%.2f", asset.getCurrentValue()));
            amountReceivedField.setText(String.format(Locale.ENGLISH, "%.2f", asset.getCurrentValue()));
            quantitySoldField.setText(String.format(Locale.ENGLISH, "%.2f", asset.getQuantity()));
            valueRemovedField.setText(String.format(Locale.ENGLISH, "%.2f", asset.getCurrentValue()));
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

    private List<Account> activeAccounts() {
        return database.listAccounts().stream()
                .filter(account -> !"INACTIVE".equalsIgnoreCase(CoreWorkspaceSupport.safe(account.getStatus())))
                .toList();
    }
}
