package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Asset;
import com.wk.pfmis.models.AssetEvent;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;
import java.util.Locale;

final class AssetLifecycleSupport {
    private AssetLifecycleSupport() {
    }

    static List<Asset> loadAssets(DatabaseHandler database, ComboBox<Asset> assetBox, Integer selectedId) {
        List<Asset> assets = database.listAssets();
        assetBox.setItems(FXCollections.observableArrayList(assets));
        Asset selected = selectedId == null ? null : CoreWorkspaceSupport.assetById(assets, selectedId);
        if (selected == null && !assets.isEmpty()) {
            selected = assets.get(0);
        }
        assetBox.getSelectionModel().select(selected);
        return assets;
    }

    static void setAssetSummary(Asset asset, Label statusLabel, Label conditionLabel, Label valueLabel, Label locationLabel) {
        if (asset == null) {
            statusLabel.setText("-");
            conditionLabel.setText("-");
            valueLabel.setText("-");
            locationLabel.setText("-");
            return;
        }
        statusLabel.setText(displayStatus(asset.getStatus()));
        conditionLabel.setText(CoreWorkspaceSupport.dash(asset.getAssetCondition()));
        valueLabel.setText(CoreWorkspaceSupport.money(asset.getCurrency(), asset.getCurrentValue()));
        locationLabel.setText(CoreWorkspaceSupport.dash(asset.getLocation()));
    }

    static void configureEventTable(
            TableView<AssetEvent> table,
            TableColumn<AssetEvent, String> dateColumn,
            TableColumn<AssetEvent, String> eventColumn,
            TableColumn<AssetEvent, String> amountColumn,
            TableColumn<AssetEvent, String> counterpartyColumn,
            TableColumn<AssetEvent, String> transactionColumn,
            TableColumn<AssetEvent, String> statusColumn,
            TableColumn<AssetEvent, String> notesColumn
    ) {
        CoreWorkspaceSupport.bind(dateColumn, AssetEvent::getEventDate);
        CoreWorkspaceSupport.bind(eventColumn, event -> displayStatus(event.getEventType()));
        CoreWorkspaceSupport.bind(amountColumn, event -> CoreWorkspaceSupport.money(event.getCurrency(), event.getAmount()));
        CoreWorkspaceSupport.bind(counterpartyColumn, event -> CoreWorkspaceSupport.dash(event.getCounterparty()));
        CoreWorkspaceSupport.bind(transactionColumn, event -> event.getTransactionId() == null ? "-" : "#" + event.getTransactionId());
        CoreWorkspaceSupport.bind(statusColumn, event -> displayStatus(event.getPaymentStatus()));
        CoreWorkspaceSupport.bind(notesColumn, event -> CoreWorkspaceSupport.dash(event.getReason()) + " " + CoreWorkspaceSupport.dash(event.getNotes()));
        TableActions.configureScrollableTable(table);
    }

    static void loadEvents(DatabaseHandler database, Asset asset, TableView<AssetEvent> table, Label stateLabel) {
        List<AssetEvent> events = asset == null ? List.of() : database.listAssetEvents(asset.getId());
        CoreWorkspaceSupport.setItems(table, events, stateLabel, asset == null ? "No asset selected." : "No asset events yet.");
    }

    static String displayStatus(String status) {
        String clean = CoreWorkspaceSupport.safe(status).replace('_', ' ').toLowerCase(Locale.ENGLISH);
        if (clean.isBlank()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : clean.split("\\s+")) {
            builder.append(part.substring(0, 1).toUpperCase(Locale.ENGLISH)).append(part.substring(1)).append(' ');
        }
        return builder.toString().trim();
    }

    static boolean isAssetCandidate(com.wk.pfmis.models.FinanceTransaction transaction) {
        if (!"EXPENSE".equalsIgnoreCase(CoreWorkspaceSupport.safe(transaction.getTransactionType()))) {
            return false;
        }
        String text = (CoreWorkspaceSupport.safe(transaction.getCategoryName()) + " "
                + CoreWorkspaceSupport.safe(transaction.getDescription()) + " "
                + CoreWorkspaceSupport.safe(transaction.getTransactionPurpose())).toLowerCase(Locale.ENGLISH);
        return text.contains("asset")
                || text.contains("equipment")
                || text.contains("furniture")
                || text.contains("vehicle")
                || text.contains("land")
                || text.contains("building");
    }
}
