package com.wk.pfmis.controllers;

import com.wk.pfmis.services.OverviewWorkspaceService;
import com.wk.pfmis.services.OverviewWorkspaceService.OverviewRow;
import com.wk.pfmis.services.OverviewWorkspaceService.ProjectOverviewData;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class ProjectOverviewController {
    @FXML private Label activeValueLabel;
    @FXML private Label atRiskValueLabel;
    @FXML private Label overdueValueLabel;
    @FXML private Label milestonesValueLabel;
    @FXML private Label emptyStateLabel;
    @FXML private Label projectsStateLabel;
    @FXML private Label attentionStateLabel;
    @FXML private Label activityStateLabel;
    @FXML private TableView<OverviewRow> projectsTable;
    @FXML private TableColumn<OverviewRow, String> projectColumn;
    @FXML private TableColumn<OverviewRow, String> plannedColumn;
    @FXML private TableColumn<OverviewRow, String> actualColumn;
    @FXML private TableColumn<OverviewRow, String> progressColumn;
    @FXML private TableColumn<OverviewRow, String> milestoneColumn;
    @FXML private TableColumn<OverviewRow, String> statusColumn;
    @FXML private TableView<OverviewRow> attentionTable;
    @FXML private TableColumn<OverviewRow, String> attentionProjectColumn;
    @FXML private TableColumn<OverviewRow, String> attentionReasonColumn;
    @FXML private TableColumn<OverviewRow, String> attentionGapColumn;
    @FXML private TableColumn<OverviewRow, String> attentionRemainingColumn;
    @FXML private TableColumn<OverviewRow, String> attentionDateColumn;
    @FXML private TableColumn<OverviewRow, String> attentionStatusColumn;
    @FXML private TableView<OverviewRow> activityTable;
    @FXML private TableColumn<OverviewRow, String> activityDateColumn;
    @FXML private TableColumn<OverviewRow, String> activityProjectColumn;
    @FXML private TableColumn<OverviewRow, String> activityNameColumn;
    @FXML private TableColumn<OverviewRow, String> activityAmountColumn;
    @FXML private TableColumn<OverviewRow, String> activityProgressColumn;
    @FXML private TableColumn<OverviewRow, String> activityStatusColumn;

    private final OverviewWorkspaceService service = new OverviewWorkspaceService();

    @FXML
    public void initialize() {
        OverviewScreenSupport.configureTable(projectsTable, projectColumn, plannedColumn, actualColumn,
                progressColumn, milestoneColumn, statusColumn, null);
        OverviewScreenSupport.configureTable(attentionTable, attentionProjectColumn, attentionReasonColumn, attentionGapColumn,
                attentionRemainingColumn, attentionDateColumn, attentionStatusColumn, null);
        OverviewScreenSupport.configureTable(activityTable, activityDateColumn, activityProjectColumn, activityNameColumn,
                activityAmountColumn, activityProgressColumn, activityStatusColumn, null);
        refresh();
    }

    @FXML
    private void refresh() {
        try {
            ProjectOverviewData data = service.projectOverview();
            activeValueLabel.setText(data.activeProjects());
            atRiskValueLabel.setText(data.atRisk());
            overdueValueLabel.setText(data.overdueActivities());
            milestonesValueLabel.setText(data.upcomingMilestones());
            OverviewScreenSupport.setRows(projectsTable, data.projects(), projectsStateLabel, "No project records yet.");
            OverviewScreenSupport.setRows(attentionTable, data.attention(), attentionStateLabel, "No projects need attention.");
            OverviewScreenSupport.setRows(activityTable, data.recentActivity(), activityStateLabel, "No recent project activity.");
            OverviewScreenSupport.setEmptyState(emptyStateLabel, data.empty(),
                    "No projects are currently recorded. Add a project before tracking activities, finances and milestones.",
                    "Project actuals are derived from linked posted project transactions.");
        } catch (RuntimeException exception) {
            emptyStateLabel.setText("Project overview could not refresh: " + exception.getMessage());
        }
    }

    @FXML private void openAddProject() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.ADD_PROJECT); }
    @FXML private void openProjectActivities() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.PROJECT_ACTIVITIES); }
    @FXML private void openProjectFinances() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.PROJECT_FINANCES); }
    @FXML private void openMilestonesStatus() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.PROJECT_MILESTONES_STATUS); }
    @FXML private void openProjectHistory() { OverviewScreenSupport.navigate(CoreWorkspaceRoute.PROJECT_HISTORY); }
}
