package com.wk.pfmis.controllers;

import com.wk.pfmis.MainApp;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupOverview;
import com.wk.pfmis.fx.ConversionResult;
import com.wk.pfmis.fx.ExchangeRateService;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.DashboardStats;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ReportRow;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.utils.MoneyUtil;
import com.wk.pfmis.utils.ReadableTextSupport;
import com.wk.pfmis.utils.RequiredFieldSupport;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DashboardController {
    @FXML private Label totalBalanceLabel;
    @FXML private Label incomeLabel;
    @FXML private Label expensesLabel;
    @FXML private Label savingsLabel;
    @FXML private Label balanceDetailLabel;
    @FXML private Label incomeDetailLabel;
    @FXML private Label expensesDetailLabel;
    @FXML private Label savingsDetailLabel;
    @FXML private Label activeAccountsLabel;
    @FXML private Label activeProjectsLabel;
    @FXML private Label activeGoalsLabel;
    @FXML private Label moneyGivenLabel;
    @FXML private Label communitySavingsBalanceLabel;
    @FXML private Label communitySavingsDetailLabel;
    @FXML private Label savingsGroupContributionsLabel;
    @FXML private Label savingsGroupContributionsDetailLabel;
    @FXML private Label savingsGroupNextContributionLabel;
    @FXML private Label savingsGroupNextContributionDetailLabel;
    @FXML private Label savingsGroupExpectedPayoutLabel;
    @FXML private Label savingsGroupExpectedPayoutDetailLabel;
    @FXML private Label sectionTitleLabel;
    @FXML private Label signedInUserLabel;
    @FXML private Label activeWorkspaceLabel;
    @FXML private VBox sidebarNavigation;
    @FXML private Button dashboardButton;
    @FXML private VBox setupNavigation;
    @FXML private Button setupAdministrationButton;
    @FXML private Button setupSecurityUsersButton;
    @FXML private Button setupFinancialConfigurationButton;
    @FXML private Button setupSmartIntelligenceButton;
    @FXML private Button setupDataSystemButton;
    @FXML private VBox reportNavigation;
    @FXML private Button reportOverviewButton;
    @FXML private Button reportIncomeExpensesButton;
    @FXML private Button reportAccountsPositionButton;
    @FXML private Button reportProjectsGoalsButton;
    @FXML private Button reportLoansObligationsButton;
    @FXML private Button reportSmartAnalysisButton;
    @FXML private Button reportSystemReportsButton;
    @FXML private VBox dataRecordsNavigation;
    @FXML private Button dataIntakeButton;
    @FXML private Button recordsControlButton;
    @FXML private Button dataQualityRecordsButton;
    @FXML private Button auditHistoryButton;
    @FXML private Button syncRecoveryButton;
    @FXML private Button dataMaintenanceButton;
    @FXML private Button savingsOverviewButton;
    @FXML private Button addSavingsGroupButton;
    @FXML private Button bankNkhondeButton;
    @FXML private Button chipeleganyuButton;
    @FXML private Button communityContributionsButton;
    @FXML private Button communityPayoutsButton;
    @FXML private Button communitySavingsReportsButton;
    @FXML private Button budgetOverviewButton;
    @FXML private Button createBudgetButton;
    @FXML private Button budgetAllocationsButton;
    @FXML private Button budgetPerformanceButton;
    @FXML private Button householdBudgetButton;
    @FXML private Button budgetHistoryButton;
    @FXML private Button incomeOverviewButton;
    @FXML private Button addIncomeButton;
    @FXML private Button incomeRecordsButton;
    @FXML private Button expectedIncomeButton;
    @FXML private Button recurringIncomeButton;
    @FXML private Button expenseOverviewButton;
    @FXML private Button recordExpenseButton;
    @FXML private Button expenseRecordsButton;
    @FXML private Button plannedExpensesButton;
    @FXML private Button transactionsOverviewButton;
    @FXML private Button transactionLedgerButton;
    @FXML private Button transferMoneyButton;
    @FXML private Button scheduledTransfersButton;
    @FXML private Button correctionsReversalsButton;
    @FXML private Button loanOverviewButton;
    @FXML private Button newLoanButton;
    @FXML private Button loanRecordsButton;
    @FXML private Button recordRepaymentButton;
    @FXML private Button repaymentScheduleButton;
    @FXML private Button loanContactsButton;
    @FXML private Button goalsOverviewButton;
    @FXML private Button addGoalButton;
    @FXML private Button goalContributionsButton;
    @FXML private Button goalStepsButton;
    @FXML private Button goalHistoryButton;
    @FXML private Button projectOverviewButton;
    @FXML private Button addProjectButton;
    @FXML private Button projectActivitiesButton;
    @FXML private Button projectFinancesButton;
    @FXML private Button projectMilestonesStatusButton;
    @FXML private Button projectHistoryButton;
    @FXML private Button assetOverviewButton;
    @FXML private Button assetRegisterButton;
    @FXML private Button assetRecognitionButton;
    @FXML private Button registerAssetButton;
    @FXML private Button assetMaintenanceButton;
    @FXML private Button assetValuationButton;
    @FXML private Button assetTransferCustodyButton;
    @FXML private Button assetSaleDisposalButton;
    @FXML private Button assetHistoryButton;
    @FXML private Button returnWorkspaceButton;
    @FXML private VBox dashboardSummaryPane;
    @FXML private VBox planSnapshotBox;
    @FXML private StackPane contentPane;
    @FXML private LineChart<String, Number> cashFlowChart;
    @FXML private PieChart expenseDistributionChart;
    @FXML private PieChart accountBalanceChart;
    @FXML private PieChart incomeSourceChart;
    @FXML private PieChart moneyPositionChart;
    @FXML private BarChart<String, Number> projectSpendingChart;
    @FXML private TableView<FinanceTransaction> dashboardTransactionsTable;
    @FXML private TableColumn<FinanceTransaction, String> dashboardDateColumn;
    @FXML private TableColumn<FinanceTransaction, String> dashboardTypeColumn;
    @FXML private TableColumn<FinanceTransaction, String> dashboardAccountColumn;
    @FXML private TableColumn<FinanceTransaction, String> dashboardCategoryColumn;
    @FXML private TableColumn<FinanceTransaction, Double> dashboardAmountColumn;
    @FXML private VBox alertsBox;
    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final ExchangeRateService exchangeRateService = ExchangeRateService.getInstance();
    private static final String REPORT_GROUP_OVERVIEW = "Overview";
    private static final String REPORT_GROUP_INCOME_EXPENSES = "Income and Expenses";
    private static final String REPORT_GROUP_ACCOUNTS_POSITION = "Accounts and Position";
    private static final String REPORT_GROUP_PROJECTS_GOALS = "Projects and Goals";
    private static final String REPORT_GROUP_LOANS_OBLIGATIONS = "Loans and Obligations";
    private static final String REPORT_GROUP_SMART_ANALYSIS = "Smart Analysis";
    private static final String REPORT_GROUP_SYSTEM_REPORTS = "System Reports";
    private static final String DATA_SECTION_INTAKE = "Data Intake";
    private static final String DATA_SECTION_RECORDS_CONTROL = "Records Control";
    private static final String DATA_SECTION_QUALITY = "Data Quality and Reconciliation";
    private static final String DATA_SECTION_AUDIT_HISTORY = "Audit and History";
    private static final String DATA_SECTION_SYNC_RECOVERY = "Sync and Recovery";
    private static final String DATA_SECTION_MAINTENANCE = "Data Maintenance";
    private static final DateTimeFormatter MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private String currentViewFileName;
    private String currentViewTitle = "Dashboard";
    private boolean currentViewIsDashboard = true;
    private String previousViewFileName;
    private String previousViewTitle = "Dashboard";
    private boolean previousViewIsDashboard = true;
    private boolean navigatingBack;
    private boolean passwordChangeNoticeShown;
    private boolean openingSetupSection;
    private boolean openingReportGroup;
    private boolean openingDataRecordsSection;
    private Button pendingNavigationButton;

    @FXML
    public void initialize() {
        configureUserSecurityHeader();
        DataRefreshBus.addListener(this::refreshDashboard);
        NavigationBus.onAccountHistoryRequested(this::showAccountHistory);
        NavigationBus.onAccountReconciliationRequested(this::showAccountReconciliation);
        NavigationBus.onBackRequested(this::goBack);
        NavigationBus.onReportTitleChanged(reportType -> sectionTitleLabel.setText(reportTitle(reportType)));
        NavigationBus.onTransactionEntryRequested(title -> loadView("Expenses.fxml", title));
        NavigationBus.onLoanRepaymentRequested(this::showRecordRepayment);
        NavigationBus.onLoanLedgerRequested(this::showLoanRecords);
        NavigationBus.onGoalContributionRequested(this::showGoalContribution);
        NavigationBus.onGoalProjectRequested(() -> loadView("GoalProject.fxml", "Turn Goal Into Project"));
        NavigationBus.onGoalStepsRequested(this::showGoalSteps);
        NavigationBus.onAssetRegistrationRequested(this::showRegisterAsset);
        NavigationBus.onCoreWorkspaceRequested(this::openCoreWorkspaceRoute);
        NavigationBus.onSmartNavigationRequested(this::openSmartNavigationTarget);
        configureSidebarNavigationState();
        configureDashboardTable();
        showHome();
    }

    @FXML
    private void showHome() {
        if (redirectToPasswordChangeIfRequired("Dashboard.fxml")) {
            return;
        }
        Button navigationButton = consumePendingNavigationButton();
        markNavigationButton(navigationButton == null ? dashboardButton : navigationButton);
        rememberCurrentView();
        sectionTitleLabel.setText("Dashboard");
        setDashboardSummaryVisible(true);
        contentPane.getChildren().clear();
        currentViewFileName = null;
        currentViewTitle = "Dashboard";
        currentViewIsDashboard = true;
        refreshDashboard();
    }

    @FXML
    private void showDashboardSummary() {
        showHome();
    }

    @FXML
    private void showDashboardRecentTransactions() {
        loadView("Transactions.fxml", "Recent Transactions");
    }

    @FXML
    private void showAiCenter() {
        showSmartOverview();
    }

    @FXML
    private void showSmartOverview() {
        openSmartAnalysis(SmartAnalysisMode.OVERVIEW, "Smart Analysis Overview");
    }

    @FXML
    private void showFinancialHealthAnalysis() {
        openSmartAnalysis(SmartAnalysisMode.FINANCIAL_HEALTH, "Financial Health Analysis");
    }

    @FXML
    private void showReportsTrendAnalysis() {
        openSmartAnalysis(SmartAnalysisMode.REPORTS_TRENDS, "Reports and Trends Analysis");
    }

    @FXML
    private void showBudgetForecastAnalysis() {
        openSmartAnalysis(SmartAnalysisMode.BUDGET_FORECAST, "Budget and Cash Forecast");
    }

    @FXML
    private void showGoalsProjectsAnalysis() {
        openSmartAnalysis(SmartAnalysisMode.GOALS_PROJECTS, "Goals and Projects Analysis");
    }

    @FXML
    private void showLoansRepaymentsAnalysis() {
        openSmartAnalysis(SmartAnalysisMode.LOANS_REPAYMENTS, "Loans and Repayments Analysis");
    }

    @FXML
    private void showDataQualityAnalysis() {
        openSmartAnalysis(SmartAnalysisMode.DATA_QUALITY, "Data Quality Analysis");
    }

    @FXML
    private void showSmartAssistant() {
        openSmartAnalysis(SmartAnalysisMode.SMART_ASSISTANT, "Ask Smart Assistant");
    }

    private void openSmartAnalysis(SmartAnalysisMode mode, String pageTitle) {
        NavigationBus.requestSmartAnalysisMode(mode);
        loadView("AiCenter.fxml", pageTitle);
    }

    private void openSmartNavigationTarget(SmartNavigationTarget target) {
        switch (target) {
            case ACCOUNT_OVERVIEW -> showAccounts();
            case BUDGETS -> showBudgetOverview();
            case SMART_ANALYSIS_REPORTS -> openSmartAnalysisReports();
            case GOALS -> showGoalsOverview();
            case PROJECTS -> showProjectOverview();
            case LOAN_LEDGER -> showLoanRecords();
            case REPAYMENT_SCHEDULE -> showRepaymentSchedule();
            case DATA_QUALITY_RECORDS -> openDataQualityRecords();
            case TRANSACTION_LEDGER -> showTransactions();
        }
    }

    @FXML
    private void showMyAccount() {
        loadView("MyAccount.fxml", "My Account");
    }

    @FXML
    private void showLoginSecurityHistory() {
        openSecurityAndUsersTab("securityHistoryTab");
    }

    @FXML
    private void showSessionAutoLockSettings() {
        openSecurityAndUsersTab("sessionAutoLockTab");
    }

    @FXML
    private void showUserManagement() {
        if (!UserSession.isSuperAdmin()) {
            UiAlerts.info("Only a super administrator can manage users.");
            return;
        }
        openSecurityAndUsersTab("userManagementTab");
    }

    @FXML
    private void returnToMyWorkspace() {
        MainApp.returnToOwnWorkspace();
    }

    @FXML
    private void logout() {
        MainApp.logout();
    }

    @FXML
    private void showCashFlowChart() {
        showReport("Cash Flow Report", "Cash Flow Report");
    }

    @FXML
    private void showDashboardProjectSpending() {
        showReport("Project Report", "Project Spending Summary");
    }

    @FXML
    private void showAlerts() {
        showHome();
    }

    @FXML
    private void showAccountHistory() {
        Integer selectedAccountId = NavigationBus.selectedAccountId();
        if (selectedAccountId != null) {
            NavigationBus.requestAccountHistory(selectedAccountId);
        }
        loadView("AccountHistory.fxml", "Account Ledger");
    }

    @FXML
    private void showAccounts() {
        NavigationBus.requestAccountsMode("OVERVIEW");
        loadView("Accounts.fxml", "Account Overview");
    }

    @FXML
    private void showAddAccount() {
        NavigationBus.requestAccountsMode("ADD");
        loadView("Accounts.fxml", "Add Account");
    }

    @FXML
    private void showAccountReconciliation() {
        NavigationBus.requestAccountReconciliation(NavigationBus.selectedAccountId());
        loadView("AccountReconciliation.fxml", "Account Reconciliation");
    }

    @FXML
    private void showSavingsGroups() {
        openCommunitySavings(CommunitySavingsMode.OVERVIEW, "Savings Groups");
    }

    @FXML
    private void showCommunitySavingsOverview() {
        openCommunitySavings(CommunitySavingsMode.OVERVIEW, "Savings Groups Overview");
    }

    @FXML
    private void showAddSavingsGroup() {
        openCommunitySavings(CommunitySavingsMode.ADD_GROUP, "Add Savings Group");
    }

    @FXML
    private void showBankNkhonde() {
        openCommunitySavings(CommunitySavingsMode.BANK_NKHONDE, "Bank Nkhonde");
    }

    @FXML
    private void showZipeleganyu() {
        showChipeleganyu();
    }

    @FXML
    private void showChipeleganyu() {
        openCommunitySavings(CommunitySavingsMode.CHIPELEGANYU, "Chipeleganyu");
    }

    @FXML
    private void showCommunityContributions() {
        openCommunitySavings(CommunitySavingsMode.CONTRIBUTIONS, "Savings Group Contributions");
    }

    @FXML
    private void showCommunityLoans() {
        openCommunitySavings(CommunitySavingsMode.LOANS_REPAYMENTS, "Bank Nkhonde Borrowing");
    }

    @FXML
    private void showCommunityPayouts() {
        openCommunitySavings(CommunitySavingsMode.PAYOUTS_SHARE_OUTS, "Savings Group Payouts and Share-outs");
    }

    @FXML
    private void showCommunitySavingsReports() {
        openCommunitySavings(CommunitySavingsMode.HISTORY, "Savings Group Ledger and History");
    }

    private void openCommunitySavings(CommunitySavingsMode mode, String pageTitle) {
        NavigationBus.requestCommunitySavingsMode(mode);
        pendingNavigationButton = communitySavingsButtonFor(mode);
        loadView("CommunitySavings.fxml", pageTitle);
    }

    private void openCoreWorkspaceRoute(CoreWorkspaceRoute route) {
        switch (route) {
            case INCOME_OVERVIEW -> showIncomeOverview();
            case ADD_INCOME -> showIncome();
            case INCOME_RECORDS -> showIncomeRecords();
            case EXPECTED_INCOME -> showExpectedIncome();
            case RECURRING_INCOME -> showRecurringIncome();
            case EXPENSE_OVERVIEW -> showExpenseOverview();
            case RECORD_EXPENSE -> showExpenses();
            case EXPENSE_RECORDS -> showExpenseRecords();
            case PLANNED_RECURRING_EXPENSES -> showPlannedRecurringExpenses();
            case TRANSACTIONS_OVERVIEW -> showTransactionsOverview();
            case TRANSACTION_LEDGER -> showTransactions();
            case TRANSFER_MONEY -> showTransferMoney();
            case SCHEDULED_TRANSFERS -> showScheduledTransfers();
            case CORRECTIONS_REVERSALS -> showCorrectionsReversals();
            case LOAN_OVERVIEW -> showLoanOverview();
            case NEW_LOAN -> showNewLoan();
            case LOAN_RECORDS -> showLoanRecords();
            case RECORD_REPAYMENT -> showRecordRepayment();
            case REPAYMENT_SCHEDULE -> showRepaymentSchedule();
            case LOAN_CONTACTS -> showPeople();
            case GOALS_OVERVIEW -> showGoalsOverview();
            case ADD_GOAL -> showGoals();
            case GOAL_CONTRIBUTIONS -> showGoalContribution();
            case GOAL_STEPS -> showGoalSteps();
            case GOAL_HISTORY -> showGoalHistory();
            case PROJECT_OVERVIEW -> showProjectOverview();
            case ADD_PROJECT -> showProjects();
            case PROJECT_ACTIVITIES -> showProjectActivities();
            case PROJECT_FINANCES -> showProjectFinances();
            case PROJECT_MILESTONES_STATUS -> showProjectMilestonesStatus();
            case PROJECT_HISTORY -> showProjectHistory();
            case ASSET_OVERVIEW -> showAssetOverview();
            case ASSET_REGISTER -> showAssetRecords();
            case ASSET_RECOGNITION -> showAssetRecognition();
            case REGISTER_ASSET -> showRegisterAsset();
            case ASSET_MAINTENANCE -> showAssetMaintenance();
            case ASSET_VALUATION -> showAssetValuation();
            case ASSET_TRANSFER_CUSTODY -> showAssetTransferCustody();
            case ASSET_SALE_DISPOSAL -> showAssetSaleDisposal();
            case ASSET_HISTORY -> showAssetHistory();
        }
    }

    @FXML
    private void showIncomeOverview() {
        openCoreView("IncomeOverview.fxml", "Income Overview", incomeOverviewButton);
    }

    @FXML
    private void showIncome() {
        openCoreView("Income.fxml", "Add Income", addIncomeButton);
    }

    @FXML
    private void showIncomeRecords() {
        openCoreView("IncomeRecords.fxml", "Income Records", incomeRecordsButton);
    }

    @FXML
    private void showExpectedIncome() {
        openCoreView("ExpectedIncome.fxml", "Expected Income", expectedIncomeButton);
    }

    @FXML
    private void showRecurringIncome() {
        openCoreView("RecurringIncome.fxml", "Recurring Income", recurringIncomeButton);
    }

    @FXML
    private void showIncomeReport() {
        showReport("Income Report", "Income Report");
    }

    @FXML
    private void showCategories() {
        openFinancialConfigurationTab("categoriesTab");
    }

    @FXML
    private void showTransactions() {
        NavigationBus.requestTransactionLedgerFilter(null);
        openCoreView("Transactions.fxml", "Transaction Ledger", transactionLedgerButton);
    }

    @FXML
    private void showExpenses() {
        NavigationBus.requestTransactionType("EXPENSE");
        openCoreView("Expenses.fxml", "Record Expense", recordExpenseButton);
    }

    @FXML
    private void showExpenseOverview() {
        openCoreView("ExpenseOverview.fxml", "Expense Overview", expenseOverviewButton);
    }

    @FXML
    private void showExpenseReport() {
        showExpenseRecords();
    }

    @FXML
    private void showExpenseRecords() {
        NavigationBus.requestTransactionLedgerFilter("Expense");
        openCoreView("Transactions.fxml", "Expense Records", expenseRecordsButton);
    }

    @FXML
    private void showPlannedRecurringExpenses() {
        openCoreView("PlannedRecurringExpenses.fxml", "Planned & Recurring Expenses", plannedExpensesButton);
    }

    @FXML
    private void showTransferMoney() {
        openCoreView("TransferMoney.fxml", "Transfer Money", transferMoneyButton);
    }

    @FXML
    private void showScheduledTransfers() {
        openCoreView("ScheduledTransfers.fxml", "Scheduled Transfers", scheduledTransfersButton);
    }

    @FXML
    private void showTransactionsOverview() {
        openCoreView("TransactionsOverview.fxml", "Transactions Overview", transactionsOverviewButton);
    }

    @FXML
    private void showCorrectionsReversals() {
        openCoreView("CorrectionsReversals.fxml", "Corrections & Reversals", correctionsReversalsButton);
    }

    @FXML
    private void showProjects() {
        openCoreView("Projects.fxml", "Add Project", addProjectButton);
    }

    @FXML
    private void showViewProjects() {
        loadView("ProjectList.fxml", "Project Records");
    }

    @FXML
    private void showProjectList() {
        loadView("ProjectList.fxml", "Project Records");
    }

    @FXML
    private void showProjectActivities() {
        openCoreView("ProjectActivities.fxml", "Project Activities", projectActivitiesButton);
    }

    @FXML
    private void showProjectOverview() {
        openCoreView("ProjectOverview.fxml", "Project Overview", projectOverviewButton);
    }

    @FXML
    private void showProjectFinances() {
        openCoreView("ProjectFinances.fxml", "Project Finances", projectFinancesButton);
    }

    @FXML
    private void showProjectMilestonesStatus() {
        openCoreView("ProjectMilestonesStatus.fxml", "Milestones & Status", projectMilestonesStatusButton);
    }

    @FXML
    private void showProjectHistory() {
        openCoreView("ProjectHistoryLifecycle.fxml", "Project History & Lifecycle", projectHistoryButton);
    }

    @FXML
    private void showRegisterAsset() {
        openCoreView("RegisterAsset.fxml", "Register Asset", registerAssetButton);
    }

    @FXML
    private void showAssetRecords() {
        openCoreView("AssetRecords.fxml", "Asset Register", assetRegisterButton);
    }

    @FXML
    private void showAssetOverview() {
        openCoreView("AssetOverview.fxml", "Asset Overview", assetOverviewButton);
    }

    @FXML
    private void showAssetRecognition() {
        openCoreView("AssetRecognition.fxml", "Asset Recognition", assetRecognitionButton);
    }

    @FXML
    private void showAssetMaintenance() {
        openCoreView("AssetMaintenance.fxml", "Maintenance & Condition", assetMaintenanceButton);
    }

    @FXML
    private void showAssetValuation() {
        openCoreView("AssetValuation.fxml", "Valuation & Depreciation", assetValuationButton);
    }

    @FXML
    private void showAssetTransferCustody() {
        openCoreView("AssetTransferCustody.fxml", "Transfer & Custody", assetTransferCustodyButton);
    }

    @FXML
    private void showAssetSaleDisposal() {
        openCoreView("AssetSaleDisposal.fxml", "Sale & Disposal", assetSaleDisposalButton);
    }

    @FXML
    private void showAssetHistory() {
        openCoreView("AssetHistory.fxml", "Asset History", assetHistoryButton);
    }

    @FXML
    private void showPeople() {
        openCoreView("People.fxml", "Loan Contacts", loanContactsButton);
    }

    @FXML
    private void showNewLoan() {
        openCoreView("NewLoan.fxml", "New Loan", newLoanButton);
    }

    @FXML
    private void showRecordRepayment() {
        openCoreView("LoanRepayment.fxml", "Record Repayment", recordRepaymentButton);
    }

    @FXML
    private void showLoanLedger() {
        showLoanRecords();
    }

    @FXML
    private void showLoanOverview() {
        openCoreView("LoanOverview.fxml", "Loan Overview", loanOverviewButton);
    }

    @FXML
    private void showLoanRecords() {
        openCoreView("LoanLedger.fxml", "Loan Records", loanRecordsButton);
    }

    @FXML
    private void showRepaymentSchedule() {
        openCoreView("LoanRepaymentSchedule.fxml", "Repayment Schedule", repaymentScheduleButton);
    }

    @FXML
    private void showGoals() {
        openCoreView("Goals.fxml", "Add Goal", addGoalButton);
    }

    @FXML
    private void showGoalRecords() {
        loadView("GoalRecords.fxml", "Goal Records");
    }

    @FXML
    private void showGoalContribution() {
        openCoreView("GoalContribution.fxml", "Goal Contributions", goalContributionsButton);
    }

    @FXML
    private void showGoalProgress() {
        loadView("Goals.fxml", "Goal Progress");
    }

    @FXML
    private void showGoalStatus() {
        loadView("Goals.fxml", "Goal Status");
    }

    @FXML
    private void showGoalProject() {
        loadView("GoalProject.fxml", "Turn Goal Into Project");
    }

    @FXML
    private void showGoalSteps() {
        openCoreView("GoalSteps.fxml", "Goal Steps", goalStepsButton);
    }

    @FXML
    private void showGoalsOverview() {
        openCoreView("GoalsOverview.fxml", "Goals Overview", goalsOverviewButton);
    }

    @FXML
    private void showGoalHistory() {
        openCoreView("GoalHistoryLifecycle.fxml", "Goal History & Lifecycle", goalHistoryButton);
    }

    @FXML
    private void showBudgets() {
        showBudgetOverview();
    }

    @FXML
    private void showBudgetOverview() {
        openBudgetMode(BudgetMode.OVERVIEW, "Budget Overview");
    }

    @FXML
    private void showCreateBudget() {
        openBudgetMode(BudgetMode.CREATE, "Create Budget");
    }

    @FXML
    private void showBudgetAllocations() {
        openBudgetMode(BudgetMode.ALLOCATIONS, "Category Allocations");
    }

    @FXML
    private void showBudgetPerformance() {
        openBudgetMode(BudgetMode.PERFORMANCE, "Performance & Variance");
    }

    @FXML
    private void showHouseholdBudget() {
        openBudgetMode(BudgetMode.HOUSEHOLD, "Household Budget");
    }

    @FXML
    private void showBudgetHistory() {
        openBudgetMode(BudgetMode.HISTORY, "Budget History & Lifecycle");
    }

    private void openBudgetMode(BudgetMode mode, String pageTitle) {
        NavigationBus.requestBudgetMode(mode);
        pendingNavigationButton = budgetButtonFor(mode);
        loadView("Budgets.fxml", pageTitle);
    }

    @FXML
    private void showSettings() {
        openAdministration();
    }

    @FXML
    private void openAdministration() {
        openSetupSection("SetupAdministration.fxml", "Administration", setupAdministrationButton, null);
    }

    private void openAdministrationTab(String tabKey) {
        openSetupSection("SetupAdministration.fxml", "Administration", setupAdministrationButton, tabKey);
    }

    @FXML
    private void showAdministration() {
        openAdministration();
    }

    @FXML
    private void showSystemHealth() {
        openAdministrationTab("systemHealthTab");
    }

    @FXML
    private void showWorkspaceManagement() {
        openAdministrationTab("workspaceManagementTab");
    }

    @FXML
    private void openSecurityAndUsers() {
        openSetupSection("SetupSecurityUsers.fxml", "Security and Users", setupSecurityUsersButton, null);
    }

    private void openSecurityAndUsersTab(String tabKey) {
        openSetupSection("SetupSecurityUsers.fxml", "Security and Users", setupSecurityUsersButton, tabKey);
    }

    @FXML
    private void openFinancialConfiguration() {
        openSetupSection("SetupFinancialConfiguration.fxml", "Financial Configuration", setupFinancialConfigurationButton, null);
    }

    private void openFinancialConfigurationTab(String tabKey) {
        openSetupSection("SetupFinancialConfiguration.fxml", "Financial Configuration", setupFinancialConfigurationButton, tabKey);
    }

    @FXML
    private void openSmartIntelligence() {
        openSetupSection("SetupSmartIntelligence.fxml", "Smart Intelligence", setupSmartIntelligenceButton, null);
    }

    private void openSmartIntelligenceTab(String tabKey) {
        openSetupSection("SetupSmartIntelligence.fxml", "Smart Intelligence", setupSmartIntelligenceButton, tabKey);
    }

    @FXML
    private void openDataAndSystem() {
        openSetupSection("SetupDataSystem.fxml", "Data and System", setupDataSystemButton, null);
    }

    private void openDataAndSystemTab(String tabKey) {
        openSetupSection("SetupDataSystem.fxml", "Data and System", setupDataSystemButton, tabKey);
    }

    @FXML
    private void showReports() {
        openOverviewReports();
    }

    @FXML
    private void openOverviewReports() {
        openReportGroup(REPORT_GROUP_OVERVIEW, "Monthly Summary", reportOverviewButton);
    }

    @FXML
    private void openIncomeExpenseReports() {
        openReportGroup(REPORT_GROUP_INCOME_EXPENSES, "Income Report", reportIncomeExpensesButton);
    }

    @FXML
    private void openAccountsPositionReports() {
        openReportGroup(REPORT_GROUP_ACCOUNTS_POSITION, "Account Balance Report", reportAccountsPositionButton);
    }

    @FXML
    private void openProjectsGoalsReports() {
        openReportGroup(REPORT_GROUP_PROJECTS_GOALS, "Project Report", reportProjectsGoalsButton);
    }

    @FXML
    private void openLoansObligationsReports() {
        openReportGroup(REPORT_GROUP_LOANS_OBLIGATIONS, "Loan Report", reportLoansObligationsButton);
    }

    @FXML
    private void openSmartAnalysisReports() {
        openReportGroup(REPORT_GROUP_SMART_ANALYSIS, "Trends and Forecast", reportSmartAnalysisButton);
    }

    @FXML
    private void openSystemReports() {
        openReportGroup(REPORT_GROUP_SYSTEM_REPORTS, "Data Quality Report", reportSystemReportsButton);
    }

    @FXML
    private void showMonthlySummaryReport() {
        showReport("Monthly Summary", "Monthly Summary");
    }

    @FXML
    private void showQuarterlySummaryReport() {
        showReport("Quarterly Summary", "Quarterly Summary");
    }

    @FXML
    private void showHalfYearSummaryReport() {
        showReport("Half-Year Summary", "Half-Year Summary");
    }

    @FXML
    private void showAnnualSummaryReport() {
        showReport("Annual Summary", "Annual Summary");
    }

    @FXML
    private void showYearToYearReport() {
        showReport("Year-to-Year Comparison", "Year-to-Year Comparison");
    }

    @FXML
    private void showIncomeSourceAnalysisReport() {
        showReport("Income Source Analysis", "Income Source Analysis");
    }

    @FXML
    private void showCashFlowReportScreen() {
        showReport("Cash Flow Report", "Cash Flow Report");
    }

    @FXML
    private void showCategorySpendingReport() {
        showReport("Category Spending", "Category Spending");
    }

    @FXML
    private void showBudgetVsActualReport() {
        showReport("Budget vs Actual", "Budget vs Actual");
    }

    @FXML
    private void showRecurringTransactionsReport() {
        showReport("Recurring Transactions", "Recurring Transactions");
    }

    @FXML
    private void showNetWorthReport() {
        showReport("Net Worth Report", "Net Worth Report");
    }

    @FXML
    private void showFinancialPositionReport() {
        showReport("Financial Position", "Financial Position");
    }

    @FXML
    private void showAccountReconciliationReport() {
        showReport("Account Reconciliation", "Account Reconciliation");
    }

    @FXML
    private void showTransferReportScreen() {
        showReport("Transfer Report", "Transfer Report");
    }

    @FXML
    private void showProjectReport() {
        showReport("Project Report", "Project Report");
    }

    @FXML
    private void showProjectPerformanceReport() {
        showReport("Project Performance", "Project Performance");
    }

    @FXML
    private void showSavingsGoalsReport() {
        showReport("Savings and Goals Progress", "Savings And Goals Progress");
    }

    @FXML
    private void showAccountReport() {
        showReport("Account Balance Report", "Account Report");
    }

    @FXML
    private void showLendingReport() {
        showReport("Loan Report", "Loan Report");
    }

    @FXML
    private void showMoneyBorrowedReport() {
        showReport("Money Borrowed Report", "Money Borrowed Report");
    }

    @FXML
    private void showMoneyLentReport() {
        showReport("Money Lent Report", "Money Lent Report");
    }

    @FXML
    private void showDebtAgingReport() {
        showReport("Debt Aging Report", "Debt Aging Report");
    }

    @FXML
    private void showUpcomingObligationsReport() {
        showReport("Upcoming Obligations", "Upcoming Obligations");
    }

    @FXML
    private void showTrendsForecastReport() {
        showReport("Trends and Forecast", "Trends And Forecast");
    }

    @FXML
    private void showExpenseTrendReport() {
        showReport("Expense Trend Report", "Expense Trend Report");
    }

    @FXML
    private void showFinancialHealthReport() {
        showReport("Financial Health", "Financial Health");
    }

    @FXML
    private void showUnusualTransactionsReport() {
        showReport("Unusual Transactions", "Unusual Transactions");
    }

    @FXML
    private void showRecommendationsReport() {
        showReport("Recommendations", "Recommendations");
    }

    @FXML
    private void showDataQualityReport() {
        showReport("Data Quality Report", "Data Quality Report");
    }

    @FXML
    private void showAuditTrailReport() {
        showReport("Audit Trail", "Audit Trail");
    }

    @FXML
    private void showBackupHistoryReport() {
        showReport("Backup and Restore History", "Backup And Restore History");
    }

    @FXML
    private void openDataIntake() {
        openDataRecordsSection("DataRecordsDataIntake.fxml", DATA_SECTION_INTAKE, dataIntakeButton, null);
    }

    private void openDataIntakeTab(String tabKey) {
        openDataRecordsSection("DataRecordsDataIntake.fxml", DATA_SECTION_INTAKE, dataIntakeButton, tabKey);
    }

    @FXML
    private void openRecordsControl() {
        openDataRecordsSection("DataRecordsRecordsControl.fxml", DATA_SECTION_RECORDS_CONTROL, recordsControlButton, null);
    }

    private void openRecordsControlTab(String tabKey) {
        openDataRecordsSection("DataRecordsRecordsControl.fxml", DATA_SECTION_RECORDS_CONTROL, recordsControlButton, tabKey);
    }

    @FXML
    private void openDataQualityRecords() {
        openDataRecordsSection("DataRecordsQuality.fxml", DATA_SECTION_QUALITY, dataQualityRecordsButton, null);
    }

    private void openDataQualityRecordsTab(String tabKey) {
        openDataRecordsSection("DataRecordsQuality.fxml", DATA_SECTION_QUALITY, dataQualityRecordsButton, tabKey);
    }

    @FXML
    private void openAuditHistory() {
        openDataRecordsSection("DataRecordsAuditHistory.fxml", DATA_SECTION_AUDIT_HISTORY, auditHistoryButton, null);
    }

    private void openAuditHistoryTab(String tabKey) {
        openDataRecordsSection("DataRecordsAuditHistory.fxml", DATA_SECTION_AUDIT_HISTORY, auditHistoryButton, tabKey);
    }

    @FXML
    private void openSyncRecovery() {
        openDataRecordsSection("DataRecordsSyncRecovery.fxml", DATA_SECTION_SYNC_RECOVERY, syncRecoveryButton, null);
    }

    private void openSyncRecoveryTab(String tabKey) {
        openDataRecordsSection("DataRecordsSyncRecovery.fxml", DATA_SECTION_SYNC_RECOVERY, syncRecoveryButton, tabKey);
    }

    @FXML
    private void openDataMaintenance() {
        if (!UserSession.isSuperAdmin()) {
            UiAlerts.info("Only a Super Administrator may open Data Maintenance.");
            return;
        }
        openDataRecordsSection("DataRecordsMaintenance.fxml", DATA_SECTION_MAINTENANCE, dataMaintenanceButton, null);
    }

    private void openDataMaintenanceTab(String tabKey) {
        if (!UserSession.isSuperAdmin()) {
            UiAlerts.info("Only a Super Administrator may open Data Maintenance.");
            return;
        }
        openDataRecordsSection("DataRecordsMaintenance.fxml", DATA_SECTION_MAINTENANCE, dataMaintenanceButton, tabKey);
    }

    @FXML
    private void showPaymentMethods() {
        openFinancialConfigurationTab("paymentMethodsTab");
    }

    @FXML
    private void showCurrencies() {
        openFinancialConfigurationTab("currenciesTab");
    }

    @FXML
    private void showBackupRestore() {
        openDataAndSystemTab("backupRestoreTab");
    }

    @FXML
    private void showAiConfiguration() {
        openSmartIntelligenceTab("aiConfigurationTab");
    }

    @FXML
    private void showSmartRules() {
        openSmartIntelligenceTab("smartRulesTab");
    }

    @FXML
    private void showFinancialProfile() {
        openFinancialConfigurationTab("financialProfileTab");
    }

    @FXML
    private void showAlertsNotifications() {
        openSmartIntelligenceTab("alertsTab");
    }

    @FXML
    private void showAutomationSchedules() {
        openSmartIntelligenceTab("automationTab");
    }

    @FXML
    private void showDataQualityReconciliation() {
        openDataQualityRecordsTab("dataHealthOverviewTab");
    }

    @FXML
    private void showImportExport() {
        openDataIntakeTab("fileImportTab");
    }

    @FXML
    private void showArchiveRestore() {
        openDataAndSystemTab("archiveRestoreTab");
    }

    @FXML
    private void showDatabaseMaintenance() {
        openDataMaintenanceTab("recordDisposalTab");
    }

    @FXML
    private void showReportPreferences() {
        openFinancialConfigurationTab("reportPreferencesTab");
    }

    @FXML
    private void showDangerZone() {
        openDataMaintenanceTab("recordDisposalTab");
    }

    @FXML
    private void showAuditLogs() {
        openAuditHistoryTab("activityAuditTab");
    }

    @FXML
    private void showReportInputs() {
        openDataIntakeTab("supplementaryInputsTab");
    }

    @FXML
    private void showSyncCenter() {
        openSyncRecoveryTab("syncStatusTab");
    }

    @FXML
    private void showMaintenance() {
        openDataMaintenanceTab("maintenanceHistoryTab");
    }

    private void configureUserSecurityHeader() {
        SystemUser signedIn = UserSession.getAuthenticatedUser();
        SystemUser workspace = UserSession.getWorkspaceUser();
        signedInUserLabel.setText("Signed in: " + signedIn.getDisplayName());
        activeWorkspaceLabel.setText("Workspace: " + workspace.getUsername());
        boolean superAdmin = signedIn.isSuperAdmin();
        if (dataMaintenanceButton != null) {
            dataMaintenanceButton.setVisible(superAdmin);
            dataMaintenanceButton.setManaged(superAdmin);
            if (!dataMaintenanceButton.getStyleClass().contains("danger-nav-button")) {
                dataMaintenanceButton.getStyleClass().add("danger-nav-button");
            }
        }
        boolean anotherWorkspace = superAdmin && !UserSession.isViewingOwnWorkspace();
        returnWorkspaceButton.setVisible(anotherWorkspace);
        returnWorkspaceButton.setManaged(anotherWorkspace);
    }

    @FXML
    private void refreshDashboard() {
        DashboardStats stats = database.getDashboardStats();
        List<FinanceTransaction> transactions = database.listRecentTransactions(500);
        List<FinanceTransaction> currentMonthTransactions = transactions.stream()
                .filter(this::isCurrentMonth)
                .toList();
        long incomeRecords = currentMonthTransactions.stream()
                .filter(transaction -> "INCOME".equals(transaction.getTransactionType()))
                .count();
        long expenseRecords = currentMonthTransactions.stream()
                .filter(transaction -> "EXPENSE".equals(transaction.getTransactionType()))
                .count();
        double savingsRate = stats.getMonthlyIncome() == 0 ? 0 : (stats.getMonthlySavings() / stats.getMonthlyIncome()) * 100;
        String baseCurrency = database.getBaseCurrencyCode();
        SavingsGroupOverview savingsGroups = database.getSavingsGroupOverview();
        DashboardFxBalance convertedBalance = dashboardFxBalance(baseCurrency);
        double communitySavingsBalance = database.getCommunitySavingsBalance();
        totalBalanceLabel.setText(MoneyUtil.format(baseCurrency, convertedBalance.total()));
        incomeLabel.setText(MoneyUtil.format(baseCurrency, stats.getMonthlyIncome()));
        expensesLabel.setText(MoneyUtil.format(baseCurrency, stats.getMonthlyExpenses()));
        savingsLabel.setText(MoneyUtil.format(baseCurrency, stats.getMonthlySavings()));
        balanceDetailLabel.setText(convertedBalance.detail());
        incomeDetailLabel.setText(incomeRecords + " income records");
        expensesDetailLabel.setText(expenseRecords + " expense records");
        savingsDetailLabel.setText(String.format("%.1f%% savings rate", savingsRate));
        activeAccountsLabel.setText(String.valueOf(stats.getActiveAccounts()));
        activeProjectsLabel.setText(String.valueOf(stats.getActiveProjects()));
        activeGoalsLabel.setText(String.valueOf(stats.getActiveGoals()));
        moneyGivenLabel.setText(MoneyUtil.format(baseCurrency, stats.getMoneyGivenOut()));
        communitySavingsBalanceLabel.setText(MoneyUtil.format(baseCurrency, communitySavingsBalance));
        communitySavingsDetailLabel.setText("Not included in available cash");
        savingsGroupContributionsLabel.setText(MoneyUtil.format(baseCurrency, savingsGroups.contributionsThisMonth()));
        savingsGroupContributionsDetailLabel.setText("This year: " + MoneyUtil.format(baseCurrency, savingsGroups.contributionsThisYear()));
        savingsGroupNextContributionLabel.setText(blankToDashboard(savingsGroups.nextContributionDueDate()));
        savingsGroupNextContributionDetailLabel.setText(savingsGroups.activeSavingsAccounts() + " active Savings Group(s)");
        savingsGroupExpectedPayoutLabel.setText(MoneyUtil.format(baseCurrency, savingsGroups.expectedPayout()));
        savingsGroupExpectedPayoutDetailLabel.setText(savingsGroups.cyclesNearingCompletion() + " cycle(s) nearing completion");
        refreshDashboardCharts(stats, transactions);
        refreshDashboardTable(transactions);
        refreshPlanSnapshot(stats, savingsGroups, baseCurrency);
        refreshAlerts(stats, baseCurrency);
    }

    private DashboardFxBalance dashboardFxBalance(String baseCurrency) {
        BigDecimal total = BigDecimal.ZERO;
        int converted = 0;
        int missing = 0;
        boolean cached = false;
        Set<String> currencies = new LinkedHashSet<>();
        for (Account account : database.listAccounts()) {
            if (!"ACTIVE".equalsIgnoreCase(account.getStatus()) || account.isLiabilityAccount()) {
                continue;
            }
            String currency = account.getCurrency() == null || account.getCurrency().isBlank()
                    ? baseCurrency
                    : account.getCurrency().trim().toUpperCase(Locale.ENGLISH);
            currencies.add(currency);
            BigDecimal balance = BigDecimal.valueOf(account.getCurrentBalance());
            if (currency.equalsIgnoreCase(baseCurrency)) {
                total = total.add(balance);
                converted++;
                continue;
            }
            try {
                ConversionResult result = exchangeRateService.convertUsingLastKnown(balance, currency, baseCurrency);
                total = total.add(result.convertedAmount());
                cached = cached || result.quote().stale();
                converted++;
            } catch (RuntimeException exception) {
                missing++;
            }
        }
        String detail = "Across " + converted + " account(s) in " + currencies.size() + " currenc" + (currencies.size() == 1 ? "y" : "ies");
        if (missing > 0) {
            detail += ". Some balances could not be converted because an exchange rate is unavailable.";
        } else if (cached) {
            detail += ". Using saved exchange rates.";
        }
        return new DashboardFxBalance(total, detail);
    }

    private record DashboardFxBalance(BigDecimal total, String detail) {
    }

    private void configureDashboardTable() {
        if (dashboardTransactionsTable == null || dashboardDateColumn == null) {
            return;
        }
        dashboardDateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        dashboardTypeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionType"));
        dashboardAccountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        dashboardCategoryColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        dashboardAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        TableActions.configureScrollableTable(dashboardTransactionsTable);
        TableActions.installRowContextMenu(dashboardTransactionsTable, this::dashboardTransactionMenuItems);
    }

    private List<javafx.scene.control.MenuItem> dashboardTransactionMenuItems(FinanceTransaction transaction) {
        return List.of(
                TableActions.menuItem("View Transaction", () -> viewDashboardTransaction(transaction)),
                TableActions.menuItem("Open Full Ledger", this::showTransactions),
                TableActions.separator(),
                TableActions.copyRowItem(dashboardTransactionsTable, transaction),
                TableActions.exportTableItem(dashboardTransactionsTable, "Dashboard Recent Transactions"),
                TableActions.printTableItem(dashboardTransactionsTable, "Dashboard Recent Transactions"),
                TableActions.refreshItem(this::refreshDashboard)
        );
    }

    private void viewDashboardTransaction(FinanceTransaction transaction) {
        if (transaction == null) {
            return;
        }
        UiAlerts.info(
                "Date: " + transaction.getTransactionDate()
                        + "\nType: " + transaction.getTransactionType()
                        + "\nAccount: " + transaction.getAccountName()
                        + "\nCategory: " + labelOrDefault(transaction.getCategoryName(), "-")
                        + "\nAmount: " + MoneyUtil.mwk(transaction.getAmount())
                        + "\nStatus: " + labelOrDefault(transaction.getTransactionStatus(), "-")
                        + "\nDescription: " + labelOrDefault(transaction.getDescription(), "-")
        );
    }

    private void refreshDashboardCharts(DashboardStats stats, List<FinanceTransaction> transactions) {
        if (expenseDistributionChart != null) {
            setPieData(expenseDistributionChart, database.categorySpendingReport(), "No expenses");
        }
        if (accountBalanceChart != null) {
            setPieData(accountBalanceChart, database.accountBalanceReport(), "No balances");
        }
        if (incomeSourceChart != null) {
            setPieData(incomeSourceChart, incomeSourceRows(transactions), "No income");
        }
        if (moneyPositionChart != null) {
            setPieData(moneyPositionChart, moneyPositionRows(stats), "No money position");
        }
        if (cashFlowChart != null) {
            refreshCashFlowChart(transactions);
        }
        if (projectSpendingChart != null) {
            refreshProjectSpendingChart();
        }
    }

    private void setPieData(PieChart chart, List<ReportRow> rows, String emptyLabel) {
        List<PieChart.Data> data = rows.stream()
                .filter(row -> row.getAmount() > 0)
                .map(row -> new PieChart.Data(row.getLabel(), row.getAmount()))
                .toList();
        if (data.isEmpty()) {
            data = List.of(new PieChart.Data(emptyLabel, 1));
        }
        chart.setData(FXCollections.observableArrayList(data));
    }

    private List<ReportRow> incomeSourceRows(List<FinanceTransaction> transactions) {
        return transactions.stream()
                .filter(transaction -> "INCOME".equals(transaction.getTransactionType()))
                .collect(Collectors.groupingBy(
                        transaction -> labelOrDefault(transaction.getCategoryName(), "Other"),
                        LinkedHashMap::new,
                        Collectors.summingDouble(FinanceTransaction::getAmount)
                ))
                .entrySet()
                .stream()
                .map(entry -> new ReportRow(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<ReportRow> moneyPositionRows(DashboardStats stats) {
        List<ReportRow> rows = new ArrayList<>();
        rows.add(new ReportRow("Available Cash and Bank", Math.max(database.getAvailableCashAndBankBalance(), 0)));
        rows.add(new ReportRow("Community Savings", Math.max(database.getCommunitySavingsBalance(), 0)));
        rows.add(new ReportRow("Expenses", Math.max(stats.getMonthlyExpenses(), 0)));
        rows.add(new ReportRow("Savings", Math.max(stats.getMonthlySavings(), 0)));
        rows.add(new ReportRow("Loans Receivable", Math.max(stats.getMoneyGivenOut(), 0)));
        return rows;
    }

    private String blankToDashboard(String value) {
        return value == null || value.isBlank() ? "None scheduled" : value;
    }

    private void refreshCashFlowChart(List<FinanceTransaction> transactions) {
        if (cashFlowChart == null) {
            return;
        }
        cashFlowChart.getData().clear();
        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("Income");
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Expenses");
        Map<YearMonth, double[]> totals = new LinkedHashMap<>();
        YearMonth start = YearMonth.now().minusMonths(5);
        for (int index = 0; index < 6; index++) {
            totals.put(start.plusMonths(index), new double[]{0, 0});
        }
        for (FinanceTransaction transaction : transactions) {
            YearMonth month = parseMonth(transaction.getTransactionDate());
            if (month == null || !totals.containsKey(month)) {
                continue;
            }
            double[] monthTotals = totals.get(month);
            if ("INCOME".equals(transaction.getTransactionType())) {
                monthTotals[0] += transaction.getAmount();
            } else if ("EXPENSE".equals(transaction.getTransactionType())) {
                monthTotals[1] += transaction.getAmount();
            }
        }
        for (Map.Entry<YearMonth, double[]> entry : totals.entrySet()) {
            String label = entry.getKey().atDay(1).format(MONTH_LABEL_FORMAT);
            incomeSeries.getData().add(new XYChart.Data<>(label, entry.getValue()[0]));
            expenseSeries.getData().add(new XYChart.Data<>(label, entry.getValue()[1]));
        }
        cashFlowChart.getData().addAll(incomeSeries, expenseSeries);
    }

    private void refreshProjectSpendingChart() {
        if (projectSpendingChart == null) {
            return;
        }
        projectSpendingChart.getData().clear();
        XYChart.Series<String, Number> budgetSeries = new XYChart.Series<>();
        budgetSeries.setName("Budget");
        XYChart.Series<String, Number> spentSeries = new XYChart.Series<>();
        spentSeries.setName("Spent");
        for (Project project : database.listProjects()) {
            budgetSeries.getData().add(new XYChart.Data<>(project.getProjectName(), project.getPlannedBudget()));
            spentSeries.getData().add(new XYChart.Data<>(project.getProjectName(), project.getAmountSpent()));
        }
        projectSpendingChart.getData().addAll(budgetSeries, spentSeries);
    }

    private void refreshDashboardTable(List<FinanceTransaction> transactions) {
        if (dashboardTransactionsTable == null) {
            return;
        }
        dashboardTransactionsTable.setItems(FXCollections.observableArrayList(transactions.stream().limit(10).toList()));
    }

    private void refreshPlanSnapshot(DashboardStats stats, SavingsGroupOverview savingsGroups, String baseCurrency) {
        if (planSnapshotBox == null) {
            return;
        }
        planSnapshotBox.getChildren().clear();
        planSnapshotBox.getChildren().add(snapshotLine("Accounts", stats.getActiveAccounts() + " active accounts"));
        planSnapshotBox.getChildren().add(snapshotLine("Goals", stats.getActiveGoals() + " active goals"));
        planSnapshotBox.getChildren().add(snapshotLine("Projects", stats.getActiveProjects() + " active projects"));
        planSnapshotBox.getChildren().add(snapshotLine("Savings Groups", savingsGroups.activeSavingsAccounts()
                + " active groups, next due " + blankToDashboard(savingsGroups.nextContributionDueDate())));
        planSnapshotBox.getChildren().add(snapshotLine("Loan Position", MoneyUtil.format(baseCurrency, stats.getMoneyGivenOut())));
    }

    private void refreshAlerts(DashboardStats stats, String baseCurrency) {
        if (alertsBox == null) {
            return;
        }
        alertsBox.getChildren().clear();
        if (stats.getActiveGoals() > 0 && stats.getMonthlySavings() <= 0) {
            alertsBox.getChildren().add(alertLabel("No positive savings recorded this month."));
        }
        for (Project project : database.listProjects()) {
            if (project.getPlannedBudget() > 0 && project.getAmountSpent() / project.getPlannedBudget() >= 0.7) {
                alertsBox.getChildren().add(alertLabel(project.getProjectName() + " has used at least 70% of budget."));
            }
        }
        if (stats.getMoneyGivenOut() > 0) {
            alertsBox.getChildren().add(alertLabel(MoneyUtil.format(baseCurrency, stats.getMoneyGivenOut()) + " is still owed to you."));
        } else if (stats.getMoneyGivenOut() < 0) {
            alertsBox.getChildren().add(alertLabel(MoneyUtil.format(baseCurrency, Math.abs(stats.getMoneyGivenOut())) + " is still owed by you."));
        }
        if (alertsBox.getChildren().isEmpty()) {
            alertsBox.getChildren().add(alertLabel("No urgent reminders."));
        }
    }

    private Label alertLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("alert-line");
        return label;
    }

    private HBox snapshotLine(String title, String detail) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("field-label");
        Label detailLabel = new Label(detail);
        detailLabel.setWrapText(true);
        detailLabel.getStyleClass().add("form-note");
        HBox line = new HBox(10, titleLabel, detailLabel);
        line.getStyleClass().add("dashboard-snapshot-line");
        HBox.setHgrow(detailLabel, Priority.ALWAYS);
        return line;
    }

    private boolean isCurrentMonth(FinanceTransaction transaction) {
        YearMonth month = parseMonth(transaction.getTransactionDate());
        return YearMonth.now().equals(month);
    }

    private YearMonth parseMonth(String date) {
        try {
            return date == null || date.isBlank() ? null : YearMonth.from(LocalDate.parse(date));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String labelOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void showReport(String reportType, String title) {
        String reportGroup = reportGroupForReport(reportType);
        openReportGroup(reportGroup, reportType, reportButtonForGroup(reportGroup), title);
    }

    private String reportTitle(String reportType) {
        if ("Account Balance Report".equals(reportType)) {
            return "Account Report";
        }
        return reportType == null || reportType.isBlank() ? "Reports" : reportType;
    }

    private void openReportGroup(String reportGroup, String selectedReport, Button selectedButton) {
        openReportGroup(reportGroup, selectedReport, selectedButton, reportGroup);
    }

    private void openReportGroup(String reportGroup, String selectedReport, Button selectedButton, String title) {
        if (UserSession.getAuthenticatedUser().isMustChangePassword()) {
            loadView("Reports.fxml", title);
            return;
        }
        NavigationBus.requestReport(reportGroup, selectedReport);
        openingReportGroup = true;
        try {
            loadView("Reports.fxml", title);
            markReportGroupButton(selectedButton);
        } finally {
            openingReportGroup = false;
        }
    }

    private String reportGroupForReport(String reportType) {
        return switch (reportType == null ? "" : reportType) {
            case "Income Report", "Income Source Analysis", "Expense Report",
                 "Category Spending", "Budget vs Actual", "Recurring Transactions",
                 "Expense Trend Report" -> REPORT_GROUP_INCOME_EXPENSES;
            case "Account Balance Report", "Net Worth Report", "Financial Position",
                 "Account Reconciliation", "Transfer Report", "Asset Register",
                 "Asset Valuation", "Asset Disposal" -> REPORT_GROUP_ACCOUNTS_POSITION;
            case "Project Report", "Project Performance", "Savings and Goals Progress" -> REPORT_GROUP_PROJECTS_GOALS;
            case "Loan Report", "Lending Report", "Money Borrowed Report", "Money Lent Report",
                 "Debt Aging Report", "Upcoming Obligations" -> REPORT_GROUP_LOANS_OBLIGATIONS;
            case "Trends and Forecast", "Financial Health", "Unusual Transactions",
                 "Recommendations" -> REPORT_GROUP_SMART_ANALYSIS;
            case "Data Quality Report", "Audit Trail", "Backup and Restore History" -> REPORT_GROUP_SYSTEM_REPORTS;
            default -> REPORT_GROUP_OVERVIEW;
        };
    }

    private Button reportButtonForGroup(String reportGroup) {
        return switch (reportGroup == null ? "" : reportGroup) {
            case REPORT_GROUP_INCOME_EXPENSES -> reportIncomeExpensesButton;
            case REPORT_GROUP_ACCOUNTS_POSITION -> reportAccountsPositionButton;
            case REPORT_GROUP_PROJECTS_GOALS -> reportProjectsGoalsButton;
            case REPORT_GROUP_LOANS_OBLIGATIONS -> reportLoansObligationsButton;
            case REPORT_GROUP_SMART_ANALYSIS -> reportSmartAnalysisButton;
            case REPORT_GROUP_SYSTEM_REPORTS -> reportSystemReportsButton;
            default -> reportOverviewButton;
        };
    }

    private void showLoanTransaction(String title, String transactionType, String purpose) {
        NavigationBus.requestTransaction(transactionType, purpose, null);
        loadView("Expenses.fxml", title);
    }

    private void loadSetupPolicy(String area, String title) {
        loadView("SetupPolicy.fxml", title, area);
    }

    private void openSetupSection(String fileName, String title, Button selectedButton, String tabKey) {
        if (UserSession.getAuthenticatedUser().isMustChangePassword()) {
            loadView(fileName, title);
            return;
        }
        SetupSectionController.rememberTab(title, tabKey);
        openingSetupSection = true;
        try {
            loadView(fileName, title);
            markSetupSectionButton(selectedButton);
        } finally {
            openingSetupSection = false;
        }
    }

    private void openDataRecordsSection(String fileName, String title, Button selectedButton, String tabKey) {
        if (UserSession.getAuthenticatedUser().isMustChangePassword()) {
            loadView(fileName, title);
            return;
        }
        DataRecordsSectionController.rememberTab(title, tabKey);
        openingDataRecordsSection = true;
        try {
            loadView(fileName, title);
            markDataRecordsButton(selectedButton);
        } finally {
            openingDataRecordsSection = false;
        }
    }

    private void openCoreView(String fileName, String title, Button selectedButton) {
        pendingNavigationButton = selectedButton;
        loadView(fileName, title);
    }

    private void loadView(String fileName, String title) {
        loadView(fileName, title, null);
    }

    private void loadView(String fileName, String title, String setupArea) {
        if (redirectToPasswordChangeIfRequired(fileName)) {
            return;
        }
        Button navigationButton = consumePendingNavigationButton();
        if (navigationButton != null) {
            markNavigationButton(navigationButton);
        } else if (!openingSetupSection
                && !openingReportGroup
                && !openingDataRecordsSection
                && setupButtonFor(fileName) == null
                && dataRecordsButtonFor(fileName) == null
                && !"Reports.fxml".equals(fileName)
                && !"CommunitySavings.fxml".equals(fileName)
                && !"Budgets.fxml".equals(fileName)) {
            clearNavigationSelection();
        }
        if (!openingSetupSection) {
            Button setupButton = setupButtonFor(fileName);
            if (setupButton == null) {
                clearSetupSectionSelection();
            } else {
                markSetupSectionButton(setupButton);
            }
        }
        if (!openingReportGroup) {
            if (!"Reports.fxml".equals(fileName)) {
                clearReportGroupSelection();
            }
        }
        if (!openingDataRecordsSection) {
            Button dataRecordsButton = dataRecordsButtonFor(fileName);
            if (dataRecordsButton == null) {
                clearDataRecordsSelection();
            } else {
                markDataRecordsButton(dataRecordsButton);
            }
        }
        if (!"CommunitySavings.fxml".equals(fileName)) {
            clearCommunitySavingsSelection();
        }
        if ("Budgets.fxml".equals(fileName)) {
            boolean hasActiveBudgetButton = budgetButtons().stream()
                    .anyMatch(button -> button != null && button.getStyleClass().contains("active"));
            if (!hasActiveBudgetButton) {
                markBudgetButton(budgetOverviewButton);
            }
        } else {
            clearBudgetSelection();
        }
        try {
            rememberCurrentView();
            sectionTitleLabel.setText(title);
            setDashboardSummaryVisible(false);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/wk/pfmis/views/" + fileName));
            Parent view = loader.load();
            RequiredFieldSupport.apply(view);
            if (setupArea != null && loader.getController() instanceof SetupPolicyController controller) {
                controller.selectArea(setupArea);
            }
            RequiredFieldSupport.apply(view);
            ReadableTextSupport.apply(view);
            Node displayView = unwrapScrollPane(view);
            makeDynamic(displayView);
            contentPane.getChildren().setAll(displayView);
            currentViewFileName = fileName;
            currentViewTitle = title;
            currentViewIsDashboard = false;
            refreshDashboard();
        } catch (IOException | RuntimeException exception) {
            showViewLoadFailure(fileName, title, setupArea, exception);
        }
    }

    private void showViewLoadFailure(String fileName, String title, String setupArea, Exception exception) {
        sectionTitleLabel.setText(title);
        setDashboardSummaryVisible(false);
        Throwable root = rootCause(exception);
        String rootMessage = blankFailureMessage(root);
        database.recordSystemLog(
                "Navigation",
                "View Load Failure",
                "ERROR",
                title + " failed while loading " + fileName + ". Root cause: "
                        + root.getClass().getName() + ": " + rootMessage
        );

        Label heading = new Label(title + " could not be opened.");
        heading.getStyleClass().add("section-heading");

        Label message = new Label("A problem occurred while preparing this screen."
                + System.lineSeparator() + System.lineSeparator()
                + "Reason: " + rootMessage);
        message.setWrapText(true);
        message.getStyleClass().add("form-note");

        TextArea details = new TextArea(failureDetails(exception));
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(8);
        details.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(details, Priority.ALWAYS);
        TitledPane technicalDetails = new TitledPane("Technical details", details);
        technicalDetails.setExpanded(false);
        technicalDetails.setMaxWidth(Double.MAX_VALUE);

        Button retryButton = new Button("Retry");
        retryButton.getStyleClass().add("secondary-button");
        retryButton.setOnAction(event -> loadView(fileName, title, setupArea));

        VBox failurePane = new VBox(10, heading, message, technicalDetails, retryButton);
        failurePane.getStyleClass().add("panel");
        failurePane.setMaxWidth(Double.MAX_VALUE);
        contentPane.getChildren().setAll(failurePane);
        currentViewFileName = fileName;
        currentViewTitle = title;
        currentViewIsDashboard = false;
    }

    private String failureDetails(Exception exception) {
        Throwable root = rootCause(exception);
        String summary = "Failure: " + exception.getClass().getName() + ": " + blankFailureMessage(exception);
        String rootSummary = root == exception
                ? ""
                : System.lineSeparator() + System.lineSeparator()
                + "Underlying failure: " + root.getClass().getName() + ": " + blankFailureMessage(root);
        return summary + rootSummary;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private String blankFailureMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank() ? "No detailed message was provided." : message;
    }

    private void rememberCurrentView() {
        if (navigatingBack) {
            return;
        }
        previousViewFileName = currentViewFileName;
        previousViewTitle = currentViewTitle;
        previousViewIsDashboard = currentViewIsDashboard;
    }

    private void goBack() {
        navigatingBack = true;
        try {
            if (previousViewIsDashboard || previousViewFileName == null) {
                showHome();
            } else {
                loadView(previousViewFileName, previousViewTitle);
            }
        } finally {
            navigatingBack = false;
        }
    }

    private Node unwrapScrollPane(Parent view) {
        if (view instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            Node content = scrollPane.getContent();
            scrollPane.setContent(null);
            return content;
        }
        return view;
    }

    private boolean redirectToPasswordChangeIfRequired(String requestedFileName) {
        if (!UserSession.getAuthenticatedUser().isMustChangePassword()
                || "MyAccount.fxml".equals(requestedFileName)) {
            return false;
        }
        if (!passwordChangeNoticeShown && contentPane.getScene() != null) {
            UiAlerts.info("Change your temporary password before continuing normal work.");
            passwordChangeNoticeShown = true;
        }
        loadView("MyAccount.fxml", "My Account");
        return true;
    }

    private Button setupButtonFor(String fileName) {
        return switch (fileName) {
            case "SetupAdministration.fxml" -> setupAdministrationButton;
            case "SetupSecurityUsers.fxml" -> setupSecurityUsersButton;
            case "SetupFinancialConfiguration.fxml" -> setupFinancialConfigurationButton;
            case "SetupSmartIntelligence.fxml" -> setupSmartIntelligenceButton;
            case "SetupDataSystem.fxml" -> setupDataSystemButton;
            default -> null;
        };
    }

    private Button dataRecordsButtonFor(String fileName) {
        return switch (fileName) {
            case "DataRecordsDataIntake.fxml" -> dataIntakeButton;
            case "DataRecordsRecordsControl.fxml" -> recordsControlButton;
            case "DataRecordsQuality.fxml" -> dataQualityRecordsButton;
            case "DataRecordsAuditHistory.fxml" -> auditHistoryButton;
            case "DataRecordsSyncRecovery.fxml" -> syncRecoveryButton;
            case "DataRecordsMaintenance.fxml" -> dataMaintenanceButton;
            default -> null;
        };
    }

    private Button communitySavingsButtonFor(CommunitySavingsMode mode) {
        return switch (mode == null ? CommunitySavingsMode.OVERVIEW : mode) {
            case OVERVIEW -> savingsOverviewButton;
            case ADD_GROUP -> addSavingsGroupButton;
            case BANK_NKHONDE -> bankNkhondeButton;
            case CHIPELEGANYU -> chipeleganyuButton;
            case CONTRIBUTIONS -> communityContributionsButton;
            case PAYOUTS_SHARE_OUTS -> communityPayoutsButton;
            case HISTORY -> communitySavingsReportsButton;
            case LOANS_REPAYMENTS -> bankNkhondeButton;
        };
    }

    private void markCommunitySavingsButton(Button selectedButton) {
        markNavigationButton(selectedButton);
    }

    private void clearCommunitySavingsSelection() {
        for (Button button : communitySavingsButtons()) {
            if (button != null) {
                button.getStyleClass().remove("active");
            }
        }
        updateNavigationParentState();
    }

    private List<Button> communitySavingsButtons() {
        return List.of(
                savingsOverviewButton,
                addSavingsGroupButton,
                bankNkhondeButton,
                chipeleganyuButton,
                communityContributionsButton,
                communityPayoutsButton,
                communitySavingsReportsButton
        );
    }

    private Button budgetButtonFor(BudgetMode mode) {
        return switch (mode == null ? BudgetMode.OVERVIEW : mode) {
            case OVERVIEW -> budgetOverviewButton;
            case CREATE -> createBudgetButton;
            case ALLOCATIONS -> budgetAllocationsButton;
            case PERFORMANCE -> budgetPerformanceButton;
            case HOUSEHOLD -> householdBudgetButton;
            case HISTORY -> budgetHistoryButton;
        };
    }

    private void markBudgetButton(Button selectedButton) {
        markNavigationButton(selectedButton);
    }

    private void clearBudgetSelection() {
        for (Button button : budgetButtons()) {
            if (button != null) {
                button.getStyleClass().remove("active");
            }
        }
        updateNavigationParentState();
    }

    private List<Button> budgetButtons() {
        return List.of(
                budgetOverviewButton,
                createBudgetButton,
                budgetAllocationsButton,
                budgetPerformanceButton,
                householdBudgetButton,
                budgetHistoryButton
        );
    }

    private void markSetupSectionButton(Button selectedButton) {
        markNavigationButton(selectedButton);
    }

    private void clearSetupSectionSelection() {
        for (Button button : setupSectionButtons()) {
            if (button != null) {
                button.getStyleClass().remove("active");
            }
        }
        updateNavigationParentState();
    }

    private List<Button> setupSectionButtons() {
        return List.of(
                setupAdministrationButton,
                setupSecurityUsersButton,
                setupFinancialConfigurationButton,
                setupSmartIntelligenceButton,
                setupDataSystemButton
        );
    }

    private void markDataRecordsButton(Button selectedButton) {
        markNavigationButton(selectedButton);
    }

    private void clearDataRecordsSelection() {
        for (Button button : dataRecordsButtons()) {
            if (button != null) {
                button.getStyleClass().remove("active");
            }
        }
        updateNavigationParentState();
    }

    private List<Button> dataRecordsButtons() {
        List<Button> buttons = new ArrayList<>(List.of(
                dataIntakeButton,
                recordsControlButton,
                dataQualityRecordsButton,
                auditHistoryButton,
                syncRecoveryButton
        ));
        if (dataMaintenanceButton != null) {
            buttons.add(dataMaintenanceButton);
        }
        return buttons;
    }

    private void markReportGroupButton(Button selectedButton) {
        markNavigationButton(selectedButton);
    }

    private void clearReportGroupSelection() {
        for (Button button : reportGroupButtons()) {
            if (button != null) {
                button.getStyleClass().remove("active");
            }
        }
        updateNavigationParentState();
    }

    private List<Button> reportGroupButtons() {
        return List.of(
                reportOverviewButton,
                reportIncomeExpensesButton,
                reportAccountsPositionButton,
                reportProjectsGoalsButton,
                reportLoansObligationsButton,
                reportSmartAnalysisButton,
                reportSystemReportsButton
        );
    }

    private void configureSidebarNavigationState() {
        if (sidebarNavigation == null) {
            return;
        }
        attachSidebarNavigationState(sidebarNavigation);
    }

    private void attachSidebarNavigationState(Node node) {
        if (node instanceof Button button && isSidebarNavigationButton(button)) {
            button.addEventFilter(ActionEvent.ACTION, event -> pendingNavigationButton = button);
        }
        if (node instanceof TitledPane titledPane && titledPane.getContent() != null) {
            attachSidebarNavigationState(titledPane.getContent());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                attachSidebarNavigationState(child);
            }
        }
    }

    private boolean isSidebarNavigationButton(Button button) {
        return button.getStyleClass().contains("nav-button")
                || button.getStyleClass().contains("nav-sub-button")
                || button.getStyleClass().contains("setup-section-button");
    }

    private Button consumePendingNavigationButton() {
        Button button = pendingNavigationButton;
        pendingNavigationButton = null;
        return button;
    }

    private void markNavigationButton(Button selectedButton) {
        clearNavigationSelection();
        if (selectedButton == null) {
            return;
        }
        if (!selectedButton.getStyleClass().contains("active")) {
            selectedButton.getStyleClass().add("active");
        }
        TitledPane parentPane = parentNavigationPane(selectedButton);
        if (parentPane != null) {
            if (!parentPane.getStyleClass().contains("active-parent")) {
                parentPane.getStyleClass().add("active-parent");
            }
            parentPane.setExpanded(true);
        }
    }

    private void clearNavigationSelection() {
        if (sidebarNavigation == null) {
            return;
        }
        clearNavigationSelection(sidebarNavigation);
    }

    private void clearNavigationSelection(Node node) {
        if (node instanceof Button button) {
            button.getStyleClass().remove("active");
        }
        if (node instanceof TitledPane titledPane) {
            titledPane.getStyleClass().remove("active-parent");
            if (titledPane.getContent() != null) {
                clearNavigationSelection(titledPane.getContent());
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                clearNavigationSelection(child);
            }
        }
    }

    private void updateNavigationParentState() {
        if (sidebarNavigation == null) {
            return;
        }
        updateNavigationParentState(sidebarNavigation);
    }

    private boolean updateNavigationParentState(Node node) {
        boolean activeChild = node instanceof Button button && button.getStyleClass().contains("active");
        if (node instanceof TitledPane titledPane) {
            boolean contentHasActiveChild = titledPane.getContent() != null
                    && updateNavigationParentState(titledPane.getContent());
            titledPane.getStyleClass().remove("active-parent");
            if (contentHasActiveChild) {
                titledPane.getStyleClass().add("active-parent");
            }
            return contentHasActiveChild;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                activeChild = updateNavigationParentState(child) || activeChild;
            }
        }
        return activeChild;
    }

    private TitledPane parentNavigationPane(Node node) {
        Parent parent = node == null ? null : node.getParent();
        while (parent != null) {
            if (parent instanceof TitledPane titledPane) {
                return titledPane;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private void setDashboardSummaryVisible(boolean visible) {
        dashboardSummaryPane.setVisible(visible);
        dashboardSummaryPane.setManaged(visible);
    }

    private void makeDynamic(Node node) {
        if (node instanceof Region region && !isMetricCard(node) && !isLeafControl(node)) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
        }
        if (!isMetricCard(node) && !isLeafControl(node)) {
            if (VBox.getVgrow(node) == null && !(node instanceof HBox)) {
                VBox.setVgrow(node, Priority.ALWAYS);
            }
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        if (node instanceof TableView<?> tableView) {
            TableActions.configureScrollableTable(tableView);
        }

        if (node instanceof GridPane gridPane) {
            configureGrid(gridPane);
        }

        if (node instanceof TitledPane titledPane) {
            makeDynamic(titledPane.getContent());
        }

        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                makeDynamic(child);
            }
        }
    }

    private void configureGrid(GridPane gridPane) {
        int columns = gridPane.getChildren().stream()
                .map(GridPane::getColumnIndex)
                .map(index -> index == null ? 0 : index)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
        if (gridPane.getColumnConstraints().isEmpty() && columns > 0) {
            for (int index = 0; index < columns; index++) {
                ColumnConstraints constraints = new ColumnConstraints();
                constraints.setPercentWidth(100.0 / columns);
                constraints.setHgrow(Priority.ALWAYS);
                constraints.setFillWidth(true);
                gridPane.getColumnConstraints().add(constraints);
            }
        }
        for (Node child : gridPane.getChildren()) {
            if (!isMetricCard(child) && !isLeafControl(child)) {
                GridPane.setHgrow(child, Priority.ALWAYS);
            }
            if (child instanceof Region region && !isMetricCard(child) && !isLeafControl(child)) {
                region.setMaxWidth(Double.MAX_VALUE);
            }
        }
    }

    private boolean isMetricCard(Node node) {
        return node.getStyleClass().contains("metric-card")
                || node.getStyleClass().contains("compact-metric-card")
                || node.getStyleClass().contains("wide-compact-metric-card");
    }

    private boolean isLeafControl(Node node) {
        return node instanceof Button
                || node instanceof CheckBox
                || node instanceof Label
                || node instanceof TextInputControl
                || node instanceof ComboBoxBase<?>;
    }
}
