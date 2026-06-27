package com.wk.pfmis.controllers;

import java.util.ArrayList;
import java.util.List;

final class DataRefreshBus {
    private static final List<Runnable> listeners = new ArrayList<>();

    private DataRefreshBus() {
    }

    static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    static void notifyDataChanged() {
        List<Runnable> snapshot = new ArrayList<>(listeners);
        for (Runnable listener : snapshot) {
            listener.run();
        }
    }
}
