package com.wk.pfmis.controllers;

import com.wk.pfmis.ai.PfmisIntelligenceService;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.AccountReconciliationRecord;
import com.wk.pfmis.models.AiInteractionRecord;
import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.models.BudgetProgress;
import com.wk.pfmis.models.DashboardStats;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.LoanScheduleRecord;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.RecurringTransactionPlan;
import com.wk.pfmis.models.ReportRow;
import com.wk.pfmis.models.ScheduledObligation;
import com.wk.pfmis.utils.ExportPathService;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AiCenterController {
    private static final String PERIOD_NEXT_7 = "Next 7 days";
    private static final String PERIOD_NEXT_30 = "Next 30 days";
    private static final String PERIOD_END_OF_MONTH = "End of month";
    private static final String PERIOD_CUSTOM = "Custom period";
    private static final int TRANSACTION_SCAN_LIMIT = 5_000;
    private static final DateTimeFormatter RESULT_TIME = DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm", Locale.ENGLISH);
    private static final String RESULT_CATEGORY_OVERVIEW = "Overview";
    private static final String RESULT_CATEGORY_ACTIONS = "Actions & Risks";
    private static final String RESULT_CATEGORY_DETAILS = "Other Details";
    private static final Set<String> OVERVIEW_SECTION_KEYS = Set.of("SUMMARY", "CHECKS", "CONCLUSION");
    private static final Set<String> ACTION_SECTION_KEYS = Set.of("RISKS", "RECOMMENDED ACTIONS", "LIMITATIONS", "PROVIDER FAILURE");
    private static final Map<String, String> KNOWN_RESULT_HEADINGS = Map.ofEntries(
            Map.entry("SUMMARY", "Summary"),
            Map.entry("CHECKS", "Checks"),
            Map.entry("RISKS", "Risks"),
            Map.entry("RECOMMENDED ACTIONS", "Recommended Actions"),
            Map.entry("RECOMMENDED ACTION", "Recommended Actions"),
            Map.entry("ACTIONS", "Recommended Actions"),
            Map.entry("ACTION ITEMS", "Recommended Actions"),
            Map.entry("CONCLUSION", "Conclusion"),
            Map.entry("LIMITATIONS", "Limitations"),
            Map.entry("LIMITATION", "Limitations"),
            Map.entry("PROVIDER FAILURE", "Provider failure"),
            Map.entry("EVIDENCE", "Evidence"),
            Map.entry("ASSUMPTIONS", "Assumptions"),
            Map.entry("FORECAST", "Forecast"),
            Map.entry("NEXT STEPS", "Next Steps")
    );

    @FXML private VBox aiCenterRoot;
    @FXML private Label pageTitleLabel;
    @FXML private Label pageDescriptionLabel;
    @FXML private Label providerStatusLabel;
    @FXML private Label lastUpdatedLabel;
    @FXML private Label financialOverviewLabel;
    @FXML private ProgressIndicator requestProgressIndicator;
    @FXML private VBox overviewSection;
    @FXML private VBox financialHealthSection;
    @FXML private VBox reportsTrendsSection;
    @FXML private VBox budgetForecastSection;
    @FXML private VBox goalsProjectsSection;
    @FXML private VBox loansRepaymentsSection;
    @FXML private VBox dataQualitySection;
    @FXML private VBox smartAssistantSection;
    @FXML private Label overviewAccountPositionLabel;
    @FXML private Label overviewIncomeLabel;
    @FXML private Label overviewExpensesLabel;
    @FXML private Label overviewNetCashFlowLabel;
    @FXML private Label overviewSavingsRateLabel;
    @FXML private Label overviewBudgetPressureLabel;
    @FXML private Label overviewObligationsLabel;
    @FXML private Label overviewLoansLabel;
    @FXML private Label overviewGoalsProjectsLabel;
    @FXML private Label overviewDataQualityLabel;
    @FXML private Label overviewProviderLabel;
    @FXML private Label recentActivityLabel;
    @FXML private Label financialBalanceLabel;
    @FXML private Label financialIncomeLabel;
    @FXML private Label financialExpensesLabel;
    @FXML private Label financialCashFlowLabel;
    @FXML private Label financialSavingsRateLabel;
    @FXML private Label financialSpendingMixLabel;
    @FXML private Label financialBudgetPressureLabel;
    @FXML private Label financialDebtLabel;
    @FXML private Label financialGoalCommitmentLabel;
    @FXML private Label financialProjectCommitmentLabel;
    @FXML private Label financialCompletenessLabel;
    @FXML private Label reportsStatusSummaryLabel;
    @FXML private Label reportsTrendSummaryLabel;
    @FXML private Label budgetSummaryLabel;
    @FXML private Label forecastSummaryLabel;
    @FXML private Label actionPlanSummaryLabel;
    @FXML private Label goalsSummaryLabel;
    @FXML private Label projectsSummaryLabel;
    @FXML private Label loansBorrowedLabel;
    @FXML private Label loansLentLabel;
    @FXML private Label loansScheduleLabel;
    @FXML private Label dataQualityRulesLabel;
    @FXML private TitledPane analysisResultPane;
    @FXML private ComboBox<String> forecastPeriodBox;
    @FXML private DatePicker forecastStartDatePicker;
    @FXML private DatePicker forecastEndDatePicker;
    @FXML private Label resultTitleLabel;
    @FXML private Label resultMetaLabel;
    @FXML private Label requestStatusLabel;
    @FXML private FlowPane resultCategoryPane;
    @FXML private TextArea answerArea;
    @FXML private Button openRecommendedAreaButton;
    @FXML private Button viewEvidenceButton;
    @FXML private Button exportAnalysisButton;
    @FXML private Button runAgainButton;
    @FXML private Button overviewRunFullButton;
    @FXML private Button overviewActionPlanButton;
    @FXML private Button overviewHighestRiskButton;
    @FXML private Button runFinancialHealthButton;
    @FXML private Button runReportsButton;
    @FXML private Button reviewBudgetButton;
    @FXML private Button forecastCashButton;
    @FXML private Button actionPlanButton;
    @FXML private Button reviewGoalsProjectsButton;
    @FXML private Button planGoalStepsButton;
    @FXML private Button reviewLoansButton;
    @FXML private Button reviewOverdueLoansButton;
    @FXML private Button runDataQualityButton;
    @FXML private Button askQuestionButton;
    @FXML private TextArea questionArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final PfmisIntelligenceService intelligence = new PfmisIntelligenceService();

    private String lastAnalysisName = "Ready";
    private String lastResult = "";
    private String lastEvidence = "";
    private RecommendedArea recommendedArea = RecommendedArea.NONE;
    private final Map<String, String> resultCategoryContents = new LinkedHashMap<>();
    private String activeResultCategory = RESULT_CATEGORY_OVERVIEW;
    private SmartAnalysisMode activeMode = SmartAnalysisMode.OVERVIEW;
    private String currentResultBasis = "Rules";
    private boolean requestInProgress;
    private Runnable lastRunAction;

    @FXML
    public void initialize() {
        forecastPeriodBox.getItems().setAll(PERIOD_NEXT_7, PERIOD_NEXT_30, PERIOD_END_OF_MONTH, PERIOD_CUSTOM);
        forecastPeriodBox.getSelectionModel().select(PERIOD_NEXT_30);
        forecastStartDatePicker.setValue(LocalDate.now());
        forecastEndDatePicker.setValue(LocalDate.now().plusDays(30));
        forecastPeriodBox.valueProperty().addListener((observable, oldValue, newValue) -> updateForecastDateControls());
        updateForecastDateControls();
        DataRefreshBus.addListener(this::refresh);
        activeMode = NavigationBus.consumeRequestedSmartAnalysisMode();
        refresh();
        clearResultPanel();
        applySmartAnalysisMode(activeMode);
    }

    @FXML
    private void refresh() {
        AiSettings settings = database.getAiSettings();
        providerStatusLabel.setText(providerStatus(settings));
        lastUpdatedLabel.setText("Last updated: " + LocalDateTime.now().format(RESULT_TIME));
        financialOverviewLabel.setText(financialOverviewText());
        refreshModeSummaries(settings);
    }

    @FXML
    private void refreshContext() {
        refresh();
        requestStatusLabel.setText("Data refreshed. No new analysis was run.");
    }

    private void refreshModeSummaries(AiSettings settings) {
        DashboardStats stats = database.getDashboardStats();
        DataQualitySummary quality = dataQualitySummary();
        String month = YearMonth.now().toString();
        List<BudgetProgress> budgets = activeBudgetProgress(month);
        List<LoanScheduleRecord> schedules = activeLoanSchedules();
        List<Goal> goals = activeGoals();
        List<Project> projects = activeProjects();
        LocalDate today = LocalDate.now();
        LocalDate nextThirtyDays = today.plusDays(30);
        double netCashFlow = stats.getMonthlyIncome() - stats.getMonthlyExpenses();
        long dueObligations = activeScheduledObligations().stream()
                .filter(obligation -> dateBetween(parseDate(obligation.getDueDate()), today, nextThirtyDays))
                .count();
        double dueObligationAmount = activeScheduledObligations().stream()
                .filter(obligation -> dateBetween(parseDate(obligation.getDueDate()), today, nextThirtyDays))
                .mapToDouble(ScheduledObligation::getAmount)
                .sum();
        long overdueLoans = schedules.stream()
                .filter(schedule -> isOverdue(parseDate(schedule.getDueDate())))
                .count();
        double borrowedOutstanding = schedules.stream()
                .filter(schedule -> "BORROWED".equalsIgnoreCase(blankAs(schedule.getLoanDirection(), "")))
                .mapToDouble(LoanScheduleRecord::getOutstandingAmount)
                .sum();
        double lentOutstanding = schedules.stream()
                .filter(schedule -> "LENT".equalsIgnoreCase(blankAs(schedule.getLoanDirection(), "")))
                .mapToDouble(LoanScheduleRecord::getOutstandingAmount)
                .sum();
        double goalCommitments = goals.stream().mapToDouble(Goal::getMonthlyContribution).sum();
        double projectRemaining = projects.stream()
                .mapToDouble(project -> Math.max(0, project.getRemainingBudget()))
                .sum();

        setLabel(overviewAccountPositionLabel,
                financialStatusLevel(stats, quality) + " - " + MoneyUtil.mwk(stats.getTotalBalance())
                        + " across " + stats.getActiveAccounts() + " active account(s).");
        setLabel(overviewIncomeLabel, dataPresenceLabel(stats.getMonthlyIncome(), "Recorded", "Insufficient data"));
        setLabel(overviewExpensesLabel, dataPresenceLabel(stats.getMonthlyExpenses(), "Recorded", "Insufficient data"));
        setLabel(overviewNetCashFlowLabel, netCashFlowLabel(netCashFlow));
        setLabel(overviewSavingsRateLabel, savingsRateLabel(stats));
        setLabel(overviewBudgetPressureLabel, budgetPressureText(budgets));
        setLabel(overviewObligationsLabel,
                dueObligations == 0
                        ? "No problem detected - no scheduled obligation due in the next 30 days."
                        : "Warning - " + dueObligations + " obligation(s) due, total " + MoneyUtil.mwk(dueObligationAmount) + ".");
        setLabel(overviewLoansLabel,
                overdueLoans == 0
                        ? "No problem detected - no overdue active repayment schedule."
                        : "Critical risk - " + overdueLoans + " overdue active loan repayment schedule(s).");
        setLabel(overviewGoalsProjectsLabel, goalsProjectsRiskText());
        setLabel(overviewDataQualityLabel, dataQualityOverviewText(quality));
        setLabel(overviewProviderLabel,
                providerStatus(settings) + " | Model: " + (settings == null ? "-" : settings.getModel()));
        setLabel(recentActivityLabel, recentAnalysisActivityText());

        setLabel(financialBalanceLabel, MoneyUtil.mwk(stats.getTotalBalance()) + " active-account position.");
        setLabel(financialIncomeLabel, MoneyUtil.mwk(stats.getMonthlyIncome()) + " recorded for " + month + ".");
        setLabel(financialExpensesLabel, MoneyUtil.mwk(stats.getMonthlyExpenses()) + " recorded for " + month + ".");
        setLabel(financialCashFlowLabel, netCashFlowLabel(netCashFlow));
        setLabel(financialSavingsRateLabel, savingsRateLabel(stats));
        setLabel(financialSpendingMixLabel, spendingMixText());
        setLabel(financialBudgetPressureLabel, budgetPressureText(budgets));
        setLabel(financialDebtLabel, "Borrowed outstanding: " + MoneyUtil.mwk(borrowedOutstanding)
                + ". Due in 30 days: " + MoneyUtil.mwk(loanRepaymentsBetween(today, nextThirtyDays)) + ".");
        setLabel(financialGoalCommitmentLabel, goals.size() + " active goal(s), monthly commitment "
                + MoneyUtil.mwk(goalCommitments) + ".");
        setLabel(financialProjectCommitmentLabel, projects.size() + " active project(s), remaining budget "
                + MoneyUtil.mwk(projectRemaining) + ".");
        setLabel(financialCompletenessLabel, dataStatus() + " - " + checksStatusText());

        setLabel(reportsStatusSummaryLabel, reportStatusText());
        setLabel(reportsTrendSummaryLabel, reportTrendSummaryText(month));
        setLabel(budgetSummaryLabel, budgetHouseholdEvidence());
        setLabel(forecastSummaryLabel, forecastSummaryText());
        setLabel(actionPlanSummaryLabel, "Create no more than seven prioritized, evidence-backed actions. Smart Analysis does not create or post transactions.");
        setLabel(goalsSummaryLabel, goalsSummaryText(goals));
        setLabel(projectsSummaryLabel, projectsSummaryText(projects));
        setLabel(loansBorrowedLabel, "Outstanding borrowed principal: " + MoneyUtil.mwk(borrowedOutstanding)
                + ". Due in 30 days: " + MoneyUtil.mwk(loanRepaymentsBetween(today, nextThirtyDays)) + ".");
        setLabel(loansLentLabel, "Outstanding money lent: " + MoneyUtil.mwk(lentOutstanding)
                + ". Expected collections in 30 days: " + MoneyUtil.mwk(loanCollectionsBetween(today, nextThirtyDays))
                + ". This is not available cash until collected.");
        setLabel(loansScheduleLabel, loanScheduleQualityText(schedules));
        setLabel(dataQualityRulesLabel, dataQualityChecksText(quality));
    }

    private void setLabel(Label label, String text) {
        if (label != null) {
            label.setText(text);
        }
    }

    @FXML
    private void reviewHighestRisk() {
        lastRunAction = this::reviewHighestRisk;
        DashboardStats stats = database.getDashboardStats();
        DataQualitySummary quality = dataQualitySummary();
        List<BudgetProgress> budgets = activeBudgetProgress(YearMonth.now().toString());
        long overdueLoans = activeLoanSchedules().stream()
                .filter(schedule -> isOverdue(parseDate(schedule.getDueDate())))
                .count();
        if (stats.getTotalBalance() < 0) {
            showResult("Highest Risk Review", financialHealthFallback(), currentFiguresEvidence(), RecommendedArea.ACCOUNTS, dataStatus());
        } else if (overdueLoans > 0) {
            showResult("Highest Risk Review", overdueLoansFallback(), loansEvidence(), RecommendedArea.LOANS, dataStatus());
        } else if (quality.totalIssues() > 0) {
            showResult("Highest Risk Review", dataQualityFallback(), dataQualityEvidence(), RecommendedArea.DATA_QUALITY, dataStatus());
        } else if (budgets.stream().anyMatch(progress -> progress.getPercentUsed() >= 80)) {
            showResult("Highest Risk Review", budgetHouseholdFallback(), budgetHouseholdEvidence(), RecommendedArea.BUDGETS, dataStatus());
        } else if (currentMonthPostedTransactions().isEmpty()) {
            showResult("Highest Risk Review", financialHealthFallback(), currentFiguresEvidence(), RecommendedArea.TRANSACTIONS, dataStatus());
        } else {
            showResult(
                    "Highest Risk Review",
                    """
                            Summary
                            No critical risk was detected by the local overview checks.

                            Checks
                            Account balance, current-month transactions, budget pressure, overdue loans and data-quality warning counts were reviewed.

                            Risks
                            No problem detected from the available records. Missing future records can still hide upcoming pressure.

                            Recommended Actions
                            Refresh data after posting new transactions, then run Financial Health Analysis before making major decisions.

                            Conclusion
                            Smart Overview did not find one dominant risk.

                            Limitations
                            This review is rule-based and does not replace the full financial-health analysis.
                            """,
                    currentFiguresEvidence(),
                    RecommendedArea.REPORTS,
                    dataStatus()
            );
        }
        requestStatusLabel.setText("Highest visible risk reviewed from current records. No data was changed.");
    }

    @FXML
    private void runAgain() {
        if (requestInProgress) {
            requestStatusLabel.setText("Analysing... wait for the current request to finish.");
            return;
        }
        if (lastRunAction == null) {
            UiAlerts.info("Run an analysis before using Run Again.");
            return;
        }
        lastRunAction.run();
    }

    @FXML
    private void useSuggestedQuestion(ActionEvent event) {
        if (event.getSource() instanceof Button button) {
            questionArea.setText(button.getText());
            requestStatusLabel.setText("Suggested question selected. Click Ask to run it.");
        }
    }

    @FXML
    private void openReports() {
        openNavigationTarget(SmartNavigationTarget.SMART_ANALYSIS_REPORTS, "Open Reports from the sidebar, then review Smart Analysis reports.");
    }

    @FXML
    private void openBudgets() {
        openNavigationTarget(SmartNavigationTarget.BUDGETS, "Open Budgets > Manage Budgets from the sidebar.");
    }

    @FXML
    private void openTransactionLedger() {
        openNavigationTarget(SmartNavigationTarget.TRANSACTION_LEDGER, "Open Transaction Ledger from the sidebar.");
    }

    @FXML
    private void openGoals() {
        openNavigationTarget(SmartNavigationTarget.GOALS, "Open Goals > Goal Records from the sidebar.");
    }

    @FXML
    private void openProjects() {
        openNavigationTarget(SmartNavigationTarget.PROJECTS, "Open Projects > Project Records from the sidebar.");
    }

    @FXML
    private void openLoanLedger() {
        openNavigationTarget(SmartNavigationTarget.LOAN_LEDGER, "Open Loans > Loan Ledger from the sidebar.");
    }

    @FXML
    private void openRepaymentSchedule() {
        openNavigationTarget(SmartNavigationTarget.REPAYMENT_SCHEDULE, "Open Loans > Repayment Schedule from the sidebar.");
    }

    @FXML
    private void reviewOverdueLoans() {
        lastRunAction = this::reviewOverdueLoans;
        runAiRequest(
                "Overdue Loan Review",
                """
                        Review overdue loan repayment schedules only. Separate borrowed repayment risk from lent collection risk.
                        Do not present loan receivables as available cash.
                        """,
                overdueLoansFallback(),
                loansEvidence(),
                RecommendedArea.LOANS,
                dataStatus()
        );
    }

    @FXML
    private void openDataQualityRecords() {
        openNavigationTarget(SmartNavigationTarget.DATA_QUALITY_RECORDS, "Open Data And Records > Data Quality and Reconciliation from the sidebar.");
    }

    @FXML
    private void reviewUnreconciledAccounts() {
        lastRunAction = this::reviewUnreconciledAccounts;
        runAiRequest(
                "Unreconciled Accounts Review",
                """
                        Review active accounts without a recent reconciliation record. Explain balance risk and direct the user to
                        Data Quality and Account Reconciliation. Do not change account records automatically.
                        """,
                unreconciledAccountsFallback(),
                dataQualityEvidence(),
                RecommendedArea.DATA_QUALITY,
                dataStatus()
        );
    }

    private void openNavigationTarget(SmartNavigationTarget target, String fallbackMessage) {
        if (!NavigationBus.showSmartNavigationTarget(target)) {
            UiAlerts.info(fallbackMessage);
        }
    }

    @FXML
    private void viewProviderDetails() {
        AiSettings settings = database.getAiSettings();
        String body = """
                Summary
                %s

                Checks
                Provider: %s
                Model: %s
                Endpoint: %s
                Recommendation status: %s

                Risks
                %s

                Recommended Actions
                Keep provider identifiers in this details view unless technical troubleshooting is required.

                Conclusion
                Smart Analysis can still show rule-based evidence when the provider is unavailable.

                Limitations
                Provider connectivity is not tested by refreshing data; it is tested when an analysis request is sent.
                """.formatted(
                settings == null ? "Smart Analysis is not configured." : settings.getDisplayName(),
                settings == null ? "-" : settings.getProvider(),
                settings == null ? "-" : settings.getModel(),
                settings == null ? "-" : settings.getEndpoint(),
                settings != null && settings.canGenerateRecommendations() ? "Ready" : "Not ready",
                settings != null && settings.isLocalProvider()
                        ? "Local mode keeps prepared analysis on this computer."
                        : "External mode sends prepared summaries only; entered form values are withheld."
        );
        lastRunAction = null;
        showResult("Provider Details", body, currentFiguresEvidence(), RecommendedArea.NONE, dataStatus());
        requestStatusLabel.setText("Provider details shown. No financial data was changed.");
    }

    @FXML
    private void runFinancialHealthAnalysis() {
        lastRunAction = this::runFinancialHealthAnalysis;
        runAiRequest(
                "Financial Health",
                """
                        Analyse the user's financial health. Use the fixed headings Summary, Checks, Risks, Recommended Actions, Conclusion and Limitations.
                        Include account balances, income, expenses, net cash flow, savings rate, loans, upcoming obligations, budget pressure,
                        goal progress, project spending and data-quality limitations. If income and expenses are absent for the period,
                        state clearly that the data is incomplete and do not call the position healthy merely because the balance is positive.
                        """,
                financialHealthFallback(),
                currentFiguresEvidence(),
                recommendedAreaForFinancialHealth(),
                dataStatus()
        );
    }

    @FXML
    private void runReportsAnalysis() {
        lastRunAction = this::runReportsAnalysis;
        runAiRequest(
                "Reports Analysis",
                """
                        Analyse generated PFMIS reports without duplicating the whole financial health analysis.
                        Explain what changed, abnormal figures, report gaps, trends that are improving or worsening,
                        and the action recommended for each report family.
                        """,
                reportsFallback(),
                reportsEvidence(),
                RecommendedArea.REPORTS,
                dataStatus()
        );
    }

    @FXML
    private void reviewBudgetPlan() {
        lastRunAction = this::reviewBudgetPlan;
        runAiRequest(
                "Budget and Household Review",
                """
                        Review active budgets, budget utilisation, essential versus discretionary spending, household size,
                        per-person expenditure, categories likely to exceed budget, unbudgeted expenses and remaining days in the period.
                        If no active household budget exists, say that instead of giving generic advice.
                        """,
                budgetHouseholdFallback(),
                budgetHouseholdEvidence(),
                RecommendedArea.BUDGETS,
                dataStatus()
        );
    }

    @FXML
    private void forecastCashPosition() {
        if (!validateForecastPeriod()) {
            return;
        }
        lastRunAction = this::forecastCashPosition;
        ForecastResult forecast = buildForecast();
        runAiRequest(
                "Cash Position Forecast",
                """
                        Forecast the user's cash position for %s to %s.
                        Use current balances, expected income, recurring income, planned expenses, recurring expenses,
                        scheduled obligations, loan repayments, expected loan collections, goal contributions,
                        project commitments and scheduled transfers. Distinguish facts from estimates and show limitations.
                        """.formatted(forecast.start(), forecast.end()),
                forecastFallback(forecast),
                forecastEvidence(forecast),
                RecommendedArea.BUDGETS,
                forecast.dataStatus()
        );
    }

    @FXML
    private void buildActionPlan() {
        lastRunAction = this::buildActionPlan;
        runAiRequest(
                "7-Day Action Plan",
                """
                        Create a practical seven-day action plan. Use no more than seven actions, and only include actions supported by
                        upcoming obligations, expected income, overdue loans, budget risks, goal contributions, data-quality issues or project deadlines.
                        """,
                actionPlanFallback(),
                currentFiguresEvidence() + "\n\n" + dataQualityEvidence(),
                RecommendedArea.DATA_QUALITY,
                dataStatus()
        );
    }

    @FXML
    private void reviewGoalReadiness() {
        lastRunAction = this::reviewGoalReadiness;
        runAiRequest(
                "Goals and Projects Review",
                """
                        Review active goals, contribution progress, required future contributions, completion forecast, overdue steps,
                        active projects, project budget utilisation, progress, funding gaps and whether a goal should remain a goal or become a project.
                        Recommend conversion only when the goal needs activities, a detailed budget, dates, procurement or implementation monitoring.
                        """,
                goalsProjectsFallback(),
                goalsProjectsEvidence(),
                RecommendedArea.GOALS_PROJECTS,
                dataStatus()
        );
    }

    @FXML
    private void reviewLoanPosition() {
        lastRunAction = this::reviewLoanPosition;
        runAiRequest(
                "Loans and Repayments Review",
                """
                        Review money lent, money borrowed, outstanding principal, interest, upcoming repayments, overdue repayments,
                        borrower concentration, repayment affordability and settlement forecasts.
                        """,
                loansFallback(),
                loansEvidence(),
                RecommendedArea.LOANS,
                dataStatus()
        );
    }

    @FXML
    private void reviewDataQuality() {
        lastRunAction = this::reviewDataQuality;
        runAiRequest(
                "Data Quality Review",
                """
                        Review missing categories, payment methods, duplicates, invalid dates, future-dated records, zero values,
                        unreconciled accounts, unsupported currency conversion, incomplete loan schedules, unallocated goal savings
                        and project expenses without activities.
                        """,
                dataQualityFallback(),
                dataQualityEvidence(),
                RecommendedArea.DATA_QUALITY,
                dataStatus()
        );
    }

    @FXML
    private void planGoalSteps() {
        lastRunAction = this::planGoalSteps;
        runAiRequest(
                "Goal Step Plan",
                """
                        Build practical next steps for active goals only. Use goal target amounts, current amounts, funding gaps,
                        contribution frequency, required future contribution, target dates and overdue steps. Do not convert a goal
                        into a project unless it clearly requires activities, milestones, dates, procurement or implementation monitoring.
                        """,
                goalStepsFallback(),
                goalsProjectsEvidence(),
                RecommendedArea.GOALS,
                dataStatus()
        );
    }

    @FXML
    private void askQuestion() {
        String question = questionArea.getText() == null ? "" : questionArea.getText().trim();
        if (question.isEmpty()) {
            UiAlerts.info("Enter a question for Smart Analysis.");
            return;
        }
        if (question.length() > 600) {
            UiAlerts.info("Keep Smart Assistant questions under 600 characters.");
            return;
        }
        if (containsSensitiveQuestionContent(question)) {
            lastRunAction = null;
            showResult("Question Review", sensitiveQuestionResponse(), currentFiguresEvidence(), RecommendedArea.NONE, dataStatus());
            requestStatusLabel.setText("Question was not sent because it appears to contain sensitive secret information.");
            return;
        }
        if (!isFinancialQuestion(question)) {
            lastRunAction = null;
            showResult("Question Review", unrelatedQuestionResponse(question), currentFiguresEvidence(), RecommendedArea.NONE, dataStatus());
            requestStatusLabel.setText("Question reviewed locally. Smart Analysis only handles personal financial management questions.");
            return;
        }
        lastRunAction = this::askQuestion;
        runAiRequest(
                "User Question",
                question,
                customQuestionFallback(question),
                currentFiguresEvidence(),
                RecommendedArea.NONE,
                dataStatus()
        );
    }

    @FXML
    private void clearConversation() {
        questionArea.clear();
        clearResultPanel();
        requestStatusLabel.setText("Ready for a new question.");
    }

    @FXML
    private void openRecommendedArea() {
        if (recommendedArea == RecommendedArea.NONE) {
            UiAlerts.info(recommendedArea.openHint());
            return;
        }
        if (NavigationBus.showSmartNavigationTarget(recommendedArea.target())) {
            requestStatusLabel.setText("Opened " + recommendedArea.buttonText() + ".");
            return;
        }
        UiAlerts.info(recommendedArea.openHint());
    }

    @FXML
    private void viewEvidence() {
        if (lastEvidence.isBlank()) {
            UiAlerts.info("No evidence is available for this result yet.");
            return;
        }
        String evidenceResult = """
                Summary
                The figures below show the records and calculations behind the last Smart Analysis result.

                Checks
                %s

                Risks
                Evidence is limited by missing, cancelled, invalid or out-of-period records.

                Recommended Actions
                Use this evidence to verify the result before acting on recommendations.

                Conclusion
                The analysis is explainable from the listed records and calculations.

                Limitations
                Evidence is based on records currently available in PFMIS.
        """.formatted(lastEvidence);
        showResult("Evidence for " + lastAnalysisName, evidenceResult, lastEvidence, recommendedArea, dataStatus());
        requestStatusLabel.setText("Evidence shown for the last result.");
    }

    @FXML
    private void exportAnalysis() {
        if (lastResult.isBlank()) {
            UiAlerts.info("Run an analysis before exporting.");
            return;
        }
        try {
            Path exportFile = ExportPathService.writeTextExport(
                    ExportPathService.defaultFileName("Smart Analysis " + slug(lastAnalysisName), "txt"),
                    exportBody()
            );
            database.recordSystemLog("Smart Analysis", "Export Analysis", "INFO", "Analysis exported to " + exportFile);
            requestStatusLabel.setText(ExportPathService.successMessage(exportFile));
        } catch (IOException exception) {
            requestStatusLabel.setText(ExportPathService.failureMessage(exception));
            database.recordSystemLog("Smart Analysis", "Export Analysis Failed", "ERROR", rootMessage(exception));
        }
    }

    private void runAiRequest(
            String actionName,
            String question,
            String fallback,
            String evidence,
            RecommendedArea area,
            String resultDataStatus
    ) {
        if (requestInProgress) {
            requestStatusLabel.setText("Analysing... wait for the current request to finish.");
            return;
        }
        AiSettings settings = database.getAiSettings();
        String provider = providerName(settings);
        database.recordSystemLog("Smart Analysis", actionName, "INFO", "Analysis request started.");
        database.recordAiInteraction("Smart Analysis", actionName, provider, "STARTED");
        currentResultBasis = "Rules";
        showResult(actionName, fallback, evidence, area, resultDataStatus);
        if (settings == null || !settings.canGenerateRecommendations()) {
            requestStatusLabel.setText("Rule-based result shown. Smart Analysis provider is not ready.");
            database.recordSystemLog("Smart Analysis", actionName, "WARN", "Provider is not ready; rule-based result was shown.");
            database.recordAiInteraction("Smart Analysis", actionName, provider, "RULE_BASED_FALLBACK");
            return;
        }

        setRequestInProgress(true);
        requestStatusLabel.setText("Analysing...");
        answerArea.setText("Working...\n\nA rule-based result has already been prepared for this analysis.");
        String preparedPrompt = intelligence.buildPrompt(
                settings,
                "Smart Analysis",
                actionName,
                question + "\n\nReturn with exact headings: Summary, Checks, Risks, Recommended Actions, Conclusion, Limitations. "
                        + "If records are absent, state that data is incomplete instead of giving generic advice.",
                aiCenterRoot
        );
        CompletableFuture.supplyAsync(() -> intelligence.askPrepared("Smart Analysis", actionName, preparedPrompt))
                .whenComplete((answer, throwable) -> Platform.runLater(() -> {
                    try {
                        if (throwable == null) {
                            currentResultBasis = "Rules + AI";
                            showResult(actionName, structuredAnswer(answer, fallback), evidence, area, resultDataStatus);
                            requestStatusLabel.setText("Completed by " + settings.getDisplayName() + ". No data was changed.");
                            database.recordSystemLog("Smart Analysis", actionName, "INFO", "Analysis request completed.");
                            database.recordAiInteraction("Smart Analysis", actionName, provider, "SUCCESS");
                        } else {
                            currentResultBasis = "Rules fallback";
                            showResult(actionName, fallbackWithProviderFailure(fallback), evidence, area, resultDataStatus);
                            requestStatusLabel.setText("Provider request failed. Rule-based result remains available.");
                            database.recordSystemLog("Smart Analysis", actionName, "ERROR", safeLogMessage(throwable));
                            database.recordAiInteraction("Smart Analysis", actionName, provider, "FAILED_FALLBACK_USED");
                        }
                    } finally {
                        setRequestInProgress(false);
                        refresh();
                    }
                }));
    }

    private void clearResultPanel() {
        lastAnalysisName = "No result yet";
        lastResult = "";
        lastEvidence = "";
        recommendedArea = RecommendedArea.NONE;
        lastRunAction = null;
        currentResultBasis = "Rules";
        activeResultCategory = RESULT_CATEGORY_OVERVIEW;
        resultCategoryContents.clear();
        resultTitleLabel.setText("Analysis: No result yet");
        resultMetaLabel.setText("Generated: - | Period: - | Provider: - | Data status: - | Method: -");
        answerArea.clear();
        resultCategoryPane.getChildren().clear();
        updateResultButtonState();
    }

    private void showResult(String analysisName, String body, String evidence, RecommendedArea area, String resultDataStatus) {
        lastAnalysisName = blankAs(analysisName, "Smart Analysis");
        lastResult = blankAs(body, "");
        lastEvidence = blankAs(evidence, "");
        recommendedArea = area == null ? RecommendedArea.NONE : area;
        if (!requestInProgress) {
            currentResultBasis = "Rules";
        }
        AiSettings settings = database.getAiSettings();
        resultTitleLabel.setText("Analysis: " + lastAnalysisName);
        resultMetaLabel.setText("Generated: " + LocalDateTime.now().format(RESULT_TIME)
                + " | Period: " + YearMonth.now()
                + " | Provider: " + providerName(settings)
                + " | Data status: " + resultDataStatus
                + " | Method: " + currentResultBasis);
        activeResultCategory = RESULT_CATEGORY_OVERVIEW;
        rebuildResultCategories();
        openRecommendedAreaButton.setText(recommendedArea.buttonText());
        updateResultButtonState();
    }

    private void updateResultButtonState() {
        boolean noResult = lastResult == null || lastResult.isBlank();
        if (viewEvidenceButton != null) {
            viewEvidenceButton.setDisable(noResult || lastEvidence.isBlank() || requestInProgress);
        }
        if (exportAnalysisButton != null) {
            exportAnalysisButton.setDisable(noResult || requestInProgress);
        }
        if (runAgainButton != null) {
            runAgainButton.setDisable(noResult || lastRunAction == null || requestInProgress);
        }
        if (openRecommendedAreaButton != null) {
            openRecommendedAreaButton.setText(recommendedArea.buttonText());
            openRecommendedAreaButton.setDisable(noResult || recommendedArea == RecommendedArea.NONE || requestInProgress);
        }
    }

    private void setRequestInProgress(boolean inProgress) {
        requestInProgress = inProgress;
        setContentVisible(requestProgressIndicator, inProgress);
        Button[] buttons = {
                overviewRunFullButton,
                overviewActionPlanButton,
                overviewHighestRiskButton,
                runFinancialHealthButton,
                runReportsButton,
                reviewBudgetButton,
                forecastCashButton,
                actionPlanButton,
                reviewGoalsProjectsButton,
                planGoalStepsButton,
                reviewLoansButton,
                reviewOverdueLoansButton,
                runDataQualityButton,
                askQuestionButton
        };
        for (Button button : buttons) {
            if (button != null) {
                button.setDisable(inProgress);
            }
        }
        updateResultButtonState();
    }

    private void applySmartAnalysisMode(SmartAnalysisMode mode) {
        activeMode = mode == null ? SmartAnalysisMode.OVERVIEW : mode;
        pageTitleLabel.setText(modeTitle(activeMode));
        pageDescriptionLabel.setText(modeDescription(activeMode));

        setContentVisible(overviewSection, activeMode == SmartAnalysisMode.OVERVIEW);
        setContentVisible(financialHealthSection, activeMode == SmartAnalysisMode.FINANCIAL_HEALTH);
        setContentVisible(reportsTrendsSection, activeMode == SmartAnalysisMode.REPORTS_TRENDS);
        setContentVisible(budgetForecastSection, activeMode == SmartAnalysisMode.BUDGET_FORECAST);
        setContentVisible(goalsProjectsSection, activeMode == SmartAnalysisMode.GOALS_PROJECTS);
        setContentVisible(loansRepaymentsSection, activeMode == SmartAnalysisMode.LOANS_REPAYMENTS);
        setContentVisible(dataQualitySection, activeMode == SmartAnalysisMode.DATA_QUALITY);
        setContentVisible(smartAssistantSection, activeMode == SmartAnalysisMode.SMART_ASSISTANT);

        if (analysisResultPane != null) {
            analysisResultPane.setExpanded(true);
        }
    }

    private String modeTitle(SmartAnalysisMode mode) {
        return switch (mode) {
            case FINANCIAL_HEALTH -> "Financial Health Analysis";
            case REPORTS_TRENDS -> "Reports and Trends Analysis";
            case BUDGET_FORECAST -> "Budget and Cash Forecast";
            case GOALS_PROJECTS -> "Goals and Projects Analysis";
            case LOANS_REPAYMENTS -> "Loans and Repayments Analysis";
            case DATA_QUALITY -> "Data Quality Analysis";
            case SMART_ASSISTANT -> "Ask Smart Assistant";
            default -> "Smart Analysis Overview";
        };
    }

    private String modeDescription(SmartAnalysisMode mode) {
        return switch (mode) {
            case FINANCIAL_HEALTH ->
                    "Analyse balances, income, expenses, cash flow, savings, budgets, obligations and overall financial sustainability.";
            case REPORTS_TRENDS ->
                    "Interpret financial reports, identify changes, abnormal figures, worsening trends and reporting gaps.";
            case BUDGET_FORECAST ->
                    "Review budget performance, forecast future cash position and prepare a practical seven-day financial action plan.";
            case GOALS_PROJECTS ->
                    "Assess goal readiness, contribution requirements, project progress, deadlines, funding gaps and implementation risks.";
            case LOANS_REPAYMENTS ->
                    "Review money borrowed, money lent, outstanding balances, due dates, affordability, concentrations and overdue repayments.";
            case DATA_QUALITY ->
                    "Identify incomplete, duplicated, invalid, unreconciled or unsupported financial records that may affect decisions.";
            case SMART_ASSISTANT ->
                    "Ask a specific question about the financial records currently stored in PFMIS.";
            default ->
                    "Review your current financial position, available data, detected risks and the most important actions requiring attention.";
        };
    }

    private void setContentVisible(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void rebuildResultCategories() {
        resultCategoryContents.clear();
        Map<String, String> sections = splitResultSections(lastResult);
        Set<String> assignedKeys = new LinkedHashSet<>();
        String overview = joinSections(sections, OVERVIEW_SECTION_KEYS, assignedKeys);
        String actions = joinSections(sections, ACTION_SECTION_KEYS, assignedKeys);
        String otherDetails = joinUnassignedSections(sections, assignedKeys);

        resultCategoryContents.put(
                RESULT_CATEGORY_OVERVIEW,
                overview.isBlank() ? blankAs(lastResult, "No analysis result is available.") : overview
        );
        resultCategoryContents.put(
                RESULT_CATEGORY_ACTIONS,
                actions.isBlank() ? "No risks, recommendations or limitations were separated from this result." : actions
        );
        if (!otherDetails.isBlank()) {
            resultCategoryContents.put(RESULT_CATEGORY_DETAILS, otherDetails);
        }
        renderResultCategory();
    }

    private void renderResultCategory() {
        if (!resultCategoryContents.containsKey(activeResultCategory)) {
            activeResultCategory = RESULT_CATEGORY_OVERVIEW;
        }
        answerArea.setText(resultCategoryContents.getOrDefault(activeResultCategory, lastResult));
        resultCategoryPane.getChildren().clear();
        resultCategoryContents.keySet().forEach(category -> {
            Button button = new Button(category);
            button.getStyleClass().setAll(category.equals(activeResultCategory) ? "primary-button" : "secondary-button");
            button.setOnAction(event -> {
                activeResultCategory = category;
                renderResultCategory();
            });
            resultCategoryPane.getChildren().add(button);
        });
    }

    private Map<String, String> splitResultSections(String result) {
        Map<String, StringBuilder> builders = new LinkedHashMap<>();
        String currentHeading = null;
        for (String rawLine : blankAs(result, "").split("\\R", -1)) {
            String heading = recognizedResultHeading(rawLine);
            if (heading != null) {
                currentHeading = heading;
                builders.computeIfAbsent(currentHeading, ignored -> new StringBuilder());
                continue;
            }
            if (currentHeading == null) {
                if (rawLine.isBlank()) {
                    continue;
                }
                currentHeading = "Summary";
                builders.computeIfAbsent(currentHeading, ignored -> new StringBuilder());
            }
            builders.get(currentHeading).append(rawLine).append('\n');
        }

        Map<String, String> sections = new LinkedHashMap<>();
        builders.forEach((heading, builder) -> sections.put(heading, builder.toString().strip()));
        return sections;
    }

    private String recognizedResultHeading(String line) {
        String clean = blankAs(line, "");
        if (clean.isBlank()) {
            return null;
        }
        clean = clean.replaceFirst("^#{1,6}\\s*", "");
        clean = clean.replaceFirst("^\\d+[.)]\\s*", "");
        clean = clean.replaceAll("^\\*\\*", "").replaceAll("\\*\\*$", "");
        clean = clean.replaceAll("[:：]+$", "").trim();
        return KNOWN_RESULT_HEADINGS.get(sectionKey(clean));
    }

    private String joinSections(Map<String, String> sections, Set<String> requestedKeys, Set<String> assignedKeys) {
        StringBuilder builder = new StringBuilder();
        sections.forEach((heading, content) -> {
            String key = sectionKey(heading);
            if (!requestedKeys.contains(key)) {
                return;
            }
            assignedKeys.add(key);
            appendResultSection(builder, heading, content);
        });
        return builder.toString().strip();
    }

    private String joinUnassignedSections(Map<String, String> sections, Set<String> assignedKeys) {
        StringBuilder builder = new StringBuilder();
        sections.forEach((heading, content) -> {
            if (!assignedKeys.contains(sectionKey(heading))) {
                appendResultSection(builder, heading, content);
            }
        });
        return builder.toString().strip();
    }

    private void appendResultSection(StringBuilder builder, String heading, String content) {
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(heading);
        if (!blankAs(content, "").isBlank()) {
            builder.append('\n').append(content.strip());
        }
    }

    private String sectionKey(String heading) {
        return blankAs(heading, "")
                .toUpperCase(Locale.ENGLISH)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String financialStatusLevel(DashboardStats stats, DataQualitySummary quality) {
        if (stats.getTotalBalance() < 0) {
            return "Critical risk";
        }
        if (currentMonthPostedTransactions().isEmpty()
                || (stats.getMonthlyIncome() <= 0 && stats.getMonthlyExpenses() <= 0)) {
            return "Insufficient data";
        }
        if (quality.totalIssues() > 0 || stats.getMonthlyExpenses() > stats.getMonthlyIncome()) {
            return "Warning";
        }
        return "No problem detected";
    }

    private String dataPresenceLabel(double amount, String presentStatus, String missingStatus) {
        if (amount <= 0) {
            return missingStatus + " - " + MoneyUtil.mwk(amount) + " recorded for " + YearMonth.now() + ".";
        }
        return presentStatus + " - " + MoneyUtil.mwk(amount) + " recorded for " + YearMonth.now() + ".";
    }

    private String netCashFlowLabel(double netCashFlow) {
        if (netCashFlow < 0) {
            return "Warning - current-month outflow exceeds inflow by " + MoneyUtil.mwk(Math.abs(netCashFlow)) + ".";
        }
        if (netCashFlow == 0) {
            return "Insufficient data - net flow is zero from current records.";
        }
        return "No problem detected - current-month net inflow is " + MoneyUtil.mwk(netCashFlow) + ".";
    }

    private String savingsRateLabel(DashboardStats stats) {
        if (stats.getMonthlyIncome() <= 0) {
            return "Insufficient data - no current-month income is recorded.";
        }
        double rate = savingsRate(stats);
        String status = rate < 0 ? "Warning" : "No problem detected";
        return status + " - savings rate is " + String.format(Locale.ENGLISH, "%.1f%%", rate) + ".";
    }

    private String budgetPressureText(List<BudgetProgress> budgets) {
        if (budgets.isEmpty()) {
            return "Insufficient data - no active budget is available for " + YearMonth.now() + ".";
        }
        long critical = budgets.stream().filter(progress -> progress.getPercentUsed() >= 100).count();
        long warning = budgets.stream().filter(progress -> progress.getPercentUsed() >= 80 && progress.getPercentUsed() < 100).count();
        if (critical > 0) {
            return "Critical risk - " + critical + " budget allocation(s) exceeded.";
        }
        if (warning > 0) {
            return "Warning - " + warning + " budget allocation(s) are above 80% usage.";
        }
        return "No problem detected - active budgets are below the warning threshold.";
    }

    private String dataQualityOverviewText(DataQualitySummary quality) {
        if (quality.totalIssues() == 0) {
            return "No problem detected - local checks found no urgent issue.";
        }
        String status = quality.criticalIssueCount() > 0 ? "Critical risk" : "Warning";
        return status + " - " + quality.totalIssues() + " issue(s) need review.";
    }

    private String recentAnalysisActivityText() {
        List<AiInteractionRecord> records = database.listAiInteractionHistory(5);
        if (records.isEmpty()) {
            return "No recent analysis activity.";
        }
        return records.stream()
                .map(record -> record.getCreatedAt() + " | " + record.getActionName() + " | " + record.getStatus())
                .collect(Collectors.joining("\n"));
    }

    private String spendingMixText() {
        List<FinanceTransaction> expenses = currentMonthPostedTransactions().stream()
                .filter(this::isExpense)
                .toList();
        if (expenses.isEmpty()) {
            return "Insufficient data - no posted current-month expense transactions.";
        }
        double essential = expenses.stream()
                .filter(transaction -> looksEssentialCategory(transaction.getCategoryName()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();
        double total = expenses.stream().mapToDouble(FinanceTransaction::getAmount).sum();
        double discretionary = Math.max(0, total - essential);
        return "Estimated - essential-like expenses " + MoneyUtil.mwk(essential)
                + ", discretionary or uncategorized " + MoneyUtil.mwk(discretionary)
                + ". Category names are used as a proxy.";
    }

    private boolean looksEssentialCategory(String category) {
        String text = blankAs(category, "").toLowerCase(Locale.ENGLISH);
        return text.contains("rent")
                || text.contains("food")
                || text.contains("grocery")
                || text.contains("transport")
                || text.contains("utility")
                || text.contains("water")
                || text.contains("electric")
                || text.contains("medical")
                || text.contains("school")
                || text.contains("loan");
    }

    private String reportTrendSummaryText(String month) {
        return """
                Income trend rows: %d
                Expense category rows: %d
                Account-balance rows: %d
                Budget-versus-actual rows: %d
                Loan and obligation rows: %d
                Goal/project trend rows: %d
                """.formatted(
                database.incomeSourceByAccountReport(month).size(),
                database.categorySpendingReport(month).size(),
                database.accountBalanceReport().size(),
                database.listBudgetProgress(month).size(),
                database.lendingByPersonReport(month).size(),
                database.projectSpendingReport(month).size() + database.listGoals().size()
        );
    }

    private String forecastSummaryText() {
        ForecastResult forecast = buildForecast();
        return """
                Recorded opening balance: %s
                Scheduled income: %s
                Estimated expenses and commitments: %s
                Scheduled loan collections: %s
                Scheduled borrowed-loan repayments: %s
                Forecast status: %s
                """.formatted(
                MoneyUtil.mwk(forecast.openingBalance()),
                MoneyUtil.mwk(forecast.scheduledExpectedIncome() + forecast.recurringIncome()),
                MoneyUtil.mwk(forecast.upcomingExpenses() + forecast.goalContributions() + forecast.projectCommitments()),
                MoneyUtil.mwk(forecast.loanCollections()),
                MoneyUtil.mwk(forecast.loanRepayments()),
                forecast.dataStatus()
        );
    }

    private String goalsSummaryText(List<Goal> goals) {
        if (goals.isEmpty()) {
            return "Insufficient data - no active goals are registered.";
        }
        double target = goals.stream().mapToDouble(Goal::getTargetAmount).sum();
        double current = goals.stream().mapToDouble(Goal::getCurrentAmount).sum();
        double gap = goals.stream().mapToDouble(Goal::getRemainingAmount).sum();
        long overdue = goals.stream()
                .filter(goal -> parseDate(goal.getTargetDate()) != null)
                .filter(goal -> parseDate(goal.getTargetDate()).isBefore(LocalDate.now()))
                .filter(goal -> goal.getRemainingAmount() > 0)
                .count();
        return "Goals: " + goals.size()
                + ". Target " + MoneyUtil.mwk(target)
                + ", current " + MoneyUtil.mwk(current)
                + ", gap " + MoneyUtil.mwk(gap)
                + ". Overdue or underfunded: " + overdue + ".";
    }

    private String projectsSummaryText(List<Project> projects) {
        if (projects.isEmpty()) {
            return "Insufficient data - no active projects are registered.";
        }
        double budget = projects.stream().mapToDouble(Project::getPlannedBudget).sum();
        double spent = projects.stream().mapToDouble(Project::getAmountSpent).sum();
        long overBudget = projects.stream()
                .filter(project -> project.getPlannedBudget() > 0 && project.getAmountSpent() > project.getPlannedBudget())
                .count();
        long overdue = projects.stream()
                .filter(project -> parseDate(project.getEndDate()) != null)
                .filter(project -> parseDate(project.getEndDate()).isBefore(LocalDate.now()))
                .count();
        return "Projects: " + projects.size()
                + ". Budget " + MoneyUtil.mwk(budget)
                + ", spent " + MoneyUtil.mwk(spent)
                + ". Over-budget: " + overBudget
                + ", overdue deadlines: " + overdue + ".";
    }

    private String loanScheduleQualityText(List<LoanScheduleRecord> schedules) {
        if (schedules.isEmpty()) {
            return "Insufficient data - no active loan schedules are registered.";
        }
        long overdue = schedules.stream().filter(schedule -> isOverdue(parseDate(schedule.getDueDate()))).count();
        long incomplete = incompleteLoanScheduleCount();
        return "Active schedules: " + schedules.size()
                + ". Overdue: " + overdue
                + ". Incomplete schedules: " + incomplete
                + ". Borrowed and lent records are interpreted separately.";
    }

    private String dataQualityChecksText(DataQualitySummary quality) {
        return """
                Missing categories: %s
                Missing payment methods: %s
                Duplicate transactions: %s
                Zero-value transactions: %s
                Invalid or missing dates: %s
                Future-dated records: %s
                Unreconciled accounts: %s
                Unsupported currency conversions: %s
                Incomplete loan schedules: %s
                Unallocated goal savings: %s
                Project expenses without activities: %s
                Cancelled transactions excluded from analysis: %s
                Reversed transactions excluded from analysis: %s
                Missing expected-income dates: %s
                Invalid recurring plans: %s
                """.formatted(
                qualityStatus(quality.missingCategories()),
                qualityStatus(quality.missingPaymentMethods()),
                qualityStatus(quality.duplicateTransactions()),
                qualityStatus(quality.zeroValue()),
                qualityStatus(quality.invalidDates()),
                qualityStatus(quality.futureDated()),
                qualityStatus(quality.unreconciledAccounts()),
                qualityStatus(quality.mixedCurrencyWarnings()),
                qualityStatus(quality.incompleteLoanSchedules()),
                qualityStatus(quality.goalFundingGaps()),
                qualityStatus(quality.projectExpensesWithoutActivities()),
                quality.cancelledTransactions() == 0 ? "Pass" : "Warning - " + quality.cancelledTransactions(),
                quality.reversedTransactions() == 0 ? "Pass" : "Warning - " + quality.reversedTransactions(),
                qualityStatus(quality.missingExpectedIncomeDates()),
                qualityStatus(quality.invalidRecurringPlans())
        );
    }

    private String qualityStatus(long count) {
        return count == 0 ? "Pass" : "Warning - " + count;
    }

    private boolean validateForecastPeriod() {
        LocalDate start = forecastStartDatePicker.getValue();
        LocalDate end = forecastEndDatePicker.getValue();
        if (start == null) {
            UiAlerts.info("Select a forecast start date.");
            return false;
        }
        if (end == null) {
            UiAlerts.info("Select a forecast end date.");
            return false;
        }
        if (end.isBefore(start)) {
            UiAlerts.info("Forecast end date cannot be before the start date.");
            return false;
        }
        long days = ChronoUnit.DAYS.between(start, end);
        if (days > 366) {
            UiAlerts.info("Keep cash forecasts within one year.");
            return false;
        }
        return true;
    }

    private String goalStepsFallback() {
        List<Goal> goals = activeGoals();
        if (goals.isEmpty()) {
            return """
                    Summary
                    No active goals are available for step planning.

                    Checks
                    Active goals: 0

                    Risks
                    Goal-step planning cannot estimate required contributions without active goals.

                    Recommended Actions
                    Open Goals and create or activate a goal before planning steps.

                    Conclusion
                    No financial records were changed.

                    Limitations
                    This review only uses active goal records.
                    """;
        }
        String steps = goals.stream()
                .limit(7)
                .map(goal -> {
                    double contribution = goal.getMonthlyContribution() > 0
                            ? goal.getMonthlyContribution()
                            : Math.max(0, goal.getRemainingAmount());
                    return "Priority: " + blankAs(goal.getPriority(), "Medium")
                            + "\nReason: " + goal.getGoalName() + " has a funding gap of " + MoneyUtil.mwk(goal.getRemainingAmount())
                            + "\nRecommended date: " + LocalDate.now().plusDays(7)
                            + "\nRelated module: Goals"
                            + "\nExpected outcome: confirm a contribution plan of " + MoneyUtil.mwk(contribution) + ".";
                })
                .collect(Collectors.joining("\n\n"));
        return """
                Summary
                Goal-step planning prepared next actions for %d active goal(s).

                Checks
                Target amounts, current amounts, remaining gaps, contribution frequency and target dates were reviewed.

                Risks
                Goals without a contribution amount or funding account may fall behind without a deliberate funding decision.

                Recommended Actions
                %s

                Conclusion
                Goal steps are advisory only; PFMIS did not create contributions or change goals.

                Limitations
                Forecast completion dates depend on current contribution values.
                """.formatted(goals.size(), steps);
    }

    private String overdueLoansFallback() {
        List<LoanScheduleRecord> overdue = activeLoanSchedules().stream()
                .filter(schedule -> isOverdue(parseDate(schedule.getDueDate())))
                .toList();
        if (overdue.isEmpty()) {
            return """
                    Summary
                    No overdue active loan schedules were found.

                    Checks
                    Active repayment schedules were checked against today's date.

                    Risks
                    Missing due dates or incomplete schedules may hide overdue obligations.

                    Recommended Actions
                    Refresh loan schedules after recording repayments or collections.

                    Conclusion
                    No overdue loan action is visible from current schedules.

                    Limitations
                    This check depends on schedule due dates and statuses.
                    """;
        }
        String rows = overdue.stream()
                .limit(7)
                .map(schedule -> blankAs(schedule.getLoanDirection(), "-")
                        + " | " + blankAs(schedule.getPersonName(), "-")
                        + " | due " + blankAs(schedule.getDueDate(), "-")
                        + " | outstanding " + MoneyUtil.mwk(schedule.getOutstandingAmount()))
                .collect(Collectors.joining("\n"));
        return """
                Summary
                %d overdue active loan schedule(s) need attention.

                Checks
                %s

                Risks
                Borrowed-loan overdue amounts can create cash pressure. Lent-loan overdue amounts are receivables, not available cash.

                Recommended Actions
                Open the Loan Ledger or Repayment Schedule and resolve the oldest overdue item first.

                Conclusion
                Loan records were reviewed only; no repayment was recorded.

                Limitations
                Incomplete due dates or statuses can affect overdue counts.
                """.formatted(overdue.size(), rows);
    }

    private String unreconciledAccountsFallback() {
        Set<String> reconciledAccounts = database.listLatestAccountReconciliations().stream()
                .map(AccountReconciliationRecord::getAccountName)
                .collect(Collectors.toSet());
        List<Account> unreconciled = database.listAccounts().stream()
                .filter(this::isActiveAccount)
                .filter(account -> !reconciledAccounts.contains(account.getAccountName()))
                .toList();
        String rows = unreconciled.stream()
                .limit(10)
                .map(account -> account.getAccountName() + " | " + blankAs(account.getStatus(), "ACTIVE")
                        + " | balance " + MoneyUtil.mwk(account.getCurrentBalance()))
                .collect(Collectors.joining("\n"));
        return """
                Summary
                %d active account(s) do not have a latest reconciliation record.

                Checks
                %s

                Risks
                Unreconciled balances can weaken forecasts, budget pressure review and account-position reports.

                Recommended Actions
                Open Data Quality and Account Reconciliation, then reconcile accounts with material balances first.

                Conclusion
                No reconciliation record was created automatically.

                Limitations
                This check only verifies that a reconciliation record exists; it does not confirm external bank statements.
                """.formatted(
                unreconciled.size(),
                rows.isBlank() ? "No active unreconciled accounts found." : rows
        );
    }

    private String financialHealthFallback() {
        DashboardStats stats = database.getDashboardStats();
        double savingsRate = savingsRate(stats);
        DataQualitySummary quality = dataQualitySummary();
        boolean incompleteTransactions = currentMonthPostedTransactions().isEmpty()
                || (stats.getMonthlyIncome() <= 0 && stats.getMonthlyExpenses() <= 0);
        String balanceInterpretation = incompleteTransactions
                ? "The balance is positive, but current-period income and expense records are incomplete, so financial health cannot be confirmed from the balance alone."
                : "Current-period income and expense records are available for savings-rate and cash-flow review.";
        return """
                Summary
                Balance is %s. Current-month income is %s, expenses are %s and savings are %s. Savings rate is %.1f%%.

                Checks
                %s
                Active accounts: %d
                Active goals: %d
                Active projects: %d
                Active loan schedules: %d

                Risks
                %s

                Recommended Actions
                %s

                Conclusion
                %s

                Limitations
                Data quality status is %s with %d issue(s) detected by local checks. Use View Evidence before relying on the recommendation.
                """.formatted(
                MoneyUtil.mwk(stats.getTotalBalance()),
                MoneyUtil.mwk(stats.getMonthlyIncome()),
                MoneyUtil.mwk(stats.getMonthlyExpenses()),
                MoneyUtil.mwk(stats.getMonthlySavings()),
                savingsRate,
                balanceInterpretation,
                stats.getActiveAccounts(),
                stats.getActiveGoals(),
                stats.getActiveProjects(),
                activeLoanSchedules().size(),
                riskSummary(quality, incompleteTransactions),
                firstAction(quality, incompleteTransactions),
                incompleteTransactions
                        ? "Treat this as an incomplete-data review, not a healthy-position confirmation."
                        : "The records are sufficient for a basic financial-health interpretation.",
                dataStatus(),
                quality.totalIssues()
        );
    }

    private String reportsFallback() {
        List<ReportRow> income = database.incomeSourceReport();
        List<ReportRow> expenses = database.categorySpendingReport(YearMonth.now().toString());
        List<BudgetProgress> budgets = database.listBudgetProgress(YearMonth.now().toString());
        return """
                Summary
                Report readiness is based on current records. Monthly income rows: %d. Current-month expense categories: %d. Current-month budget records: %d.

                Checks
                %s

                Risks
                Reports with no source transactions may look complete while their conclusions are weak.

                Recommended Actions
                Review the report families marked insufficient before relying on trends or forecasts.

                Conclusion
                Reports Analysis should focus on changes, abnormal figures and weak report evidence, not repeat the full financial-health review.

                Limitations
                Report status is derived from available transaction, budget, goal, project and loan records.
                """.formatted(
                income.size(),
                expenses.size(),
                budgets.size(),
                reportStatusText()
        );
    }

    private String budgetHouseholdFallback() {
        String month = YearMonth.now().toString();
        List<BudgetProgress> budgets = activeBudgetProgress(month);
        double householdUnits = database.householdUnitsForMonth(month);
        if (budgets.isEmpty() || householdUnits <= 0) {
            return """
                    Summary
                    No active household budget with household units was found for %s.

                    Checks
                    Active budget records: %d
                    Household units: %.1f

                    Risks
                    Budget and per-person analysis are incomplete without an active budget and household data.

                    Recommended Actions
                    Create or activate a budget and register household members before requesting household budget analysis.

                    Conclusion
                    The system should not generate generic household spending advice without budget and household records.

                    Limitations
                    Only current-month budget and household records were checked.
                    """.formatted(month, budgets.size(), householdUnits);
        }
        BudgetProgress highestUse = budgets.stream()
                .max((left, right) -> Double.compare(left.getPercentUsed(), right.getPercentUsed()))
                .orElse(null);
        int daysLeft = Math.max(0, YearMonth.now().atEndOfMonth().getDayOfMonth() - LocalDate.now().getDayOfMonth());
        return """
                Summary
                %d active budget allocation(s) are available for %s. Household units: %.1f.

                Checks
                Highest utilisation: %s
                Total planned: %s
                Total spent: %s
                Days left: %d

                Risks
                %s

                Recommended Actions
                Open the budget workspace and review categories above 80%% used or forecast to exceed their limit.

                Conclusion
                Budget analysis is available from posted expense records; users should not type actual spending into the budget manually.

                Limitations
                Forecasting assumes current-month records are complete.
                """.formatted(
                budgets.size(),
                month,
                householdUnits,
                highestUse == null ? "-" : highestUse.getBudgetName() + " at " + String.format(Locale.ENGLISH, "%.1f%%", highestUse.getPercentUsed()),
                MoneyUtil.mwk(budgets.stream().mapToDouble(BudgetProgress::getAmountLimit).sum()),
                MoneyUtil.mwk(budgets.stream().mapToDouble(BudgetProgress::getSpent).sum()),
                daysLeft,
                budgetRiskText(budgets)
        );
    }

    private String forecastFallback(ForecastResult forecast) {
        return """
                Summary
                Cash forecast for %s to %s.

                Checks
                Opening available balance:     %s
                Expected income:               %s
                Expected loan collections:     %s
                Upcoming expenses:             %s
                Loan repayments:               %s
                Planned goal contributions:    %s
                Project commitments:           %s
                Scheduled transfers net effect:%s
                Projected closing balance:     %s

                Risks
                %s

                Recommended Actions
                %s

                Conclusion
                %s

                Limitations
                Forecast quality is %s. Values from budgets and goal contributions are estimates unless exact due dates are recorded.
                """.formatted(
                forecast.start(),
                forecast.end(),
                MoneyUtil.mwk(forecast.openingBalance()),
                MoneyUtil.mwk(forecast.expectedIncome()),
                MoneyUtil.mwk(forecast.loanCollections()),
                MoneyUtil.mwk(forecast.upcomingExpenses()),
                MoneyUtil.mwk(forecast.loanRepayments()),
                MoneyUtil.mwk(forecast.goalContributions()),
                MoneyUtil.mwk(forecast.projectCommitments()),
                MoneyUtil.mwk(0),
                MoneyUtil.mwk(forecast.projectedClosingBalance()),
                forecastRisk(forecast),
                forecastAction(forecast),
                forecast.projectedClosingBalance() < 0
                        ? "The forecast shows a cash shortfall in the selected period."
                        : "The forecast remains positive for the selected period.",
                forecast.dataStatus()
        );
    }

    private String goalsProjectsFallback() {
        List<Goal> goals = database.listGoals();
        List<Project> projects = database.listProjects();
        double requiredContributions = activeGoals().stream().mapToDouble(Goal::getMonthlyContribution).sum();
        long projectCandidates = goals.stream().filter(this::likelyProjectGoal).count();
        long overBudgetProjects = projects.stream()
                .filter(project -> project.getPlannedBudget() > 0 && project.getAmountSpent() > project.getPlannedBudget())
                .count();
        return """
                Summary
                Goals registered: %d. Projects registered: %d. Active monthly goal contributions: %s.

                Checks
                Possible goal-to-project candidates: %d
                Projects over budget: %d
                Active projects: %d

                Risks
                %s

                Recommended Actions
                Keep simple savings goals as goals. Convert only goals that need activities, project dates, procurement, budget tracking or implementation monitoring.

                Conclusion
                The label should be Review Goals and Projects because not every goal should become a project.

                Limitations
                Goal-step detail is limited to records available in the Goals module.
                """.formatted(
                goals.size(),
                projects.size(),
                MoneyUtil.mwk(requiredContributions),
                projectCandidates,
                overBudgetProjects,
                activeProjects().size(),
                goalsProjectsRiskText()
        );
    }

    private String loansFallback() {
        List<LoanScheduleRecord> schedules = activeLoanSchedules();
        LocalDate today = LocalDate.now();
        LocalDate thirtyDays = today.plusDays(30);
        double borrowedDue = schedules.stream()
                .filter(schedule -> "BORROWED".equalsIgnoreCase(blankAs(schedule.getLoanDirection(), "")))
                .filter(schedule -> dateBetween(parseDate(schedule.getDueDate()), today, thirtyDays))
                .mapToDouble(this::loanPaymentAmount)
                .sum();
        double lentDue = schedules.stream()
                .filter(schedule -> "LENT".equalsIgnoreCase(blankAs(schedule.getLoanDirection(), "")))
                .filter(schedule -> dateBetween(parseDate(schedule.getDueDate()), today, thirtyDays))
                .mapToDouble(this::loanPaymentAmount)
                .sum();
        double borrowedOutstanding = schedules.stream()
                .filter(schedule -> "BORROWED".equalsIgnoreCase(blankAs(schedule.getLoanDirection(), "")))
                .mapToDouble(LoanScheduleRecord::getOutstandingAmount)
                .sum();
        double lentOutstanding = schedules.stream()
                .filter(schedule -> "LENT".equalsIgnoreCase(blankAs(schedule.getLoanDirection(), "")))
                .mapToDouble(LoanScheduleRecord::getOutstandingAmount)
                .sum();
        return """
                Summary
                Active loan schedules: %d. Borrowed outstanding: %s. Money lent outstanding: %s.

                Checks
                Borrowed repayments due within 30 days: %s
                Expected collections within 30 days: %s
                Overdue schedules: %d
                Largest borrower concentration: %.1f%%

                Risks
                %s

                Recommended Actions
                Review repayment schedules and overdue records before relying only on transaction history.

                Conclusion
                Loans and repayments are ready for smart review when each open loan has a schedule and due date.

                Limitations
                Affordability uses current-month income only; missing income records weaken the ratio.
                """.formatted(
                schedules.size(),
                MoneyUtil.mwk(borrowedOutstanding),
                MoneyUtil.mwk(lentOutstanding),
                MoneyUtil.mwk(borrowedDue),
                MoneyUtil.mwk(lentDue),
                schedules.stream().filter(schedule -> isOverdue(parseDate(schedule.getDueDate()))).count(),
                borrowerConcentration(schedules),
                loanRiskText(borrowedDue)
        );
    }

    private String dataQualityFallback() {
        DataQualitySummary quality = dataQualitySummary();
        return """
                Summary
                Data quality status: %s.

                Checks
                Transactions without categories: %d
                Transactions without payment method: %d
                Possible duplicate transactions: %d
                Invalid or missing dates: %d
                Future-dated transactions: %d
                Zero-value transactions: %d
                Accounts without reconciliation: %d
                Incomplete loan schedules: %d
                Goals without contribution/funding details: %d
                Project expenses without activities: %d
                Mixed-currency warnings: %d
                Cancelled transactions excluded: %d
                Reversed transactions excluded: %d
                Missing expected-income dates: %d
                Invalid recurring plans: %d

                Risks
                Incomplete or inconsistent data can distort balances, budgets, forecasts, goals and loan recommendations.

                Recommended Actions
                Correct the largest issue count first, then refresh data and run the relevant analysis again.

                Conclusion
                Data Reliability should remain one section with contextual actions in the result, not many permanent command buttons.

                Limitations
                Duplicate detection uses same date, account, type, category and amount as a risk indicator.
                """.formatted(
                dataStatus(),
                quality.missingCategories(),
                quality.missingPaymentMethods(),
                quality.duplicateTransactions(),
                quality.invalidDates(),
                quality.futureDated(),
                quality.zeroValue(),
                quality.unreconciledAccounts(),
                quality.incompleteLoanSchedules(),
                quality.goalFundingGaps(),
                quality.projectExpensesWithoutActivities(),
                quality.mixedCurrencyWarnings(),
                quality.cancelledTransactions(),
                quality.reversedTransactions(),
                quality.missingExpectedIncomeDates(),
                quality.invalidRecurringPlans()
        );
    }

    private String actionPlanFallback() {
        List<String> actions = new ArrayList<>();
        DataQualitySummary quality = dataQualitySummary();
        if (currentMonthPostedTransactions().isEmpty()) {
            actions.add("Today\nRecord or import this month's income and expense transactions before relying on savings-rate analysis.");
        }
        if (quality.missingCategories() > 0) {
            actions.add("Within 2 days\nAssign categories to " + quality.missingCategories() + " transaction(s).");
        }
        List<BudgetProgress> atRiskBudgets = activeBudgetProgress(YearMonth.now().toString()).stream()
                .filter(progress -> progress.getPercentUsed() >= 80)
                .limit(2)
                .toList();
        for (BudgetProgress budget : atRiskBudgets) {
            actions.add("Within 3 days\nReview " + budget.getBudgetName() + "; it has used "
                    + String.format(Locale.ENGLISH, "%.0f%%", budget.getPercentUsed()) + " of its limit.");
        }
        double loanDue = loanRepaymentsBetween(LocalDate.now(), LocalDate.now().plusDays(7));
        if (loanDue > 0) {
            actions.add("Within 7 days\nPrepare " + MoneyUtil.mwk(loanDue) + " for borrowed-loan repayments due this week.");
        }
        Goal urgentGoal = activeGoals().stream()
                .filter(goal -> goal.getRemainingAmount() > 0 && goal.getMonthlyContribution() > 0)
                .findFirst()
                .orElse(null);
        if (urgentGoal != null) {
            actions.add("Within 7 days\nConfirm the next " + MoneyUtil.mwk(urgentGoal.getMonthlyContribution())
                    + " contribution for " + urgentGoal.getGoalName() + ".");
        }
        Project projectRisk = activeProjects().stream()
                .filter(project -> project.getPlannedBudget() > 0 && project.getAmountSpent() / project.getPlannedBudget() >= 0.7)
                .findFirst()
                .orElse(null);
        if (projectRisk != null) {
            actions.add("Within 7 days\nReview " + projectRisk.getProjectName() + " because budget use is above 70%.");
        }
        if (actions.isEmpty()) {
            actions.add("Within 7 days\nRefresh records and continue entering transactions, budgets, goals, projects and loan schedules consistently.");
        }
        return """
                Summary
                The plan below contains %d supported action(s).

                Checks
                %s

                Risks
                Actions are limited to issues visible in current records.

                Recommended Actions
                %s

                Conclusion
                A seven-day plan should show only justified actions, not seven items by default.

                Limitations
                Missing expected-income, obligation or recurring-transaction dates can hide urgent actions.
                """.formatted(
                Math.min(7, actions.size()),
                checksStatusText(),
                actions.stream().limit(7).collect(Collectors.joining("\n\n"))
        );
    }

    private String customQuestionFallback(String question) {
        return """
                Summary
                Smart Analysis prepared the question using the current verified records.

                Checks
                Question: %s

                Risks
                If the question depends on missing transactions, inactive budgets, unscheduled loans or incomplete goals, the answer may be limited.

                Recommended Actions
                Review View Evidence, then ask a more specific follow-up if needed.

                Conclusion
                No financial data was changed.

                Limitations
                Rule-based fallback cannot infer facts that are not recorded in PFMIS.
                """.formatted(question);
    }

    private String currentFiguresEvidence() {
        DashboardStats stats = database.getDashboardStats();
        List<Account> accounts = database.listAccounts();
        List<FinanceTransaction> transactions = allTransactions();
        YearMonth currentMonth = YearMonth.now();
        List<FinanceTransaction> monthTransactions = currentMonthPostedTransactions();
        double openingBalances = accounts.stream()
                .filter(this::isActiveAccount)
                .mapToDouble(Account::getOpeningBalance)
                .sum();
        double storedAccountBalances = accounts.stream()
                .filter(this::isActiveAccount)
                .mapToDouble(Account::getCurrentBalance)
                .sum();
        long cancelled = transactions.stream().filter(this::isCancelled).count();
        long reversed = transactions.stream().filter(this::isReversed).count();
        long outOfPeriod = transactions.stream()
                .filter(this::isPosted)
                .filter(transaction -> !dateInMonth(transaction.getTransactionDate(), currentMonth))
                .count();
        return """
                Date range: %s-01 to %s
                Currency scope: %s
                Included accounts: %d active account(s)
                Opening balances:       %s
                Valid income:           %s
                Valid expenses:         %s
                Transfers net effect:   %s
                Loan cash-flow effects: included in calculated dashboard balance
                Calculated balance:     %s
                Stored account balance: %s
                Valid current-period transactions: %d
                Cancelled records excluded: %d
                Reversed records excluded: %d
                Posted records outside period: %d
                Savings rate basis: %s
                """.formatted(
                currentMonth,
                currentMonth.atEndOfMonth(),
                currencyScope(accounts),
                accounts.stream().filter(this::isActiveAccount).count(),
                MoneyUtil.mwk(openingBalances),
                MoneyUtil.mwk(stats.getMonthlyIncome()),
                MoneyUtil.mwk(stats.getMonthlyExpenses()),
                MoneyUtil.mwk(0),
                MoneyUtil.mwk(stats.getTotalBalance()),
                MoneyUtil.mwk(storedAccountBalances),
                monthTransactions.size(),
                cancelled,
                reversed,
                outOfPeriod,
                stats.getMonthlyIncome() <= 0
                        ? "Savings rate is 0 because no current-period income is recorded."
                        : "Savings rate = monthly savings divided by monthly income."
        );
    }

    private String reportsEvidence() {
        String month = YearMonth.now().toString();
        return """
                Monthly income report rows: %d
                Monthly expense category rows: %d
                Budget progress rows: %d
                Account balance report rows: %d
                Loan report rows: %d
                Project spending report rows: %d
                Cancelled transactions are excluded by report queries where valid transaction filters are available.
                """.formatted(
                database.incomeSourceByAccountReport(month).size(),
                database.categorySpendingReport(month).size(),
                database.listBudgetProgress(month).size(),
                database.accountBalanceReport().size(),
                database.lendingByPersonReport(month).size(),
                database.projectSpendingReport(month).size()
        );
    }

    private String budgetHouseholdEvidence() {
        String month = YearMonth.now().toString();
        List<BudgetProgress> budgets = database.listBudgetProgress(month);
        String rows = budgets.stream()
                .limit(8)
                .map(progress -> progress.getBudgetName()
                        + " | limit=" + MoneyUtil.mwk(progress.getAmountLimit())
                        + " | spent=" + MoneyUtil.mwk(progress.getSpent())
                        + " | used=" + String.format(Locale.ENGLISH, "%.1f%%", progress.getPercentUsed())
                        + " | status=" + progress.getMonthResult())
                .collect(Collectors.joining("\n"));
        return """
                Budget month: %s
                Household units: %.1f
                Budget records: %d
                Budget evidence:
                %s
                Actual spending comes from posted expense transactions, not manual budget totals.
                """.formatted(
                month,
                database.householdUnitsForMonth(month),
                budgets.size(),
                rows.isBlank() ? "No budget records found." : rows
        );
    }

    private String forecastEvidence(ForecastResult forecast) {
        return """
                Forecast period: %s to %s
                Opening balance: %s
                Scheduled expected income: %s
                Recurring income plans: %s
                Expected loan collections: %s
                Budget remainder estimate: %s
                Scheduled obligations: %s
                Recurring expense plans: %s
                Borrowed-loan repayments: %s
                Planned goal contribution estimate: %s
                Scheduled transfer fees: %s
                Internal scheduled transfer net effect: %s
                Project commitments included in obligations: %s
                Projected closing balance: %s
                Data status: %s
                """.formatted(
                forecast.start(),
                forecast.end(),
                MoneyUtil.mwk(forecast.openingBalance()),
                MoneyUtil.mwk(forecast.scheduledExpectedIncome()),
                MoneyUtil.mwk(forecast.recurringIncome()),
                MoneyUtil.mwk(forecast.loanCollections()),
                MoneyUtil.mwk(forecast.budgetRemainderEstimate()),
                MoneyUtil.mwk(forecast.scheduledObligations()),
                MoneyUtil.mwk(forecast.recurringExpenses()),
                MoneyUtil.mwk(forecast.loanRepayments()),
                MoneyUtil.mwk(forecast.goalContributions()),
                MoneyUtil.mwk(forecast.scheduledTransferFees()),
                MoneyUtil.mwk(0),
                MoneyUtil.mwk(forecast.projectCommitments()),
                MoneyUtil.mwk(forecast.projectedClosingBalance()),
                forecast.dataStatus()
        );
    }

    private String goalsProjectsEvidence() {
        String goals = database.listGoals().stream()
                .limit(8)
                .map(goal -> goal.getGoalName()
                        + " | target=" + MoneyUtil.mwk(goal.getTargetAmount())
                        + " | allocated=" + MoneyUtil.mwk(goal.getCurrentAmount())
                        + " | remaining=" + MoneyUtil.mwk(goal.getRemainingAmount())
                        + " | target date=" + blankAs(goal.getTargetDate(), "-")
                        + " | status=" + blankAs(goal.getStatus(), "-"))
                .collect(Collectors.joining("\n"));
        String projects = database.listProjects().stream()
                .limit(8)
                .map(project -> project.getProjectName()
                        + " | budget=" + MoneyUtil.mwk(project.getPlannedBudget())
                        + " | spent=" + MoneyUtil.mwk(project.getAmountSpent())
                        + " | remaining=" + MoneyUtil.mwk(project.getRemainingBudget())
                        + " | status=" + blankAs(project.getStatus(), "-"))
                .collect(Collectors.joining("\n"));
        return """
                Goal evidence:
                %s

                Project evidence:
                %s

                Goal-to-project conversion is recommended only when implementation work and project-level budget control are needed.
                """.formatted(
                goals.isBlank() ? "No goals registered." : goals,
                projects.isBlank() ? "No projects registered." : projects
        );
    }

    private String loansEvidence() {
        String rows = activeLoanSchedules().stream()
                .limit(10)
                .map(schedule -> "#" + schedule.getId()
                        + " | " + blankAs(schedule.getLoanDirection(), "-")
                        + " | " + blankAs(schedule.getPersonName(), "-")
                        + " | principal=" + MoneyUtil.mwk(schedule.getPrincipalAmount())
                        + " | outstanding=" + MoneyUtil.mwk(schedule.getOutstandingAmount())
                        + " | due=" + blankAs(schedule.getDueDate(), "-")
                        + " | status=" + blankAs(schedule.getStatus(), "-"))
                .collect(Collectors.joining("\n"));
        return """
                Active loan schedules: %d
                Loan evidence:
                %s
                Loan payment amount uses payment amount when set; otherwise outstanding amount.
                """.formatted(
                activeLoanSchedules().size(),
                rows.isBlank() ? "No active loan schedules." : rows
        );
    }

    private String dataQualityEvidence() {
        return dataQualityFallback().replace("Summary\n", "");
    }

    private ForecastResult buildForecast() {
        ForecastPeriod period = selectedForecastPeriod();
        LocalDate start = period.start();
        LocalDate end = period.end();
        DashboardStats stats = database.getDashboardStats();
        double scheduledExpectedIncome = database.listExpectedIncomeRecords(1_000).stream()
                .filter(this::isActiveExpectedIncome)
                .filter(record -> dateBetween(parseDate(record.expectedDate()), start, end))
                .mapToDouble(DatabaseHandler.ExpectedIncomeRecord::expectedAmount)
                .sum();
        double recurringIncome = activeRecurringPlans().stream()
                .filter(plan -> "INCOME".equalsIgnoreCase(blankAs(plan.getTransactionType(), "")))
                .filter(plan -> dateBetween(parseDate(plan.getNextDueDate()), start, end))
                .mapToDouble(RecurringTransactionPlan::getAmount)
                .sum();
        double budgetRemainder = budgetRemainderEstimate(start, end);
        double scheduledObligations = activeScheduledObligations().stream()
                .filter(obligation -> dateBetween(parseDate(obligation.getDueDate()), start, end))
                .mapToDouble(ScheduledObligation::getAmount)
                .sum();
        double recurringExpenses = activeRecurringPlans().stream()
                .filter(plan -> "EXPENSE".equalsIgnoreCase(blankAs(plan.getTransactionType(), "")))
                .filter(plan -> dateBetween(parseDate(plan.getNextDueDate()), start, end))
                .mapToDouble(RecurringTransactionPlan::getAmount)
                .sum();
        double loanRepayments = loanRepaymentsBetween(start, end);
        double loanCollections = loanCollectionsBetween(start, end);
        double goalContributions = goalContributionEstimate(start, end);
        double transferFees = activeScheduledTransfers().stream()
                .filter(transfer -> dateBetween(parseDate(transfer.nextDueDate()), start, end))
                .mapToDouble(DatabaseHandler.ScheduledTransferRecord::transferFee)
                .sum();
        double projectCommitments = activeScheduledObligations().stream()
                .filter(obligation -> !blankAs(obligation.getProjectName(), "").isBlank())
                .filter(obligation -> dateBetween(parseDate(obligation.getDueDate()), start, end))
                .mapToDouble(ScheduledObligation::getAmount)
                .sum();
        double expectedIncome = scheduledExpectedIncome + recurringIncome;
        double upcomingExpenses = budgetRemainder + scheduledObligations + recurringExpenses + transferFees;
        double closing = stats.getTotalBalance()
                + expectedIncome
                + loanCollections
                - upcomingExpenses
                - loanRepayments
                - goalContributions;
        String forecastStatus = dataStatus();
        if (scheduledExpectedIncome <= 0 && recurringIncome <= 0) {
            forecastStatus = "Incomplete";
        }
        return new ForecastResult(
                start,
                end,
                stats.getTotalBalance(),
                scheduledExpectedIncome,
                recurringIncome,
                expectedIncome,
                loanCollections,
                budgetRemainder,
                scheduledObligations,
                recurringExpenses,
                upcomingExpenses,
                loanRepayments,
                goalContributions,
                transferFees,
                projectCommitments,
                closing,
                forecastStatus
        );
    }

    private void updateForecastDateControls() {
        boolean custom = PERIOD_CUSTOM.equals(forecastPeriodBox.getValue());
        forecastStartDatePicker.setDisable(!custom);
        forecastEndDatePicker.setDisable(!custom);
        if (!custom) {
            ForecastPeriod period = selectedForecastPeriod();
            forecastStartDatePicker.setValue(period.start());
            forecastEndDatePicker.setValue(period.end());
        }
    }

    private ForecastPeriod selectedForecastPeriod() {
        LocalDate today = LocalDate.now();
        String selected = blankAs(forecastPeriodBox.getValue(), PERIOD_NEXT_30);
        return switch (selected) {
            case PERIOD_NEXT_7 -> new ForecastPeriod(today, today.plusDays(7));
            case PERIOD_END_OF_MONTH -> new ForecastPeriod(today, YearMonth.now().atEndOfMonth());
            case PERIOD_CUSTOM -> {
                LocalDate start = forecastStartDatePicker.getValue() == null ? today : forecastStartDatePicker.getValue();
                LocalDate end = forecastEndDatePicker.getValue() == null ? start : forecastEndDatePicker.getValue();
                yield new ForecastPeriod(start, end.isBefore(start) ? start : end);
            }
            default -> new ForecastPeriod(today, today.plusDays(30));
        };
    }

    private String financialOverviewText() {
        DashboardStats stats = database.getDashboardStats();
        String overview = "Balance: " + MoneyUtil.mwk(stats.getTotalBalance())
                + " | Income: " + MoneyUtil.mwk(stats.getMonthlyIncome())
                + " | Expenses: " + MoneyUtil.mwk(stats.getMonthlyExpenses())
                + " | Savings: " + MoneyUtil.mwk(stats.getMonthlySavings())
                + " | Savings rate: " + String.format(Locale.ENGLISH, "%.1f%%", savingsRate(stats))
                + " | Active goals: " + stats.getActiveGoals()
                + " | Active projects: " + stats.getActiveProjects() + ".";
        if (stats.getMonthlyIncome() <= 0 && stats.getMonthlyExpenses() <= 0) {
            overview += " Current-period transaction data is incomplete; the positive balance alone does not prove financial health.";
        }
        return overview;
    }

    private String dataCoverageText() {
        String month = YearMonth.now().toString();
        return """
                Accounts: %d
                Transactions this month: %d
                Active budgets: %d
                Active goals: %d
                Active projects: %d
                Active loans: %d
                """.formatted(
                database.listAccounts().stream().filter(this::isActiveAccount).count(),
                currentMonthPostedTransactions().size(),
                activeBudgetProgress(month).size(),
                activeGoals().size(),
                activeProjects().size(),
                activeLoanSchedules().size()
        );
    }

    private String reportStatusText() {
        String month = YearMonth.now().toString();
        long incomeTransactions = currentMonthPostedTransactions().stream().filter(this::isIncome).count();
        long expenseTransactions = currentMonthPostedTransactions().stream().filter(this::isExpense).count();
        return """
                Monthly Summary: %s
                Cash Flow: %s
                Budget Report: %s
                Loan Report: %s
                Project Report: %s
                """.formatted(
                currentMonthPostedTransactions().isEmpty() ? "Insufficient transaction data" : "Available",
                incomeTransactions > 0 || expenseTransactions > 0 ? "Available" : "Insufficient transaction data",
                database.listBudgetProgress(month).isEmpty() ? "No active budget" : "Available",
                activeLoanSchedules().isEmpty() ? "No active loans" : "Available",
                activeProjects().isEmpty() ? "No active projects" : "Available"
        );
    }

    private String checksStatusText() {
        if (currentMonthPostedTransactions().isEmpty()) {
            return "Data is insufficient to complete all checks. No current-month income or expense transactions were found.";
        }
        DataQualitySummary quality = dataQualitySummary();
        if (quality.totalIssues() > 0) {
            return quality.totalIssues() + " issue(s) require attention.";
        }
        return "No urgent issues were detected from the available records.";
    }

    private String dataStatus() {
        DashboardStats stats = database.getDashboardStats();
        if (currentMonthPostedTransactions().isEmpty()
                || (stats.getMonthlyIncome() <= 0 && stats.getMonthlyExpenses() <= 0)) {
            return "Incomplete";
        }
        return dataQualitySummary().totalIssues() > 0 ? "Needs attention" : "Usable";
    }

    private DataQualitySummary dataQualitySummary() {
        List<FinanceTransaction> transactions = allTransactions();
        long missingCategories = transactions.stream()
                .filter(this::isPosted)
                .filter(transaction -> isIncome(transaction) || isExpense(transaction))
                .filter(transaction -> blankAs(transaction.getCategoryName(), "").isBlank()
                        || "Uncategorized".equalsIgnoreCase(transaction.getCategoryName()))
                .count();
        long missingPaymentMethods = transactions.stream()
                .filter(this::isPosted)
                .filter(transaction -> isIncome(transaction) || isExpense(transaction) || isLoan(transaction))
                .filter(transaction -> blankAs(transaction.getPaymentMethod(), "").isBlank())
                .count();
        long invalidDates = transactions.stream().filter(transaction -> parseDate(transaction.getTransactionDate()) == null).count();
        long futureDated = transactions.stream()
                .filter(this::isPosted)
                .filter(transaction -> {
                    LocalDate date = parseDate(transaction.getTransactionDate());
                    return date != null && date.isAfter(LocalDate.now());
                })
                .count();
        long zeroValue = transactions.stream()
                .filter(this::isPosted)
                .filter(transaction -> Math.abs(transaction.getAmount()) < 0.005)
                .count();
        long duplicates = duplicateTransactionCount(transactions);
        long unreconciled = unreconciledAccountCount();
        long loanGaps = incompleteLoanScheduleCount();
        long goalGaps = database.listGoals().stream()
                .filter(goal -> goal.getRemainingAmount() > 0)
                .filter(goal -> goal.getMonthlyContribution() <= 0 || blankAs(goal.getFundingAccountName(), "").isBlank())
                .count();
        long projectExpenseGaps = transactions.stream()
                .filter(this::isPosted)
                .filter(transaction -> "PROJECT_EXPENSE".equalsIgnoreCase(blankAs(transaction.getTransactionPurpose(), "")))
                .filter(transaction -> transaction.getProjectActivityId() == null)
                .count();
        long mixedCurrencies = database.listAccounts().stream()
                .map(Account::getCurrency)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ENGLISH))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .size() > 1 ? 1 : 0;
        long cancelledTransactions = transactions.stream().filter(this::isCancelled).count();
        long reversedTransactions = transactions.stream().filter(this::isReversed).count();
        long missingExpectedIncomeDates = database.listExpectedIncomeRecords(1_000).stream()
                .filter(this::isActiveExpectedIncome)
                .filter(record -> parseDate(record.expectedDate()) == null)
                .count();
        long invalidRecurringPlans = activeRecurringPlans().stream()
                .filter(plan -> plan.getAmount() <= 0 || parseDate(plan.getNextDueDate()) == null)
                .count();
        return new DataQualitySummary(
                missingCategories,
                missingPaymentMethods,
                duplicates,
                invalidDates,
                futureDated,
                zeroValue,
                unreconciled,
                loanGaps,
                goalGaps,
                projectExpenseGaps,
                mixedCurrencies,
                cancelledTransactions,
                reversedTransactions,
                missingExpectedIncomeDates,
                invalidRecurringPlans
        );
    }

    private long duplicateTransactionCount(List<FinanceTransaction> transactions) {
        Map<String, Long> groups = transactions.stream()
                .filter(this::isPosted)
                .collect(Collectors.groupingBy(
                        transaction -> String.join("|",
                                blankAs(transaction.getTransactionDate(), ""),
                                blankAs(transaction.getAccountName(), ""),
                                blankAs(transaction.getTransactionType(), ""),
                                blankAs(transaction.getCategoryName(), ""),
                                String.format(Locale.ENGLISH, "%.2f", transaction.getAmount())),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        return groups.values().stream()
                .filter(count -> count > 1)
                .mapToLong(count -> count - 1)
                .sum();
    }

    private long unreconciledAccountCount() {
        Set<String> reconciledAccounts = database.listLatestAccountReconciliations().stream()
                .map(AccountReconciliationRecord::getAccountName)
                .collect(Collectors.toSet());
        return database.listAccounts().stream()
                .filter(this::isActiveAccount)
                .filter(account -> !reconciledAccounts.contains(account.getAccountName()))
                .count();
    }

    private long incompleteLoanScheduleCount() {
        return activeLoanSchedules().stream()
                .filter(schedule -> parseDate(schedule.getDueDate()) == null || schedule.getOutstandingAmount() > 0 && loanPaymentAmount(schedule) <= 0)
                .count();
    }

    private List<FinanceTransaction> allTransactions() {
        return database.listRecentTransactions(TRANSACTION_SCAN_LIMIT);
    }

    private List<FinanceTransaction> currentMonthPostedTransactions() {
        YearMonth month = YearMonth.now();
        return allTransactions().stream()
                .filter(this::isPosted)
                .filter(transaction -> dateInMonth(transaction.getTransactionDate(), month))
                .toList();
    }

    private List<BudgetProgress> activeBudgetProgress(String month) {
        return database.listBudgetProgress(month).stream()
                .filter(progress -> isOperationalStatus(progress.getStatus()))
                .toList();
    }

    private List<Goal> activeGoals() {
        return database.listGoals().stream()
                .filter(goal -> isOperationalStatus(goal.getStatus()))
                .toList();
    }

    private List<Project> activeProjects() {
        return database.listProjects().stream()
                .filter(project -> isOperationalStatus(project.getStatus()))
                .toList();
    }

    private List<LoanScheduleRecord> activeLoanSchedules() {
        return database.listLoanSchedules().stream()
                .filter(schedule -> isOperationalStatus(schedule.getStatus()))
                .toList();
    }

    private List<ScheduledObligation> activeScheduledObligations() {
        return database.listScheduledObligations().stream()
                .filter(obligation -> isOperationalStatus(obligation.getStatus()))
                .toList();
    }

    private List<RecurringTransactionPlan> activeRecurringPlans() {
        return database.listRecurringTransactionPlans().stream()
                .filter(plan -> isOperationalStatus(plan.getStatus()))
                .toList();
    }

    private List<DatabaseHandler.ScheduledTransferRecord> activeScheduledTransfers() {
        return database.listScheduledTransfers(1_000).stream()
                .filter(transfer -> isOperationalStatus(transfer.status()))
                .toList();
    }

    private boolean isOperationalStatus(String status) {
        String value = blankAs(status, "ACTIVE").toUpperCase(Locale.ENGLISH);
        return !List.of("CANCELLED", "INACTIVE", "COMPLETED", "SETTLED", "ARCHIVED", "CLOSED", "DRAFT", "PAUSED").contains(value);
    }

    private boolean isActiveExpectedIncome(DatabaseHandler.ExpectedIncomeRecord record) {
        String status = blankAs(record.status(), "UPCOMING").toUpperCase(Locale.ENGLISH);
        return !List.of("CANCELLED", "RECEIVED", "PARTIALLY RECEIVED", "ARCHIVED").contains(status);
    }

    private boolean isActiveAccount(Account account) {
        return !"INACTIVE".equalsIgnoreCase(blankAs(account.getStatus(), "ACTIVE"));
    }

    private boolean isPosted(FinanceTransaction transaction) {
        return !isCancelled(transaction) && !isReversed(transaction);
    }

    private boolean isCancelled(FinanceTransaction transaction) {
        return "CANCELLED".equalsIgnoreCase(blankAs(transaction.getTransactionStatus(), "COMPLETED"));
    }

    private boolean isReversed(FinanceTransaction transaction) {
        return "REVERSED".equalsIgnoreCase(blankAs(transaction.getTransactionStatus(), "COMPLETED"));
    }

    private boolean isIncome(FinanceTransaction transaction) {
        return "INCOME".equalsIgnoreCase(blankAs(transaction.getTransactionType(), ""));
    }

    private boolean isExpense(FinanceTransaction transaction) {
        return "EXPENSE".equalsIgnoreCase(blankAs(transaction.getTransactionType(), ""));
    }

    private boolean isLoan(FinanceTransaction transaction) {
        return "LOAN".equalsIgnoreCase(blankAs(transaction.getTransactionType(), ""));
    }

    private double savingsRate(DashboardStats stats) {
        return stats.getMonthlyIncome() <= 0 ? 0 : (stats.getMonthlySavings() / stats.getMonthlyIncome()) * 100;
    }

    private double budgetRemainderEstimate(LocalDate start, LocalDate end) {
        YearMonth currentMonth = YearMonth.now();
        if (end.isBefore(LocalDate.now()) || !YearMonth.from(start).equals(currentMonth)) {
            return 0;
        }
        double remaining = activeBudgetProgress(currentMonth.toString()).stream()
                .mapToDouble(progress -> Math.max(0, progress.getRemaining()))
                .sum();
        int remainingDays = Math.max(1, currentMonth.atEndOfMonth().getDayOfMonth() - LocalDate.now().getDayOfMonth() + 1);
        int coveredDays = (int) Math.max(1, ChronoUnit.DAYS.between(LocalDate.now(), end.isAfter(currentMonth.atEndOfMonth()) ? currentMonth.atEndOfMonth() : end) + 1);
        return remaining * Math.min(1, coveredDays / (double) remainingDays);
    }

    private double goalContributionEstimate(LocalDate start, LocalDate end) {
        long days = Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
        double scale = Math.min(1, days / 30.0);
        return activeGoals().stream()
                .filter(goal -> goal.getRemainingAmount() > 0)
                .mapToDouble(goal -> Math.max(0, goal.getMonthlyContribution()) * scale)
                .sum();
    }

    private double loanRepaymentsBetween(LocalDate start, LocalDate end) {
        return activeLoanSchedules().stream()
                .filter(schedule -> "BORROWED".equalsIgnoreCase(blankAs(schedule.getLoanDirection(), "")))
                .filter(schedule -> dateBetween(parseDate(schedule.getDueDate()), start, end))
                .mapToDouble(this::loanPaymentAmount)
                .sum();
    }

    private double loanCollectionsBetween(LocalDate start, LocalDate end) {
        return activeLoanSchedules().stream()
                .filter(schedule -> "LENT".equalsIgnoreCase(blankAs(schedule.getLoanDirection(), "")))
                .filter(schedule -> dateBetween(parseDate(schedule.getDueDate()), start, end))
                .mapToDouble(this::loanPaymentAmount)
                .sum();
    }

    private double loanPaymentAmount(LoanScheduleRecord schedule) {
        return schedule.getPaymentAmount() > 0 ? schedule.getPaymentAmount() : Math.max(0, schedule.getOutstandingAmount());
    }

    private boolean likelyProjectGoal(Goal goal) {
        String text = (blankAs(goal.getGoalName(), "") + " "
                + blankAs(goal.getGoalType(), "") + " "
                + blankAs(goal.getDescription(), "")).toLowerCase(Locale.ENGLISH);
        return text.contains("business")
                || text.contains("project")
                || text.contains("construction")
                || text.contains("farming")
                || text.contains("equipment");
    }

    private String riskSummary(DataQualitySummary quality, boolean incompleteTransactions) {
        if (incompleteTransactions) {
            return "Income and expense records are incomplete for the selected period.";
        }
        if (quality.totalIssues() > 0) {
            return quality.totalIssues() + " data-quality issue(s) may weaken analysis.";
        }
        return "No major risk was detected from the available records.";
    }

    private String firstAction(DataQualitySummary quality, boolean incompleteTransactions) {
        if (incompleteTransactions) {
            return "Enter or import the missing income and expense transactions, then refresh data.";
        }
        if (quality.missingCategories() > 0) {
            return "Assign missing transaction categories first.";
        }
        if (quality.unreconciledAccounts() > 0) {
            return "Reconcile account balances before relying on forecasts.";
        }
        return "Review forecast and budget pressure next.";
    }

    private RecommendedArea recommendedAreaForFinancialHealth() {
        if ("Incomplete".equals(dataStatus()) || dataQualitySummary().totalIssues() > 0) {
            return RecommendedArea.DATA_QUALITY;
        }
        return RecommendedArea.REPORTS;
    }

    private String budgetRiskText(List<BudgetProgress> budgets) {
        long exceeded = budgets.stream().filter(progress -> progress.getPercentUsed() >= 100).count();
        long warning = budgets.stream().filter(progress -> progress.getPercentUsed() >= 80 && progress.getPercentUsed() < 100).count();
        if (exceeded > 0) {
            return exceeded + " budget allocation(s) have been exceeded.";
        }
        if (warning > 0) {
            return warning + " budget allocation(s) are above the 80% warning threshold.";
        }
        return "No active budget allocation is above the warning threshold.";
    }

    private String forecastRisk(ForecastResult forecast) {
        if (forecast.projectedClosingBalance() < 0) {
            return "Projected cash becomes negative by " + MoneyUtil.mwk(Math.abs(forecast.projectedClosingBalance())) + ".";
        }
        if ("Incomplete".equals(forecast.dataStatus())) {
            return "Forecast evidence is incomplete because expected income or current transactions are missing.";
        }
        DashboardStats stats = database.getDashboardStats();
        double oneWeekExpenses = stats.getMonthlyExpenses() <= 0 ? 0 : stats.getMonthlyExpenses() / 4;
        if (oneWeekExpenses > 0 && forecast.projectedClosingBalance() < oneWeekExpenses) {
            return "Projected closing balance is below one week of recent expense pressure.";
        }
        return "No cash shortfall is visible for the selected period.";
    }

    private String forecastAction(ForecastResult forecast) {
        if (forecast.projectedClosingBalance() < 0) {
            return "Delay optional spending or confirm extra income before the due dates in this period.";
        }
        if ("Incomplete".equals(forecast.dataStatus())) {
            return "Record expected income, recurring expenses and due dates before relying on this forecast.";
        }
        return "Keep the forecast under review after each posted transaction.";
    }

    private String goalsProjectsRiskText() {
        long atRiskGoals = activeGoals().stream()
                .filter(goal -> parseDate(goal.getTargetDate()) != null)
                .filter(goal -> parseDate(goal.getTargetDate()).isBefore(LocalDate.now()) && goal.getRemainingAmount() > 0)
                .count();
        long overBudgetProjects = activeProjects().stream()
                .filter(project -> project.getPlannedBudget() > 0 && project.getAmountSpent() > project.getPlannedBudget())
                .count();
        if (atRiskGoals > 0 || overBudgetProjects > 0) {
            return atRiskGoals + " goal(s) are overdue or underfunded and " + overBudgetProjects + " project(s) are over budget.";
        }
        return "No overdue active goal or over-budget active project was detected.";
    }

    private String loanRiskText(double borrowedDue) {
        DashboardStats stats = database.getDashboardStats();
        if (borrowedDue > 0 && stats.getMonthlyIncome() > 0) {
            double ratio = borrowedDue / stats.getMonthlyIncome() * 100;
            if (ratio >= 30) {
                return "Borrowed-loan repayments due within 30 days are " + String.format(Locale.ENGLISH, "%.1f%%", ratio)
                        + " of current-month income.";
            }
        }
        if (borrowedDue > 0 && stats.getMonthlyIncome() <= 0) {
            return "Loan repayments are due, but no current-month income is recorded for affordability review.";
        }
        return "No major 30-day repayment pressure is visible from active schedules.";
    }

    private double borrowerConcentration(List<LoanScheduleRecord> schedules) {
        Map<String, Double> lentByPerson = new LinkedHashMap<>();
        for (LoanScheduleRecord schedule : schedules) {
            if (!"LENT".equalsIgnoreCase(blankAs(schedule.getLoanDirection(), ""))) {
                continue;
            }
            lentByPerson.merge(blankAs(schedule.getPersonName(), "Unknown"), schedule.getOutstandingAmount(), Double::sum);
        }
        double total = lentByPerson.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return 0;
        }
        return lentByPerson.values().stream().mapToDouble(Double::doubleValue).max().orElse(0) / total * 100;
    }

    private String providerStatus(AiSettings settings) {
        if (settings == null) {
            return "AI: Not configured";
        }
        if (!settings.isEnabled()) {
            return "AI: Configured but disabled";
        }
        if (!settings.canGenerateRecommendations()) {
            return "AI: Provider not ready";
        }
        return settings.isLocalProvider() ? "AI: Local and private - Connected" : "AI: External provider - Connected";
    }

    private String providerName(AiSettings settings) {
        return settings == null ? "Not configured" : settings.getDisplayName();
    }

    private String currencyScope(List<Account> accounts) {
        Set<String> currencies = accounts.stream()
                .map(Account::getCurrency)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ENGLISH))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return currencies.isEmpty() ? "Not set" : String.join(", ", currencies);
    }

    private String structuredAnswer(String answer, String fallback) {
        String clean = blankAs(answer, "");
        if (clean.isBlank()) {
            return fallback;
        }
        String lower = clean.toLowerCase(Locale.ENGLISH);
        if (lower.contains("summary")
                && lower.contains("checks")
                && lower.contains("risks")
                && lower.contains("recommended")
                && lower.contains("conclusion")
                && lower.contains("limitations")) {
            return clean;
        }
        return """
                Summary
                %s

                Checks
                Use View Evidence to inspect the records behind this response.

                Risks
                The provider response did not use the requested full structure.

                Recommended Actions
                Verify the evidence before acting on the recommendation.

                Conclusion
                No financial data was changed.

                Limitations
                The structured local fallback remains available if provider output is incomplete.
                """.formatted(clean);
    }

    private boolean containsSensitiveQuestionContent(String question) {
        String lower = blankAs(question, "").toLowerCase(Locale.ENGLISH);
        return lower.contains("password")
                || lower.contains("passcode")
                || lower.contains("api key")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("private key")
                || lower.contains("credential");
    }

    private boolean isFinancialQuestion(String question) {
        String lower = blankAs(question, "").toLowerCase(Locale.ENGLISH);
        return lower.contains("finance")
                || lower.contains("financial")
                || lower.contains("money")
                || lower.contains("cash")
                || lower.contains("income")
                || lower.contains("expense")
                || lower.contains("budget")
                || lower.contains("account")
                || lower.contains("balance")
                || lower.contains("loan")
                || lower.contains("repayment")
                || lower.contains("goal")
                || lower.contains("project")
                || lower.contains("risk")
                || lower.contains("obligation")
                || lower.contains("forecast")
                || lower.contains("transaction")
                || lower.contains("saving")
                || lower.contains("data");
    }

    private String sensitiveQuestionResponse() {
        return """
                Summary
                The question appears to contain sensitive secret information and was not sent to a Smart Analysis provider.

                Checks
                Smart Analysis can answer financial-record questions without passwords, API keys, tokens or credentials.

                Risks
                Secrets should not be placed in financial analysis prompts, logs or exports.

                Recommended Actions
                Remove secret values and ask about the financial record, account, budget, loan, goal or project issue instead.

                Conclusion
                No provider request was made and no data was changed.

                Limitations
                This local check uses keyword detection and may be conservative.
                """;
    }

    private String unrelatedQuestionResponse(String question) {
        return """
                Summary
                Smart Analysis only answers questions about personal financial management records in PFMIS.

                Checks
                The question was reviewed locally and does not appear to ask about accounts, budgets, transactions, loans, goals, projects, reports or financial data quality.

                Risks
                Unrelated questions can produce unsupported responses if sent to an external provider.

                Recommended Actions
                Ask a specific financial question, such as which budget is at risk, which loan needs attention, or what to do in the next seven days.

                Conclusion
                No provider request was made and no data was changed.

                Limitations
                Original question length: %d character(s).
                """.formatted(blankAs(question, "").length());
    }

    private String fallbackWithProviderFailure(String fallback) {
        return fallback + """

                Provider failure
                The Smart Analysis provider was unavailable. The rule-based result above remains available.
                """;
    }

    private String safeLogMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? "Provider request failed." : current.getClass().getSimpleName();
    }

    private String exportBody() {
        return """
                Smart Analysis Export
                Analysis: %s
                Exported: %s
                Data status: %s

                Result
                %s

                Evidence
                %s
                """.formatted(
                lastAnalysisName,
                LocalDateTime.now().format(RESULT_TIME),
                dataStatus(),
                lastResult,
                lastEvidence.isBlank() ? "No evidence captured." : lastEvidence
        );
    }

    private String slug(String value) {
        String slug = blankAs(value, "analysis").toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        return slug.isBlank() ? "analysis" : slug;
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private boolean dateInMonth(String dateValue, YearMonth month) {
        LocalDate date = parseDate(dateValue);
        return date != null && YearMonth.from(date).equals(month);
    }

    private boolean dateBetween(LocalDate date, LocalDate start, LocalDate end) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
    }

    private boolean isOverdue(LocalDate date) {
        return date != null && date.isBefore(LocalDate.now());
    }

    private String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
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

    private enum RecommendedArea {
        NONE("Open Recommended Area", "No recommended destination is available for this result.", null),
        ACCOUNTS("Open Account Overview", "Open Accounts > Account Overview from the sidebar.", SmartNavigationTarget.ACCOUNT_OVERVIEW),
        REPORTS("Open Reports", "Open Reports from the sidebar, then review Smart Analysis or the relevant report group.", SmartNavigationTarget.SMART_ANALYSIS_REPORTS),
        BUDGETS("Open Manage Budgets", "Open Budgets > Manage Budgets from the sidebar.", SmartNavigationTarget.BUDGETS),
        GOALS("Open Goals", "Open Goals > Goal Records from the sidebar.", SmartNavigationTarget.GOALS),
        PROJECTS("Open Projects", "Open Projects > Project Records from the sidebar.", SmartNavigationTarget.PROJECTS),
        GOALS_PROJECTS("Open Goal Records", "Open Goals > Goal Records or Projects > Project Records from the sidebar.", SmartNavigationTarget.GOALS),
        LOANS("Open Loan Ledger", "Open Loans > Loan Ledger from the sidebar.", SmartNavigationTarget.LOAN_LEDGER),
        REPAYMENT_SCHEDULE("Open Repayment Schedule", "Open Loans > Repayment Schedule from the sidebar.", SmartNavigationTarget.REPAYMENT_SCHEDULE),
        DATA_QUALITY("Open Data Quality Records", "Open Data And Records > Data Quality and Reconciliation from the sidebar.", SmartNavigationTarget.DATA_QUALITY_RECORDS),
        TRANSACTIONS("Open Transaction Ledger", "Open Transaction Ledger from the sidebar.", SmartNavigationTarget.TRANSACTION_LEDGER);

        private final String buttonText;
        private final String openHint;
        private final SmartNavigationTarget target;

        RecommendedArea(String buttonText, String openHint, SmartNavigationTarget target) {
            this.buttonText = buttonText;
            this.openHint = openHint;
            this.target = target;
        }

        String buttonText() {
            return buttonText;
        }

        String openHint() {
            return openHint;
        }

        SmartNavigationTarget target() {
            return target;
        }
    }

    private record ForecastPeriod(LocalDate start, LocalDate end) {
    }

    private record ForecastResult(
            LocalDate start,
            LocalDate end,
            double openingBalance,
            double scheduledExpectedIncome,
            double recurringIncome,
            double expectedIncome,
            double loanCollections,
            double budgetRemainderEstimate,
            double scheduledObligations,
            double recurringExpenses,
            double upcomingExpenses,
            double loanRepayments,
            double goalContributions,
            double scheduledTransferFees,
            double projectCommitments,
            double projectedClosingBalance,
            String dataStatus
    ) {
    }

    private record DataQualitySummary(
            long missingCategories,
            long missingPaymentMethods,
            long duplicateTransactions,
            long invalidDates,
            long futureDated,
            long zeroValue,
            long unreconciledAccounts,
            long incompleteLoanSchedules,
            long goalFundingGaps,
            long projectExpensesWithoutActivities,
            long mixedCurrencyWarnings,
            long cancelledTransactions,
            long reversedTransactions,
            long missingExpectedIncomeDates,
            long invalidRecurringPlans
    ) {
        long totalIssues() {
            return missingCategories
                    + missingPaymentMethods
                    + duplicateTransactions
                    + invalidDates
                    + futureDated
                    + zeroValue
                    + unreconciledAccounts
                    + incompleteLoanSchedules
                    + goalFundingGaps
                    + projectExpensesWithoutActivities
                    + mixedCurrencyWarnings
                    + missingExpectedIncomeDates
                    + invalidRecurringPlans;
        }

        long criticalIssueCount() {
            return invalidDates
                    + futureDated
                    + unreconciledAccounts
                    + incompleteLoanSchedules
                    + mixedCurrencyWarnings
                    + missingExpectedIncomeDates
                    + invalidRecurringPlans;
        }
    }
}
