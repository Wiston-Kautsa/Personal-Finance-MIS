package com.wk.pfmis.ai;

public enum LocalAiStatus {
    DISABLED("Disabled"),
    NOT_INSTALLED("Not Installed"),
    RUNTIME_MISSING("Runtime Missing"),
    MODEL_MISSING("Model Missing"),
    STARTING("Starting"),
    LOADING_MODEL("Loading Model"),
    WAITING_FOR_SERVER("Waiting For Server"),
    READY("Ready"),
    STOPPED("Stopped"),
    ERROR("Error"),
    PORT_CONFLICT("Port Conflict"),
    STARTUP_TIMEOUT("Startup Timeout"),
    RUNTIME_ERROR("Runtime Error"),
    MODEL_ERROR("Model Error");

    private final String displayName;

    LocalAiStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean ready() {
        return this == READY;
    }

    public boolean starting() {
        return this == STARTING || this == LOADING_MODEL || this == WAITING_FOR_SERVER;
    }
}
