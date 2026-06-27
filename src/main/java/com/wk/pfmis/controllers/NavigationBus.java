package com.wk.pfmis.controllers;

final class NavigationBus {
    private static Runnable accountHistoryHandler;
    private static Integer requestedAccountHistoryId;
    private static String requestedReportType;
    private static String requestedTransactionType;

    private NavigationBus() {
    }

    static void onAccountHistoryRequested(Runnable handler) {
        accountHistoryHandler = handler;
    }

    static void showAccountHistory(int accountId) {
        requestedAccountHistoryId = accountId;
        if (accountHistoryHandler != null) {
            accountHistoryHandler.run();
        }
    }

    static Integer consumeRequestedAccountHistoryId() {
        Integer accountId = requestedAccountHistoryId;
        requestedAccountHistoryId = null;
        return accountId;
    }

    static void requestReportType(String reportType) {
        requestedReportType = reportType;
    }

    static String consumeRequestedReportType() {
        String reportType = requestedReportType;
        requestedReportType = null;
        return reportType;
    }

    static void requestTransactionType(String transactionType) {
        requestedTransactionType = transactionType;
    }

    static String consumeRequestedTransactionType() {
        String transactionType = requestedTransactionType;
        requestedTransactionType = null;
        return transactionType;
    }
}
