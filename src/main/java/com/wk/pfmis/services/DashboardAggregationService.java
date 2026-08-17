package com.wk.pfmis.services;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.DashboardMonthlyCashFlow;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupOverview;
import com.wk.pfmis.fx.ConversionResult;
import com.wk.pfmis.fx.ExchangeRateService;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.BudgetProgress;
import com.wk.pfmis.models.DashboardStats;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.LoanScheduleRecord;
import com.wk.pfmis.models.ReportRow;
import com.wk.pfmis.models.ScheduledObligation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DashboardAggregationService {
    private static final int CASH_FLOW_MONTHS = 6;
    private static final int RECENT_TRANSACTION_LIMIT = 5;
    private static final int CURRENT_MONTH_READ_LIMIT = 5000;
    private static final int MAX_CATEGORY_SLICES = 6;
    private static final int MAX_ACCOUNT_BALANCES = 8;
    private static final int MAX_PROGRESS_ROWS = 5;
    private static final DateTimeFormatter MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final DatabaseHandler database;
    private final ExchangeRateService exchangeRateService;

    public DashboardAggregationService() {
        this(DatabaseHandler.getInstance(), ExchangeRateService.getInstance());
    }

    public DashboardAggregationService(DatabaseHandler database, ExchangeRateService exchangeRateService) {
        this.database = database;
        this.exchangeRateService = exchangeRateService;
    }

    public DashboardSnapshot loadSnapshot() {
        String baseCurrency = normalizeCurrency(database.getBaseCurrencyCode());
        DashboardStats stats = database.getDashboardStats();
        List<FinanceTransaction> currentMonthSample = database.listRecentTransactions(CURRENT_MONTH_READ_LIMIT);
        YearMonth currentMonth = YearMonth.now();
        long incomeRecords = currentMonthSample.stream()
                .filter(transaction -> isType(transaction, "INCOME"))
                .filter(transaction -> currentMonth.equals(monthOf(transaction.getTransactionDate())))
                .count();
        long expenseRecords = currentMonthSample.stream()
                .filter(transaction -> isType(transaction, "EXPENSE"))
                .filter(transaction -> currentMonth.equals(monthOf(transaction.getTransactionDate())))
                .count();

        AvailableBalance availableBalance = availableBalance(baseCurrency);
        SavingsGroupOverview savingsGroups = database.getSavingsGroupOverview();
        SavingsSummary savingsSummary = new SavingsSummary(
                savingsGroups.activeSavingsAccounts(),
                savingsGroups.totalCommunitySavings(),
                savingsGroups.contributionsThisMonth(),
                savingsGroups.contributionsThisYear(),
                blankToNone(savingsGroups.nextContributionDueDate()),
                savingsGroups.expectedPayout(),
                savingsGroups.cyclesNearingCompletion()
        );
        List<BudgetPerformance> budgets = budgetPerformance(baseCurrency);
        List<GoalProgress> goals = goalProgress();
        LoanSummary loans = loanSummary(database.listLoanSchedules());
        List<ScheduledObligation> obligations = database.listScheduledObligations();

        return new DashboardSnapshot(
                baseCurrency,
                YearMonth.now().format(MONTH_LABEL_FORMAT),
                new PrimaryKpis(
                        availableBalance.amount(),
                        stats.getMonthlyIncome(),
                        stats.getMonthlyExpenses(),
                        stats.getNetCashFlow(),
                        availableBalance.detail(),
                        incomeRecords + " posted income record" + plural(incomeRecords),
                        expenseRecords + " posted expense record" + plural(expenseRecords),
                        stats.getNetCashFlow() >= 0 ? "Income exceeds expenses this month" : "Expenses exceed income this month"
                ),
                cashFlowTrend(),
                spendingCategories(database.categorySpendingReport(currentMonth.toString())),
                accountBalances(),
                budgets,
                goals,
                savingsSummary,
                loans,
                attentionItems(stats, budgets, goals, savingsSummary, loans, obligations, baseCurrency),
                database.listRecentTransactions(RECENT_TRANSACTION_LIMIT),
                availableBalance.missingFxRates() > 0,
                "Posted transactions only. Scheduled and draft items are shown as planning alerts."
        );
    }

    private AvailableBalance availableBalance(String baseCurrency) {
        BigDecimal total = BigDecimal.ZERO;
        int included = 0;
        int missingRates = 0;
        boolean cachedRateUsed = false;
        Set<String> currencies = new LinkedHashSet<>();
        for (Account account : database.listAccounts()) {
            if (!"ACTIVE".equalsIgnoreCase(account.getStatus())
                    || account.isLiabilityAccount()
                    || account.isCommunitySavingsAccount()
                    || account.isSystemAccount()) {
                continue;
            }
            String currency = normalizeCurrency(account.getCurrency(), baseCurrency);
            currencies.add(currency);
            BigDecimal balance = BigDecimal.valueOf(account.getCurrentBalance());
            if (currency.equalsIgnoreCase(baseCurrency)) {
                total = total.add(balance);
                included++;
                continue;
            }
            try {
                ConversionResult result = exchangeRateService.convertUsingLastKnown(balance, currency, baseCurrency);
                total = total.add(result.convertedAmount());
                cachedRateUsed = cachedRateUsed || result.quote().stale();
                included++;
            } catch (RuntimeException exception) {
                missingRates++;
            }
        }
        String detail = included + " active asset account" + plural(included);
        if (!currencies.isEmpty()) {
            detail += " across " + currencies.size() + " currenc" + (currencies.size() == 1 ? "y" : "ies");
        }
        if (missingRates > 0) {
            detail += "; " + missingRates + " account" + plural(missingRates) + " excluded pending FX rates";
        } else if (cachedRateUsed) {
            detail += "; saved FX rates used";
        }
        return new AvailableBalance(total, detail, missingRates);
    }

    private List<CashFlowPoint> cashFlowTrend() {
        return database.listDashboardMonthlyCashFlow(CASH_FLOW_MONTHS)
                .stream()
                .map(row -> {
                    YearMonth month = YearMonth.parse(row.month());
                    return new CashFlowPoint(
                            row.month(),
                            month.format(MONTH_LABEL_FORMAT),
                            row.income(),
                            row.expenses(),
                            row.income() - row.expenses()
                    );
                })
                .toList();
    }

    static List<SpendingCategory> spendingCategories(List<ReportRow> rows) {
        return spendingCategories(rows, MAX_CATEGORY_SLICES);
    }

    static List<SpendingCategory> spendingCategories(List<ReportRow> rows, int maxSlices) {
        List<ReportRow> positiveRows = rows == null ? List.of() : rows.stream()
                .filter(row -> row.getAmount() > 0)
                .sorted(Comparator.comparingDouble(ReportRow::getAmount).reversed())
                .toList();
        double total = positiveRows.stream().mapToDouble(ReportRow::getAmount).sum();
        if (total <= 0) {
            return List.of();
        }
        int directRows = Math.max(1, maxSlices);
        List<SpendingCategory> categories = new ArrayList<>();
        positiveRows.stream()
                .limit(directRows)
                .map(row -> new SpendingCategory(safeLabel(row.getLabel(), "Uncategorized"), row.getAmount(), percentage(row.getAmount(), total)))
                .forEach(categories::add);
        if (positiveRows.size() > directRows) {
            double other = positiveRows.stream().skip(directRows).mapToDouble(ReportRow::getAmount).sum();
            categories.add(new SpendingCategory("Other categories", other, percentage(other, total)));
        }
        return categories;
    }

    private List<AccountBalancePoint> accountBalances() {
        List<ReportRow> rows = database.accountBalanceReport();
        return rows.stream()
                .sorted(Comparator.comparingDouble((ReportRow row) -> Math.abs(row.getAmount())).reversed()
                        .thenComparing(ReportRow::getLabel, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_ACCOUNT_BALANCES)
                .map(row -> new AccountBalancePoint(safeLabel(row.getLabel(), "Account"), row.getAmount()))
                .toList();
    }

    private List<BudgetPerformance> budgetPerformance(String baseCurrency) {
        return database.listBudgetProgress(YearMonth.now().toString())
                .stream()
                .filter(progress -> !isInactivePlanningStatus(progress.getStatus()))
                .sorted(Comparator.comparingDouble(BudgetProgress::getPercentUsed).reversed())
                .limit(MAX_PROGRESS_ROWS)
                .map(progress -> new BudgetPerformance(
                        safeLabel(progress.getBudgetName(), "Budget"),
                        safeLabel(progress.getCategoryName(), "All categories"),
                        normalizeCurrency(progress.getCurrency(), baseCurrency),
                        progress.getAmountLimit(),
                        progress.getSpent(),
                        progress.getRemaining(),
                        clampPercent(progress.getPercentUsed()),
                        progress.getMonthResult()
                ))
                .toList();
    }

    private List<GoalProgress> goalProgress() {
        return database.listGoals()
                .stream()
                .filter(goal -> !isInactivePlanningStatus(goal.getStatus()))
                .sorted(Comparator.comparingDouble(DashboardAggregationService::goalCompletionPercent)
                        .thenComparing(Goal::getGoalName, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_PROGRESS_ROWS)
                .map(goal -> new GoalProgress(
                        safeLabel(goal.getGoalName(), "Goal"),
                        normalizeCurrency(goal.getCurrency()),
                        goal.getTargetAmount(),
                        goal.getCurrentAmount(),
                        goal.getRemainingAmount(),
                        goalCompletionPercent(goal),
                        blankToNone(goal.getTargetDate()),
                        safeLabel(goal.getStatus(), "Active")
                ))
                .toList();
    }

    private LoanSummary loanSummary(List<LoanScheduleRecord> schedules) {
        double receivable = 0;
        double payable = 0;
        LocalDate nextDue = null;
        int active = 0;
        for (LoanScheduleRecord schedule : schedules) {
            if (schedule.getOutstandingAmount() <= 0 || isInactivePlanningStatus(schedule.getStatus())) {
                continue;
            }
            active++;
            if ("LENT".equalsIgnoreCase(schedule.getLoanDirection())) {
                receivable += schedule.getOutstandingAmount();
            } else {
                payable += schedule.getOutstandingAmount();
            }
            LocalDate dueDate = parseDate(schedule.getDueDate());
            if (dueDate != null && (nextDue == null || dueDate.isBefore(nextDue))) {
                nextDue = dueDate;
            }
        }
        return new LoanSummary(active, receivable, payable, nextDue == null ? "None scheduled" : nextDue.toString());
    }

    private List<AttentionItem> attentionItems(
            DashboardStats stats,
            List<BudgetPerformance> budgets,
            List<GoalProgress> goals,
            SavingsSummary savings,
            LoanSummary loans,
            List<ScheduledObligation> obligations,
            String baseCurrency
    ) {
        LocalDate today = LocalDate.now();
        List<AttentionItem> items = new ArrayList<>();
        if (stats.getNetCashFlow() < 0) {
            items.add(new AttentionItem(
                    "HIGH",
                    "Negative monthly cash flow",
                    "Expenses exceed income by " + com.wk.pfmis.utils.MoneyUtil.format(baseCurrency, Math.abs(stats.getNetCashFlow())) + "."
            ));
        }
        budgets.stream()
                .filter(budget -> budget.utilizationPercent() >= 90)
                .limit(3)
                .map(budget -> new AttentionItem("HIGH", budget.name(), budget.status() + " at " + Math.round(budget.utilizationPercent()) + "% utilization."))
                .forEach(items::add);
        budgets.stream()
                .filter(budget -> budget.utilizationPercent() >= 75 && budget.utilizationPercent() < 90)
                .limit(2)
                .map(budget -> new AttentionItem("MEDIUM", budget.name(), "Approaching limit at " + Math.round(budget.utilizationPercent()) + "% utilization."))
                .forEach(items::add);
        LocalDate savingsDue = parseDate(savings.nextDueDate());
        if (savingsDue != null && !savingsDue.isAfter(today.plusDays(14))) {
            String severity = savingsDue.isBefore(today) ? "HIGH" : "MEDIUM";
            items.add(new AttentionItem(severity, "Savings contribution due", "Next Savings Group contribution date: " + savings.nextDueDate() + "."));
        }
        LocalDate loanDue = parseDate(loans.nextDueDate());
        if (loans.activeLoanCount() > 0 && loanDue != null && !loanDue.isAfter(today.plusDays(30))) {
            String severity = loanDue.isBefore(today) ? "HIGH" : "MEDIUM";
            items.add(new AttentionItem(severity, "Loan payment approaching", "Next loan due date: " + loans.nextDueDate() + "."));
        }
        if (loans.receivableOutstanding() > 0 || loans.payableOutstanding() > 0) {
            items.add(new AttentionItem(
                    "INFO",
                    "Outstanding loans",
                    "Receivable " + com.wk.pfmis.utils.MoneyUtil.format(baseCurrency, loans.receivableOutstanding())
                            + "; payable " + com.wk.pfmis.utils.MoneyUtil.format(baseCurrency, loans.payableOutstanding()) + "."
            ));
        }
        obligations.stream()
                .filter(obligation -> !isInactivePlanningStatus(obligation.getStatus()))
                .filter(obligation -> {
                    LocalDate dueDate = parseDate(obligation.getDueDate());
                    return dueDate != null && !dueDate.isAfter(today.plusDays(14));
                })
                .sorted(Comparator.comparing(obligation -> parseDate(obligation.getDueDate())))
                .limit(4)
                .map(obligation -> {
                    LocalDate dueDate = parseDate(obligation.getDueDate());
                    String severity = dueDate != null && dueDate.isBefore(today) ? "HIGH" : "MEDIUM";
                    return new AttentionItem(severity, obligation.getObligationName(), "Scheduled " + safeLabel(obligation.getObligationType(), "obligation")
                            + " due " + blankToNone(obligation.getDueDate()) + ".");
                })
                .forEach(items::add);
        goals.stream()
                .filter(goal -> {
                    LocalDate dueDate = parseDate(goal.targetDate());
                    return dueDate != null && goal.percentComplete() < 100 && !dueDate.isAfter(today.plusDays(30));
                })
                .limit(2)
                .map(goal -> new AttentionItem("MEDIUM", goal.name(), "Goal target date is " + goal.targetDate() + "."))
                .forEach(items::add);
        if (items.isEmpty()) {
            items.add(new AttentionItem("INFO", "No immediate financial alerts", "Budgets, loans, savings and obligations have no urgent dashboard signal."));
        }
        return items.stream().limit(8).toList();
    }

    private static boolean isType(FinanceTransaction transaction, String type) {
        return type.equalsIgnoreCase(transaction.getTransactionType());
    }

    private static YearMonth monthOf(String date) {
        try {
            return date == null || date.isBlank() ? null : YearMonth.from(LocalDate.parse(date));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() || "None scheduled".equalsIgnoreCase(value)
                    ? null
                    : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static boolean isInactivePlanningStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ENGLISH);
        return Set.of("CANCELLED", "REVERSED", "CLOSED", "ARCHIVED", "PAID", "COMPLETED", "INACTIVE").contains(status);
    }

    private static double goalCompletionPercent(Goal goal) {
        return goal.getTargetAmount() <= 0 ? 0 : clampPercent((goal.getCurrentAmount() / goal.getTargetAmount()) * 100);
    }

    private static double percentage(double amount, double total) {
        return total <= 0 ? 0 : clampPercent((amount / total) * 100);
    }

    private static double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return Math.max(0, Math.min(100, value));
    }

    private static String normalizeCurrency(String value) {
        return normalizeCurrency(value, "MWK");
    }

    private static String normalizeCurrency(String value, String fallback) {
        String cleanFallback = fallback == null || fallback.isBlank() ? "MWK" : fallback.trim().toUpperCase(Locale.ROOT);
        return value == null || value.isBlank() ? cleanFallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String safeLabel(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNone(String value) {
        return value == null || value.isBlank() ? "None scheduled" : value.trim();
    }

    private static String plural(long value) {
        return value == 1 ? "" : "s";
    }

    private record AvailableBalance(BigDecimal amount, String detail, int missingFxRates) {
    }

    public record DashboardSnapshot(
            String baseCurrency,
            String periodLabel,
            PrimaryKpis kpis,
            List<CashFlowPoint> cashFlow,
            List<SpendingCategory> spendingCategories,
            List<AccountBalancePoint> accountBalances,
            List<BudgetPerformance> budgetPerformance,
            List<GoalProgress> goalProgress,
            SavingsSummary savingsSummary,
            LoanSummary loanSummary,
            List<AttentionItem> attentionItems,
            List<FinanceTransaction> recentTransactions,
            boolean missingExchangeRates,
            String financialBasisNote
    ) {
    }

    public record PrimaryKpis(
            BigDecimal availableBalance,
            double monthlyIncome,
            double monthlyExpenses,
            double netCashFlow,
            String availableBalanceDetail,
            String incomeDetail,
            String expenseDetail,
            String netCashFlowDetail
    ) {
    }

    public record CashFlowPoint(String month, String label, double income, double expenses, double netCashFlow) {
    }

    public record SpendingCategory(String category, double amount, double percentage) {
    }

    public record AccountBalancePoint(String account, double balance) {
    }

    public record BudgetPerformance(
            String name,
            String category,
            String currency,
            double planned,
            double actual,
            double remaining,
            double utilizationPercent,
            String status
    ) {
    }

    public record GoalProgress(
            String name,
            String currency,
            double target,
            double achieved,
            double remaining,
            double percentComplete,
            String targetDate,
            String status
    ) {
    }

    public record SavingsSummary(
            int activeGroups,
            double balance,
            double contributionThisMonth,
            double contributionThisYear,
            String nextDueDate,
            double expectedPayout,
            int cyclesNearingCompletion
    ) {
    }

    public record LoanSummary(int activeLoanCount, double receivableOutstanding, double payableOutstanding, String nextDueDate) {
    }

    public record AttentionItem(String severity, String title, String detail) {
    }
}
