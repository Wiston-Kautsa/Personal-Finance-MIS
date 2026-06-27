package com.wk.pfmis.controllers;

final class NavigationBus {
    private static Runnable accountHistoryHandler;
    private static Integer requestedAccountHistoryId;

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
}
