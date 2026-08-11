package com.wk.pfmis.controllers;

import com.wk.pfmis.services.OverviewWorkspaceService;
import com.wk.pfmis.services.OverviewWorkspaceService.GoalsOverviewData;
import com.wk.pfmis.services.OverviewWorkspaceService.OverviewRow;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class GoalsOverviewController {
    @FXML private Label activeValueLabel;
    @FXML private Label onTrackValueLabel;
    @FXML private Label atRiskValueLabel;
    @FXML private Label dueSoonValueLabel;
    @FXML private Label emptyStateLabel;
    @FXML private Label progressStateLabel;
    @FXML private Label stepsStateLabel;
    @FXML private Label attentionStateLabel;
    @FXML private TableView<OverviewRow> progressTable;
    @FXML private TableColumn<OverviewRow, String> goalColumn;
    @FXML private TableColumn<OverviewRow, String> targetColumn;
    @FXML private TableColumn<OverviewRow, String> allocatedColumn;
    @FXML private TableColumn<OverviewRow, String> remainingColumn;
    @FXML private TableColumn<OverviewRow, String> deadlineColumn;
    @FXML private TableColumn<OverviewRow, String> progressColumn;
    @FXML private TableView<OverviewRow> stepsTable;
    @FXML private TableColumn<OverviewRow, String> stepGoalColumn;
    @FXML private TableColumn<OverviewRow, String> stepNameColumn;
    @FXML private TableColumn<OverviewRow, String> stepCostColumn;
    @FXML private TableColumn<OverviewRow, String> stepMissingColumn;
    @FXML private TableColumn<OverviewRow, String> stepDateColumn;
    @FXML private TableColumn<OverviewRow, String> stepStatusColumn;
    @FXML private TableView<OverviewRow> attentionTable;
    @FXML private TableColumn<OverviewRow, String> attentionGoalColumn;
    @FXML private TableColumn<OverviewRow, String> attentionReasonColumn;
    @FXML private TableColumn<OverviewRow, String> attentionRemainingColumn;
    @FXML private TableColumn<OverviewRow, String> attentionRequiredColumn;
    @FXML private TableColumn<OverviewRow, String> attentionDateColumn;
    @FXML private TableColumn<OverviewRow, String> attentionStatusColumn;

    private final OverviewWorkspaceService service = new OverviewWorkspaceService();

    @FXML
    public void initialize() {
        OverviewScreenSupport.configureTable(progressTable, goalColumn, targetColumn, allocatedColumn,
                remainingColumn, deadlineColumn, progressColumn, null);
        OverviewScreenSupport.configureTable(stepsTable, stepGoalColumn, stepNameColumn, stepCostColumn,
                stepMissingColumn, stepDateColumn, stepStatusColumn, null);
        OverviewScreenSupport.configureTable(attentionTable, attentionGoalColumn, attentionReasonColumn, attentionRemainingColumn,
                attentionRequiredColumn, attentionDateColumn, attentionStatusColumn, null);
        refresh();
    }

    @FXML
    private void refresh() {
        try {
            GoalsOverviewData data = service.goalsOverview();
            activeValueLabel.setText(data.activeGoals());
            onTrackValueLabel.setText(data.onTrack());
            atRiskValueLabel.setText(data.atRisk());
            dueSoonValueLabel.setText(data.dueSoon());
            OverviewScreenSupport.setRows(progressTable, data.progress(), progressStateLabel, "No goal records yet.");
            OverviewScreenSupport.setRows(stepsTable, data.nextSteps(), stepsStateLabel, "No unfinished goal steps.");
            OverviewScreenSupport.setRows(attentionTable, data.attention(), attentionStateLabel, "No goals need attention.");
            OverviewScreenSupport.setEmptyState(emptyStateLabel, data.empty(),
                    "No goals are currently recorded. Add a goal before tracking contributions and steps.",
                    "Goal progress uses the saved goal contribution ledger.");
        } catch (RuntimeException exception) {
            emptyStateLabel.setText("Goals overview could not refresh: " + exception.getMessage());
        }
    }

    @FXML private void openAddGoal() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.ADD_GOAL); }
    @FXML private void openGoalContributions() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.GOAL_CONTRIBUTIONS); }
    @FXML private void openGoalSteps() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.GOAL_STEPS); }
    @FXML private void openGoalHistory() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.GOAL_HISTORY); }
}
