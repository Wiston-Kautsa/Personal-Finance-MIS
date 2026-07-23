package com.wk.pfmis.controllers;

import com.wk.pfmis.ai.PfmisIntelligenceService;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.AiSettings;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.util.concurrent.CompletableFuture;

public class AiCenterController {
    @FXML private VBox aiCenterRoot;
    @FXML private Label providerStatusLabel;
    @FXML private Label privacyStatusLabel;
    @FXML private Label financialOverviewLabel;
    @FXML private Label systemCoverageLabel;
    @FXML private Label reportCoverageLabel;
    @FXML private VBox actionQueueBox;
    @FXML private TextArea questionArea;
    @FXML private TextArea answerArea;
    @FXML private Label requestStatusLabel;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final PfmisIntelligenceService intelligence = new PfmisIntelligenceService();

    @FXML
    public void initialize() {
        DataRefreshBus.addListener(this::refresh);
        refresh();
    }

    @FXML
    private void refresh() {
        AiSettings settings = database.getAiSettings();
        providerStatusLabel.setText(providerStatus(settings));
        privacyStatusLabel.setText(settings != null && settings.isLocalProvider()
                ? "Private local mode: recommendations run on this computer."
                : "External provider mode: PFMIS withholds entered form values and sends only prepared financial summaries.");
        financialOverviewLabel.setText(intelligence.deterministicOverview());
        systemCoverageLabel.setText(systemCoverageText());
        reportCoverageLabel.setText(reportCoverageText());
        refreshActionQueue();
    }

    @FXML
    private void generateDailyBriefing() {
        runAiRequest(
                "Daily financial briefing",
                "Give me a concise daily financial briefing. Identify the most important risk, one positive trend, and the three actions I should take next in PFMIS."
        );
    }

    @FXML
    private void runDeepSystemAnalysis() {
        runAiRequest(
                "Deep system analysis",
                "Do a deep PFMIS system analysis using the verified financial facts. Cover the full system workflow: setup and administration, accounts, income, expenses, transfers, lending, borrowing, repayments, budgets, household members, goals, goal steps, turning goals into projects, projects, project activities, project status, all reports, backups, currencies, payment methods, data quality, system logs, analysis logs, risks, corrections, and priority actions."
        );
    }

    @FXML
    private void runReportsAnalysis() {
        runAiRequest(
                "Reports analysis",
                "Analyse all PFMIS reports using verified data: monthly, quarterly, half-year and annual summaries, cash flow, budget versus actual, net worth, financial position, income source analysis, category spending, expense trends, savings and goals progress, loan, money borrowed, money lent, debt aging, upcoming obligations, recurring transactions, project performance, account reconciliation, transfers, forecast, financial health, unusual transactions, recommendations, data quality, audit trail and backup history. Explain what each report shows, missing or risky data, whether cancelled transactions are excluded, and what the user should verify before relying on the reports."
        );
    }

    @FXML
    private void reviewDataQuality() {
        runAiRequest(
                "Data quality review",
                "Review the available PFMIS data for missing records, inconsistent classifications, duplicate-risk indicators, mixed-currency problems, stale backups, and values that should be verified."
        );
    }

    @FXML
    private void reviewBudgetPlan() {
        runAiRequest(
                "Budget review",
                "Review current-month budgets, household size, per-person spending, budget status, overspending risk, and what should change for next month."
        );
    }

    @FXML
    private void reviewGoalReadiness() {
        runAiRequest(
                "Goal readiness review",
                "Review registered goals, goal steps, savings gaps, target dates, monthly contribution realism, and explain which goals are ready to become projects. Include what information must be completed before project creation."
        );
    }

    @FXML
    private void reviewLoanPosition() {
        runAiRequest(
                "Loan and repayment review",
                "Review lending, borrowing, repayments, open balances, overdue or unclear positions, person-level loan records, and loan report risks. Recommend what should be corrected or followed up."
        );
    }

    @FXML
    private void planGoalSteps() {
        runAiRequest(
                "Goal steps planning",
                "Suggest practical goal steps with expected costs, order, missing information, and what should be recorded before turning a goal into a project."
        );
    }

    @FXML
    private void buildActionPlan() {
        runAiRequest(
                "Prioritised action plan",
                "Create a prioritised seven-day action plan covering expenses, budgets, household size, goals, projects, loans, data quality, and backup protection."
        );
    }

    @FXML
    private void askQuestion() {
        String question = questionArea.getText() == null ? "" : questionArea.getText().trim();
        if (question.isEmpty()) {
            UiAlerts.info("Enter a question for Smart Analysis.");
            return;
        }
        runAiRequest("User question", question);
    }

    @FXML
    private void clearConversation() {
        questionArea.clear();
        answerArea.clear();
        requestStatusLabel.setText("Ready for a new question.");
    }

    @FXML
    private void goBack() {
        NavigationBus.goBack();
    }

    private void runAiRequest(String actionName, String question) {
        AiSettings settings = database.getAiSettings();
        database.recordSystemLog("Smart Analysis", actionName, "INFO", "Analysis request started.");
        if (settings == null || !settings.canGenerateRecommendations()) {
            requestStatusLabel.setText("Smart Analysis is not ready. Open Smart Analysis Settings or restore the local provider.");
            answerArea.setText("Rule-based overview:\n" + intelligence.deterministicOverview()
                    + "\n\nImmediate checks:\n- " + String.join("\n- ", intelligence.smartNudges()));
            database.recordSystemLog("Smart Analysis", actionName, "WARN", "Provider is not ready; rule-based checks were shown.");
            return;
        }
        requestStatusLabel.setText("Smart Analysis is analysing verified financial data...");
        answerArea.setText("Working...");
        String preparedPrompt = intelligence.buildPrompt(settings, "Smart Analysis", actionName, question, aiCenterRoot);
        CompletableFuture.supplyAsync(() -> intelligence.askPrepared("Smart Analysis", actionName, preparedPrompt))
                .whenComplete((answer, throwable) -> Platform.runLater(() -> {
                    if (throwable == null) {
                        answerArea.setText(answer);
                        requestStatusLabel.setText("Completed by " + settings.getDisplayName() + ". No data was changed.");
                        database.recordSystemLog("Smart Analysis", actionName, "INFO", "Analysis request completed.");
                    } else {
                        answerArea.setText(rootMessage(throwable));
                        requestStatusLabel.setText("Smart Analysis request failed. Rule-based checks remain available.");
                        database.recordSystemLog("Smart Analysis", actionName, "ERROR", rootMessage(throwable));
                    }
                    refresh();
                }));
    }

    private void refreshActionQueue() {
        actionQueueBox.getChildren().clear();
        for (String nudge : intelligence.smartNudges()) {
            Label label = new Label(nudge);
            label.setWrapText(true);
            label.getStyleClass().add("ai-action-line");
            actionQueueBox.getChildren().add(label);
        }
    }

    private String systemCoverageText() {
        return "Admin, transactions, budgets, household, goals, projects.";
    }

    private String reportCoverageText() {
        return "Summary, cash flow, budgets, net worth, goals, debts, obligations, trends, quality, audit.";
    }

    private String providerStatus(AiSettings settings) {
        if (settings == null) {
            return "Smart Analysis is not configured.";
        }
        if (!settings.isEnabled()) {
            return settings.getDisplayName() + " is configured but disabled.";
        }
        return settings.getDisplayName() + " | " + settings.getProvider() + " | model " + settings.getModel()
                + (settings.isLocalProvider() ? " | local/private" : " | external provider");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
