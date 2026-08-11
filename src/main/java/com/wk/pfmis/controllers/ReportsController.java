package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.AccountReconciliationRecord;
import com.wk.pfmis.models.AiInteractionRecord;
import com.wk.pfmis.models.Asset;
import com.wk.pfmis.models.AssetEvent;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.BudgetProgress;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.LoanScheduleRecord;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectActivity;
import com.wk.pfmis.models.RecurringTransactionPlan;
import com.wk.pfmis.models.ReportInsightRow;
import com.wk.pfmis.models.ReportPositionItem;
import com.wk.pfmis.models.ReportRow;
import com.wk.pfmis.models.ScheduledObligation;
import com.wk.pfmis.models.SystemLogRecord;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ReportsController {
    private static final String ALL_ACCOUNTS = "All accounts";
    private static final String ALL_CATEGORIES = "All categories";
    private static final String ALL_PROJECTS = "All projects";
    private static final String POSTED_TRANSACTIONS = "Posted transactions";
    private static final String ALL_STATUSES = "All statuses";
    private static final int REPORT_TRANSACTION_LIMIT = 100_000;

    @FXML private Label reportGroupTitleLabel;
    @FXML private Label reportGroupDescriptionLabel;
    @FXML private FlowPane quickReportsPane;
    @FXML private ComboBox<String> reportTypeBox;
    @FXML private ComboBox<String> monthBox;
    @FXML private ComboBox<Integer> yearBox;
    @FXML private ComboBox<String> groupingBox;
    @FXML private ComboBox<String> accountFilterBox;
    @FXML private ComboBox<String> categoryFilterBox;
    @FXML private ComboBox<String> projectFilterBox;
    @FXML private ComboBox<String> statusFilterBox;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Label summaryIncomeLabel;
    @FXML private Label summaryExpenseLabel;
    @FXML private Label summarySavingsLabel;
    @FXML private Label summarySavingsRateLabel;
    @FXML private VBox categoryReportPane;
    @FXML private Label categoryReportTitle;
    @FXML private TableView<ReportRow> categoryTable;
    @FXML private TableColumn<ReportRow, String> categoryLabelColumn;
    @FXML private TableColumn<ReportRow, String> categoryAccountColumn;
    @FXML private TableColumn<ReportRow, Double> categoryAmountColumn;
    @FXML private VBox projectReportPane;
    @FXML private Label projectReportTitle;
    @FXML private TableView<ReportRow> projectTable;
    @FXML private TableColumn<ReportRow, String> projectLabelColumn;
    @FXML private TableColumn<ReportRow, Double> projectAmountColumn;
    @FXML private VBox accountReportPane;
    @FXML private Label accountReportTitle;
    @FXML private TableView<ReportRow> accountTable;
    @FXML private TableColumn<ReportRow, String> accountLabelColumn;
    @FXML private TableColumn<ReportRow, Double> accountAmountColumn;
    @FXML private VBox analysisReportPane;
    @FXML private Label analysisReportTitle;
    @FXML private BarChart<String, Number> analysisChart;
    @FXML private TableView<ReportInsightRow> analysisTable;
    @FXML private TableColumn<ReportInsightRow, String> analysisAreaColumn;
    @FXML private TableColumn<ReportInsightRow, String> analysisItemColumn;
    @FXML private TableColumn<ReportInsightRow, Double> analysisAmountColumn;
    @FXML private TableColumn<ReportInsightRow, Double> analysisComparisonColumn;
    @FXML private TableColumn<ReportInsightRow, Double> analysisVarianceColumn;
    @FXML private TableColumn<ReportInsightRow, Double> analysisPercentColumn;
    @FXML private TableColumn<ReportInsightRow, String> analysisStatusColumn;
    @FXML private TableColumn<ReportInsightRow, String> analysisRecommendationColumn;
    @FXML private VBox smartConclusionPane;
    @FXML private Label financialInterpretationLabel;
    @FXML private Label risksIdentifiedLabel;
    @FXML private Label recommendedActionsLabel;
    @FXML private Label confidenceLimitationsLabel;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private ReportGroup activeReportGroup;
    private boolean initializing;

    @FXML
    public void initialize() {
        initializing = true;
        String requestedReportGroup = NavigationBus.consumeRequestedReportGroup();
        String requestedReportType = NavigationBus.consumeRequestedReportType();
        activeReportGroup = reportGroupFor(requestedReportGroup, requestedReportType);
        configureReportGroup(activeReportGroup, requestedReportType);
        monthBox.setItems(FXCollections.observableArrayList(IntStream.rangeClosed(1, 12)
                .mapToObj(month -> java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .toList()));
        monthBox.getSelectionModel().select(LocalDate.now().getMonthValue() - 1);
        int year = Year.now().getValue();
        yearBox.setItems(FXCollections.observableArrayList(IntStream.rangeClosed(year - 5, year + 1).boxed().toList()));
        yearBox.getSelectionModel().select(Integer.valueOf(year));
        groupingBox.setItems(FXCollections.observableArrayList("Daily", "Weekly", "Monthly", "Quarterly", "Annual"));
        groupingBox.getSelectionModel().select("Monthly");
        populateFilters();
        configureTables();
        reportTypeBox.setOnAction(event -> refresh());
        monthBox.setOnAction(event -> refresh());
        yearBox.setOnAction(event -> refresh());
        groupingBox.setOnAction(event -> refresh());
        accountFilterBox.setOnAction(event -> refresh());
        categoryFilterBox.setOnAction(event -> refresh());
        projectFilterBox.setOnAction(event -> refresh());
        statusFilterBox.setOnAction(event -> refresh());
        startDatePicker.setOnAction(event -> refresh());
        endDatePicker.setOnAction(event -> refresh());
        configureContextMenus();
        initializing = false;
        refresh();
    }

    private void configureReportGroup(ReportGroup group, String requestedReportType) {
        reportGroupTitleLabel.setText(group.title());
        reportGroupDescriptionLabel.setText(group.description());
        reportTypeBox.setItems(FXCollections.observableArrayList(group.reportTypes()));
        String selectedReport = requestedReportType != null && group.reportTypes().contains(requestedReportType)
                ? requestedReportType
                : group.defaultReport();
        reportTypeBox.getSelectionModel().select(selectedReport);
        configureQuickReports(group);
    }

    private void configureQuickReports(ReportGroup group) {
        quickReportsPane.getChildren().clear();
        if (group.quickReports().isEmpty()) {
            quickReportsPane.setManaged(false);
            quickReportsPane.setVisible(false);
            return;
        }
        quickReportsPane.setManaged(true);
        quickReportsPane.setVisible(true);
        for (String reportType : group.quickReports()) {
            Button button = new Button(shortQuickReportLabel(reportType));
            button.getStyleClass().add("quick-report-button");
            button.setOnAction(event -> selectQuickReport(reportType));
            quickReportsPane.getChildren().add(button);
        }
    }

    private void selectQuickReport(String reportType) {
        if (!reportTypeBox.getItems().contains(reportType)) {
            return;
        }
        reportTypeBox.getSelectionModel().select(reportType);
        refresh();
    }

    private String shortQuickReportLabel(String reportType) {
        return switch (reportType) {
            case "Income Report" -> "Income";
            case "Expense Report" -> "Expenses";
            case "Budget vs Actual" -> "Budget vs Actual";
            case "Category Spending" -> "Category Spending";
            case "Account Balance Report" -> "Balances";
            case "Net Worth Report" -> "Net Worth";
            case "Asset Register" -> "Assets";
            case "Asset Valuation" -> "Valuation";
            case "Asset Disposal" -> "Disposals";
            case "Project Report" -> "Projects";
            case "Savings and Goals Progress" -> "Goals";
            case "Money Borrowed Report" -> "Borrowed";
            case "Money Lent Report" -> "Lent";
            case "Trends and Forecast" -> "Forecast";
            case "Recommendations" -> "Recommendations";
            case "Data Quality Report" -> "Data Quality";
            case "Audit Trail" -> "Audit Trail";
            default -> reportType;
        };
    }

    private ReportGroup reportGroupFor(String requestedGroup, String requestedReportType) {
        List<ReportGroup> groups = reportGroups();
        if (requestedGroup != null && !requestedGroup.isBlank()) {
            for (ReportGroup group : groups) {
                if (group.title().equalsIgnoreCase(requestedGroup.trim())) {
                    return group;
                }
            }
        }
        if (requestedReportType != null && !requestedReportType.isBlank()) {
            for (ReportGroup group : groups) {
                if (group.reportTypes().contains(requestedReportType)) {
                    return group;
                }
            }
        }
        return groups.getFirst();
    }

    private List<ReportGroup> reportGroups() {
        return List.of(
                new ReportGroup(
                        "Overview",
                        "Generate high-level financial summaries, period comparisons, cash-flow position and financial health reports.",
                        List.of(
                                "Monthly Summary",
                                "Quarterly Summary",
                                "Half-Year Summary",
                                "Annual Summary",
                                "Year-to-Year Comparison",
                                "Financial Health",
                                "Net Worth Report",
                                "Cash Flow Report"
                        ),
                        List.of("Monthly Summary", "Financial Health", "Net Worth Report", "Cash Flow Report")
                ),
                new ReportGroup(
                        "Income and Expenses",
                        "Select and generate income, expense, budget, category, recurring transaction and trend reports.",
                        List.of(
                                "Income Report",
                                "Income Source Analysis",
                                "Expense Report",
                                "Category Spending",
                                "Budget vs Actual",
                                "Recurring Transactions",
                                "Expense Trend Report"
                        ),
                        List.of("Income Report", "Expense Report", "Budget vs Actual", "Category Spending")
                ),
                new ReportGroup(
                        "Accounts and Position",
                        "Review account balances, transfers, reconciliation, asset records, net worth and financial position reports.",
                        List.of(
                                "Account Balance Report",
                                "Account Reconciliation",
                                "Transfer Report",
                                "Asset Register",
                                "Asset Valuation",
                                "Asset Disposal",
                                "Net Worth Report",
                                "Financial Position"
                        ),
                        List.of("Account Balance Report", "Asset Register", "Asset Valuation", "Net Worth Report")
                ),
                new ReportGroup(
                        "Projects and Goals",
                        "Review project spending, project performance and savings or goal progress reports.",
                        List.of(
                                "Project Report",
                                "Project Performance",
                                "Savings and Goals Progress"
                        ),
                        List.of("Project Report", "Project Performance", "Savings and Goals Progress")
                ),
                new ReportGroup(
                        "Loans and Obligations",
                        "Generate loan position, debt aging, money lent, money borrowed and upcoming obligation reports.",
                        List.of(
                                "Loan Report",
                                "Debt Aging Report",
                                "Money Lent Report",
                                "Money Borrowed Report",
                                "Upcoming Obligations"
                        ),
                        List.of("Loan Report", "Money Borrowed Report", "Money Lent Report", "Upcoming Obligations")
                ),
                new ReportGroup(
                        "Smart Analysis",
                        "Generate forecast, financial health, unusual transaction and recommendation reports.",
                        List.of(
                                "Trends and Forecast",
                                "Financial Health",
                                "Unusual Transactions",
                                "Recommendations",
                                "Expense Trend Report"
                        ),
                        List.of("Trends and Forecast", "Financial Health", "Recommendations")
                ),
                new ReportGroup(
                        "System Reports",
                        "Review data quality, audit trail and backup or restore history reports.",
                        List.of(
                                "Data Quality Report",
                                "Audit Trail",
                                "Backup and Restore History"
                        ),
                        List.of("Data Quality Report", "Audit Trail", "Backup and Restore History")
                )
        );
    }

    @FXML
    private void refresh() {
        if (initializing) {
            return;
        }
        refreshSummaryLine();
        clearAnalysis();
        switch (reportTypeBox.getValue()) {
            case "Income Report" -> refreshIncomeReport();
            case "Expense Report" -> refreshExpenseReport();
            case "Project Report" -> refreshProjectReport();
            case "Account Balance Report" -> refreshAccountReport();
            case "Loan Report", "Lending Report" -> refreshLoanReport();
            case "Quarterly Summary" -> refreshAnalyticalOnly(periodSummaryReport("Quarterly Summary", selectedQuarterRange()));
            case "Half-Year Summary" -> refreshAnalyticalOnly(periodSummaryReport("Half-Year Summary", selectedHalfYearRange()));
            case "Annual Summary" -> refreshAnalyticalOnly(periodSummaryReport("Annual Summary", selectedYearRange()));
            case "Year-to-Year Comparison" -> refreshAnalyticalOnly(yearToYearReport());
            case "Cash Flow Report" -> refreshAnalyticalOnly(cashFlowReport(selectedRangeForGrouping()));
            case "Budget vs Actual" -> refreshAnalyticalOnly(budgetVsActualReport());
            case "Net Worth Report" -> refreshAnalyticalOnly(netWorthReport(selectedMonthRange()));
            case "Financial Position" -> refreshAnalyticalOnly(financialPositionReport(selectedMonthRange()));
            case "Category Spending" -> refreshAnalyticalOnly(categorySpendingAnalysis(selectedMonthRange()));
            case "Income Source Analysis" -> refreshAnalyticalOnly(incomeSourceAnalysis(selectedMonthRange()));
            case "Expense Trend Report" -> refreshAnalyticalOnly(expenseTrendReport(selectedRangeForGrouping()));
            case "Savings and Goals Progress" -> refreshAnalyticalOnly(savingsGoalsReport());
            case "Money Borrowed Report" -> refreshAnalyticalOnly(loanPositionReport("Money Borrowed Report"));
            case "Money Lent Report" -> refreshAnalyticalOnly(loanPositionReport("Money Lent Report"));
            case "Debt Aging Report" -> refreshAnalyticalOnly(debtAgingReport());
            case "Upcoming Obligations" -> refreshAnalyticalOnly(upcomingObligationsReport());
            case "Recurring Transactions" -> refreshAnalyticalOnly(recurringTransactionsReport());
            case "Project Performance" -> refreshAnalyticalOnly(projectPerformanceReport());
            case "Account Reconciliation" -> refreshAnalyticalOnly(accountReconciliationReport());
            case "Transfer Report" -> refreshAnalyticalOnly(transferReport(selectedMonthRange()));
            case "Asset Register" -> refreshAnalyticalOnly(assetRegisterReport());
            case "Asset Valuation" -> refreshAnalyticalOnly(assetValuationReport());
            case "Asset Disposal" -> refreshAnalyticalOnly(assetDisposalReport());
            case "Trends and Forecast" -> refreshAnalyticalOnly(forecastReport());
            case "Financial Health" -> refreshAnalyticalOnly(financialHealthReport());
            case "Unusual Transactions" -> refreshAnalyticalOnly(unusualTransactionsReport());
            case "Recommendations" -> refreshAnalyticalOnly(recommendationsReport());
            case "Data Quality Report" -> refreshAnalyticalOnly(dataQualityReport());
            case "Audit Trail" -> refreshAnalyticalOnly(auditTrailReport());
            case "Backup and Restore History" -> refreshAnalyticalOnly(backupHistoryReport());
            default -> refreshMonthlySummary();
        }
    }

    @FXML
    private void previewReport() {
        refresh();
    }

    @FXML
    private void resetFilters() {
        initializing = true;
        try {
            monthBox.getSelectionModel().select(LocalDate.now().getMonthValue() - 1);
            Integer currentYear = Year.now().getValue();
            if (yearBox.getItems().contains(currentYear)) {
                yearBox.getSelectionModel().select(currentYear);
            }
            groupingBox.getSelectionModel().select("Monthly");
            accountFilterBox.getSelectionModel().select(ALL_ACCOUNTS);
            categoryFilterBox.getSelectionModel().select(ALL_CATEGORIES);
            projectFilterBox.getSelectionModel().select(ALL_PROJECTS);
            statusFilterBox.getSelectionModel().select(POSTED_TRANSACTIONS);
            startDatePicker.setValue(null);
            endDatePicker.setValue(null);
        } finally {
            initializing = false;
        }
        refresh();
    }

    private void populateFilters() {
        accountFilterBox.setItems(FXCollections.observableArrayList(withAll(
                ALL_ACCOUNTS,
                database.listAccounts().stream().map(Account::getAccountName).toList()
        )));
        categoryFilterBox.setItems(FXCollections.observableArrayList(withAll(
                ALL_CATEGORIES,
                database.listCategories().stream().map(Category::getCategoryName).toList()
        )));
        projectFilterBox.setItems(FXCollections.observableArrayList(withAll(
                ALL_PROJECTS,
                database.listProjects().stream().map(Project::getProjectName).toList()
        )));
        statusFilterBox.setItems(FXCollections.observableArrayList(
                POSTED_TRANSACTIONS,
                ALL_STATUSES,
                "Completed",
                "Open or pending",
                "Cancelled"
        ));
        accountFilterBox.getSelectionModel().select(ALL_ACCOUNTS);
        categoryFilterBox.getSelectionModel().select(ALL_CATEGORIES);
        projectFilterBox.getSelectionModel().select(ALL_PROJECTS);
        statusFilterBox.getSelectionModel().select(POSTED_TRANSACTIONS);
    }

    private List<String> withAll(String allLabel, List<String> values) {
        List<String> result = new ArrayList<>();
        result.add(allLabel);
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(result::add);
        return result;
    }

    private void configureTables() {
        categoryLabelColumn.setCellValueFactory(new PropertyValueFactory<>("label"));
        categoryAccountColumn.setCellValueFactory(new PropertyValueFactory<>("account"));
        categoryAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        projectLabelColumn.setCellValueFactory(new PropertyValueFactory<>("label"));
        projectAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        accountLabelColumn.setCellValueFactory(new PropertyValueFactory<>("label"));
        accountAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        analysisAreaColumn.setCellValueFactory(new PropertyValueFactory<>("area"));
        analysisItemColumn.setCellValueFactory(new PropertyValueFactory<>("item"));
        analysisAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        analysisComparisonColumn.setCellValueFactory(new PropertyValueFactory<>("comparisonAmount"));
        analysisVarianceColumn.setCellValueFactory(new PropertyValueFactory<>("varianceAmount"));
        analysisPercentColumn.setCellValueFactory(new PropertyValueFactory<>("percentage"));
        analysisStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        analysisRecommendationColumn.setCellValueFactory(new PropertyValueFactory<>("recommendation"));
    }

    private void refreshSummaryLine() {
        String month = selectedMonthKey();
        double income = database.transactionTotalByTypeForMonth("INCOME", month);
        double expenses = database.transactionTotalByTypeForMonth("EXPENSE", month);
        double savings = income - expenses;
        double rate = income == 0 ? 0 : (savings / income) * 100;
        summaryIncomeLabel.setText(MoneyUtil.mwk(income));
        summaryExpenseLabel.setText(MoneyUtil.mwk(expenses));
        summarySavingsLabel.setText(MoneyUtil.mwk(savings));
        summarySavingsRateLabel.setText(String.format("%.2f%%", rate));
        NavigationBus.updateReportTitle(activeReportGroup == null ? reportTypeBox.getValue() : activeReportGroup.title());
    }

    private void refreshMonthlySummary() {
        String month = selectedMonthKey();
        setCategoryReport("Expense Categories", "Category", database.categorySpendingReport(month));
        setProjectReport("Project Spending", database.projectSpendingReport(month));
        setAccountReport("Account Balances", database.accountBalanceReportThroughMonth(month));
        showReportPanes(true, true, true);
        setAnalysisReport(periodSummaryReport("Monthly Summary", selectedMonthRange()));
    }

    private void refreshIncomeReport() {
        setCategoryReport("Income Sources", "Source", true, database.incomeSourceByAccountReport(selectedMonthKey()));
        projectTable.getItems().clear();
        accountTable.getItems().clear();
        showReportPanes(true, false, false);
        setAnalysisReport(incomeSourceAnalysis(selectedMonthRange()));
    }

    private void refreshExpenseReport() {
        String month = selectedMonthKey();
        setCategoryReport("Expense Categories", "Category", true, database.categorySpendingByAccountReport(month));
        setProjectReport("Project Expense Spending", database.projectSpendingReport(month));
        accountTable.getItems().clear();
        showReportPanes(true, true, false);
        setAnalysisReport(categorySpendingAnalysis(selectedMonthRange()));
    }

    private void refreshProjectReport() {
        categoryTable.getItems().clear();
        setProjectReport("Project Spending", database.projectSpendingReport(selectedMonthKey()));
        accountTable.getItems().clear();
        showReportPanes(false, true, false);
        setAnalysisReport(projectPerformanceReport());
    }

    private void refreshAccountReport() {
        categoryTable.getItems().clear();
        projectTable.getItems().clear();
        setAccountReport("Account Balances", database.accountBalanceReportThroughMonth(selectedMonthKey()));
        showReportPanes(false, false, true);
        setAnalysisReport(financialPositionReport(selectedMonthRange()));
    }

    private void refreshLoanReport() {
        setCategoryReport("Net Loan Position By Person", "Person", database.lendingByPersonReport(selectedMonthKey()));
        projectTable.getItems().clear();
        accountTable.getItems().clear();
        showReportPanes(true, false, false);
        setAnalysisReport(loanPositionReport("Loan Report"));
    }

    private void refreshAnalyticalOnly(ReportPackage report) {
        categoryTable.getItems().clear();
        projectTable.getItems().clear();
        accountTable.getItems().clear();
        showReportPanes(false, false, false);
        setAnalysisReport(report);
    }

    private ReportPackage periodSummaryReport(String title, DateRange range) {
        List<FinanceTransaction> current = filteredTransactions(range);
        DateRange previousRange = previousRange(range);
        List<FinanceTransaction> previous = filteredTransactions(previousRange);
        double income = totalByType(current, "INCOME");
        double expenses = totalByType(current, "EXPENSE");
        double previousIncome = totalByType(previous, "INCOME");
        double previousExpenses = totalByType(previous, "EXPENSE");
        double net = income - expenses;
        double previousNet = previousIncome - previousExpenses;
        double openingBalance = accountBalanceThrough(previousMonth(range.start()));
        double closingBalance = accountBalanceThrough(YearMonth.from(range.end()));
        LoanTotals loans = loanTotalsThrough(range.end());
        Map<String, Double> categories = totalsBy(current, this::categoryName, this::isExpense);
        Map.Entry<String, Double> topCategory = largestEntry(categories);
        List<ReportInsightRow> rows = new ArrayList<>();
        rows.add(row("Summary", "Total income", income, previousIncome, income - previousIncome, percentChange(income, previousIncome), statusForNet(income), "Protect reliable income sources and record missing expected income."));
        rows.add(row("Summary", "Total expenses", expenses, previousExpenses, expenses - previousExpenses, percentChange(expenses, previousExpenses), expenses > income ? "Pressure" : "Controlled", expenses > income ? "Reduce discretionary spending before adding new obligations." : "Keep monitoring expense categories."));
        rows.add(row("Summary", "Net cash flow", net, previousNet, net - previousNet, percentChange(net, previousNet), net < 0 ? "Negative" : "Positive", net < 0 ? "Review high categories and delay non-essential spending." : "Allocate surplus to goals, debt, or reserves."));
        rows.add(row("Balances", "Opening balance", openingBalance, 0, 0, 0, "Reference", "Use this with closing balance to check whether transactions are complete."));
        rows.add(row("Balances", "Closing balance", closingBalance, openingBalance, closingBalance - openingBalance, percentChange(closingBalance, openingBalance), closingBalance < 0 ? "Negative" : "Available", closingBalance < 0 ? "Reconcile accounts and cover the deficit immediately." : "Separate committed money from freely available cash."));
        rows.add(row("Savings", "Amount saved", net, income, income == 0 ? 0 : net, income == 0 ? 0 : net / income * 100, net < 0 ? "Not saved" : "Saved", net < 0 ? "Set a spending cap for the next period." : "Move part of the surplus to a goal or emergency reserve."));
        rows.add(row("Loans", "Money owed to you", loans.receivable(), 0, loans.receivable(), 0, loans.receivable() > 0 ? "Receivable" : "None", loans.receivable() > 0 ? "Follow up open receivables and record repayments." : "No receivable pressure recorded."));
        rows.add(row("Loans", "Money you owe", loans.liability(), 0, -loans.liability(), 0, loans.liability() > 0 ? "Debt" : "None", loans.liability() > 0 ? "Prioritize overdue or high-pressure debt repayments." : "No outstanding borrowed-money position recorded."));
        if (topCategory != null) {
            rows.add(row("Expenses", "Highest expense category: " + topCategory.getKey(), topCategory.getValue(), expenses, topCategory.getValue(), expenses == 0 ? 0 : topCategory.getValue() / expenses * 100, "Largest category", "Review this category first for possible savings."));
        }
        return packageFor(
                title + " - " + range.label(),
                rows,
                "Net cash flow for " + range.label() + " is " + MoneyUtil.mwk(net) + ", compared with " + MoneyUtil.mwk(previousNet) + " in the previous comparable period.",
                net < 0 ? "Spending exceeded income. Closing balance and upcoming obligations require attention." : "No negative cash-flow pressure is visible in this period.",
                net < 0 ? "Reduce or postpone non-essential spending and inspect the highest expense category." : "Use the surplus for savings goals, emergency reserves, or debt reduction.",
                dataConfidence(current)
        );
    }

    private ReportPackage yearToYearReport() {
        int year = selectedYear();
        DateRange currentRange = new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), String.valueOf(year));
        DateRange previousRange = new DateRange(LocalDate.of(year - 1, 1, 1), LocalDate.of(year - 1, 12, 31), String.valueOf(year - 1));
        PeriodTotals current = totals(filteredTransactions(currentRange));
        PeriodTotals previous = totals(filteredTransactions(previousRange));
        double currentNet = current.net();
        double previousNet = previous.net();
        List<ReportInsightRow> rows = List.of(
                row("Year", "Income", current.income(), previous.income(), current.income() - previous.income(), percentChange(current.income(), previous.income()), "Comparison", "Investigate income changes by source."),
                row("Year", "Expenses", current.expenses(), previous.expenses(), current.expenses() - previous.expenses(), percentChange(current.expenses(), previous.expenses()), current.expenses() > previous.expenses() ? "Increased" : "Reduced", "Review categories with the largest annual movement."),
                row("Year", "Net cash flow", currentNet, previousNet, currentNet - previousNet, percentChange(currentNet, previousNet), currentNet < previousNet ? "Weaker" : "Improved", currentNet < previousNet ? "Use category and cash-flow reports to find the cause." : "Preserve the improvement through goals and reserves.")
        );
        return packageFor(
                "Year-to-Year Comparison",
                rows,
                "Year " + year + " net cash flow is " + MoneyUtil.mwk(currentNet) + " versus " + MoneyUtil.mwk(previousNet) + " in " + (year - 1) + ".",
                currentNet < previousNet ? "Financial performance weakened compared with the previous year." : "Financial performance improved or held steady compared with the previous year.",
                currentNet < previousNet ? "Inspect annual category spending, income concentration, and obligations." : "Continue tracking categories and protect recurring income.",
                "Comparison uses posted transactions recorded for the selected and previous year."
        );
    }

    private ReportPackage cashFlowReport(DateRange range) {
        List<FinanceTransaction> transactions = filteredTransactions(range);
        Map<String, PeriodTotals> periodTotals = groupedTotals(transactions, this::periodKey);
        List<ReportInsightRow> rows = new ArrayList<>();
        for (Map.Entry<String, PeriodTotals> entry : periodTotals.entrySet()) {
            PeriodTotals totals = entry.getValue();
            rows.add(row("Period", entry.getKey(), totals.income(), totals.expenses(), totals.net(), totals.margin(), totals.net() < 0 ? "Negative cash flow" : "Positive cash flow", totals.net() < 0 ? "Find the largest expense source in this period." : "Consider saving part of the surplus."));
        }
        rows.addAll(totalsBy(transactions, FinanceTransaction::getAccountName, this::isCashAffecting).entrySet().stream()
                .map(entry -> row("Account", entry.getKey(), entry.getValue(), 0, entry.getValue(), 0, entry.getValue() < 0 ? "Outflow pressure" : "Net inflow", entry.getValue() < 0 ? "Check account-specific spending and transfers." : "No account cash-flow pressure."))
                .toList());
        rows.addAll(totalsBy(transactions, this::categoryName, this::isExpense).entrySet().stream()
                .limit(8)
                .map(entry -> row("Category", entry.getKey(), entry.getValue(), totalByType(transactions, "EXPENSE"), entry.getValue(), percentOf(entry.getValue(), totalByType(transactions, "EXPENSE")), "Expense driver", "Review this category for possible reductions."))
                .toList());
        rows.addAll(totalsBy(transactions, FinanceTransaction::getProjectName, this::isExpense).entrySet().stream()
                .filter(entry -> !"Unassigned".equalsIgnoreCase(entry.getKey()))
                .limit(8)
                .map(entry -> row("Project", entry.getKey(), entry.getValue(), totalByType(transactions, "EXPENSE"), entry.getValue(), percentOf(entry.getValue(), totalByType(transactions, "EXPENSE")), "Project outflow", "Compare this project outflow against project budget and activity progress."))
                .toList());
        PeriodTotals total = totals(transactions);
        return packageFor(
                "Cash Flow Report - " + range.label(),
                rows,
                "Total inflow was " + MoneyUtil.mwk(total.income()) + ", total outflow was " + MoneyUtil.mwk(total.expenses()) + ", and net cash flow was " + MoneyUtil.mwk(total.net()) + ".",
                total.net() < 0 ? "The selected period has negative cash flow." : "The selected period has positive cash flow.",
                total.net() < 0 ? "Reduce expenses by at least " + MoneyUtil.mwk(Math.abs(total.net())) + " or add income before taking on new obligations." : "Move surplus into planned goals or reserves.",
                dataConfidence(transactions)
        );
    }

    private ReportPackage budgetVsActualReport() {
        List<BudgetProgress> budgets = database.listBudgetProgress(selectedMonthKey());
        List<ReportInsightRow> rows = new ArrayList<>();
        for (BudgetProgress progress : budgets) {
            double predicted = predictedMonthEnd(progress.getSpent());
            double variance = progress.getAmountLimit() - progress.getSpent();
            String status = progress.getPercentUsed() > 100 ? "Over budget" : "Within budget";
            rows.add(row(
                    "Budget",
                    progress.getBudgetName() + " / " + blankAs(progress.getCategoryName(), "All categories"),
                    progress.getAmountLimit(),
                    progress.getSpent(),
                    variance,
                    progress.getPercentUsed(),
                    status,
                    progress.getPercentUsed() > 100
                            ? "Reduce this category by about " + MoneyUtil.mwk(Math.abs(variance)) + "."
                            : "Remaining budget is " + MoneyUtil.mwk(variance) + ". Predicted month-end spend is " + MoneyUtil.mwk(predicted) + "."
            ));
        }
        long overBudget = budgets.stream().filter(progress -> progress.getPercentUsed() > 100).count();
        return packageFor(
                "Budget vs Actual - " + selectedMonthKey(),
                rows,
                budgets.isEmpty() ? "No budget has been created for the selected month." : budgets.size() + " budget line(s) compared with actual spending.",
                overBudget > 0 ? overBudget + " budget line(s) are over budget." : "No over-budget category detected.",
                overBudget > 0 ? "Open the over-budget rows first and set a spending stop or adjustment." : "Keep tracking remaining budget before month-end.",
                "Prediction uses current month-to-date pace when the selected month is the current month."
        );
    }

    private ReportPackage netWorthReport(DateRange range) {
        double accountAssets = database.listAccounts().stream()
                .filter(account -> !"INACTIVE".equalsIgnoreCase(account.getStatus()))
                .mapToDouble(Account::getCurrentBalance)
                .sum();
        double savingsGroupAssets = database.getCommunitySavingsBalance();
        LoanTotals loans = loanTotalsThrough(range.end());
        List<ReportPositionItem> positionItems = activePositionItems();
        double additionalAssets = positionItems.stream()
                .filter(item -> "ASSET".equalsIgnoreCase(item.getPositionType()))
                .mapToDouble(ReportPositionItem::getCurrentValue)
                .sum();
        double registeredAssets = activeAssetRecords().stream()
                .mapToDouble(Asset::getCurrentValue)
                .sum();
        double additionalLiabilities = positionItems.stream()
                .filter(item -> "LIABILITY".equalsIgnoreCase(item.getPositionType()))
                .mapToDouble(ReportPositionItem::getCurrentValue)
                .sum();
        double scheduledBorrowed = activeLoanSchedules().stream()
                .filter(schedule -> "BORROWED".equalsIgnoreCase(schedule.getLoanDirection()))
                .mapToDouble(LoanScheduleRecord::getOutstandingAmount)
                .sum();
        double scheduledLent = activeLoanSchedules().stream()
                .filter(schedule -> "LENT".equalsIgnoreCase(schedule.getLoanDirection()))
                .mapToDouble(LoanScheduleRecord::getOutstandingAmount)
                .sum();
        double totalAssets = accountAssets + savingsGroupAssets + loans.receivable() + registeredAssets + additionalAssets + scheduledLent;
        double totalLiabilities = loans.liability() + additionalLiabilities + scheduledBorrowed;
        double netWorth = totalAssets - totalLiabilities;
        List<ReportInsightRow> rows = new ArrayList<>();
        rows.add(row("Assets", "Available account balances", accountAssets, 0, accountAssets, 0, accountAssets < 0 ? "Negative" : "Asset", "Reconcile real accounts and keep committed funds separate."));
        rows.add(row("Assets", "Savings Groups", savingsGroupAssets, 0, savingsGroupAssets, 0, savingsGroupAssets > 0 ? "Committed savings" : "None", "Savings Group money is part of net worth but not ordinary available cash."));
        rows.add(row("Assets", "Money owed to you from transactions", loans.receivable(), 0, loans.receivable(), 0, loans.receivable() > 0 ? "Receivable" : "None", "Follow up recoveries and record repayments."));
        rows.add(row("Assets", "Scheduled money lent", scheduledLent, 0, scheduledLent, 0, scheduledLent > 0 ? "Receivable" : "None", "Use loan schedules to track due dates and recoveries."));
        rows.add(row("Assets", "Registered assets from Asset Records", registeredAssets, 0, registeredAssets, 0, registeredAssets > 0 ? "Asset" : "None", "Update valuations and record disposals from Asset Records."));
        rows.add(row("Assets", "Additional assets", additionalAssets, 0, additionalAssets, 0, additionalAssets > 0 ? "Asset" : "None", "Keep valuations current in Report Data Inputs."));
        rows.add(row("Liabilities", "Money you owe from transactions", loans.liability(), 0, -loans.liability(), 0, loans.liability() > 0 ? "Liability" : "None", "Plan repayment from free cash flow."));
        rows.add(row("Liabilities", "Scheduled borrowed money", scheduledBorrowed, 0, -scheduledBorrowed, 0, scheduledBorrowed > 0 ? "Liability" : "None", "Use due dates to prioritize repayment."));
        rows.add(row("Liabilities", "Additional liabilities", additionalLiabilities, 0, -additionalLiabilities, 0, additionalLiabilities > 0 ? "Liability" : "None", "Update liabilities when balances change."));
        rows.add(row("Net Worth", "Total assets minus liabilities", netWorth, 0, netWorth, 0, netWorth < 0 ? "Needs attention" : "Positive", netWorth < 0 ? "Reduce liabilities and avoid new commitments." : "Protect positive net worth through reserves and goals."));
        return packageFor(
                "Net Worth Report",
                rows,
                "Estimated net worth is " + MoneyUtil.mwk(netWorth) + ".",
                netWorth < 0 ? "Liabilities exceed recorded assets." : "Recorded assets exceed liabilities.",
                netWorth < 0 ? "Prioritize debt reduction and reconcile account balances." : "Track whether net worth is increasing over time.",
                "Assets and liabilities use real account balances, Savings Groups, transactions, Asset Records, report position items and loan schedules."
        );
    }

    private ReportPackage financialPositionReport(DateRange range) {
        double availableCash = database.getAvailableCashAndBankBalance();
        double savingsGroupAssets = database.getCommunitySavingsBalance();
        double committed = database.listBudgetProgress(selectedMonthKey()).stream()
                .mapToDouble(progress -> Math.max(0, progress.getRemaining()))
                .sum();
        double obligations = upcomingObligationsAmount();
        double explicitAssets = activePositionItems().stream()
                .filter(item -> "ASSET".equalsIgnoreCase(item.getPositionType()))
                .mapToDouble(ReportPositionItem::getCurrentValue)
                .sum();
        double registeredAssets = activeAssetRecords().stream()
                .mapToDouble(Asset::getCurrentValue)
                .sum();
        double explicitLiabilities = activePositionItems().stream()
                .filter(item -> "LIABILITY".equalsIgnoreCase(item.getPositionType()))
                .mapToDouble(ReportPositionItem::getCurrentValue)
                .sum();
        LoanTotals loans = loanTotalsThrough(range.end());
        double scheduledBorrowed = activeLoanSchedules().stream()
                .filter(schedule -> "BORROWED".equalsIgnoreCase(schedule.getLoanDirection()))
                .mapToDouble(LoanScheduleRecord::getOutstandingAmount)
                .sum();
        double freePosition = availableCash + registeredAssets + explicitAssets - committed - obligations - loans.liability() - scheduledBorrowed - explicitLiabilities;
        List<ReportInsightRow> rows = new ArrayList<>();
        rows.add(row("Position", "Available cash", availableCash, 0, availableCash, 0, availableCash < 0 ? "Negative" : "Available", "Reconcile account balances."));
        rows.add(row("Position", "Savings Groups", savingsGroupAssets, 0, savingsGroupAssets, 0, savingsGroupAssets > 0 ? "Committed savings" : "None", "Track separately from available cash to avoid double counting."));
        rows.add(row("Position", "Registered assets", registeredAssets, 0, registeredAssets, 0, registeredAssets > 0 ? "Recorded" : "None", "Keep Asset Records valuations and disposal statuses current."));
        rows.add(row("Position", "Additional report assets", explicitAssets, 0, explicitAssets, 0, explicitAssets > 0 ? "Available/recorded" : "None", "Keep non-account asset valuations current."));
        rows.add(row("Position", "Restricted or committed money", committed, availableCash, availableCash - committed, percentOf(committed, availableCash), "Committed", "Do not treat committed budget money as free cash."));
        rows.add(row("Position", "Upcoming obligations", obligations, availableCash, availableCash - obligations, percentOf(obligations, availableCash), obligations > availableCash ? "Shortfall risk" : "Covered", "Prepare funds before due dates."));
        rows.add(row("Position", "Outstanding transaction debt", loans.liability(), availableCash, availableCash - loans.liability(), percentOf(loans.liability(), availableCash), loans.liability() > 0 ? "Debt pressure" : "No debt", "Plan repayments from surplus cash flow."));
        rows.add(row("Position", "Scheduled borrowed money", scheduledBorrowed, availableCash, availableCash - scheduledBorrowed, percentOf(scheduledBorrowed, availableCash), scheduledBorrowed > 0 ? "Debt schedule" : "No schedule", "Use loan due dates for cash planning."));
        rows.add(row("Position", "Additional liabilities", explicitLiabilities, availableCash, availableCash - explicitLiabilities, percentOf(explicitLiabilities, availableCash), explicitLiabilities > 0 ? "Liability" : "None", "Update liability balances as they change."));
        rows.add(row("Position", "Estimated free financial position", freePosition, availableCash, freePosition, percentOf(freePosition, availableCash), freePosition < 0 ? "Needs attention" : "Usable buffer", freePosition < 0 ? "Reduce obligations or increase available funds." : "Keep buffer separate from discretionary spending."));
        return packageFor(
                "Financial Position",
                rows,
                "Estimated free position after committed money, obligations and debt is " + MoneyUtil.mwk(freePosition) + ".",
                freePosition < 0 ? "PFMIS should not treat all balances as freely spendable." : "Current free position is positive after known commitments.",
                freePosition < 0 ? "Cut discretionary spending and reschedule non-essential obligations." : "Maintain this buffer for near-term obligations.",
                "Committed money uses budgets, scheduled obligations, recurring plans, report position items and loan schedules."
        );
    }

    private ReportPackage assetRegisterReport() {
        List<Asset> assets = database.listAssets();
        List<ReportInsightRow> rows = assets.stream()
                .map(asset -> row(
                        blankAs(asset.getAssetCategory(), "Other"),
                        assetRegisterItem(asset),
                        asset.getCurrentValue(),
                        asset.getTotalCost(),
                        asset.getCurrentValue() - asset.getTotalCost(),
                        percentOf(asset.getCurrentValue(), asset.getTotalCost()),
                        assetStatusLabel(asset.getStatus()),
                        assetRecommendation(asset)
                ))
                .toList();
        double activeValue = activeAssetRecords().stream().mapToDouble(Asset::getCurrentValue).sum();
        long activeCount = activeAssetRecords().size();
        long closedCount = assets.stream().filter(asset -> isTerminalAssetStatus(asset.getStatus())).count();
        return packageFor(
                "Asset Register",
                rows,
                assets.isEmpty()
                        ? "No assets have been registered yet."
                        : assets.size() + " asset record(s) exist. Active asset value is " + MoneyUtil.mwk(activeValue) + ".",
                closedCount > 0
                        ? closedCount + " asset record(s) are closed, sold, transferred or disposed and retained for history."
                        : "No closed asset records were found.",
                activeCount > 0
                        ? "Review active asset valuations and record maintenance or disposal from Asset Records when needed."
                        : "Register assets or reopen valid active records before relying on asset totals.",
                "Asset Register uses Asset Records. Purchase links, project links, sale events and disposal history remain traceable from the asset record."
        );
    }

    private ReportPackage assetValuationReport() {
        List<Asset> activeAssets = activeAssetRecords();
        Map<String, List<Asset>> byCategory = activeAssets.stream()
                .collect(Collectors.groupingBy(asset -> blankAs(asset.getAssetCategory(), "Other"), TreeMap::new, Collectors.toList()));
        List<ReportInsightRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<Asset>> entry : byCategory.entrySet()) {
            double cost = entry.getValue().stream().mapToDouble(Asset::getTotalCost).sum();
            double currentValue = entry.getValue().stream().mapToDouble(Asset::getCurrentValue).sum();
            rows.add(row(
                    "Asset category",
                    entry.getKey() + " / " + entry.getValue().size() + " active record(s)",
                    currentValue,
                    cost,
                    currentValue - cost,
                    percentOf(currentValue, cost),
                    valuationStatus(currentValue, cost),
                    currentValue < cost
                            ? "Review depreciation, condition and replacement planning."
                            : "Keep valuation evidence current and avoid double-counting manual report-input assets."
            ));
        }
        double totalCost = activeAssets.stream().mapToDouble(Asset::getTotalCost).sum();
        double totalValue = activeAssets.stream().mapToDouble(Asset::getCurrentValue).sum();
        return packageFor(
                "Asset Valuation",
                rows,
                activeAssets.isEmpty()
                        ? "No active assets are available for valuation."
                        : "Active assets are valued at " + MoneyUtil.mwk(totalValue) + " against recorded cost of " + MoneyUtil.mwk(totalCost) + ".",
                totalValue < totalCost
                        ? "Recorded asset value is below cost. This may be normal depreciation, damage or disposal preparation."
                        : "Recorded asset value is at or above cost.",
                "Update asset values from Asset Records and attach valuation evidence for important assets.",
                "Valuation excludes assets with Sold, Donated, Transferred, Lost, Written Off, Disposed or Archived status."
        );
    }

    private ReportPackage assetDisposalReport() {
        List<Asset> assets = database.listAssets();
        List<ReportInsightRow> rows = new ArrayList<>();
        for (Asset asset : assets) {
            List<AssetEvent> disposalEvents = database.listAssetEvents(asset.getId()).stream()
                    .filter(event -> isAssetDisposalEvent(event.getEventType()))
                    .toList();
            for (AssetEvent event : disposalEvents) {
                rows.add(row(
                        assetStatusLabel(event.getEventType()),
                        asset.getAssetName() + " / " + blankAs(event.getEventDate(), "no date"),
                        event.getAmount(),
                        asset.getTotalCost(),
                        event.getAmount() - asset.getTotalCost(),
                        percentOf(event.getAmount(), asset.getTotalCost()),
                        assetStatusLabel(asset.getStatus()),
                        disposalRecommendation(event)
                ));
            }
            if (disposalEvents.isEmpty() && isTerminalAssetStatus(asset.getStatus())) {
                rows.add(row(
                        assetStatusLabel(asset.getStatus()),
                        asset.getAssetName() + " / " + blankAs(asset.getPurchaseDate(), "no date"),
                        asset.getCurrentValue(),
                        asset.getTotalCost(),
                        asset.getCurrentValue() - asset.getTotalCost(),
                        percentOf(asset.getCurrentValue(), asset.getTotalCost()),
                        assetStatusLabel(asset.getStatus()),
                        "Closed asset has no matching disposal event. Review Asset Records history."
                ));
            }
        }
        double disposalProceeds = rows.stream().mapToDouble(ReportInsightRow::getAmount).sum();
        return packageFor(
                "Asset Disposal",
                rows,
                rows.isEmpty()
                        ? "No asset sale, transfer, donation, write-off, loss or disposal history was found."
                        : rows.size() + " disposal or closure event(s) were found. Recorded disposal value is " + MoneyUtil.mwk(disposalProceeds) + ".",
                rows.stream().anyMatch(row -> row.getRecommendation().contains("no matching disposal event"))
                        ? "Some closed assets do not have complete disposal history."
                        : "Disposal history is linked to Asset Records events.",
                "Use Asset Records -> Open Asset -> More Actions for every sale, transfer, donation, write-off, loss or disposal.",
                "The full sale price is not treated as ordinary income. Sale proceeds, selling costs and gain or loss are preserved as asset evidence."
        );
    }

    private ReportPackage categorySpendingAnalysis(DateRange range) {
        List<FinanceTransaction> current = filteredTransactions(range);
        Map<String, Double> spending = totalsBy(current, this::categoryName, this::isExpense);
        double totalExpense = totalByType(current, "EXPENSE");
        Map<String, Double> previousAverage = averageCategorySpendingBefore(range.start(), 3);
        List<ReportInsightRow> rows = spending.entrySet().stream()
                .map(entry -> {
                    double average = previousAverage.getOrDefault(entry.getKey(), 0.0);
                    double variance = entry.getValue() - average;
                    return row(
                            "Category",
                            entry.getKey(),
                            entry.getValue(),
                            average,
                            variance,
                            percentOf(entry.getValue(), totalExpense),
                            variance > 0 ? "Increasing" : "Stable or lower",
                            variance > 0 ? "Review why this category increased by " + MoneyUtil.mwk(variance) + "." : "Keep monitoring this category."
                    );
                })
                .toList();
        Map.Entry<String, Double> top = largestEntry(spending);
        return packageFor(
                "Category Spending - " + range.label(),
                rows,
                top == null ? "No expense categories were recorded for the selected period." : top.getKey() + " is the largest expense category at " + MoneyUtil.mwk(top.getValue()) + ".",
                rows.stream().anyMatch(row -> row.getVarianceAmount() > 0) ? "At least one category is above its previous average." : "No category increase against the recent average was detected.",
                top == null ? "Record expense categories for better analysis." : "Start cost control with " + top.getKey() + ".",
                dataConfidence(current)
        );
    }

    private ReportPackage incomeSourceAnalysis(DateRange range) {
        List<FinanceTransaction> transactions = filteredTransactions(range);
        Map<String, Double> income = totalsBy(transactions, tx -> categoryName(tx) + " / " + blankAs(tx.getAccountName(), "Unknown account"), this::isIncome);
        double totalIncome = income.values().stream().mapToDouble(Double::doubleValue).sum();
        List<ReportInsightRow> rows = income.entrySet().stream()
                .map(entry -> row(
                        "Income source",
                        entry.getKey(),
                        entry.getValue(),
                        totalIncome,
                        entry.getValue(),
                        percentOf(entry.getValue(), totalIncome),
                        percentOf(entry.getValue(), totalIncome) >= 70 ? "Concentration risk" : "Diversified",
                        percentOf(entry.getValue(), totalIncome) >= 70 ? "Create a backup income or reserve plan." : "Keep tracking reliability."
                ))
                .toList();
        Map.Entry<String, Double> top = largestEntry(income);
        double concentration = top == null ? 0 : percentOf(top.getValue(), totalIncome);
        return packageFor(
                "Income Source Analysis - " + range.label(),
                rows,
                top == null ? "No income was recorded in the selected period." : top.getKey() + " contributed " + String.format("%.1f%%", concentration) + " of income.",
                concentration >= 70 ? "Income is concentrated in one source." : "Income concentration is not high in the selected period.",
                concentration >= 70 ? "Build emergency reserves and monitor delayed income." : "Preserve reliable sources and record irregular income separately.",
                dataConfidence(transactions)
        );
    }

    private ReportPackage expenseTrendReport(DateRange range) {
        List<FinanceTransaction> transactions = filteredTransactions(range).stream()
                .filter(this::isExpense)
                .toList();
        Map<String, Double> grouped = totalsBy(transactions, this::periodKey, tx -> true);
        double total = grouped.values().stream().mapToDouble(Double::doubleValue).sum();
        double average = grouped.isEmpty() ? 0 : total / grouped.size();
        List<ReportInsightRow> rows = grouped.entrySet().stream()
                .map(entry -> row("Expense trend", entry.getKey(), entry.getValue(), average, entry.getValue() - average, percentChange(entry.getValue(), average), entry.getValue() > average ? "Above average" : "At or below average", entry.getValue() > average ? "Inspect transactions in this period." : "No unusual trend pressure."))
                .toList();
        Map.Entry<String, Double> highest = largestEntry(grouped);
        return packageFor(
                "Expense Trend Report - " + range.label(),
                rows,
                highest == null ? "No expense trend data is available." : highest.getKey() + " is the highest spending period at " + MoneyUtil.mwk(highest.getValue()) + ".",
                highest != null && highest.getValue() > average ? "One or more periods are above the average spending pace." : "No above-average spending period detected.",
                highest == null ? "Record expenses consistently." : "Review the highest spending period and separate essential from discretionary expenses.",
                dataConfidence(transactions)
        );
    }

    private ReportPackage savingsGoalsReport() {
        List<Goal> goals = database.listGoals();
        LocalDate today = LocalDate.now();
        List<ReportInsightRow> rows = new ArrayList<>();
        for (Goal goal : goals) {
            double percent = percentOf(goal.getCurrentAmount(), goal.getTargetAmount());
            double requiredMonthly = requiredMonthlyContribution(goal, today);
            double predictedMonths = goal.getMonthlyContribution() <= 0 ? 0 : Math.ceil(goal.getRemainingAmount() / goal.getMonthlyContribution());
            String status = goal.getRemainingAmount() <= 0 ? "Completed" : goal.getMonthlyContribution() >= requiredMonthly ? "On track" : "Behind target";
            rows.add(row(
                    "Goal",
                    goal.getGoalName(),
                    goal.getTargetAmount(),
                    goal.getCurrentAmount(),
                    goal.getRemainingAmount(),
                    percent,
                    status,
                    goal.getRemainingAmount() <= 0
                            ? "Mark the goal complete or move surplus to the next goal."
                            : "Required monthly contribution is about " + MoneyUtil.mwk(requiredMonthly) + "; current contribution predicts completion in " + (int) predictedMonths + " month(s)."
            ));
        }
        long behind = rows.stream().filter(row -> "Behind target".equals(row.getStatus())).count();
        return packageFor(
                "Savings and Goals Progress",
                rows,
                goals.isEmpty() ? "No goals are recorded." : goals.size() + " goal(s) reviewed for target progress.",
                behind > 0 ? behind + " goal(s) need higher monthly contributions." : "No goal contribution shortfall detected.",
                behind > 0 ? "Increase contributions for behind-target goals or revise target dates." : "Keep funding goals on schedule.",
                "Predicted completion uses current recorded monthly contribution and target dates."
        );
    }

    private ReportPackage loanPositionReport(String reportName) {
        Map<String, LoanPosition> positions = loanPositionsThrough(LocalDate.now());
        List<ReportInsightRow> rows = new ArrayList<>();
        for (LoanPosition position : positions.values()) {
            if ("Money Borrowed Report".equals(reportName)) {
                if (position.borrowedOutstanding() > 0) {
                    rows.add(row("Money borrowed", position.person(), position.borrowed(), position.borrowedRepaid(), position.borrowedOutstanding(), 0, "Outstanding", "Plan repayment and maintain the loan schedule."));
                }
            } else if ("Money Lent Report".equals(reportName)) {
                if (position.lentOutstanding() > 0) {
                    rows.add(row("Money lent", position.person(), position.lent(), position.lentRepaid(), position.lentOutstanding(), 0, agingStatus(position.oldestLentDate()), "Follow up recovery and record repayments."));
                }
            } else {
                if (position.lentOutstanding() > 0) {
                    rows.add(row("Receivable", position.person(), position.lent(), position.lentRepaid(), position.lentOutstanding(), 0, agingStatus(position.oldestLentDate()), "Follow up money owed to you."));
                }
                if (position.borrowedOutstanding() > 0) {
                    rows.add(row("Debt", position.person(), position.borrowed(), position.borrowedRepaid(), position.borrowedOutstanding(), 0, agingStatus(position.oldestBorrowedDate()), "Prioritize repayment planning."));
                }
            }
        }
        for (LoanScheduleRecord schedule : activeLoanSchedules()) {
            boolean borrowed = "BORROWED".equalsIgnoreCase(schedule.getLoanDirection());
            if ("Money Borrowed Report".equals(reportName) && !borrowed) {
                continue;
            }
            if ("Money Lent Report".equals(reportName) && borrowed) {
                continue;
            }
            String area = borrowed ? "Scheduled borrowed" : "Scheduled lent";
            rows.add(row(
                    area,
                    blankAs(schedule.getPersonName(), "Unassigned") + " due " + blankAs(schedule.getDueDate(), "no date"),
                    schedule.getPrincipalAmount(),
                    schedule.getPaymentAmount(),
                    schedule.getOutstandingAmount(),
                    schedule.getInterestRate(),
                    agingBucket(parseDate(schedule.getDueDate())),
                    borrowed ? "Prioritize repayment by due date and payment amount." : "Follow up expected recovery by due date."
            ));
        }
        double receivable = positions.values().stream().mapToDouble(LoanPosition::lentOutstanding).sum();
        double liability = positions.values().stream().mapToDouble(LoanPosition::borrowedOutstanding).sum();
        double scheduledReceivable = activeLoanSchedules().stream()
                .filter(schedule -> "LENT".equalsIgnoreCase(schedule.getLoanDirection()))
                .mapToDouble(LoanScheduleRecord::getOutstandingAmount)
                .sum();
        double scheduledLiability = activeLoanSchedules().stream()
                .filter(schedule -> "BORROWED".equalsIgnoreCase(schedule.getLoanDirection()))
                .mapToDouble(LoanScheduleRecord::getOutstandingAmount)
                .sum();
        return packageFor(
                reportName,
                rows,
                "Open transaction receivables total " + MoneyUtil.mwk(receivable) + ", scheduled receivables total " + MoneyUtil.mwk(scheduledReceivable) + ", transaction debt totals " + MoneyUtil.mwk(liability) + " and scheduled borrowed money totals " + MoneyUtil.mwk(scheduledLiability) + ".",
                (liability + scheduledLiability) > (receivable + scheduledReceivable) ? "Borrowed-money pressure is higher than recoverable money." : "Receivables can help offset debt if recovered.",
                (liability + scheduledLiability) > 0 ? "Prioritize debts by age, due date and available cash." : "Keep loan schedules and repayments updated.",
                "Transaction loans are aged from transaction dates; scheduled loans use the due dates entered in Report Data Inputs."
        );
    }

    private ReportPackage debtAgingReport() {
        Map<String, LoanPosition> positions = loanPositionsThrough(LocalDate.now());
        Map<String, Double> buckets = new LinkedHashMap<>();
        buckets.put("Not yet due / current", 0.0);
        buckets.put("1-30 days overdue", 0.0);
        buckets.put("31-60 days overdue", 0.0);
        buckets.put("61-90 days overdue", 0.0);
        buckets.put("More than 90 days overdue", 0.0);
        for (LoanPosition position : positions.values()) {
            if (position.borrowedOutstanding() > 0) {
                buckets.merge(agingBucket(position.oldestBorrowedDate()), position.borrowedOutstanding(), Double::sum);
            }
            if (position.lentOutstanding() > 0) {
                buckets.merge(agingBucket(position.oldestLentDate()), position.lentOutstanding(), Double::sum);
            }
        }
        for (LoanScheduleRecord schedule : activeLoanSchedules()) {
            if (schedule.getOutstandingAmount() > 0) {
                buckets.merge(agingBucket(parseDate(schedule.getDueDate())), schedule.getOutstandingAmount(), Double::sum);
            }
        }
        List<ReportInsightRow> rows = buckets.entrySet().stream()
                .map(entry -> row("Aging", entry.getKey(), entry.getValue(), 0, entry.getValue(), 0, entry.getValue() > 0 ? "Open" : "Clear", entry.getValue() > 0 ? "Review individual loan records in this aging group." : "No open amount in this group."))
                .toList();
        double oldDebt = buckets.getOrDefault("More than 90 days overdue", 0.0);
        return packageFor(
                "Debt Aging Report",
                rows,
                "Debt and lending positions are grouped by estimated age.",
                oldDebt > 0 ? "Some loan positions are older than 90 days." : "No amount older than 90 days was detected.",
                oldDebt > 0 ? "Follow up long-aged records first and verify repayment status." : "Keep due dates and repayment schedules updated.",
                "Aging uses transaction dates and explicit due dates entered under Report Data Inputs."
        );
    }

    private ReportPackage upcomingObligationsReport() {
        double available = database.listAccounts().stream().mapToDouble(Account::getCurrentBalance).sum();
        List<ReportInsightRow> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate nextThirtyDays = today.plusDays(30);
        for (BudgetProgress progress : database.listBudgetProgress(selectedMonthKey())) {
            double remaining = Math.max(0, progress.getRemaining());
            if (remaining > 0) {
                rows.add(row("Budget", progress.getBudgetName(), remaining, available, available - remaining, percentOf(remaining, available), remaining > available ? "Shortfall" : "Covered", "Reserve this amount for the selected month."));
            }
        }
        for (Goal goal : database.listGoals()) {
            if (goal.getMonthlyContribution() > 0 && !"COMPLETED".equalsIgnoreCase(goal.getStatus())) {
                rows.add(row("Goal contribution", goal.getGoalName(), goal.getMonthlyContribution(), available, available - goal.getMonthlyContribution(), percentOf(goal.getMonthlyContribution(), available), goal.getMonthlyContribution() > available ? "Shortfall" : "Planned", "Include this contribution in monthly cash planning."));
            }
        }
        for (ScheduledObligation obligation : activeScheduledObligations()) {
            LocalDate dueDate = parseDate(obligation.getDueDate());
            if (dueDate == null || !dateBetween(dueDate, today, nextThirtyDays)) {
                continue;
            }
            rows.add(row("Scheduled obligation", obligation.getObligationName(), obligation.getAmount(), available, available - obligation.getAmount(), percentOf(obligation.getAmount(), available), obligation.getAmount() > available ? "Shortfall" : "Due soon", "Reserve funds for this due date: " + obligation.getDueDate() + "."));
        }
        for (RecurringTransactionPlan plan : activeRecurringPlans()) {
            LocalDate dueDate = parseDate(plan.getNextDueDate());
            if (!"EXPENSE".equalsIgnoreCase(plan.getTransactionType()) || dueDate == null || !dateBetween(dueDate, today, nextThirtyDays)) {
                continue;
            }
            rows.add(row("Recurring expense", plan.getPlanName(), plan.getAmount(), available, available - plan.getAmount(), percentOf(plan.getAmount(), available), plan.getAmount() > available ? "Shortfall" : "Expected", "Keep this recurring payment in the cash plan."));
        }
        for (LoanScheduleRecord schedule : activeLoanSchedules()) {
            LocalDate dueDate = parseDate(schedule.getDueDate());
            if (!"BORROWED".equalsIgnoreCase(schedule.getLoanDirection()) || dueDate == null || !dateBetween(dueDate, today, nextThirtyDays)) {
                continue;
            }
            double payment = schedule.getPaymentAmount() > 0 ? schedule.getPaymentAmount() : schedule.getOutstandingAmount();
            rows.add(row("Loan repayment", blankAs(schedule.getPersonName(), "Unassigned"), payment, available, available - payment, percentOf(payment, available), payment > available ? "Shortfall" : "Due soon", "Pay or reschedule before " + schedule.getDueDate() + "."));
        }
        LoanTotals loans = loanTotalsThrough(LocalDate.now());
        if (loans.liability() > 0) {
            rows.add(row("Debt", "Outstanding borrowed money", loans.liability(), available, available - loans.liability(), percentOf(loans.liability(), available), loans.liability() > available ? "Shortfall" : "Can plan repayment", "Schedule repayments based on available cash."));
        }
        double obligations = rows.stream().mapToDouble(ReportInsightRow::getAmount).sum();
        return packageFor(
                "Upcoming Obligations",
                rows,
                "Known obligations total " + MoneyUtil.mwk(obligations) + " against available funds of " + MoneyUtil.mwk(available) + ".",
                obligations > available ? "Projected shortfall is " + MoneyUtil.mwk(obligations - available) + "." : "Known obligations are covered by current recorded funds.",
                obligations > available ? "Reduce discretionary spending or reschedule non-essential commitments." : "Reserve funds before spending remaining balances.",
                "This report uses budgets, goal contributions, scheduled obligations, recurring plans and loan schedules."
        );
    }

    private ReportPackage recurringTransactionsReport() {
        List<FinanceTransaction> transactions = allTransactions().stream()
                .filter(this::isPosted)
                .filter(tx -> parseDate(tx.getTransactionDate()) != null)
                .toList();
        Map<String, List<FinanceTransaction>> groups = transactions.stream()
                .collect(Collectors.groupingBy(this::recurringKey, LinkedHashMap::new, Collectors.toList()));
        List<ReportInsightRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<FinanceTransaction>> entry : groups.entrySet()) {
            Set<YearMonth> months = entry.getValue().stream()
                    .map(tx -> YearMonth.from(parseDate(tx.getTransactionDate())))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (months.size() >= 2) {
                double average = entry.getValue().stream().mapToDouble(FinanceTransaction::getAmount).average().orElse(0);
                double latest = entry.getValue().stream()
                        .max(Comparator.comparing(tx -> parseDate(tx.getTransactionDate())))
                        .map(FinanceTransaction::getAmount)
                        .orElse(0.0);
                rows.add(row("Recurring", entry.getKey(), latest, average, latest - average, percentChange(latest, average), Math.abs(latest - average) > average * 0.25 ? "Changed amount" : "Regular", "Verify whether this recurring item is still needed and correctly priced."));
            }
        }
        for (RecurringTransactionPlan plan : activeRecurringPlans()) {
            rows.add(row(
                    "Planned recurring",
                    plan.getPlanName() + " / " + blankAs(plan.getNextDueDate(), "no due date"),
                    plan.getAmount(),
                    0,
                    "INCOME".equalsIgnoreCase(plan.getTransactionType()) ? plan.getAmount() : -plan.getAmount(),
                    0,
                    plan.getTransactionType(),
                    "Review this planned recurring item and record the real transaction when it happens."
            ));
        }
        return packageFor(
                "Recurring Transactions",
                rows,
                rows.isEmpty() ? "No recurring pattern was detected from recorded transactions." : rows.size() + " recurring pattern(s) detected.",
                rows.stream().anyMatch(row -> "Changed amount".equals(row.getStatus())) ? "Some recurring amounts changed materially." : "No recurring amount change above 25% detected.",
                "Cancel unnecessary subscriptions and verify expected recurring income.",
                "Detection combines repeated transaction patterns with planned recurring entries from Report Data Inputs."
        );
    }

    private ReportPackage projectPerformanceReport() {
        List<ReportInsightRow> rows = new ArrayList<>();
        for (Project project : database.listProjects()) {
            double percentUsed = percentOf(project.getAmountSpent(), project.getPlannedBudget());
            rows.add(row("Project", project.getProjectName(), project.getPlannedBudget(), project.getAmountSpent(), project.getRemainingBudget(), percentUsed, percentUsed > 100 ? "Over budget" : "Within budget", percentUsed > 100 ? "Stop non-essential project spending and revise the budget." : "Track remaining budget against incomplete activities."));
        }
        for (ProjectActivity activity : database.listProjectActivities()) {
            rows.add(row("Activity", activity.getProjectName() + " / " + activity.getActivityName(), activity.getAmountUsed(), 0, activity.getAmountUsed(), 0, blankAs(activity.getStatus(), "Pending"), "Compare activity cost against project budget and completion status."));
        }
        long overBudget = rows.stream().filter(row -> "Over budget".equals(row.getStatus())).count();
        return packageFor(
                "Project Performance",
                rows,
                "Projects and activities are compared against planned project budgets.",
                overBudget > 0 ? overBudget + " project(s) are over budget." : "No over-budget project detected.",
                overBudget > 0 ? "Review over-budget activities and revise remaining project scope." : "Keep recording project income, spending and activity status.",
                "Projected final cost uses current recorded activity and transaction spending."
        );
    }

    private ReportPackage accountReconciliationReport() {
        Map<String, AccountReconciliationRecord> latest = database.listLatestAccountReconciliations().stream()
                .collect(Collectors.toMap(AccountReconciliationRecord::getAccountName, Function.identity(), (first, second) -> first));
        List<ReportInsightRow> rows = new ArrayList<>();
        for (Account account : database.listAccounts()) {
            AccountReconciliationRecord record = latest.get(account.getAccountName());
            if (record == null) {
                rows.add(row(
                        "Account",
                        account.getAccountName(),
                        account.getCurrentBalance(),
                        0,
                        account.getCurrentBalance(),
                        0,
                        account.getCurrentBalance() < 0 ? "Negative balance" : "Needs actual balance",
                        "Open Report Data Inputs and enter the actual balance for this account."
                ));
            } else {
                rows.add(row(
                        "Account",
                        account.getAccountName() + " as of " + record.getReconciliationDate(),
                        record.getSystemBalance(),
                        record.getActualBalance(),
                        record.getDifference(),
                        percentOf(record.getDifference(), record.getSystemBalance()),
                        record.getStatus(),
                        Math.abs(record.getDifference()) > 0.005 ? "Investigate unreconciled transactions or incorrect balances." : "No reconciliation difference recorded."
                ));
            }
        }
        return packageFor(
                "Account Reconciliation",
                rows,
                latest.isEmpty() ? "No account has been reconciled yet." : latest.size() + " account(s) have reconciliation records.",
                rows.stream().anyMatch(row -> "DIFFERENCE".equalsIgnoreCase(row.getStatus()) || row.getAmount() < 0) ? "At least one account needs reconciliation attention." : "No reconciliation difference or negative balance is visible.",
                "Enter actual balances regularly and investigate differences before relying on report totals.",
                "System balance is calculated from posted transactions; actual balance is entered in Report Data Inputs."
        );
    }

    private ReportPackage transferReport(DateRange range) {
        List<FinanceTransaction> transfers = filteredTransactions(range).stream()
                .filter(this::isTransfer)
                .toList();
        List<ReportInsightRow> rows = transfers.stream()
                .map(tx -> row("Transfer", tx.getTransactionDate() + " / " + blankAs(tx.getAccountName(), "Unknown account"), tx.getAmount(), 0, transferSignedAmount(tx), 0, blankAs(tx.getTransactionStatus(), "Recorded"), "Transfers are tracked separately so they are not counted as income or expenses."))
                .toList();
        double out = transfers.stream().filter(tx -> "TRANSFER_OUT".equalsIgnoreCase(tx.getTransactionPurpose())).mapToDouble(FinanceTransaction::getAmount).sum();
        double in = transfers.stream().filter(tx -> "TRANSFER_IN".equalsIgnoreCase(tx.getTransactionPurpose())).mapToDouble(FinanceTransaction::getAmount).sum();
        return packageFor(
                "Transfer Report - " + range.label(),
                rows,
                "Transfer-in total is " + MoneyUtil.mwk(in) + " and transfer-out total is " + MoneyUtil.mwk(out) + ".",
                Math.abs(in - out) > 0.005 ? "Transfer in/out totals do not offset. Fees, missing pair entries or failed transfers may exist." : "Transfer entries offset within the selected period.",
                "Verify transfer fees, failed transfers and reversed transfers separately from income and expenses.",
                "This report uses transaction type TRANSFER and transfer purposes."
        );
    }

    private ReportPackage forecastReport() {
        YearMonth currentMonth = selectedYearMonth();
        double averageIncome = averageMonthlyTotal(currentMonth.minusMonths(3), currentMonth.minusMonths(1), "INCOME");
        double averageExpense = averageMonthlyTotal(currentMonth.minusMonths(3), currentMonth.minusMonths(1), "EXPENSE");
        double currentIncome = database.transactionTotalByTypeForMonth("INCOME", currentMonth.toString());
        double currentExpense = database.transactionTotalByTypeForMonth("EXPENSE", currentMonth.toString());
        double plannedRecurringIncome = activeRecurringPlans().stream()
                .filter(plan -> "INCOME".equalsIgnoreCase(plan.getTransactionType()))
                .mapToDouble(RecurringTransactionPlan::getAmount)
                .sum();
        double plannedRecurringExpense = activeRecurringPlans().stream()
                .filter(plan -> "EXPENSE".equalsIgnoreCase(plan.getTransactionType()))
                .mapToDouble(RecurringTransactionPlan::getAmount)
                .sum();
        double plannedObligations = activeScheduledObligations().stream()
                .filter(obligation -> dateInMonth(obligation.getDueDate(), currentMonth))
                .mapToDouble(ScheduledObligation::getAmount)
                .sum();
        double projectedExpense = Math.max(predictedMonthEnd(currentExpense), averageExpense) + plannedRecurringExpense + plannedObligations;
        double projectedIncome = Math.max(Math.max(currentIncome, averageIncome), plannedRecurringIncome);
        double projectedNet = projectedIncome - projectedExpense;
        double closingBalance = accountBalanceThrough(currentMonth);
        double expectedMonthEndBalance = closingBalance + projectedNet;
        List<ReportInsightRow> rows = List.of(
                row("Forecast", "Expected income", projectedIncome, averageIncome, projectedIncome - averageIncome, percentChange(projectedIncome, averageIncome), "Estimate", "Verify expected income dates."),
                row("Forecast", "Expected expenses", projectedExpense, averageExpense, projectedExpense - averageExpense, percentChange(projectedExpense, averageExpense), projectedExpense > averageExpense ? "Rising" : "Normal", "Reduce variable expenses if projected spending is high."),
                row("Forecast", "Planned recurring expense", plannedRecurringExpense, 0, plannedRecurringExpense, 0, plannedRecurringExpense > 0 ? "Known plan" : "None", "Keep planned recurring expenses current in Report Data Inputs."),
                row("Forecast", "Scheduled obligations this month", plannedObligations, 0, plannedObligations, 0, plannedObligations > 0 ? "Known obligation" : "None", "Reserve funds before due dates."),
                row("Forecast", "Expected net cash flow", projectedNet, 0, projectedNet, 0, projectedNet < 0 ? "Shortage risk" : "Positive", projectedNet < 0 ? "Find at least " + MoneyUtil.mwk(Math.abs(projectedNet)) + " of savings or income." : "Allocate projected surplus intentionally."),
                row("Forecast", "Expected month-end balance", expectedMonthEndBalance, closingBalance, expectedMonthEndBalance - closingBalance, percentChange(expectedMonthEndBalance, closingBalance), expectedMonthEndBalance < 0 ? "Below zero risk" : "Above zero", expectedMonthEndBalance < 0 ? "Delay non-essential payments before the balance falls below zero." : "Maintain enough reserve for obligations.")
        );
        return packageFor(
                "Trends and Forecast",
                rows,
                "Estimated month-end balance is " + MoneyUtil.mwk(expectedMonthEndBalance) + ".",
                expectedMonthEndBalance < 0 ? "The forecast shows a possible cash shortage." : "The forecast does not show a negative month-end balance.",
                expectedMonthEndBalance < 0 ? "Reduce discretionary expenses and confirm expected income." : "Use the forecast surplus for goals or reserves.",
                "Forecasts are estimates based on current month activity, previous three-month averages, recurring plans and scheduled obligations."
        );
    }

    private ReportPackage financialHealthReport() {
        YearMonth month = selectedYearMonth();
        double income = database.transactionTotalByTypeForMonth("INCOME", month.toString());
        double expenses = database.transactionTotalByTypeForMonth("EXPENSE", month.toString());
        double savings = income - expenses;
        double savingsRate = percentOf(savings, income);
        double expenseRatio = percentOf(expenses, income);
        LoanTotals loans = loanTotalsThrough(month.atEndOfMonth());
        double debtRatio = percentOf(loans.liability(), Math.max(income, 1));
        double availableCash = database.listAccounts().stream().mapToDouble(Account::getCurrentBalance).sum();
        double averageExpense = averageMonthlyTotal(month.minusMonths(3), month.minusMonths(1), "EXPENSE");
        double emergencyCoverage = averageExpense <= 0 ? 0 : availableCash / averageExpense;
        long overBudget = database.listBudgetProgress(month.toString()).stream().filter(progress -> progress.getPercentUsed() > 100).count();
        double explicitObligations = activeScheduledObligations().stream()
                .filter(obligation -> dateInMonth(obligation.getDueDate(), month))
                .mapToDouble(ScheduledObligation::getAmount)
                .sum();
        double explicitScheduledDebt = activeLoanSchedules().stream()
                .filter(schedule -> "BORROWED".equalsIgnoreCase(schedule.getLoanDirection()))
                .mapToDouble(LoanScheduleRecord::getOutstandingAmount)
                .sum();
        debtRatio = percentOf(loans.liability() + explicitScheduledDebt, Math.max(income, 1));
        String health = financialHealthStatus(savingsRate, expenseRatio, debtRatio, emergencyCoverage, overBudget);
        List<ReportInsightRow> rows = List.of(
                row("Health", "Savings rate", savings, income, savings, savingsRate, savingsRate < 10 ? "Weak" : "Healthy", "Aim for a positive savings rate before adding obligations."),
                row("Health", "Expense-to-income ratio", expenses, income, income - expenses, expenseRatio, expenseRatio > 90 ? "High" : "Manageable", "Keep expenses below income and protect essentials."),
                row("Health", "Debt-to-income ratio", loans.liability() + explicitScheduledDebt, income, income - loans.liability() - explicitScheduledDebt, debtRatio, debtRatio > 40 ? "High debt pressure" : "Manageable debt", "Prioritize repayment if debt pressure is high."),
                row("Health", "Emergency-fund coverage", availableCash, averageExpense, availableCash - averageExpense, emergencyCoverage * 100, emergencyCoverage < 1 ? "Low coverage" : "Covered", "Build at least one month of expense coverage, then increase reserve."),
                row("Health", "Over-budget lines", overBudget, 0, overBudget, 0, overBudget > 0 ? "Budget pressure" : "On budget", overBudget > 0 ? "Open Budget vs Actual and correct overspending." : "Maintain budget tracking."),
                row("Health", "Scheduled obligations this month", explicitObligations, availableCash, availableCash - explicitObligations, percentOf(explicitObligations, availableCash), explicitObligations > availableCash ? "Shortfall risk" : "Covered", "Reserve cash for known due dates.")
        );
        return packageFor(
                "Financial Health",
                rows,
                "Financial Health: " + health + ". Savings rate is " + String.format("%.1f%%", savingsRate) + " and expense-to-income ratio is " + String.format("%.1f%%", expenseRatio) + ".",
                "Needs Attention".equals(health) ? "One or more major indicators are weak." : "No severe health warning detected from available data.",
                "Needs Attention".equals(health) ? "Focus on cash flow, budget compliance, and debt before new spending." : "Continue monitoring savings, expenses and debt monthly.",
                "Health score uses transactions, budgets, account balances, loan positions, scheduled obligations and loan schedules."
        );
    }

    private ReportPackage unusualTransactionsReport() {
        List<FinanceTransaction> transactions = allTransactions();
        List<ReportInsightRow> rows = new ArrayList<>();
        double averageExpense = transactions.stream().filter(this::isPosted).filter(this::isExpense).mapToDouble(FinanceTransaction::getAmount).average().orElse(0);
        LocalDate today = LocalDate.now();
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (FinanceTransaction tx : transactions) {
            String key = tx.getTransactionDate() + "|" + tx.getTransactionType() + "|" + tx.getAccountName() + "|" + categoryName(tx) + "|" + tx.getAmount();
            if (!seen.add(key)) {
                duplicates.add(key);
            }
        }
        for (FinanceTransaction tx : transactions) {
            LocalDate date = parseDate(tx.getTransactionDate());
            String key = tx.getTransactionDate() + "|" + tx.getTransactionType() + "|" + tx.getAccountName() + "|" + categoryName(tx) + "|" + tx.getAmount();
            if (duplicates.contains(key)) {
                rows.add(row("Duplicate", transactionLabel(tx), tx.getAmount(), 0, tx.getAmount(), 0, "Possible duplicate", "Verify whether this transaction was entered more than once."));
            }
            if (isExpense(tx) && averageExpense > 0 && tx.getAmount() > averageExpense * 2) {
                rows.add(row("Large expense", transactionLabel(tx), tx.getAmount(), averageExpense, tx.getAmount() - averageExpense, percentChange(tx.getAmount(), averageExpense), "Unusual size", "Confirm this expense and its category."));
            }
            if (date != null && date.isAfter(today)) {
                rows.add(row("Future date", transactionLabel(tx), tx.getAmount(), 0, tx.getAmount(), 0, "Future transaction", "Confirm whether this is scheduled or entered with the wrong date."));
            }
            if ("CANCELLED".equalsIgnoreCase(blankAs(tx.getTransactionStatus(), ""))) {
                rows.add(row("Cancelled", transactionLabel(tx), tx.getAmount(), 0, tx.getAmount(), 0, "Cancelled", "Ensure cancelled transactions are excluded from totals."));
            }
            if ((isIncome(tx) || isExpense(tx)) && categoryName(tx).equals("Uncategorized")) {
                rows.add(row("Missing category", transactionLabel(tx), tx.getAmount(), 0, tx.getAmount(), 0, "Incomplete", "Assign a category to improve report accuracy."));
            }
            if (isIncome(tx) && blankAs(tx.getTransactionPurpose(), "").toUpperCase(Locale.ENGLISH).contains("TRANSFER")) {
                rows.add(row("Transfer as income", transactionLabel(tx), tx.getAmount(), 0, tx.getAmount(), 0, "Possible misclassification", "Record transfers with transaction type TRANSFER to avoid double-counting."));
            }
        }
        for (Account account : database.listAccounts()) {
            if (account.getCurrentBalance() < 0) {
                rows.add(row("Negative balance", account.getAccountName(), account.getCurrentBalance(), 0, account.getCurrentBalance(), 0, "Account risk", "Reconcile this account and check missing income or duplicate expenses."));
            }
        }
        return packageFor(
                "Unusual Transactions",
                rows,
                rows.isEmpty() ? "No unusual transaction issue was detected." : rows.size() + " issue(s) were flagged for review.",
                rows.isEmpty() ? "No immediate anomaly risk detected." : "Unusual records may distort recommendations and totals.",
                rows.isEmpty() ? "Continue entering complete transaction details." : "Review flagged records and correct duplicates, categories, dates or classifications.",
                "Rules flag duplicates, large expenses, future dates, negative balances, cancelled transactions and missing categories."
        );
    }

    private ReportPackage recommendationsReport() {
        ReportPackage health = financialHealthReport();
        ReportPackage budget = budgetVsActualReport();
        ReportPackage obligations = upcomingObligationsReport();
        List<ReportInsightRow> rows = new ArrayList<>();
        rows.addAll(topActionRows(health.rows()));
        rows.addAll(topActionRows(budget.rows()));
        rows.addAll(topActionRows(obligations.rows()));
        if (rows.isEmpty()) {
            rows.add(row("Recommendation", "Keep records complete", 0, 0, 0, 0, "General", "Continue recording transactions, budgets, goals, loans and obligations."));
        }
        return packageFor(
                "Recommendations",
                rows,
                "Recommendations are generated from financial health, budget compliance and obligation coverage.",
                rows.stream().anyMatch(row -> row.getStatus().toLowerCase(Locale.ENGLISH).contains("risk") || row.getStatus().toLowerCase(Locale.ENGLISH).contains("pressure")) ? "Some action rows show risk or pressure." : "No severe recommendation risk detected.",
                "Open the source report for details before acting on a recommendation.",
                "These recommendations are deterministic rules from local PFMIS data, not a guarantee."
        );
    }

    private List<ReportInsightRow> topActionRows(List<ReportInsightRow> rows) {
        return rows.stream()
                .filter(row -> {
                    String status = row.getStatus().toLowerCase(Locale.ENGLISH);
                    return status.contains("risk")
                            || status.contains("pressure")
                            || status.contains("over")
                            || status.contains("weak")
                            || status.contains("high")
                            || status.contains("shortfall");
                })
                .limit(5)
                .toList();
    }

    private ReportPackage dataQualityReport() {
        List<FinanceTransaction> transactions = allTransactions();
        List<ReportInsightRow> rows = new ArrayList<>();
        rows.add(issueRow("Transactions without categories", transactions.stream().filter(tx -> (isIncome(tx) || isExpense(tx)) && categoryName(tx).equals("Uncategorized")).count(), "Assign categories for reliable analysis."));
        rows.add(issueRow("Missing descriptions", transactions.stream().filter(tx -> blankAs(tx.getDescription(), "").isBlank()).count(), "Add descriptions to important transactions."));
        rows.add(issueRow("Zero-value transactions", transactions.stream().filter(tx -> Math.abs(tx.getAmount()) < 0.005).count(), "Correct or remove zero-value records."));
        rows.add(issueRow("Invalid or missing dates", transactions.stream().filter(tx -> parseDate(tx.getTransactionDate()) == null).count(), "Correct invalid dates."));
        rows.add(issueRow("Future-dated transactions", transactions.stream().filter(tx -> {
            LocalDate date = parseDate(tx.getTransactionDate());
            return date != null && date.isAfter(LocalDate.now());
        }).count(), "Verify scheduled versus incorrectly dated transactions."));
        rows.add(issueRow("Duplicate-looking transactions", duplicateTransactionCount(transactions), "Review possible duplicate records."));
        rows.add(issueRow("Accounts with negative balances", database.listAccounts().stream().filter(account -> account.getCurrentBalance() < 0).count(), "Reconcile negative accounts."));
        rows.add(issueRow("Projects without budgets", database.listProjects().stream().filter(project -> project.getPlannedBudget() <= 0).count(), "Add planned project budgets."));
        rows.add(issueRow("Goals without monthly contribution", database.listGoals().stream().filter(goal -> goal.getRemainingAmount() > 0 && goal.getMonthlyContribution() <= 0).count(), "Add monthly goal contributions."));
        rows.add(issueRow("Loan records without schedules", loanScheduleGapCount(), "Add due dates and repayment schedules in Report Data Inputs."));
        rows.add(issueRow("Active obligations without due dates", activeScheduledObligations().stream().filter(obligation -> parseDate(obligation.getDueDate()) == null).count(), "Add due dates so upcoming obligations can forecast cash pressure."));
        rows.add(issueRow("Recurring plans without next due date", activeRecurringPlans().stream().filter(plan -> parseDate(plan.getNextDueDate()) == null).count(), "Add next due dates for recurring income and expenses."));
        rows.add(issueRow("Accounts without reconciliation", unreconciledAccountCount(), "Enter actual balances in Report Data Inputs."));
        rows.add(issueRow("Position items without valuation dates", activePositionItems().stream().filter(item -> parseDate(item.getValuationDate()) == null).count(), "Add valuation dates for net-worth reliability."));
        long issues = rows.stream().mapToLong(row -> Math.round(row.getAmount())).sum();
        return packageFor(
                "Data Quality Report",
                rows,
                issues == 0 ? "No data quality issue was detected by the current checks." : issues + " data quality issue(s) need review.",
                issues > 0 ? "Incomplete or inconsistent data can distort smart recommendations." : "Data quality is strong for the available checks.",
                issues > 0 ? "Correct the highest-count issues first." : "Continue recording complete details.",
                "Checks cover transactions, reports inputs, reconciliations, obligations, recurring plans, position items and loan schedules."
        );
    }

    private ReportInsightRow issueRow(String item, long count, String action) {
        return row("Data quality", item, count, 0, count, 0, count > 0 ? "Issue" : "OK", count > 0 ? action : "No action needed.");
    }

    private ReportPackage auditTrailReport() {
        List<ReportInsightRow> rows = new ArrayList<>();
        for (SystemLogRecord log : database.listSystemLogHistory(100)) {
            rows.add(row("System event", log.getCreatedAt() + " / " + log.getModuleName(), 0, 0, 0, 0, log.getSeverity(), log.getActionName() + ": " + blankAs(log.getDetails(), "")));
        }
        for (AiInteractionRecord ai : database.listAiInteractionHistory(100)) {
            rows.add(row("AI interaction", ai.getCreatedAt() + " / " + ai.getModuleName(), 0, 0, 0, 0, ai.getStatus(), ai.getActionName() + " using " + ai.getProviderName()));
        }
        return packageFor(
                "Audit Trail",
                rows,
                rows.isEmpty() ? "No audit events are recorded yet." : rows.size() + " recent audit and AI event(s) are shown.",
                rows.stream().anyMatch(row -> row.getStatus().equalsIgnoreCase("ERROR")) ? "Some audit events are errors." : "No recent error event is visible in this report.",
                "Review security, backup, restore, workspace and AI actions regularly.",
                "The report uses system event and AI interaction logs. Field-level before/after values require deeper audit capture."
        );
    }

    private ReportPackage backupHistoryReport() {
        List<BackupRecord> records = database.listBackupHistory();
        List<ReportInsightRow> rows = records.stream()
                .map(record -> row("Backup", record.getCreatedAt() + " / " + record.getStatus(), record.getFileSize(), 0, record.getFileSize(), 0, record.getStatus(), record.getBackupFile()))
                .toList();
        return packageFor(
                "Backup and Restore History",
                rows,
                records.isEmpty() ? "No backup history is available." : records.size() + " backup record(s) found.",
                records.isEmpty() ? "No verified backup record is available." : "Backup files are visible in history.",
                records.isEmpty() ? "Create a verified backup before major changes." : "Validate backups periodically and keep copies outside the working computer.",
                "This report uses recorded backup history and discovered backup files."
        );
    }

    private void setCategoryReport(String title, String labelColumn, List<ReportRow> rows) {
        setCategoryReport(title, labelColumn, false, rows);
    }

    private void setCategoryReport(String title, String labelColumn, boolean showAccount, List<ReportRow> rows) {
        categoryReportTitle.setText(title);
        categoryLabelColumn.setText(labelColumn);
        categoryAccountColumn.setVisible(showAccount);
        categoryTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void setProjectReport(String title, List<ReportRow> rows) {
        projectReportTitle.setText(title);
        projectLabelColumn.setText("Project");
        projectTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void setAccountReport(String title, List<ReportRow> rows) {
        accountReportTitle.setText(title);
        accountLabelColumn.setText("Account");
        accountTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void setAnalysisReport(ReportPackage report) {
        analysisReportTitle.setText(report.title());
        analysisTable.setItems(FXCollections.observableArrayList(report.rows()));
        updateAnalysisChart(report);
        financialInterpretationLabel.setText(report.interpretation());
        risksIdentifiedLabel.setText(report.risks());
        recommendedActionsLabel.setText(report.actions());
        confidenceLimitationsLabel.setText(report.confidence());
        setVisible(analysisReportPane, true);
        setVisible(smartConclusionPane, true);
    }

    private void updateAnalysisChart(ReportPackage report) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        report.rows().stream()
                .filter(row -> Math.abs(row.getAmount()) > 0.005)
                .limit(12)
                .forEach(row -> series.getData().add(new XYChart.Data<>(chartItemLabel(row), row.getAmount())));
        analysisChart.getData().setAll(series);
        boolean hasData = !series.getData().isEmpty();
        analysisChart.setVisible(hasData);
        analysisChart.setManaged(hasData);
    }

    private String chartItemLabel(ReportInsightRow row) {
        String label = blankAs(row.getItem(), row.getArea());
        return label.length() <= 26 ? label : label.substring(0, 23) + "...";
    }

    private void clearAnalysis() {
        analysisTable.getItems().clear();
        analysisChart.getData().clear();
        financialInterpretationLabel.setText("");
        risksIdentifiedLabel.setText("");
        recommendedActionsLabel.setText("");
        confidenceLimitationsLabel.setText("");
        setVisible(analysisReportPane, false);
        setVisible(smartConclusionPane, false);
    }

    private void showReportPanes(boolean showCategory, boolean showProject, boolean showAccount) {
        setVisible(categoryReportPane, showCategory);
        setVisible(projectReportPane, showProject);
        setVisible(accountReportPane, showAccount);
    }

    private void setVisible(VBox pane, boolean visible) {
        pane.setVisible(visible);
        pane.setManaged(visible);
    }

    private String selectedMonthKey() {
        return selectedYearMonth().toString();
    }

    private YearMonth selectedYearMonth() {
        int selectedMonth = monthBox.getSelectionModel().getSelectedIndex() + 1;
        Integer selectedYear = yearBox.getValue();
        if (selectedMonth <= 0) {
            selectedMonth = LocalDate.now().getMonthValue();
        }
        if (selectedYear == null) {
            selectedYear = Year.now().getValue();
        }
        return YearMonth.of(selectedYear, selectedMonth);
    }

    private int selectedYear() {
        Integer selectedYear = yearBox.getValue();
        return selectedYear == null ? Year.now().getValue() : selectedYear;
    }

    private DateRange selectedMonthRange() {
        YearMonth month = selectedYearMonth();
        return applyCustomDateRange(new DateRange(month.atDay(1), month.atEndOfMonth(), month.toString()));
    }

    private DateRange selectedQuarterRange() {
        YearMonth selected = selectedYearMonth();
        int startMonth = ((selected.getMonthValue() - 1) / 3) * 3 + 1;
        YearMonth start = YearMonth.of(selected.getYear(), startMonth);
        YearMonth end = start.plusMonths(2);
        return applyCustomDateRange(new DateRange(start.atDay(1), end.atEndOfMonth(), "Q" + ((startMonth - 1) / 3 + 1) + " " + selected.getYear()));
    }

    private DateRange selectedHalfYearRange() {
        YearMonth selected = selectedYearMonth();
        int startMonth = selected.getMonthValue() <= 6 ? 1 : 7;
        YearMonth start = YearMonth.of(selected.getYear(), startMonth);
        YearMonth end = start.plusMonths(5);
        return applyCustomDateRange(new DateRange(start.atDay(1), end.atEndOfMonth(), (startMonth == 1 ? "H1 " : "H2 ") + selected.getYear()));
    }

    private DateRange selectedYearRange() {
        int year = selectedYear();
        return applyCustomDateRange(new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), String.valueOf(year)));
    }

    private DateRange applyCustomDateRange(DateRange fallback) {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        if (start == null && end == null) {
            return fallback;
        }
        if (start == null) {
            start = fallback.start();
        }
        if (end == null) {
            end = fallback.end();
        }
        if (start.isAfter(end)) {
            LocalDate originalStart = start;
            start = end;
            end = originalStart;
        }
        return new DateRange(start, end, start + " to " + end);
    }

    private DateRange selectedRangeForGrouping() {
        String grouping = groupingBox.getValue();
        if ("Quarterly".equals(grouping)) {
            return selectedQuarterRange();
        }
        if ("Annual".equals(grouping)) {
            return selectedYearRange();
        }
        return selectedMonthRange();
    }

    private DateRange previousRange(DateRange range) {
        long days = ChronoUnit.DAYS.between(range.start(), range.end()) + 1;
        LocalDate end = range.start().minusDays(1);
        LocalDate start = end.minusDays(days - 1);
        return new DateRange(start, end, start + " to " + end);
    }

    private YearMonth previousMonth(LocalDate startDate) {
        return YearMonth.from(startDate).minusMonths(1);
    }

    private List<FinanceTransaction> allTransactions() {
        return database.listRecentTransactions(REPORT_TRANSACTION_LIMIT);
    }

    private List<FinanceTransaction> filteredTransactions(DateRange range) {
        return allTransactions().stream()
                .filter(tx -> dateInRange(tx, range))
                .filter(this::matchesFilters)
                .toList();
    }

    private boolean dateInRange(FinanceTransaction tx, DateRange range) {
        LocalDate date = parseDate(tx.getTransactionDate());
        return date != null && !date.isBefore(range.start()) && !date.isAfter(range.end());
    }

    private boolean matchesFilters(FinanceTransaction tx) {
        if (!matchesSelection(accountFilterBox.getValue(), ALL_ACCOUNTS, tx.getAccountName())) {
            return false;
        }
        if (!matchesSelection(categoryFilterBox.getValue(), ALL_CATEGORIES, categoryName(tx))) {
            return false;
        }
        if (!matchesSelection(projectFilterBox.getValue(), ALL_PROJECTS, blankAs(tx.getProjectName(), ""))) {
            return false;
        }
        return matchesStatus(tx);
    }

    private boolean matchesSelection(String selected, String allLabel, String value) {
        return selected == null || selected.equals(allLabel) || selected.equals(blankAs(value, ""));
    }

    private boolean matchesStatus(FinanceTransaction tx) {
        String selected = statusFilterBox.getValue();
        String status = blankAs(tx.getTransactionStatus(), "COMPLETED").toUpperCase(Locale.ENGLISH);
        if (selected == null || POSTED_TRANSACTIONS.equals(selected)) {
            return !"CANCELLED".equals(status);
        }
        return switch (selected) {
            case ALL_STATUSES -> true;
            case "Completed" -> "COMPLETED".equals(status) || "RECEIVED".equals(status);
            case "Open or pending" -> "OPEN".equals(status) || "PENDING".equals(status);
            case "Cancelled" -> "CANCELLED".equals(status);
            default -> !"CANCELLED".equals(status);
        };
    }

    private boolean isPosted(FinanceTransaction tx) {
        return !"CANCELLED".equalsIgnoreCase(blankAs(tx.getTransactionStatus(), ""));
    }

    private boolean isIncome(FinanceTransaction tx) {
        return "INCOME".equalsIgnoreCase(blankAs(tx.getTransactionType(), ""));
    }

    private boolean isExpense(FinanceTransaction tx) {
        return "EXPENSE".equalsIgnoreCase(blankAs(tx.getTransactionType(), ""));
    }

    private boolean isTransfer(FinanceTransaction tx) {
        return "TRANSFER".equalsIgnoreCase(blankAs(tx.getTransactionType(), ""));
    }

    private boolean isLoanCashMovement(FinanceTransaction tx) {
        return "LOAN".equalsIgnoreCase(blankAs(tx.getTransactionType(), ""));
    }

    private boolean isCashAffecting(FinanceTransaction tx) {
        return isIncome(tx) || isExpense(tx) || isTransfer(tx) || isLoanCashMovement(tx);
    }

    private double transferSignedAmount(FinanceTransaction tx) {
        if ("TRANSFER_IN".equalsIgnoreCase(blankAs(tx.getTransactionPurpose(), ""))) {
            return tx.getAmount();
        }
        if ("TRANSFER_OUT".equalsIgnoreCase(blankAs(tx.getTransactionPurpose(), ""))) {
            return -tx.getAmount();
        }
        return 0;
    }

    private double loanSignedAmount(FinanceTransaction tx) {
        String purpose = blankAs(tx.getTransactionPurpose(), "").toUpperCase(Locale.ENGLISH);
        if ("MONEY_BORROWED".equals(purpose) || "LENT_REPAID".equals(purpose)) {
            return tx.getAmount();
        }
        if ("MONEY_LENT".equals(purpose) || "BORROWED_REPAID".equals(purpose)) {
            return -tx.getAmount();
        }
        return 0;
    }

    private double totalByType(List<FinanceTransaction> transactions, String type) {
        return transactions.stream()
                .filter(this::isPosted)
                .filter(tx -> type.equalsIgnoreCase(blankAs(tx.getTransactionType(), "")))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();
    }

    private PeriodTotals totals(List<FinanceTransaction> transactions) {
        return new PeriodTotals(totalByType(transactions, "INCOME"), totalByType(transactions, "EXPENSE"));
    }

    private Map<String, PeriodTotals> groupedTotals(List<FinanceTransaction> transactions, Function<FinanceTransaction, String> groupFunction) {
        Map<String, List<FinanceTransaction>> grouped = transactions.stream()
                .filter(this::isPosted)
                .collect(Collectors.groupingBy(groupFunction, TreeMap::new, Collectors.toList()));
        Map<String, PeriodTotals> totals = new LinkedHashMap<>();
        for (Map.Entry<String, List<FinanceTransaction>> entry : grouped.entrySet()) {
            totals.put(entry.getKey(), totals(entry.getValue()));
        }
        return totals;
    }

    private Map<String, Double> totalsBy(List<FinanceTransaction> transactions, Function<FinanceTransaction, String> groupFunction, Function<FinanceTransaction, Boolean> filter) {
        return transactions.stream()
                .filter(this::isPosted)
                .filter(tx -> Boolean.TRUE.equals(filter.apply(tx)))
                .collect(Collectors.groupingBy(
                        tx -> blankAs(groupFunction.apply(tx), "Unassigned"),
                        () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER),
                        Collectors.summingDouble(this::signedAmountForGrouping)
                ));
    }

    private double signedAmountForGrouping(FinanceTransaction tx) {
        if (isExpense(tx) || isIncome(tx)) {
            return tx.getAmount();
        }
        if (isTransfer(tx)) {
            return transferSignedAmount(tx);
        }
        if (isLoanCashMovement(tx)) {
            return loanSignedAmount(tx);
        }
        return tx.getAmount();
    }

    private Map.Entry<String, Double> largestEntry(Map<String, Double> values) {
        return values.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .orElse(null);
    }

    private Map<String, Double> averageCategorySpendingBefore(LocalDate startDate, int months) {
        Map<String, Double> totals = new HashMap<>();
        for (int index = 1; index <= months; index++) {
            YearMonth month = YearMonth.from(startDate).minusMonths(index);
            DateRange range = new DateRange(month.atDay(1), month.atEndOfMonth(), month.toString());
            totalsBy(filteredTransactions(range), this::categoryName, this::isExpense)
                    .forEach((category, amount) -> totals.merge(category, amount / months, Double::sum));
        }
        return totals;
    }

    private double averageMonthlyTotal(YearMonth start, YearMonth end, String type) {
        double total = 0;
        int count = 0;
        YearMonth cursor = start;
        while (!cursor.isAfter(end)) {
            total += database.transactionTotalByTypeForMonth(type, cursor.toString());
            count++;
            cursor = cursor.plusMonths(1);
        }
        return count == 0 ? 0 : total / count;
    }

    private double accountBalanceThrough(YearMonth month) {
        return database.accountBalanceReportThroughMonth(month.toString()).stream()
                .mapToDouble(ReportRow::getAmount)
                .sum();
    }

    private double requiredMonthlyContribution(Goal goal, LocalDate today) {
        if (goal.getRemainingAmount() <= 0) {
            return 0;
        }
        LocalDate target = parseDate(goal.getTargetDate());
        if (target == null || !target.isAfter(today)) {
            return goal.getRemainingAmount();
        }
        long months = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(target)) + 1);
        return goal.getRemainingAmount() / months;
    }

    private double predictedMonthEnd(double monthToDateAmount) {
        YearMonth selected = selectedYearMonth();
        YearMonth current = YearMonth.now();
        if (!selected.equals(current)) {
            return monthToDateAmount;
        }
        int elapsed = Math.max(1, LocalDate.now().getDayOfMonth());
        return monthToDateAmount / elapsed * selected.lengthOfMonth();
    }

    private double upcomingObligationsAmount() {
        double budgetRemaining = database.listBudgetProgress(selectedMonthKey()).stream()
                .mapToDouble(progress -> Math.max(0, progress.getRemaining()))
                .sum();
        double goalContributions = database.listGoals().stream()
                .filter(goal -> goal.getRemainingAmount() > 0)
                .mapToDouble(Goal::getMonthlyContribution)
                .sum();
        LocalDate today = LocalDate.now();
        LocalDate nextThirtyDays = today.plusDays(30);
        double scheduledObligations = activeScheduledObligations().stream()
                .filter(obligation -> {
                    LocalDate date = parseDate(obligation.getDueDate());
                    return date != null && dateBetween(date, today, nextThirtyDays);
                })
                .mapToDouble(ScheduledObligation::getAmount)
                .sum();
        double recurringExpenses = activeRecurringPlans().stream()
                .filter(plan -> "EXPENSE".equalsIgnoreCase(plan.getTransactionType()))
                .filter(plan -> {
                    LocalDate date = parseDate(plan.getNextDueDate());
                    return date != null && dateBetween(date, today, nextThirtyDays);
                })
                .mapToDouble(RecurringTransactionPlan::getAmount)
                .sum();
        double loanRepayments = activeLoanSchedules().stream()
                .filter(schedule -> "BORROWED".equalsIgnoreCase(schedule.getLoanDirection()))
                .filter(schedule -> {
                    LocalDate date = parseDate(schedule.getDueDate());
                    return date != null && dateBetween(date, today, nextThirtyDays);
                })
                .mapToDouble(schedule -> schedule.getPaymentAmount() > 0 ? schedule.getPaymentAmount() : schedule.getOutstandingAmount())
                .sum();
        return budgetRemaining + goalContributions + scheduledObligations + recurringExpenses + loanRepayments;
    }

    private List<ReportPositionItem> activePositionItems() {
        return database.listReportPositionItems().stream()
                .filter(item -> isActiveReportStatus(item.getStatus()))
                .toList();
    }

    private List<Asset> activeAssetRecords() {
        return database.listAssets().stream()
                .filter(asset -> !isTerminalAssetStatus(asset.getStatus()))
                .filter(asset -> !"PENDING_REGISTRATION".equals(assetStatusCode(asset.getStatus())))
                .toList();
    }

    private String assetRegisterItem(Asset asset) {
        List<String> parts = new ArrayList<>();
        parts.add(asset.getAssetName());
        if (asset.getQuantity() > 1.005 || asset.getQuantity() < 0.995) {
            parts.add("qty " + String.format(Locale.US, "%,.2f", asset.getQuantity()));
        }
        if (asset.getProjectName() != null && !asset.getProjectName().isBlank()) {
            parts.add("project " + asset.getProjectName());
        }
        if (asset.getPurchaseTransactionId() != null) {
            parts.add("txn " + asset.getPurchaseTransactionId());
        }
        return String.join(" / ", parts);
    }

    private String assetRecommendation(Asset asset) {
        String status = assetStatusCode(asset.getStatus());
        if ("PENDING_REGISTRATION".equals(status)) {
            return "Complete acquisition evidence before including this asset in active reports.";
        }
        if ("UNDER_MAINTENANCE".equals(status) || "DAMAGED".equals(status) || "FROZEN".equals(status)) {
            return "Review condition, maintenance and valuation before further disposal or sale.";
        }
        if (isTerminalAssetStatus(status)) {
            return "Keep this record for history. Do not delete sold or disposed assets.";
        }
        if (asset.getPurchaseTransactionId() == null && "LINK_EXISTING_TRANSACTION".equalsIgnoreCase(blankAs(asset.getPaymentTreatment(), ""))) {
            return "Link the purchase transaction so financial evidence is complete.";
        }
        return "Keep valuation, location and supporting documents current.";
    }

    private String valuationStatus(double currentValue, double cost) {
        if (cost <= 0 && currentValue > 0) {
            return "Opening value";
        }
        if (currentValue < cost * 0.75) {
            return "Value reduced";
        }
        if (currentValue > cost * 1.25) {
            return "Value increased";
        }
        return "Within range";
    }

    private boolean isAssetDisposalEvent(String eventType) {
        return Set.of(
                "SALE",
                "PARTIAL_SALE",
                "TRANSFERRED",
                "DONATED",
                "WRITTEN_OFF",
                "LOST",
                "DISPOSED",
                "ARCHIVED"
        ).contains(assetStatusCode(eventType));
    }

    private String disposalRecommendation(AssetEvent event) {
        String type = assetStatusCode(event.getEventType());
        if ("SALE".equals(type) || "PARTIAL_SALE".equals(type)) {
            return "Review sale proceeds, selling costs and gain or loss evidence.";
        }
        if ("WRITE_OFF".equals(type) || "LOST".equals(type) || "DISPOSAL".equals(type)) {
            return "Confirm approval, reason and supporting evidence are attached.";
        }
        if ("DONATION".equals(type) || "TRANSFER".equals(type)) {
            return "Confirm recipient, transfer evidence and ownership change.";
        }
        return "Preserve this asset event in history.";
    }

    private boolean isTerminalAssetStatus(String status) {
        return Set.of(
                "TRANSFERRED",
                "DONATED",
                "SOLD",
                "LOST",
                "WRITTEN_OFF",
                "DISPOSED",
                "ARCHIVED"
        ).contains(assetStatusCode(status));
    }

    private String assetStatusCode(String status) {
        String value = blankAs(status, "ACTIVE").toUpperCase(Locale.ENGLISH).replace(' ', '_');
        if ("TRANSFER".equals(value)) {
            return "TRANSFERRED";
        }
        if ("DONATION".equals(value)) {
            return "DONATED";
        }
        if ("WRITE_OFF".equals(value)) {
            return "WRITTEN_OFF";
        }
        if ("DISPOSAL".equals(value)) {
            return "DISPOSED";
        }
        if ("ARCHIVE".equals(value)) {
            return "ARCHIVED";
        }
        return value;
    }

    private String assetStatusLabel(String status) {
        String value = assetStatusCode(status).replace('_', ' ').toLowerCase(Locale.ENGLISH);
        StringBuilder builder = new StringBuilder();
        for (String part : value.split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private List<ScheduledObligation> activeScheduledObligations() {
        return database.listScheduledObligations().stream()
                .filter(obligation -> isActiveReportStatus(obligation.getStatus()))
                .toList();
    }

    private List<RecurringTransactionPlan> activeRecurringPlans() {
        return database.listRecurringTransactionPlans().stream()
                .filter(plan -> isActiveReportStatus(plan.getStatus()))
                .toList();
    }

    private List<LoanScheduleRecord> activeLoanSchedules() {
        return database.listLoanSchedules().stream()
                .filter(schedule -> isActiveReportStatus(schedule.getStatus()))
                .toList();
    }

    private boolean isActiveReportStatus(String status) {
        String value = blankAs(status, "ACTIVE").toUpperCase(Locale.ENGLISH);
        return !List.of("CANCELLED", "INACTIVE", "COMPLETED", "SETTLED", "ARCHIVED").contains(value);
    }

    private boolean dateBetween(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private boolean dateInMonth(String dateValue, YearMonth month) {
        LocalDate date = parseDate(dateValue);
        return date != null && YearMonth.from(date).equals(month);
    }

    private long unreconciledAccountCount() {
        Set<String> reconciledAccounts = database.listLatestAccountReconciliations().stream()
                .map(AccountReconciliationRecord::getAccountName)
                .collect(Collectors.toSet());
        return database.listAccounts().stream()
                .filter(account -> !"INACTIVE".equalsIgnoreCase(account.getStatus()))
                .filter(account -> !reconciledAccounts.contains(account.getAccountName()))
                .count();
    }

    private long loanScheduleGapCount() {
        long openTransactionPositions = loanPositionsThrough(LocalDate.now()).values().stream()
                .filter(position -> position.lentOutstanding() > 0 || position.borrowedOutstanding() > 0)
                .count();
        long activeSchedules = activeLoanSchedules().size();
        return Math.max(0, openTransactionPositions - activeSchedules);
    }

    private LoanTotals loanTotalsThrough(LocalDate date) {
        Map<String, LoanPosition> positions = loanPositionsThrough(date);
        double receivable = positions.values().stream().mapToDouble(LoanPosition::lentOutstanding).sum();
        double liability = positions.values().stream().mapToDouble(LoanPosition::borrowedOutstanding).sum();
        return new LoanTotals(receivable, liability);
    }

    private Map<String, LoanPosition> loanPositionsThrough(LocalDate date) {
        Map<String, LoanPosition> positions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FinanceTransaction tx : allTransactions()) {
            LocalDate txDate = parseDate(tx.getTransactionDate());
            if (txDate == null || txDate.isAfter(date) || !isPosted(tx)) {
                continue;
            }
            String purpose = blankAs(tx.getTransactionPurpose(), "").toUpperCase(Locale.ENGLISH);
            if (!Set.of("MONEY_LENT", "SUPPORT_GIVEN", "LENT_REPAID", "MONEY_BORROWED", "BORROWED_REPAID").contains(purpose)) {
                continue;
            }
            String person = blankAs(tx.getPersonName(), "Unassigned");
            LoanPosition position = positions.computeIfAbsent(person, LoanPosition::new);
            switch (purpose) {
                case "MONEY_LENT", "SUPPORT_GIVEN" -> position.addLent(tx.getAmount(), txDate);
                case "LENT_REPAID" -> position.addLentRepaid(tx.getAmount());
                case "MONEY_BORROWED" -> position.addBorrowed(tx.getAmount(), txDate);
                case "BORROWED_REPAID" -> position.addBorrowedRepaid(tx.getAmount());
                default -> {
                }
            }
        }
        return positions;
    }

    private String agingStatus(LocalDate date) {
        return agingBucket(date);
    }

    private String agingBucket(LocalDate date) {
        if (date == null) {
            return "Not yet due / current";
        }
        long days = ChronoUnit.DAYS.between(date, LocalDate.now());
        if (days <= 0) {
            return "Not yet due / current";
        }
        if (days <= 30) {
            return "1-30 days overdue";
        }
        if (days <= 60) {
            return "31-60 days overdue";
        }
        if (days <= 90) {
            return "61-90 days overdue";
        }
        return "More than 90 days overdue";
    }

    private String financialHealthStatus(double savingsRate, double expenseRatio, double debtRatio, double emergencyCoverage, long overBudget) {
        int weak = 0;
        if (savingsRate < 5) {
            weak++;
        }
        if (expenseRatio > 95) {
            weak++;
        }
        if (debtRatio > 40) {
            weak++;
        }
        if (emergencyCoverage < 1) {
            weak++;
        }
        if (overBudget > 0) {
            weak++;
        }
        if (weak >= 2) {
            return "Needs Attention";
        }
        return weak == 1 ? "Watch" : "Stable";
    }

    private long duplicateTransactionCount(List<FinanceTransaction> transactions) {
        Set<String> seen = new HashSet<>();
        long duplicates = 0;
        for (FinanceTransaction tx : transactions) {
            String key = tx.getTransactionDate() + "|" + tx.getTransactionType() + "|" + tx.getAccountName() + "|" + categoryName(tx) + "|" + tx.getAmount();
            if (!seen.add(key)) {
                duplicates++;
            }
        }
        return duplicates;
    }

    private String recurringKey(FinanceTransaction tx) {
        return blankAs(tx.getTransactionType(), "Unknown")
                + " / "
                + categoryName(tx)
                + " / "
                + blankAs(tx.getAccountName(), "Unknown account")
                + " / "
                + blankAs(tx.getPaymentMethod(), "No payment method")
                + " / "
                + normalizedDescription(tx.getDescription());
    }

    private String normalizedDescription(String description) {
        String text = blankAs(description, "No description").toLowerCase(Locale.ENGLISH).trim();
        if (text.length() > 40) {
            return text.substring(0, 40);
        }
        return text;
    }

    private String periodKey(FinanceTransaction tx) {
        LocalDate date = parseDate(tx.getTransactionDate());
        if (date == null) {
            return "Invalid date";
        }
        String grouping = groupingBox.getValue();
        return switch (grouping == null ? "Monthly" : grouping) {
            case "Daily" -> date.toString();
            case "Weekly" -> date.getYear() + "-W" + String.format("%02d", date.get(WeekFields.ISO.weekOfWeekBasedYear()));
            case "Quarterly" -> date.getYear() + "-Q" + (((date.getMonthValue() - 1) / 3) + 1);
            case "Annual" -> String.valueOf(date.getYear());
            default -> YearMonth.from(date).toString();
        };
    }

    private String categoryName(FinanceTransaction tx) {
        return blankAs(tx.getCategoryName(), "Uncategorized");
    }

    private String transactionLabel(FinanceTransaction tx) {
        return tx.getTransactionDate()
                + " / "
                + blankAs(tx.getAccountName(), "Unknown account")
                + " / "
                + categoryName(tx)
                + " / "
                + blankAs(tx.getDescription(), "No description");
    }

    private String statusForNet(double value) {
        return value < 0 ? "Negative" : value > 0 ? "Positive" : "Neutral";
    }

    private String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String normalized = value.length() >= 10 ? value.substring(0, 10) : value;
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private double percentOf(double amount, double total) {
        return Math.abs(total) < 0.005 ? 0 : amount / total * 100;
    }

    private double percentChange(double current, double previous) {
        return Math.abs(previous) < 0.005 ? 0 : (current - previous) / Math.abs(previous) * 100;
    }

    private ReportInsightRow row(
            String area,
            String item,
            double amount,
            double comparison,
            double variance,
            double percent,
            String status,
            String recommendation
    ) {
        return new ReportInsightRow(area, item, amount, comparison, variance, percent, status, recommendation);
    }

    private ReportPackage packageFor(String title, List<ReportInsightRow> rows, String interpretation, String risks, String actions, String confidence) {
        return new ReportPackage(title, rows, interpretation, risks, actions, confidence);
    }

    private String dataConfidence(List<FinanceTransaction> transactions) {
        long missingCategories = transactions.stream()
                .filter(tx -> (isIncome(tx) || isExpense(tx)) && categoryName(tx).equals("Uncategorized"))
                .count();
        long invalidDates = transactions.stream().filter(tx -> parseDate(tx.getTransactionDate()) == null).count();
        if (transactions.isEmpty()) {
            return "Confidence is limited because no transaction records matched the selected filters.";
        }
        if (missingCategories > 0 || invalidDates > 0) {
            return "Confidence is moderate. " + missingCategories + " transaction(s) are uncategorized and " + invalidDates + " have invalid dates.";
        }
        return "Confidence is based on " + transactions.size() + " posted transaction(s) that matched the selected filters.";
    }

    @FXML
    private void exportPdf() {
        UiAlerts.info("Use the print dialog and choose a PDF printer to export the visible report as PDF.");
        printReport();
    }

    @FXML
    private void exportExcel() {
        exportVisibleReports();
    }

    @FXML
    private void exportCsv() {
        exportVisibleReports();
    }

    @FXML
    private void printReport() {
        List<ReportTable<?>> tables = visibleReportTables();
        if (tables.isEmpty()) {
            UiAlerts.info("No visible report table to print.");
            return;
        }
        for (ReportTable<?> reportTable : tables) {
            TableActions.printTable(reportTable.table(), reportTable.title());
        }
    }

    private void exportVisibleReports() {
        List<ReportTable<?>> tables = visibleReportTables();
        if (tables.isEmpty()) {
            UiAlerts.info("No visible report table to export.");
            return;
        }
        for (ReportTable<?> reportTable : tables) {
            exportReportTable(reportTable);
        }
    }

    private <T> void exportReportTable(ReportTable<T> reportTable) {
        TableActions.exportVisibleTableToCsv(reportTable.table(), reportTable.title());
    }

    private void configureContextMenus() {
        TableActions.installRowContextMenu(categoryTable, row -> reportRowMenuItems(row, categoryTable, categoryReportTitle.getText()));
        TableActions.installRowContextMenu(projectTable, row -> reportRowMenuItems(row, projectTable, projectReportTitle.getText()));
        TableActions.installRowContextMenu(accountTable, row -> reportRowMenuItems(row, accountTable, accountReportTitle.getText()));
        TableActions.installRowContextMenu(analysisTable, row -> insightRowMenuItems(row));
    }

    private List<MenuItem> reportRowMenuItems(ReportRow row, TableView<ReportRow> table, String title) {
        return List.of(
                TableActions.menuItem("View Report Row", () -> viewReportRow(row, title)),
                TableActions.separator(),
                TableActions.copyRowItem(table, row),
                TableActions.exportTableItem(table, title),
                TableActions.printTableItem(table, title),
                TableActions.refreshItem(this::refresh)
        );
    }

    private List<MenuItem> insightRowMenuItems(ReportInsightRow row) {
        return List.of(
                TableActions.menuItem("View Insight", () -> viewInsightRow(row)),
                TableActions.separator(),
                TableActions.copyRowItem(analysisTable, row),
                TableActions.exportTableItem(analysisTable, analysisReportTitle.getText()),
                TableActions.printTableItem(analysisTable, analysisReportTitle.getText()),
                TableActions.refreshItem(this::refresh)
        );
    }

    private void viewReportRow(ReportRow row, String title) {
        if (row == null) {
            return;
        }
        String accountLine = row.getAccount() == null || row.getAccount().isBlank()
                ? ""
                : "\nAccount: " + row.getAccount();
        UiAlerts.info(
                title
                        + "\nLabel: " + row.getLabel()
                        + accountLine
                        + "\nAmount: " + MoneyUtil.mwk(row.getAmount())
        );
    }

    private void viewInsightRow(ReportInsightRow row) {
        if (row == null) {
            return;
        }
        UiAlerts.info(
                analysisReportTitle.getText()
                        + "\nArea: " + row.getArea()
                        + "\nItem: " + row.getItem()
                        + "\nAmount: " + MoneyUtil.mwk(row.getAmount())
                        + "\nComparison: " + MoneyUtil.mwk(row.getComparisonAmount())
                        + "\nVariance: " + MoneyUtil.mwk(row.getVarianceAmount())
                        + "\nPercent: " + String.format("%.2f%%", row.getPercentage())
                        + "\nStatus: " + row.getStatus()
                        + "\nRecommended action: " + row.getRecommendation()
        );
    }

    private List<ReportTable<?>> visibleReportTables() {
        List<ReportTable<?>> tables = new ArrayList<>();
        if (categoryReportPane.isVisible() && categoryReportPane.isManaged()) {
            tables.add(new ReportTable<>(categoryTable, categoryReportTitle.getText()));
        }
        if (projectReportPane.isVisible() && projectReportPane.isManaged()) {
            tables.add(new ReportTable<>(projectTable, projectReportTitle.getText()));
        }
        if (accountReportPane.isVisible() && accountReportPane.isManaged()) {
            tables.add(new ReportTable<>(accountTable, accountReportTitle.getText()));
        }
        if (analysisReportPane.isVisible() && analysisReportPane.isManaged()) {
            tables.add(new ReportTable<>(analysisTable, analysisReportTitle.getText()));
        }
        return tables;
    }

    private record DateRange(LocalDate start, LocalDate end, String label) {
    }

    private record PeriodTotals(double income, double expenses) {
        double net() {
            return income - expenses;
        }

        double margin() {
            return income == 0 ? 0 : net() / income * 100;
        }
    }

    private record LoanTotals(double receivable, double liability) {
    }

    private static final class LoanPosition {
        private final String person;
        private double lent;
        private double lentRepaid;
        private double borrowed;
        private double borrowedRepaid;
        private LocalDate oldestLentDate;
        private LocalDate oldestBorrowedDate;

        private LoanPosition(String person) {
            this.person = person;
        }

        private String person() {
            return person;
        }

        private void addLent(double amount, LocalDate date) {
            lent += amount;
            oldestLentDate = earliest(oldestLentDate, date);
        }

        private void addLentRepaid(double amount) {
            lentRepaid += amount;
        }

        private void addBorrowed(double amount, LocalDate date) {
            borrowed += amount;
            oldestBorrowedDate = earliest(oldestBorrowedDate, date);
        }

        private void addBorrowedRepaid(double amount) {
            borrowedRepaid += amount;
        }

        private double lent() {
            return lent;
        }

        private double lentRepaid() {
            return lentRepaid;
        }

        private double borrowed() {
            return borrowed;
        }

        private double borrowedRepaid() {
            return borrowedRepaid;
        }

        private double lentOutstanding() {
            return Math.max(0, lent - lentRepaid);
        }

        private double borrowedOutstanding() {
            return Math.max(0, borrowed - borrowedRepaid);
        }

        private LocalDate oldestLentDate() {
            return oldestLentDate;
        }

        private LocalDate oldestBorrowedDate() {
            return oldestBorrowedDate;
        }

        private LocalDate earliest(LocalDate existing, LocalDate candidate) {
            if (candidate == null) {
                return existing;
            }
            return existing == null || candidate.isBefore(existing) ? candidate : existing;
        }
    }

    private record ReportGroup(
            String title,
            String description,
            List<String> reportTypes,
            List<String> quickReports
    ) {
        private String defaultReport() {
            return reportTypes.isEmpty() ? "Monthly Summary" : reportTypes.getFirst();
        }
    }

    private record ReportPackage(
            String title,
            List<ReportInsightRow> rows,
            String interpretation,
            String risks,
            String actions,
            String confidence
    ) {
    }

    private record ReportTable<T>(TableView<T> table, String title) {
    }
}
