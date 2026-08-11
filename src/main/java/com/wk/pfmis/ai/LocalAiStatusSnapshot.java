package com.wk.pfmis.ai;

import java.net.URI;
import java.nio.file.Path;

public record LocalAiStatusSnapshot(
        LocalAiStatus status,
        String summary,
        String detail,
        boolean enabled,
        boolean runtimeInstalled,
        boolean modelInstalled,
        boolean processRunning,
        boolean httpServerReady,
        boolean inferenceReady,
        String host,
        int port,
        URI endpoint,
        Long processId,
        Path runtimePath,
        Path modelPath,
        Path logFile
) {
    public LocalAiStatusSnapshot {
        status = status == null ? LocalAiStatus.ERROR : status;
        summary = summary == null || summary.isBlank() ? defaultSummary(status) : summary.trim();
        detail = detail == null ? "" : detail.trim();
    }

    public boolean ready() {
        return status.ready() && httpServerReady;
    }

    public String statusText() {
        return "Status: " + status.displayName();
    }

    public String displayText() {
        return detail.isBlank() ? statusText() + "\n" + summary : statusText() + "\n" + summary + "\n" + detail;
    }

    private static String defaultSummary(LocalAiStatus status) {
        return switch (status) {
            case DISABLED -> "PFMIS Local AI is disabled.";
            case NOT_INSTALLED, RUNTIME_MISSING -> "Local AI runtime is incomplete.";
            case MODEL_MISSING -> "Local AI model is not installed.";
            case STARTING -> "Local AI is starting.";
            case LOADING_MODEL -> "Loading the local AI model.";
            case WAITING_FOR_SERVER -> "Waiting for the Local AI HTTP service.";
            case READY -> "Local AI is available and ready to use.";
            case STOPPED -> "Local AI service is stopped.";
            case PORT_CONFLICT -> "Local AI could not start because its network port is already in use.";
            case STARTUP_TIMEOUT -> "Local AI did not become ready in time.";
            case RUNTIME_ERROR -> "Local AI runtime could not start.";
            case MODEL_ERROR -> "Local AI model could not be loaded.";
            case ERROR -> "Local AI could not start.";
        };
    }
}
