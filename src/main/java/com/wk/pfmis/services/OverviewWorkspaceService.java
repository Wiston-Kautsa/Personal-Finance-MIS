package com.wk.pfmis.services;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanInstallmentRecord;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanRecord;
import com.wk.pfmis.models.Asset;
import com.wk.pfmis.models.AssetEvent;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.GoalStep;
import com.wk.pfmis.models.LoanScheduleRecord;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectActivity;
import com.wk.pfmis.models.ProjectMilestone;
import com.wk.pfmis.models.RecurringTransactionPlan;
import com.wk.pfmis.models.ScheduledObligation;
import com.wk.pfmis.utils.MoneyUtil;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class OverviewWorkspaceService {
    private static final int READ_LIMIT = 5000;

    private final DatabaseHandler database;

    public OverviewWorkspaceService() {
        this(DatabaseHandler.getInstance());
    }

    OverviewWorkspaceService(DatabaseHandler database) {
        this.database = database;
    }

    public IncomeOverviewData incomeOverview() {
        String currency = database.getBaseCurrencyCode();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        List<FinanceTransaction> transactions = database.listRecentTransactions(READ_LIMIT);
        List<DatabaseHandler.ExpectedIncomeRecord> expectedIncome = database.listExpectedIncomeRecords(READ_LIMIT);
        List<RecurringTransactionPlan> incomePlans = database.listRecurringTransactionPlans().stream()
                .filter(plan -> "INCOME".equalsIgnoreCase(safe(plan.getTransactionType())))
                .toList();

        double receivedThisMonth = transactions.stream()
                .filter(transaction -> isType(transaction, "INCOME"))
                .filter(transaction -> !isLoanPurpose(transaction.getTransactionPurpose()))
                .filter(transaction -> currentMonth.equals(monthOf(transaction.getTransactionDate())))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();
        double expectedNextThirty = expectedIncome.stream()
                .filter(this::isOpenExpectedIncome)
                .filter(record -> between(parseDate(record.expectedDate()), today, today.plusDays(30)))
                .mapToDouble(DatabaseHandler.ExpectedIncomeRecord::expectedAmount)
                .sum();
        double overdueExpected = expectedIncome.stream()
                .filter(this::isOpenExpectedIncome)
                .filter(record -> before(parseDate(record.expectedDate()), today))
                .mapToDouble(DatabaseHandler.ExpectedIncomeRecord::expectedAmount)
                .sum();
        long recurringDueSoon = incomePlans.stream()
                .filter(this::isActiveRecurringPlan)
                .filter(plan -> between(parseDate(plan.getNextDueDate()), today, today.plusDays(30)))
                .count();

        List<OverviewRow> recentRows = transactions.stream()
                .filter(transaction -> isType(transaction, "INCOME"))
                .filter(transaction -> !isLoanPurpose(transaction.getTransactionPurpose()))
                .limit(8)
                .map(transaction -> new OverviewRow(
                        safeDate(transaction.getTransactionDate()),
                        labelOr(transaction.getCategoryName(), "Income"),
                        labelOr(transaction.getAccountName(), "-"),
                        money(currency, transaction.getAmount()),
                        labelOr(transaction.getReferenceNumber(), "-"),
                        displayStatus(transaction.getTransactionStatus()),
                        "View Income Records"
                ))
                .toList();

        List<OverviewRow> attention = new ArrayList<>();
        expectedIncome.stream()
                .filter(this::isOpenExpectedIncome)
                .filter(record -> before(parseDate(record.expectedDate()), today))
                .limit(5)
                .map(record -> new OverviewRow(
                        labelOr(record.categoryName(), "Expected income"),
                        "Overdue expected receipt",
                        labelOr(record.accountName(), "-"),
                        money(record.currency(), record.expectedAmount()),
                        safeDate(record.expectedDate()),
                        displayStatus("Overdue"),
                        "Open Expected Income"
                ))
                .forEach(attention::add);
        incomePlans.stream()
                .filter(this::isActiveRecurringPlan)
                .filter(plan -> between(parseDate(plan.getNextDueDate()), today, today.plusDays(7)))
                .limit(4)
                .map(plan -> new OverviewRow(
                        plan.getPlanName(),
                        "Recurring income due soon",
                        labelOr(plan.getAccountName(), "-"),
                        money(currency, plan.getAmount()),
                        safeDate(plan.getNextDueDate()),
                        displayStatus(plan.getStatus()),
                        "Open Recurring Income"
                ))
                .forEach(attention::add);

        List<OverviewRow> upcoming = new ArrayList<>();
        expectedIncome.stream()
                .filter(this::isOpenExpectedIncome)
                .filter(record -> !before(parseDate(record.expectedDate()), today))
                .limit(8)
                .map(record -> new OverviewRow(
                        safeDate(record.expectedDate()),
                        labelOr(record.categoryName(), "Expected income"),
                        labelOr(record.accountName(), "-"),
                        money(record.currency(), record.expectedAmount()),
                        labelOr(record.repeatFrequency(), "One time"),
                        displayStatus(record.status()),
                        "Expected Income"
                ))
                .forEach(upcoming::add);
        incomePlans.stream()
                .filter(this::isActiveRecurringPlan)
                .filter(plan -> parseDate(plan.getNextDueDate()) != null)
                .limit(6)
                .map(plan -> new OverviewRow(
                        safeDate(plan.getNextDueDate()),
                        plan.getPlanName(),
                        labelOr(plan.getAccountName(), "-"),
                        money(currency, plan.getAmount()),
                        labelOr(plan.getFrequency(), "-"),
                        displayStatus(plan.getStatus()),
                        "Recurring Income"
                ))
                .forEach(upcoming::add);

        return new IncomeOverviewData(
                money(currency, receivedThisMonth),
                money(currency, expectedNextThirty),
                money(currency, overdueExpected),
                String.valueOf(recurringDueSoon),
                recentRows,
                attention,
                upcoming.stream().limit(10).toList(),
                recentRows.isEmpty() && expectedIncome.isEmpty() && incomePlans.isEmpty()
        );
    }

    public ExpenseOverviewData expenseOverview() {
        String currency = database.getBaseCurrencyCode();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        List<FinanceTransaction> transactions = database.listRecentTransactions(READ_LIMIT);
        List<ScheduledObligation> obligations = database.listScheduledObligations();
        List<RecurringTransactionPlan> expensePlans = database.listRecurringTransactionPlans().stream()
                .filter(plan -> "EXPENSE".equalsIgnoreCase(safe(plan.getTransactionType())))
                .toList();

        double spentThisMonth = transactions.stream()
                .filter(transaction -> isType(transaction, "EXPENSE"))
                .filter(transaction -> currentMonth.equals(monthOf(transaction.getTransactionDate())))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();
        double upcomingPlanned = obligations.stream()
                .filter(this::isActiveObligation)
                .filter(obligation -> between(parseDate(obligation.getDueDate()), today, today.plusDays(30)))
                .mapToDouble(ScheduledObligation::getAmount)
                .sum();
        long overdue = obligations.stream()
                .filter(this::isActiveObligation)
                .filter(obligation -> before(parseDate(obligation.getDueDate()), today))
                .count();
        double recurringDue = expensePlans.stream()
                .filter(this::isActiveRecurringPlan)
                .filter(plan -> between(parseDate(plan.getNextDueDate()), today, today.plusDays(30)))
                .mapToDouble(RecurringTransactionPlan::getAmount)
                .sum();

        List<OverviewRow> recentRows = transactions.stream()
                .filter(transaction -> isType(transaction, "EXPENSE"))
                .limit(8)
                .map(transaction -> new OverviewRow(
                        safeDate(transaction.getTransactionDate()),
                        labelOr(transaction.getCategoryName(), "Expense"),
                        labelOr(transaction.getAccountName(), "-"),
                        money(currency, transaction.getAmount()),
                        labelOr(transaction.getProjectName(), "-"),
                        displayStatus(transaction.getTransactionStatus()),
                        "View Expense Records"
                ))
                .toList();
        List<OverviewRow> attention = obligations.stream()
                .filter(this::isActiveObligation)
                .filter(obligation -> before(parseDate(obligation.getDueDate()), today))
                .limit(6)
                .map(obligation -> new OverviewRow(
                        obligation.getObligationName(),
                        labelOr(obligation.getObligationType(), "Planned expense"),
                        labelOr(obligation.getAccountName(), "-"),
                        money(currency, obligation.getAmount()),
                        safeDate(obligation.getDueDate()),
                        displayStatus("Overdue"),
                        "Open Planned Expenses"
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        expensePlans.stream()
                .filter(this::isActiveRecurringPlan)
                .filter(plan -> before(parseDate(plan.getNextDueDate()), today))
                .limit(4)
                .map(plan -> new OverviewRow(
                        plan.getPlanName(),
                        "Recurring expense overdue",
                        labelOr(plan.getAccountName(), "-"),
                        money(currency, plan.getAmount()),
                        safeDate(plan.getNextDueDate()),
                        displayStatus(plan.getStatus()),
                        "Open Planned Expenses"
                ))
                .forEach(attention::add);

        List<OverviewRow> upcoming = new ArrayList<>();
        obligations.stream()
                .filter(this::isActiveObligation)
                .filter(obligation -> !before(parseDate(obligation.getDueDate()), today))
                .limit(8)
                .map(obligation -> new OverviewRow(
                        safeDate(obligation.getDueDate()),
                        obligation.getObligationName(),
                        labelOr(obligation.getAccountName(), "-"),
                        money(currency, obligation.getAmount()),
                        labelOr(obligation.getFrequency(), "One-time"),
                        displayStatus(obligation.getStatus()),
                        "Planned"
                ))
                .forEach(upcoming::add);
        expensePlans.stream()
                .filter(this::isActiveRecurringPlan)
                .limit(6)
                .map(plan -> new OverviewRow(
                        safeDate(plan.getNextDueDate()),
                        plan.getPlanName(),
                        labelOr(plan.getAccountName(), "-"),
                        money(currency, plan.getAmount()),
                        labelOr(plan.getFrequency(), "-"),
                        displayStatus(plan.getStatus()),
                        "Recurring"
                ))
                .forEach(upcoming::add);

        return new ExpenseOverviewData(
                money(currency, spentThisMonth),
                money(currency, upcomingPlanned),
                String.valueOf(overdue),
                money(currency, recurringDue),
                recentRows,
                attention,
                upcoming.stream().limit(10).toList(),
                recentRows.isEmpty() && obligations.isEmpty() && expensePlans.isEmpty()
        );
    }

    public TransactionsOverviewData transactionsOverview() {
        String currency = database.getBaseCurrencyCode();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        List<FinanceTransaction> transactions = database.listRecentTransactions(READ_LIMIT);
        List<DatabaseHandler.TransferDraftRecord> transferDrafts = database.listTransferDrafts(READ_LIMIT);
        List<DatabaseHandler.ScheduledTransferRecord> schedules = database.listScheduledTransfers(READ_LIMIT);
        List<DatabaseHandler.TransactionCorrectionDraftRecord> correctionDrafts = database.listTransactionCorrectionDrafts(READ_LIMIT);

        double inflows = transactions.stream()
                .filter(transaction -> currentMonth.equals(monthOf(transaction.getTransactionDate())))
                .filter(transaction -> isType(transaction, "INCOME") || isLoanIn(transaction))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();
        double outflows = transactions.stream()
                .filter(transaction -> currentMonth.equals(monthOf(transaction.getTransactionDate())))
                .filter(transaction -> isType(transaction, "EXPENSE") || isLoanOut(transaction))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();
        double transfers = transactions.stream()
                .filter(transaction -> currentMonth.equals(monthOf(transaction.getTransactionDate())))
                .filter(transaction -> isType(transaction, "TRANSFER"))
                .filter(transaction -> "TRANSFER_OUT".equalsIgnoreCase(safe(transaction.getTransactionPurpose())))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();
        long exceptions = correctionDrafts.size()
                + transferDrafts.stream().filter(draft -> !isActiveStatus(draft.status())).count();

        List<OverviewRow> recent = transactions.stream()
                .filter(transaction -> !isTransferIn(transaction))
                .limit(10)
                .map(transaction -> new OverviewRow(
                        safeDate(transaction.getTransactionDate()),
                        transactionLabel(transaction),
                        labelOr(transaction.getAccountName(), "-"),
                        money(currency, transaction.getAmount()),
                        labelOr(transaction.getReferenceNumber(), "-"),
                        displayStatus(transaction.getTransactionStatus()),
                        "Open Ledger"
                ))
                .toList();
        List<OverviewRow> scheduled = new ArrayList<>();
        schedules.stream()
                .filter(schedule -> isActiveStatus(schedule.status()))
                .limit(6)
                .map(schedule -> new OverviewRow(
                        safeDate(schedule.nextDueDate()),
                        schedule.transferName(),
                        schedule.fromAccountName() + " to " + schedule.toAccountName(),
                        money(schedule.currency(), schedule.amount()),
                        labelOr(schedule.frequency(), "-"),
                        displayStatus(schedule.status()),
                        "Scheduled Transfers"
                ))
                .forEach(scheduled::add);
        transferDrafts.stream()
                .filter(draft -> !"Posted".equalsIgnoreCase(safe(draft.status())))
                .limit(5)
                .map(draft -> new OverviewRow(
                        safeDate(draft.transferDate()),
                        "Transfer draft #" + draft.id(),
                        draft.fromAccountName() + " to " + draft.toAccountName(),
                        money(draft.currency(), draft.amountSent()),
                        "Fee " + money(draft.currency(), draft.transferFee()),
                        displayStatus(draft.status()),
                        "Transfer Money"
                ))
                .forEach(scheduled::add);
        List<OverviewRow> corrections = correctionDrafts.stream()
                .limit(8)
                .map(draft -> new OverviewRow(
                        "#" + draft.id(),
                        "Original #" + draft.originalTransactionId(),
                        draft.accountName(),
                        money(currency, draft.amount()),
                        safeDate(draft.transactionDate()),
                        displayStatus(draft.status()),
                        labelOr(draft.reason(), "Correction draft")
                ))
                .toList();

        return new TransactionsOverviewData(
                money(currency, inflows),
                money(currency, outflows),
                money(currency, transfers),
                String.valueOf(exceptions),
                recent,
                scheduled.stream().limit(10).toList(),
                corrections,
                recent.isEmpty() && scheduled.isEmpty() && corrections.isEmpty()
        );
    }

    public LoanOverviewData loanOverview() {
        String currency = database.getBaseCurrencyCode();
        LocalDate today = LocalDate.now();
        List<CentralLoanRecord> loans = database.listCentralLoans();
        List<CentralLoanInstallmentRecord> installments = database.listCentralLoanInstallments(null);
        List<FinanceTransaction> transactions = database.listTransactionHistory(new DatabaseHandler.TransactionHistoryFilter(
                null, null, null, "Loan", "", null, "", false, READ_LIMIT, 0
        )).transactions();

        double outstandingDebt = loans.stream()
                .filter(this::isOpenCentralLoan)
                .mapToDouble(CentralLoanRecord::outstandingBalance)
                .sum();
        double originalBorrowed = loans.stream()
                .filter(loan -> !"CANCELLED".equalsIgnoreCase(safe(loan.status())))
                .mapToDouble(CentralLoanRecord::principalAmount)
                .sum();
        double dueSoon = installments.stream()
                .filter(this::isOpenInstallment)
                .filter(installment -> between(parseDate(installment.dueDate()), today, today.plusDays(30)))
                .mapToDouble(CentralLoanInstallmentRecord::remainingDue)
                .sum();
        double overdue = installments.stream()
                .filter(this::isOpenInstallment)
                .filter(installment -> before(parseDate(installment.dueDate()), today)
                        || "OVERDUE".equalsIgnoreCase(safe(installment.status())))
                .mapToDouble(CentralLoanInstallmentRecord::remainingDue)
                .sum();
        Map<Integer, CentralLoanRecord> loansById = loans.stream().collect(Collectors.toMap(CentralLoanRecord::id, loan -> loan));

        List<OverviewRow> attention = loans.stream()
                .filter(this::isOpenCentralLoan)
                .filter(loan -> "OVERDUE".equalsIgnoreCase(safe(loan.status())) || before(parseDate(loan.nextPaymentDate()), today))
                .limit(8)
                .map(loan -> centralLoanRow(loan, "Open Loan Records"))
                .toList();
        List<OverviewRow> repayments = installments.stream()
                .filter(this::isOpenInstallment)
                .filter(installment -> parseDate(installment.dueDate()) != null)
                .sorted(Comparator.comparing(installment -> parseDate(installment.dueDate())))
                .limit(8)
                .map(installment -> centralInstallmentRow(loansById.get(installment.loanId()), installment))
                .toList();
        List<OverviewRow> recent = transactions.stream()
                .filter(transaction -> isType(transaction, "LOAN") || isLoanPurpose(transaction.getTransactionPurpose()))
                .filter(transaction -> !isTransferIn(transaction))
                .limit(8)
                .map(transaction -> new OverviewRow(
                        safeDate(transaction.getTransactionDate()),
                        labelOr(transaction.getPersonName(), "Loan party"),
                        purposeLabel(transaction.getTransactionPurpose()),
                        money(currency, transaction.getAmount()),
                        labelOr(transaction.getReferenceNumber(), "-"),
                        displayStatus(transaction.getTransactionStatus()),
                        "Record Repayment"
                ))
                .toList();

        return new LoanOverviewData(
                money(currency, outstandingDebt),
                money(currency, originalBorrowed),
                money(currency, dueSoon),
                money(currency, overdue),
                attention,
                repayments,
                recent,
                loans.isEmpty()
        );
    }

    public GoalsOverviewData goalsOverview() {
        String currency = database.getBaseCurrencyCode();
        LocalDate today = LocalDate.now();
        List<Goal> goals = database.listGoals();

        long active = goals.stream().filter(this::isActiveGoal).count();
        long atRisk = goals.stream().filter(this::isGoalAtRisk).count();
        long onTrack = goals.stream().filter(this::isActiveGoal).filter(goal -> !isGoalAtRisk(goal)).count();
        long dueSoon = goals.stream()
                .filter(this::isActiveGoal)
                .filter(goal -> between(parseDate(goal.getTargetDate()), today, today.plusDays(30)))
                .count();

        List<OverviewRow> progress = goals.stream()
                .filter(goal -> !isTerminalStatus(goal.getStatus()))
                .limit(10)
                .map(goal -> new OverviewRow(
                        goal.getGoalName(),
                        money(goal.getCurrency(), goal.getTargetAmount()),
                        money(goal.getCurrency(), goal.getCurrentAmount()),
                        money(goal.getCurrency(), goal.getRemainingAmount()),
                        safeDate(goal.getTargetDate()),
                        progressText(goal),
                        "Open Goal"
                ))
                .toList();
        List<OverviewRow> steps = goals.stream()
                .flatMap(goal -> database.listGoalSteps(goal.getId()).stream())
                .filter(step -> !isCompletedStatus(step.getStatus()))
                .sorted(Comparator.comparing(step -> parseDate(step.getTargetDate()), Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(8)
                .map(step -> new OverviewRow(
                        step.getGoalName(),
                        step.getStepName(),
                        money(currency, step.getEstimatedCost()),
                        money(currency, step.getMissingAmount()),
                        safeDate(step.getTargetDate()),
                        displayStatus(step.getStatus()),
                        "Goal Steps"
                ))
                .toList();
        List<OverviewRow> attention = goals.stream()
                .filter(this::isGoalAtRisk)
                .limit(8)
                .map(goal -> new OverviewRow(
                        goal.getGoalName(),
                        goalRiskReason(goal),
                        money(goal.getCurrency(), goal.getRemainingAmount()),
                        money(goal.getCurrency(), requiredMonthly(goal)),
                        safeDate(goal.getTargetDate()),
                        displayStatus(goal.getStatus()),
                        "Open Goal"
                ))
                .toList();

        return new GoalsOverviewData(
                String.valueOf(active),
                String.valueOf(onTrack),
                String.valueOf(atRisk),
                String.valueOf(dueSoon),
                progress,
                steps,
                attention,
                goals.isEmpty()
        );
    }

    public ProjectOverviewData projectOverview() {
        String currency = database.getBaseCurrencyCode();
        LocalDate today = LocalDate.now();
        List<Project> projects = database.listProjects();
        List<ProjectActivity> activities = database.listProjectActivities();

        long active = projects.stream().filter(this::isActiveProject).count();
        long atRisk = projects.stream().filter(project -> projectAtRisk(project, activities)).count();
        long overdueActivities = activities.stream()
                .filter(activity -> !isCompletedStatus(activity.getStatus()))
                .filter(activity -> before(parseDate(activity.getEndDate()), today))
                .count();
        long upcomingMilestones = projects.stream()
                .flatMap(project -> database.listProjectMilestones(project.getId()).stream())
                .filter(milestone -> !isCompletedStatus(milestone.getStatus()))
                .filter(milestone -> between(parseDate(milestone.getTargetDate()), today, today.plusDays(30)))
                .count();

        Map<Integer, List<ProjectActivity>> activitiesByProject = activities.stream()
                .collect(Collectors.groupingBy(ProjectActivity::getProjectId));
        List<OverviewRow> projectRows = projects.stream()
                .limit(10)
                .map(project -> new OverviewRow(
                        project.getProjectName(),
                        money(project.getCurrency(), project.getPlannedBudget()),
                        money(project.getCurrency(), project.getAmountSpent()),
                        projectProgress(project, activitiesByProject.getOrDefault(project.getId(), List.of())),
                        nextMilestone(project),
                        displayStatus(project.getStatus()),
                        "Open Project"
                ))
                .toList();
        List<OverviewRow> attention = projects.stream()
                .filter(project -> projectAtRisk(project, activities))
                .limit(8)
                .map(project -> new OverviewRow(
                        project.getProjectName(),
                        projectRiskReason(project, activitiesByProject.getOrDefault(project.getId(), List.of())),
                        money(project.getCurrency(), Math.max(0, project.getAmountSpent() - project.getPlannedBudget())),
                        money(project.getCurrency(), project.getRemainingBudget()),
                        safeDate(project.getEndDate()),
                        displayStatus(project.getStatus()),
                        "Project Finances"
                ))
                .toList();
        List<OverviewRow> recent = activities.stream()
                .sorted(Comparator.comparing(activity -> parseDate(activity.getActivityDate()), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(activity -> new OverviewRow(
                        safeDate(activity.getActivityDate()),
                        activity.getProjectName(),
                        activity.getActivityName(),
                        money(currency, activity.getAmountUsed()),
                        activity.getProgress() + "%",
                        displayStatus(activity.getStatus()),
                        "Project Activities"
                ))
                .toList();

        return new ProjectOverviewData(
                String.valueOf(active),
                String.valueOf(atRisk),
                String.valueOf(overdueActivities),
                String.valueOf(upcomingMilestones),
                projectRows,
                attention,
                recent,
                projects.isEmpty()
        );
    }

    public AssetOverviewData assetOverview() {
        String currency = database.getBaseCurrencyCode();
        List<Asset> assets = database.listAssets();
        List<FinanceTransaction> transactions = database.listRecentTransactions(READ_LIMIT);

        List<Asset> currentAssets = assets.stream()
                .filter(asset -> !isTerminalAssetStatus(asset.getStatus()))
                .toList();
        long maintenanceDue = currentAssets.stream()
                .filter(this::needsAssetAttention)
                .count();
        long recognitionQueue = currentAssets.stream()
                .filter(asset -> "PENDING_REGISTRATION".equalsIgnoreCase(safe(asset.getStatus())))
                .count()
                + transactions.stream().filter(this::isAssetRecognitionCandidate).limit(100).count();

        List<OverviewRow> attention = currentAssets.stream()
                .filter(this::needsAssetAttention)
                .limit(8)
                .map(asset -> new OverviewRow(
                        asset.getAssetName(),
                        labelOr(asset.getAssetCondition(), "-"),
                        labelOr(asset.getLocation(), "-"),
                        money(asset.getCurrency(), asset.getCurrentValue()),
                        safeDate(asset.getPurchaseDate()),
                        displayStatus(asset.getStatus()),
                        "Open Asset"
                ))
                .toList();
        List<OverviewRow> recognition = transactions.stream()
                .filter(this::isAssetRecognitionCandidate)
                .filter(transaction -> !isTransferIn(transaction))
                .limit(8)
                .map(transaction -> new OverviewRow(
                        safeDate(transaction.getTransactionDate()),
                        labelOr(transaction.getDescription(), "Purchase candidate"),
                        labelOr(transaction.getAccountName(), "-"),
                        money(currency, transaction.getAmount()),
                        labelOr(transaction.getCategoryName(), "-"),
                        displayStatus(transaction.getTransactionStatus()),
                        "Asset Recognition"
                ))
                .toList();
        List<OverviewRow> recentEvents = assets.stream()
                .flatMap(asset -> database.listAssetEvents(asset.getId()).stream()
                        .map(event -> assetEventRow(asset, event)))
                .sorted(Comparator.comparing(row -> parseDate(row.primary()), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .toList();

        double currentValue = currentAssets.stream().mapToDouble(Asset::getCurrentValue).sum();
        return new AssetOverviewData(
                String.valueOf(currentAssets.size()),
                money(currency, currentValue),
                String.valueOf(maintenanceDue),
                String.valueOf(recognitionQueue),
                attention,
                recognition,
                recentEvents,
                assets.isEmpty()
        );
    }

    private OverviewRow loanRow(String currency, LoanScheduleRecord loan, String action) {
        return new OverviewRow(
                "#" + loan.getId() + " " + labelOr(loan.getPersonName(), "No contact"),
                displayDirection(loan.getLoanDirection()),
                money(currency, loan.getOutstandingAmount()),
                money(currency, scheduledPaymentAmount(loan)),
                safeDate(loan.getDueDate()),
                displayStatus(loan.getStatus()),
                action
        );
    }

    private OverviewRow centralLoanRow(CentralLoanRecord loan, String action) {
        return new OverviewRow(
                loan.loanNumber() + " " + loan.loanName(),
                displayStatus(loan.lenderType()),
                money(loan.currency(), loan.outstandingBalance()),
                money(loan.currency(), loan.nextPaymentAmount()),
                safeDate(loan.nextPaymentDate()),
                displayStatus(loan.status()),
                action
        );
    }

    private OverviewRow centralInstallmentRow(CentralLoanRecord loan, CentralLoanInstallmentRecord installment) {
        String currency = loan == null ? database.getBaseCurrencyCode() : loan.currency();
        String name = loan == null ? installment.loanNumber() : loan.loanNumber() + " " + loan.loanName();
        String lenderType = loan == null ? "Loan" : displayStatus(loan.lenderType());
        return new OverviewRow(
                name,
                lenderType,
                money(currency, installment.remainingDue()),
                money(currency, installment.totalDue()),
                safeDate(installment.dueDate()),
                displayStatus(installment.status()),
                "Record Repayment"
        );
    }

    private boolean isOpenCentralLoan(CentralLoanRecord loan) {
        return loan != null
                && loan.outstandingBalance() > 0.005
                && !List.of("COMPLETED", "CLOSED", "CANCELLED").contains(safe(loan.status()).toUpperCase(Locale.ENGLISH));
    }

    private boolean isOpenInstallment(CentralLoanInstallmentRecord installment) {
        return installment != null
                && installment.remainingDue() > 0.005
                && !List.of("PAID", "CANCELLED", "SKIPPED").contains(safe(installment.status()).toUpperCase(Locale.ENGLISH));
    }

    private OverviewRow assetEventRow(Asset asset, AssetEvent event) {
        return new OverviewRow(
                safeDate(event.getEventDate()),
                asset.getAssetName(),
                displayStatus(event.getEventType()),
                MoneyUtil.format(event.getCurrency(), event.getAmount()),
                labelOr(event.getCounterparty(), "-"),
                displayStatus(event.getPaymentStatus()),
                labelOr(event.getReason(), "Asset event")
        );
    }

    private boolean isOpenExpectedIncome(DatabaseHandler.ExpectedIncomeRecord record) {
        String status = safe(record.status()).toUpperCase(Locale.ENGLISH);
        return !List.of("RECEIVED", "CANCELLED", "ARCHIVED", "REVERSED").contains(status);
    }

    private boolean isActiveRecurringPlan(RecurringTransactionPlan plan) {
        return isActiveStatus(plan.getStatus());
    }

    private boolean isActiveObligation(ScheduledObligation obligation) {
        return isActiveStatus(obligation.getStatus());
    }

    private boolean isOpenLoan(LoanScheduleRecord loan) {
        return loan.getOutstandingAmount() > 0.005 && !isTerminalStatus(loan.getStatus());
    }

    private boolean isActiveGoal(Goal goal) {
        return !isTerminalStatus(goal.getStatus()) && !"DRAFT".equalsIgnoreCase(safe(goal.getStatus()));
    }

    private boolean isGoalAtRisk(Goal goal) {
        if (!isActiveGoal(goal)) {
            return false;
        }
        String status = safe(goal.getStatus()).toUpperCase(Locale.ENGLISH);
        return List.of("AT_RISK", "OVERDUE").contains(status)
                || (parseDate(goal.getTargetDate()) != null && parseDate(goal.getTargetDate()).isBefore(LocalDate.now()) && goal.getRemainingAmount() > 0.005)
                || (requiredMonthly(goal) > 0 && goal.getMonthlyContribution() > 0 && goal.getMonthlyContribution() + 0.005 < requiredMonthly(goal));
    }

    private double requiredMonthly(Goal goal) {
        LocalDate targetDate = parseDate(goal.getTargetDate());
        if (targetDate == null || !targetDate.isAfter(LocalDate.now()) || goal.getRemainingAmount() <= 0.005) {
            return 0;
        }
        long months = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(targetDate)) + 1);
        return goal.getRemainingAmount() / months;
    }

    private boolean isActiveProject(Project project) {
        return !isTerminalStatus(project.getStatus()) && !"DRAFT".equalsIgnoreCase(safe(project.getStatus()));
    }

    private boolean projectAtRisk(Project project, List<ProjectActivity> activities) {
        if (!isActiveProject(project)) {
            return false;
        }
        if (safe(project.getStatus()).equalsIgnoreCase("AT_RISK")) {
            return true;
        }
        if (project.getPlannedBudget() > 0 && project.getAmountSpent() > project.getPlannedBudget()) {
            return true;
        }
        LocalDate endDate = parseDate(project.getEndDate());
        if (endDate != null && endDate.isBefore(LocalDate.now())) {
            return true;
        }
        return activities.stream()
                .filter(activity -> activity.getProjectId() == project.getId())
                .anyMatch(activity -> !isCompletedStatus(activity.getStatus()) && before(parseDate(activity.getEndDate()), LocalDate.now()));
    }

    private boolean needsAssetAttention(Asset asset) {
        String status = safe(asset.getStatus()).toUpperCase(Locale.ENGLISH);
        String condition = safe(asset.getAssetCondition()).toUpperCase(Locale.ENGLISH);
        return List.of("UNDER_MAINTENANCE", "DAMAGED", "FROZEN", "PENDING_REGISTRATION").contains(status)
                || condition.contains("POOR")
                || condition.contains("DAMAGED")
                || condition.contains("NEEDS");
    }

    private boolean isAssetRecognitionCandidate(FinanceTransaction transaction) {
        if (!isType(transaction, "EXPENSE")) {
            return false;
        }
        String text = (safe(transaction.getCategoryName()) + " " + safe(transaction.getDescription()) + " "
                + safe(transaction.getTransactionPurpose())).toLowerCase(Locale.ENGLISH);
        return text.contains("asset")
                || text.contains("equipment")
                || text.contains("furniture")
                || text.contains("vehicle")
                || text.contains("land")
                || text.contains("building");
    }

    private boolean isType(FinanceTransaction transaction, String type) {
        return type.equalsIgnoreCase(safe(transaction.getTransactionType()));
    }

    private boolean isLoanPurpose(String purpose) {
        return List.of("MONEY_LENT", "MONEY_BORROWED", "LENT_REPAID", "BORROWED_REPAID", "LOAN_INTEREST", "LOAN_PENALTY")
                .contains(safe(purpose).toUpperCase(Locale.ENGLISH));
    }

    private boolean isLoanIn(FinanceTransaction transaction) {
        String purpose = safe(transaction.getTransactionPurpose()).toUpperCase(Locale.ENGLISH);
        return "MONEY_BORROWED".equals(purpose) || "LENT_REPAID".equals(purpose);
    }

    private boolean isLoanOut(FinanceTransaction transaction) {
        String purpose = safe(transaction.getTransactionPurpose()).toUpperCase(Locale.ENGLISH);
        return "MONEY_LENT".equals(purpose) || "BORROWED_REPAID".equals(purpose);
    }

    private boolean isTransferIn(FinanceTransaction transaction) {
        return "TRANSFER".equalsIgnoreCase(safe(transaction.getTransactionType()))
                && "TRANSFER_IN".equalsIgnoreCase(safe(transaction.getTransactionPurpose()));
    }

    private boolean isActiveStatus(String status) {
        String clean = safe(status).toUpperCase(Locale.ENGLISH).replace(' ', '_');
        return clean.isBlank()
                || List.of("ACTIVE", "OPEN", "DRAFT", "PENDING", "PARTIALLY_PAID", "PARTIALLY_REPAID", "OVERDUE", "IN_PROGRESS", "PLANNED").contains(clean);
    }

    private boolean isTerminalStatus(String status) {
        String clean = safe(status).toUpperCase(Locale.ENGLISH).replace(' ', '_');
        return List.of("CANCELLED", "ARCHIVED", "CLOSED", "SETTLED", "COMPLETED", "ACHIEVED", "CONVERTED_TO_PROJECT", "WRITTEN_OFF", "REVERSED").contains(clean);
    }

    private boolean isCompletedStatus(String status) {
        String clean = safe(status).toUpperCase(Locale.ENGLISH).replace(' ', '_');
        return List.of("COMPLETED", "DONE", "ACHIEVED", "CANCELLED", "ARCHIVED").contains(clean);
    }

    private boolean isTerminalAssetStatus(String status) {
        String clean = safe(status).toUpperCase(Locale.ENGLISH);
        return List.of("TRANSFERRED", "DONATED", "SOLD", "LOST", "WRITTEN_OFF", "DISPOSED", "ARCHIVED").contains(clean);
    }

    private double scheduledPaymentAmount(LoanScheduleRecord loan) {
        return loan.getPaymentAmount() > 0.005 ? loan.getPaymentAmount() : loan.getOutstandingAmount();
    }

    private String nextMilestone(Project project) {
        return database.listProjectMilestones(project.getId()).stream()
                .filter(milestone -> !isCompletedStatus(milestone.getStatus()))
                .sorted(Comparator.comparing(milestone -> parseDate(milestone.getTargetDate()), Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst()
                .map(milestone -> milestone.getMilestoneName() + " " + safeDate(milestone.getTargetDate()))
                .orElse("-");
    }

    private String projectProgress(Project project, List<ProjectActivity> activities) {
        if (!activities.isEmpty()) {
            double average = activities.stream().mapToDouble(ProjectActivity::getProgress).average().orElse(0);
            return String.format(Locale.ENGLISH, "%.1f%%", average);
        }
        if (project.getPlannedBudget() > 0) {
            return String.format(Locale.ENGLISH, "%.1f%% budget used", Math.min(100, project.getAmountSpent() / project.getPlannedBudget() * 100));
        }
        return "-";
    }

    private String projectRiskReason(Project project, List<ProjectActivity> activities) {
        if (project.getPlannedBudget() > 0 && project.getAmountSpent() > project.getPlannedBudget()) {
            return "Actual spending exceeds planned budget";
        }
        LocalDate endDate = parseDate(project.getEndDate());
        if (endDate != null && endDate.isBefore(LocalDate.now())) {
            return "Project end date has passed";
        }
        boolean overdueActivity = activities.stream()
                .anyMatch(activity -> !isCompletedStatus(activity.getStatus()) && before(parseDate(activity.getEndDate()), LocalDate.now()));
        return overdueActivity ? "Activity is overdue" : "Status requires review";
    }

    private String goalRiskReason(Goal goal) {
        LocalDate targetDate = parseDate(goal.getTargetDate());
        if (targetDate != null && targetDate.isBefore(LocalDate.now()) && goal.getRemainingAmount() > 0.005) {
            return "Target date passed before goal was completed";
        }
        if (requiredMonthly(goal) > 0 && goal.getMonthlyContribution() > 0 && goal.getMonthlyContribution() + 0.005 < requiredMonthly(goal)) {
            return "Planned contribution is below required pace";
        }
        return "Status requires review";
    }

    private String progressText(Goal goal) {
        double progress = goal.getTargetAmount() <= 0 ? 0 : Math.min(100, goal.getCurrentAmount() / goal.getTargetAmount() * 100);
        return String.format(Locale.ENGLISH, "%.1f%%", progress);
    }

    private String transactionLabel(FinanceTransaction transaction) {
        if ("TRANSFER".equalsIgnoreCase(safe(transaction.getTransactionType()))) {
            return purposeLabel(transaction.getTransactionPurpose());
        }
        String category = labelOr(transaction.getCategoryName(), transaction.getTransactionType());
        String purpose = purposeLabel(transaction.getTransactionPurpose());
        return Objects.equals(category, purpose) ? category : category + " / " + purpose;
    }

    private String displayDirection(String direction) {
        return "LENT".equalsIgnoreCase(safe(direction)) ? "Money lent" : "Money borrowed";
    }

    private String purposeLabel(String purpose) {
        String clean = safe(purpose).replace('_', ' ').trim();
        if (clean.isBlank()) {
            return "-";
        }
        return titleCase(clean);
    }

    private String displayStatus(String status) {
        return titleCase(safe(status).replace('_', ' '));
    }

    private YearMonth monthOf(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : YearMonth.from(date);
    }

    private boolean between(LocalDate value, LocalDate startInclusive, LocalDate endInclusive) {
        return value != null && !value.isBefore(startInclusive) && !value.isAfter(endInclusive);
    }

    private boolean before(LocalDate value, LocalDate other) {
        return value != null && value.isBefore(other);
    }

    private LocalDate parseDate(String value) {
        try {
            return safe(value).isBlank() ? null : LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String money(String currency, double amount) {
        return MoneyUtil.format(currency, amount);
    }

    private String safeDate(String value) {
        return safe(value).isBlank() ? "-" : value;
    }

    private String labelOr(String value, String fallback) {
        return safe(value).isBlank() ? fallback : value;
    }

    private String titleCase(String value) {
        String clean = safe(value).trim();
        if (clean.isBlank()) {
            return "-";
        }
        String[] parts = clean.toLowerCase(Locale.ENGLISH).split("\\s+");
        List<String> converted = new ArrayList<>();
        for (String part : parts) {
            converted.add(part.substring(0, 1).toUpperCase(Locale.ENGLISH) + part.substring(1));
        }
        return String.join(" ", converted);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record OverviewRow(
            String primary,
            String secondary,
            String tertiary,
            String amount,
            String date,
            String status,
            String action
    ) {
    }

    public record IncomeOverviewData(
            String receivedThisMonth,
            String expectedNextThirtyDays,
            String overdueExpected,
            String recurringDueSoon,
            List<OverviewRow> recentIncome,
            List<OverviewRow> attention,
            List<OverviewRow> upcomingIncome,
            boolean empty
    ) {
    }

    public record ExpenseOverviewData(
            String spentThisMonth,
            String upcomingPlanned,
            String overdueOrFailed,
            String recurringDue,
            List<OverviewRow> recentExpenses,
            List<OverviewRow> attention,
            List<OverviewRow> upcomingExpenses,
            boolean empty
    ) {
    }

    public record TransactionsOverviewData(
            String inflowsMonthToDate,
            String outflowsMonthToDate,
            String transfersMonthToDate,
            String exceptions,
            List<OverviewRow> recentMovement,
            List<OverviewRow> scheduledPending,
            List<OverviewRow> corrections,
            boolean empty
    ) {
    }

    public record LoanOverviewData(
            String borrowedOutstanding,
            String lentOutstanding,
            String dueSoon,
            String overdue,
            List<OverviewRow> attention,
            List<OverviewRow> repayments,
            List<OverviewRow> recentActivity,
            boolean empty
    ) {
    }

    public record GoalsOverviewData(
            String activeGoals,
            String onTrack,
            String atRisk,
            String dueSoon,
            List<OverviewRow> progress,
            List<OverviewRow> nextSteps,
            List<OverviewRow> attention,
            boolean empty
    ) {
    }

    public record ProjectOverviewData(
            String activeProjects,
            String atRisk,
            String overdueActivities,
            String upcomingMilestones,
            List<OverviewRow> projects,
            List<OverviewRow> attention,
            List<OverviewRow> recentActivity,
            boolean empty
    ) {
    }

    public record AssetOverviewData(
            String activeAssets,
            String currentValue,
            String maintenanceDue,
            String recognitionQueue,
            List<OverviewRow> attention,
            List<OverviewRow> recognitionCandidates,
            List<OverviewRow> recentEvents,
            boolean empty
    ) {
    }
}
