package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.FinanceTransaction;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.List;

public class CorrectionsReversalsController {
    @FXML private TextField transactionIdField;
    @FXML private TextArea reasonArea;
    @FXML private Label resultLabel;
    @FXML private Label draftStateLabel;
    @FXML private Label recentStateLabel;
    @FXML private TableView<DatabaseHandler.TransactionCorrectionDraftRecord> correctionDraftsTable;
    @FXML private TableColumn<DatabaseHandler.TransactionCorrectionDraftRecord, String> draftIdColumn;
    @FXML private TableColumn<DatabaseHandler.TransactionCorrectionDraftRecord, String> draftOriginalColumn;
    @FXML private TableColumn<DatabaseHandler.TransactionCorrectionDraftRecord, String> draftAccountColumn;
    @FXML private TableColumn<DatabaseHandler.TransactionCorrectionDraftRecord, String> draftTypeColumn;
    @FXML private TableColumn<DatabaseHandler.TransactionCorrectionDraftRecord, String> draftAmountColumn;
    @FXML private TableColumn<DatabaseHandler.TransactionCorrectionDraftRecord, String> draftDateColumn;
    @FXML private TableColumn<DatabaseHandler.TransactionCorrectionDraftRecord, String> draftStatusColumn;
    @FXML private TableView<FinanceTransaction> recentTransactionsTable;
    @FXML private TableColumn<FinanceTransaction, String> recentIdColumn;
    @FXML private TableColumn<FinanceTransaction, String> recentDateColumn;
    @FXML private TableColumn<FinanceTransaction, String> recentTypeColumn;
    @FXML private TableColumn<FinanceTransaction, String> recentAccountColumn;
    @FXML private TableColumn<FinanceTransaction, String> recentAmountColumn;
    @FXML private TableColumn<FinanceTransaction, String> recentStatusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        configureTables();
        refresh();
    }

    @FXML
    private void createReversal() {
        try {
            int transactionId = transactionId();
            int reversalId = database.createTransactionReversal(transactionId, reason());
            resultLabel.setText("Transaction #" + transactionId + " was reversed by transaction #" + reversalId + ".");
            DataRefreshBus.notifyDataChanged();
            refresh();
        } catch (IllegalArgumentException | SecurityException exception) {
            resultLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to reverse transaction", exception);
        }
    }

    @FXML
    private void createCorrectionDraft() {
        try {
            int transactionId = transactionId();
            int draftId = database.createCorrectedTransactionDraft(transactionId, reason());
            resultLabel.setText("Correction draft #" + draftId + " was created from transaction #" + transactionId + ".");
            DataRefreshBus.notifyDataChanged();
            refresh();
        } catch (IllegalArgumentException | SecurityException exception) {
            resultLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to create corrected transaction draft", exception);
        }
    }

    @FXML
    private void openLedger() {
        CoreWorkspaceSupport.navigate(CoreWorkspaceRoute.TRANSACTION_LEDGER);
    }

    @FXML
    private void clearForm() {
        transactionIdField.clear();
        reasonArea.clear();
        resultLabel.setText("Enter a posted transaction ID and a reason before creating a reversal or correction draft.");
    }

    @FXML
    private void refresh() {
        List<DatabaseHandler.TransactionCorrectionDraftRecord> drafts = database.listTransactionCorrectionDrafts(500);
        CoreWorkspaceSupport.setItems(correctionDraftsTable, drafts, draftStateLabel, "No correction drafts.");
        List<FinanceTransaction> recent = database.listRecentTransactions(500).stream()
                .filter(transaction -> !"TRANSFER".equalsIgnoreCase(CoreWorkspaceSupport.safe(transaction.getTransactionType())))
                .limit(30)
                .toList();
        CoreWorkspaceSupport.setItems(recentTransactionsTable, recent, recentStateLabel, "No posted income or expense transactions.");
    }

    private void configureTables() {
        CoreWorkspaceSupport.bind(draftIdColumn, item -> "#" + item.id());
        CoreWorkspaceSupport.bind(draftOriginalColumn, item -> "#" + item.originalTransactionId());
        CoreWorkspaceSupport.bind(draftAccountColumn, item -> CoreWorkspaceSupport.dash(item.accountName()));
        CoreWorkspaceSupport.bind(draftTypeColumn, item -> CoreWorkspaceSupport.dash(item.transactionType()) + " / " + CoreWorkspaceSupport.dash(item.transactionPurpose()));
        CoreWorkspaceSupport.bind(draftAmountColumn, item -> CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), item.amount()));
        CoreWorkspaceSupport.bind(draftDateColumn, DatabaseHandler.TransactionCorrectionDraftRecord::transactionDate);
        CoreWorkspaceSupport.bind(draftStatusColumn, DatabaseHandler.TransactionCorrectionDraftRecord::status);
        CoreWorkspaceSupport.bind(recentIdColumn, item -> "#" + item.getId());
        CoreWorkspaceSupport.bind(recentDateColumn, FinanceTransaction::getTransactionDate);
        CoreWorkspaceSupport.bind(recentTypeColumn, item -> CoreWorkspaceSupport.dash(item.getTransactionType()) + " / " + CoreWorkspaceSupport.dash(item.getTransactionPurpose()));
        CoreWorkspaceSupport.bind(recentAccountColumn, item -> CoreWorkspaceSupport.dash(item.getAccountName()));
        CoreWorkspaceSupport.bind(recentAmountColumn, item -> CoreWorkspaceSupport.money(database.getBaseCurrencyCode(), item.getAmount()));
        CoreWorkspaceSupport.bind(recentStatusColumn, FinanceTransaction::getTransactionStatus);
        TableActions.configureScrollableTable(correctionDraftsTable);
        TableActions.configureScrollableTable(recentTransactionsTable);
        recentTransactionsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, transaction) -> {
            if (transaction != null) {
                transactionIdField.setText(String.valueOf(transaction.getId()));
                resultLabel.setText("Selected transaction #" + transaction.getId() + " for lifecycle action.");
            }
        });
    }

    private int transactionId() {
        try {
            int id = Integer.parseInt(CoreWorkspaceSupport.required(transactionIdField, "Transaction ID"));
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Transaction ID must be a positive whole number.");
        }
    }

    private String reason() {
        String value = CoreWorkspaceSupport.safe(reasonArea.getText());
        if (value.length() < 5) {
            throw new IllegalArgumentException("Enter a clear reason before changing transaction lifecycle state.");
        }
        return value;
    }
}
