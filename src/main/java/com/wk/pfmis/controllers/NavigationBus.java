package com.wk.pfmis.controllers;

import java.util.function.Consumer;

final class NavigationBus {
    private static Runnable accountHistoryHandler;
    private static Runnable accountReconciliationHandler;
    private static Runnable backHandler;
    private static Runnable loanRepaymentHandler;
    private static Runnable loanLedgerHandler;
    private static Runnable goalContributionHandler;
    private static Runnable goalProjectHandler;
    private static Runnable goalStepsHandler;
    private static Runnable assetRegistrationHandler;
    private static Consumer<CoreWorkspaceRoute> coreWorkspaceHandler;
    private static Consumer<SmartNavigationTarget> smartNavigationHandler;
    private static Consumer<String> reportTitleHandler;
    private static Consumer<String> transactionEntryHandler;
    private static String requestedAccountsMode;
    private static SmartAnalysisMode requestedSmartAnalysisMode;
    private static CommunitySavingsMode requestedCommunitySavingsMode;
    private static BudgetMode requestedBudgetMode;
    private static Integer selectedAccountId;
    private static Integer requestedAccountHistoryId;
    private static Integer requestedAccountReconciliationId;
    private static String requestedReportGroup;
    private static String requestedReportType;
    private static String requestedLedgerTypeFilter;
    private static String requestedTransactionType;
    private static String requestedTransactionPurpose;
    private static String requestedPersonName;
    private static Integer requestedProjectId;
    private static Integer requestedProjectActivityId;
    private static Integer requestedLoanScheduleId;
    private static String requestedLoanDirection;
    private static String requestedLoanPersonName;
    private static Integer requestedGoalId;
    private static AssetRegistrationContext requestedAssetRegistrationContext;

    private NavigationBus() {
    }

    static void onAccountHistoryRequested(Runnable handler) {
        accountHistoryHandler = handler;
    }

    static void onAccountReconciliationRequested(Runnable handler) {
        accountReconciliationHandler = handler;
    }

    static void onBackRequested(Runnable handler) {
        backHandler = handler;
    }

    static void onLoanRepaymentRequested(Runnable handler) {
        loanRepaymentHandler = handler;
    }

    static void onLoanLedgerRequested(Runnable handler) {
        loanLedgerHandler = handler;
    }

    static void onGoalContributionRequested(Runnable handler) {
        goalContributionHandler = handler;
    }

    static void onGoalProjectRequested(Runnable handler) {
        goalProjectHandler = handler;
    }

    static void onGoalStepsRequested(Runnable handler) {
        goalStepsHandler = handler;
    }

    static void onAssetRegistrationRequested(Runnable handler) {
        assetRegistrationHandler = handler;
    }

    static void onCoreWorkspaceRequested(Consumer<CoreWorkspaceRoute> handler) {
        coreWorkspaceHandler = handler;
    }

    static void onSmartNavigationRequested(Consumer<SmartNavigationTarget> handler) {
        smartNavigationHandler = handler;
    }

    static void onReportTitleChanged(Consumer<String> handler) {
        reportTitleHandler = handler;
    }

    static void onTransactionEntryRequested(Consumer<String> handler) {
        transactionEntryHandler = handler;
    }

    static void showAccountHistory(int accountId) {
        requestAccountHistory(accountId);
        if (accountHistoryHandler != null) {
            accountHistoryHandler.run();
        }
    }

    static void showAccountReconciliation(int accountId) {
        requestAccountReconciliation(accountId);
        if (accountReconciliationHandler != null) {
            accountReconciliationHandler.run();
        }
    }

    static void rememberSelectedAccountId(Integer accountId) {
        selectedAccountId = accountId;
    }

    static Integer selectedAccountId() {
        return selectedAccountId;
    }

    static void requestAccountHistory(int accountId) {
        selectedAccountId = accountId;
        requestedAccountHistoryId = accountId;
    }

    static void requestAccountReconciliation(Integer accountId) {
        selectedAccountId = accountId;
        requestedAccountReconciliationId = accountId;
    }

    static void requestAccountsMode(String mode) {
        requestedAccountsMode = mode;
    }

    static void requestSmartAnalysisMode(SmartAnalysisMode mode) {
        requestedSmartAnalysisMode = mode == null ? SmartAnalysisMode.OVERVIEW : mode;
    }

    static SmartAnalysisMode consumeRequestedSmartAnalysisMode() {
        SmartAnalysisMode mode = requestedSmartAnalysisMode;
        requestedSmartAnalysisMode = null;
        return mode == null ? SmartAnalysisMode.OVERVIEW : mode;
    }

    static void requestCommunitySavingsMode(CommunitySavingsMode mode) {
        requestedCommunitySavingsMode = mode == null ? CommunitySavingsMode.OVERVIEW : mode;
    }

    static CommunitySavingsMode consumeRequestedCommunitySavingsMode() {
        CommunitySavingsMode mode = requestedCommunitySavingsMode;
        requestedCommunitySavingsMode = null;
        return mode == null ? CommunitySavingsMode.OVERVIEW : mode;
    }

    static void requestBudgetMode(BudgetMode mode) {
        requestedBudgetMode = mode == null ? BudgetMode.OVERVIEW : mode;
    }

    static BudgetMode consumeRequestedBudgetMode() {
        BudgetMode mode = requestedBudgetMode;
        requestedBudgetMode = null;
        return mode == null ? BudgetMode.OVERVIEW : mode;
    }

    static boolean showSmartNavigationTarget(SmartNavigationTarget target) {
        if (target == null || smartNavigationHandler == null) {
            return false;
        }
        smartNavigationHandler.accept(target);
        return true;
    }

    static boolean showCoreWorkspace(CoreWorkspaceRoute route) {
        if (route == null || coreWorkspaceHandler == null) {
            return false;
        }
        coreWorkspaceHandler.accept(route);
        return true;
    }

    static void goBack() {
        if (backHandler != null) {
            backHandler.run();
        }
    }

    static Integer consumeRequestedAccountHistoryId() {
        Integer accountId = requestedAccountHistoryId;
        requestedAccountHistoryId = null;
        return accountId;
    }

    static Integer consumeRequestedAccountReconciliationId() {
        Integer accountId = requestedAccountReconciliationId;
        requestedAccountReconciliationId = null;
        return accountId;
    }

    static String consumeRequestedAccountsMode() {
        String mode = requestedAccountsMode;
        requestedAccountsMode = null;
        return mode;
    }

    static void requestReportType(String reportType) {
        requestedReportType = reportType;
    }

    static void requestReport(String reportGroup, String reportType) {
        requestedReportGroup = reportGroup;
        requestedReportType = reportType;
    }

    static String consumeRequestedReportGroup() {
        String reportGroup = requestedReportGroup;
        requestedReportGroup = null;
        return reportGroup;
    }

    static String consumeRequestedReportType() {
        String reportType = requestedReportType;
        requestedReportType = null;
        return reportType;
    }

    static void requestTransactionLedgerFilter(String typeFilter) {
        requestedLedgerTypeFilter = typeFilter;
    }

    static String consumeRequestedTransactionLedgerFilter() {
        String typeFilter = requestedLedgerTypeFilter;
        requestedLedgerTypeFilter = null;
        return typeFilter;
    }

    static void updateReportTitle(String reportType) {
        if (reportTitleHandler != null) {
            reportTitleHandler.accept(reportType);
        }
    }

    static void requestTransactionType(String transactionType) {
        requestedTransactionType = transactionType;
    }

    static void requestTransaction(String transactionType, String purpose, String personName) {
        requestedTransactionType = transactionType;
        requestedTransactionPurpose = purpose;
        requestedPersonName = personName;
    }

    static void requestProjectExpense(Integer projectId, Integer projectActivityId) {
        requestedTransactionType = "EXPENSE";
        requestedTransactionPurpose = "PROJECT_EXPENSE";
        requestedProjectId = projectId;
        requestedProjectActivityId = projectActivityId;
    }

    static void showTransactionEntry(String title) {
        if (transactionEntryHandler != null) {
            transactionEntryHandler.accept(title);
        }
    }

    static void requestLoanRepayment(Integer scheduleId, String loanDirection, String personName) {
        requestedLoanScheduleId = scheduleId;
        requestedLoanDirection = loanDirection;
        requestedLoanPersonName = personName;
        if (loanRepaymentHandler != null) {
            loanRepaymentHandler.run();
        }
    }

    static void showLoanLedger() {
        if (loanLedgerHandler != null) {
            loanLedgerHandler.run();
        }
    }

    static void requestGoalContribution(Integer goalId) {
        requestedGoalId = goalId;
        if (goalContributionHandler != null) {
            goalContributionHandler.run();
        }
    }

    static void requestGoalProject(Integer goalId) {
        requestedGoalId = goalId;
        if (goalProjectHandler != null) {
            goalProjectHandler.run();
        }
    }

    static void requestGoalSteps(Integer goalId) {
        requestedGoalId = goalId;
        if (goalStepsHandler != null) {
            goalStepsHandler.run();
        }
    }

    static void requestAssetRegistration(String sourceType, Integer sourceId, String sourceName, String guidance) {
        requestedAssetRegistrationContext = new AssetRegistrationContext(sourceType, sourceId, sourceName, guidance);
        if (assetRegistrationHandler != null) {
            assetRegistrationHandler.run();
        }
    }

    static AssetRegistrationContext consumeRequestedAssetRegistrationContext() {
        AssetRegistrationContext context = requestedAssetRegistrationContext;
        requestedAssetRegistrationContext = null;
        return context;
    }

    static String consumeRequestedTransactionType() {
        String transactionType = requestedTransactionType;
        requestedTransactionType = null;
        return transactionType;
    }

    static String consumeRequestedTransactionPurpose() {
        String purpose = requestedTransactionPurpose;
        requestedTransactionPurpose = null;
        return purpose;
    }

    static String consumeRequestedPersonName() {
        String personName = requestedPersonName;
        requestedPersonName = null;
        return personName;
    }

    static Integer consumeRequestedProjectId() {
        Integer projectId = requestedProjectId;
        requestedProjectId = null;
        return projectId;
    }

    static Integer consumeRequestedProjectActivityId() {
        Integer projectActivityId = requestedProjectActivityId;
        requestedProjectActivityId = null;
        return projectActivityId;
    }

    static Integer consumeRequestedLoanScheduleId() {
        Integer scheduleId = requestedLoanScheduleId;
        requestedLoanScheduleId = null;
        return scheduleId;
    }

    static String consumeRequestedLoanDirection() {
        String direction = requestedLoanDirection;
        requestedLoanDirection = null;
        return direction;
    }

    static String consumeRequestedLoanPersonName() {
        String personName = requestedLoanPersonName;
        requestedLoanPersonName = null;
        return personName;
    }

    static Integer consumeRequestedGoalId() {
        Integer goalId = requestedGoalId;
        requestedGoalId = null;
        return goalId;
    }

    static void reset() {
        accountHistoryHandler = null;
        accountReconciliationHandler = null;
        backHandler = null;
        loanRepaymentHandler = null;
        loanLedgerHandler = null;
        goalContributionHandler = null;
        goalProjectHandler = null;
        goalStepsHandler = null;
        smartNavigationHandler = null;
        coreWorkspaceHandler = null;
        reportTitleHandler = null;
        transactionEntryHandler = null;
        requestedAccountsMode = null;
        requestedSmartAnalysisMode = null;
        requestedCommunitySavingsMode = null;
        requestedBudgetMode = null;
        selectedAccountId = null;
        requestedAccountHistoryId = null;
        requestedAccountReconciliationId = null;
        requestedReportGroup = null;
        requestedReportType = null;
        requestedLedgerTypeFilter = null;
        requestedTransactionType = null;
        requestedTransactionPurpose = null;
        requestedPersonName = null;
        requestedProjectId = null;
        requestedProjectActivityId = null;
        requestedLoanScheduleId = null;
        requestedLoanDirection = null;
        requestedLoanPersonName = null;
        requestedGoalId = null;
        requestedAssetRegistrationContext = null;
    }

    record AssetRegistrationContext(String sourceType, Integer sourceId, String sourceName, String guidance) {
    }
}
