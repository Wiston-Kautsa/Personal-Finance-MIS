package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;

import java.util.ArrayList;
import java.util.List;

final class DataRefreshBus {
    private static final List<Runnable> listeners = new ArrayList<>();

    private DataRefreshBus() {
    }

    static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    static void clearListeners() {
        listeners.clear();
    }

    static void notifyDataChanged() {
        DatabaseHandler.getInstance().recordSystemLog(
                "System",
                "Data changed",
                "INFO",
                "A financial record changed and dependent screens were refreshed."
        );
        List<Runnable> snapshot = new ArrayList<>(listeners);
        for (Runnable listener : snapshot) {
            listener.run();
        }
    }
}
