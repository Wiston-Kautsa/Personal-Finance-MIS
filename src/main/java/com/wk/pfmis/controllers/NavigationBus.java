package com.wk.pfmis.controllers;

import java.util.function.Consumer;

final class NavigationBus {
    private static Runnable accountHistoryHandler;
    private static Runnable backHandler;
    private static Consumer<String> reportTitleHandler;
    private static Consumer<String> transactionEntryHandler;
    private static Integer requestedAccountHistoryId;
    private static String requestedReportGroup;
    private static String requestedReportType;
    private static String requestedTransactionType;
    private static String requestedTransactionPurpose;
    private static String requestedPersonName;

    private NavigationBus() {
    }

    static void onAccountHistoryRequested(Runnable handler) {
        accountHistoryHandler = handler;
    }

    static void onBackRequested(Runnable handler) {
        backHandler = handler;
    }

    static void onReportTitleChanged(Consumer<String> handler) {
        reportTitleHandler = handler;
    }

    static void onTransactionEntryRequested(Consumer<String> handler) {
        transactionEntryHandler = handler;
    }

    static void showAccountHistory(int accountId) {
        requestedAccountHistoryId = accountId;
        if (accountHistoryHandler != null) {
            accountHistoryHandler.run();
        }
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

    static void showTransactionEntry(String title) {
        if (transactionEntryHandler != null) {
            transactionEntryHandler.accept(title);
        }
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

    static void reset() {
        accountHistoryHandler = null;
        backHandler = null;
        reportTitleHandler = null;
        transactionEntryHandler = null;
        requestedAccountHistoryId = null;
        requestedReportGroup = null;
        requestedReportType = null;
        requestedTransactionType = null;
        requestedTransactionPurpose = null;
        requestedPersonName = null;
    }
}
