package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Asset;
import com.wk.pfmis.models.FinanceTransaction;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.util.List;

public class AssetRecognitionController {
    @FXML private Label candidateStateLabel;
    @FXML private Label pendingAssetStateLabel;
    @FXML private Label resultLabel;
    @FXML private TextArea guidanceArea;
    @FXML private TableView<FinanceTransaction> candidateTable;
    @FXML private TableColumn<FinanceTransaction, String> candidateIdColumn;
    @FXML private TableColumn<FinanceTransaction, String> candidateDateColumn;
    @FXML private TableColumn<FinanceTransaction, String> candidateDescriptionColumn;
    @FXML private TableColumn<FinanceTransaction, String> candidateAccountColumn;
    @FXML private TableColumn<FinanceTransaction, String> candidateCategoryColumn;
    @FXML private TableColumn<FinanceTransaction, String> candidateAmountColumn;
    @FXML private TableColumn<FinanceTransaction, String> candidateStatusColumn;
    @FXML private TableView<Asset> pendingAssetTable;
    @FXML private TableColumn<Asset, String> pendingNameColumn;
    @FXML private TableColumn<Asset, String> pendingCategoryColumn;
    @FXML private TableColumn<Asset, String> pendingValueColumn;
    @FXML private TableColumn<Asset, String> pendingDateColumn;
    @FXML private TableColumn<Asset, String> pendingStatusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        configureTables();
        candidateTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, transaction) -> showTransactionGuidance(transaction));
        pendingAssetTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, asset) -> showAssetGuidance(asset));
        refresh();
    }

    @FXML
    private void refresh() {
        List<FinanceTransaction> candidates = database.listRecentTransactions(1000).stream()
                .filter(AssetLifecycleSupport::isAssetCandidate)
                .limit(100)
                .toList();
        List<Asset> pending = database.listAssets().stream()
                .filter(asset -> "PENDING_REGISTRATION".equalsIgnoreCase(CoreWorkspaceSupport.safe(asset.getStatus())))
                .toList();
        CoreWorkspaceSupport.setItems(candidateTable, candidates, candidateStateLabel, "No purchase candidates detected.");
        CoreWorkspaceSupport.setItems(pendingAssetTable, pending, pendingAssetStateLabel, "No pending asset registrations.");
        guidanceArea.setText("Select a candidate transaction when a posted expense may need to become a registered asset. Registration will link the existing transaction and will not post a duplicate payment.");
    }

    @FXML
    private void recognizeSelectedTransaction() {
        FinanceTransaction transaction = candidateTable.getSelectionModel().getSelectedItem();
        if (transaction == null) {
            UiAlerts.info("Select a candidate transaction first.");
            return;
        }
        NavigationBus.requestAssetRegistration(
                "Transaction",
                transaction.getId(),
                CoreWorkspaceSupport.blank(transaction.getDescription(), "Asset purchase candidate"),
                "Candidate amount: " + CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), transaction.getAmount())
                        + "\nAccount: " + CoreWorkspaceSupport.dash(transaction.getAccountName())
                        + "\nCategory: " + CoreWorkspaceSupport.dash(transaction.getCategoryName())
                        + "\nUse the existing transaction link so PFMIS does not create a second payment."
        );
    }

    @FXML
    private void openRegisterAsset() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.REGISTER_ASSET);
    }

    @FXML
    private void openAssetRegister() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.ASSET_REGISTER);
    }

    private void configureTables() {
        CoreWorkspaceSupport.bind(candidateIdColumn, transaction -> "#" + transaction.getId());
        CoreWorkspaceSupport.bind(candidateDateColumn, FinanceTransaction::getTransactionDate);
        CoreWorkspaceSupport.bind(candidateDescriptionColumn, FinanceTransaction::getDescription);
        CoreWorkspaceSupport.bind(candidateAccountColumn, transaction -> CoreWorkspaceSupport.dash(transaction.getAccountName()));
        CoreWorkspaceSupport.bind(candidateCategoryColumn, transaction -> CoreWorkspaceSupport.dash(transaction.getCategoryName()));
        CoreWorkspaceSupport.bind(candidateAmountColumn, transaction -> CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), transaction.getAmount()));
        CoreWorkspaceSupport.bind(candidateStatusColumn, FinanceTransaction::getTransactionStatus);
        CoreWorkspaceSupport.bind(pendingNameColumn, Asset::getAssetName);
        CoreWorkspaceSupport.bind(pendingCategoryColumn, Asset::getAssetCategory);
        CoreWorkspaceSupport.bind(pendingValueColumn, asset -> CoreWorkspaceSupport.money(asset.getCurrency(), asset.getCurrentValue()));
        CoreWorkspaceSupport.bind(pendingDateColumn, Asset::getPurchaseDate);
        CoreWorkspaceSupport.bind(pendingStatusColumn, asset -> AssetLifecycleSupport.displayStatus(asset.getStatus()));
        TableActions.configureScrollableTable(candidateTable);
        TableActions.configureScrollableTable(pendingAssetTable);
    }

    private void showTransactionGuidance(FinanceTransaction transaction) {
        if (transaction == null) {
            return;
        }
        resultLabel.setText("Candidate transaction #" + transaction.getId() + " selected.");
        guidanceArea.setText("""
                Transaction candidate

                Date: %s
                Description: %s
                Account: %s
                Category: %s
                Amount: %s

                Use Recognize Selected Transaction to open Register Asset with this transaction ID prefilled. The existing purchase transaction stays the financial posting source.
                """.formatted(
                CoreWorkspaceSupport.dash(transaction.getTransactionDate()),
                CoreWorkspaceSupport.dash(transaction.getDescription()),
                CoreWorkspaceSupport.dash(transaction.getAccountName()),
                CoreWorkspaceSupport.dash(transaction.getCategoryName()),
                CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), transaction.getAmount())
        ));
    }

    private void showAssetGuidance(Asset asset) {
        if (asset == null) {
            return;
        }
        resultLabel.setText("Pending asset #" + asset.getId() + " selected.");
        guidanceArea.setText("""
                Pending asset registration

                Asset: %s
                Category: %s
                Current value: %s
                Status: %s

                Open the Asset Register for detail review or use Register Asset for a new asset record.
                """.formatted(
                asset.getAssetName(),
                CoreWorkspaceSupport.dash(asset.getAssetCategory()),
                CoreWorkspaceSupport.money(asset.getCurrency(), asset.getCurrentValue()),
                AssetLifecycleSupport.displayStatus(asset.getStatus())
        ));
    }
}
