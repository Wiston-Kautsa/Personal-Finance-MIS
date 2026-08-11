package com.wk.pfmis.controllers;

import com.wk.pfmis.services.OverviewWorkspaceService;
import com.wk.pfmis.services.OverviewWorkspaceService.LoanOverviewData;
import com.wk.pfmis.services.OverviewWorkspaceService.OverviewRow;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class LoanOverviewController {
    @FXML private Label borrowedValueLabel;
    @FXML private Label lentValueLabel;
    @FXML private Label dueSoonValueLabel;
    @FXML private Label overdueValueLabel;
    @FXML private Label emptyStateLabel;
    @FXML private Label attentionStateLabel;
    @FXML private Label repaymentsStateLabel;
    @FXML private Label activityStateLabel;
    @FXML private TableView<OverviewRow> attentionTable;
    @FXML private TableColumn<OverviewRow, String> attentionLoanColumn;
    @FXML private TableColumn<OverviewRow, String> attentionDirectionColumn;
    @FXML private TableColumn<OverviewRow, String> attentionBalanceColumn;
    @FXML private TableColumn<OverviewRow, String> attentionPaymentColumn;
    @FXML private TableColumn<OverviewRow, String> attentionDueColumn;
    @FXML private TableColumn<OverviewRow, String> attentionStatusColumn;
    @FXML private TableView<OverviewRow> repaymentsTable;
    @FXML private TableColumn<OverviewRow, String> repaymentLoanColumn;
    @FXML private TableColumn<OverviewRow, String> repaymentDirectionColumn;
    @FXML private TableColumn<OverviewRow, String> repaymentBalanceColumn;
    @FXML private TableColumn<OverviewRow, String> repaymentAmountColumn;
    @FXML private TableColumn<OverviewRow, String> repaymentDueColumn;
    @FXML private TableColumn<OverviewRow, String> repaymentStatusColumn;
    @FXML private TableView<OverviewRow> activityTable;
    @FXML private TableColumn<OverviewRow, String> activityDateColumn;
    @FXML private TableColumn<OverviewRow, String> activityPartyColumn;
    @FXML private TableColumn<OverviewRow, String> activityTypeColumn;
    @FXML private TableColumn<OverviewRow, String> activityAmountColumn;
    @FXML private TableColumn<OverviewRow, String> activityReferenceColumn;
    @FXML private TableColumn<OverviewRow, String> activityStatusColumn;

    private final OverviewWorkspaceService service = new OverviewWorkspaceService();

    @FXML
    public void initialize() {
        OverviewScreenSupport.configureTable(attentionTable, attentionLoanColumn, attentionDirectionColumn, attentionBalanceColumn,
                attentionPaymentColumn, attentionDueColumn, attentionStatusColumn, null);
        OverviewScreenSupport.configureTable(repaymentsTable, repaymentLoanColumn, repaymentDirectionColumn, repaymentBalanceColumn,
                repaymentAmountColumn, repaymentDueColumn, repaymentStatusColumn, null);
        OverviewScreenSupport.configureTable(activityTable, activityDateColumn, activityPartyColumn, activityTypeColumn,
                activityAmountColumn, activityReferenceColumn, activityStatusColumn, null);
        refresh();
    }

    @FXML
    private void refresh() {
        try {
            LoanOverviewData data = service.loanOverview();
            borrowedValueLabel.setText(data.borrowedOutstanding());
            lentValueLabel.setText(data.lentOutstanding());
            dueSoonValueLabel.setText(data.dueSoon());
            overdueValueLabel.setText(data.overdue());
            OverviewScreenSupport.setRows(attentionTable, data.attention(), attentionStateLabel, "No loans need attention.");
            OverviewScreenSupport.setRows(repaymentsTable, data.repayments(), repaymentsStateLabel, "No upcoming repayments.");
            OverviewScreenSupport.setRows(activityTable, data.recentActivity(), activityStateLabel, "No recent loan activity.");
            OverviewScreenSupport.setEmptyState(emptyStateLabel, data.empty(),
                    "No loans are currently recorded. Create a loan schedule when money is lent or borrowed.",
                    "Borrowed and lent positions are shown separately.");
        } catch (RuntimeException exception) {
            emptyStateLabel.setText("Loan overview could not refresh: " + exception.getMessage());
        }
    }

    @FXML private void openNewLoan() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.NEW_LOAN); }
    @FXML private void openLoanRecords() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.LOAN_RECORDS); }
    @FXML private void openRecordRepayment() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.RECORD_REPAYMENT); }
    @FXML private void openRepaymentSchedule() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.REPAYMENT_SCHEDULE); }
    @FXML private void openLoanContacts() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.LOAN_CONTACTS); }
}
