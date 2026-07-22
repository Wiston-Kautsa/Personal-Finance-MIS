package com.wk.pfmis.controllers;

/** Clears controller-level static references when a user signs out or changes workspace. */
public final class ControllerSessionState {
    private ControllerSessionState() {
    }

    public static void reset() {
        DataRefreshBus.clearListeners();
        NavigationBus.reset();
    }
}
