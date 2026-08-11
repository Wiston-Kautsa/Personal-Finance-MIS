package com.wk.pfmis.ai;

public class LocalAiException extends IllegalStateException {
    private final LocalAiStatus status;
    private final String userMessage;

    public LocalAiException(LocalAiStatus status, String userMessage) {
        super(userMessage);
        this.status = status == null ? LocalAiStatus.ERROR : status;
        this.userMessage = userMessage == null || userMessage.isBlank()
                ? "Local AI could not start."
                : userMessage.trim();
    }

    public LocalAiException(LocalAiStatus status, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.status = status == null ? LocalAiStatus.ERROR : status;
        this.userMessage = userMessage == null || userMessage.isBlank()
                ? "Local AI could not start."
                : userMessage.trim();
    }

    public LocalAiStatus status() {
        return status;
    }

    public String userMessage() {
        return userMessage;
    }
}
