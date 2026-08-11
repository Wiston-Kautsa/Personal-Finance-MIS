package com.wk.pfmis.controllers;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;

import java.io.PrintWriter;
import java.io.StringWriter;

final class UiAlerts {
    private UiAlerts() {
    }

    static void error(String message, Throwable exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("PFMIS");
        alert.setHeaderText(message);
        alert.setContentText(errorContent(exception));
        if (exception != null) {
            TextArea stackTrace = new TextArea(sanitizedStackTrace(exception));
            stackTrace.setEditable(false);
            stackTrace.setWrapText(false);
            stackTrace.setMaxWidth(Double.MAX_VALUE);
            stackTrace.setMaxHeight(Double.MAX_VALUE);
            alert.getDialogPane().setExpandableContent(stackTrace);
        }
        alert.showAndWait();
    }

    static void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("PFMIS");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("PFMIS");
        alert.setHeaderText(header);
        alert.setContentText(message);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static String rootMessage(Throwable throwable) {
        Throwable root = rootCause(throwable);
        if (root == null) {
            return "";
        }
        String message = root.getMessage();
        return redact(message == null || message.isBlank() ? root.getClass().getSimpleName() : message);
    }

    private static String errorContent(Throwable exception) {
        if (exception == null) {
            return "";
        }
        String reason = rootMessage(exception);
        if (reason.isBlank()) {
            return "";
        }
        return "Reason:" + System.lineSeparator() + reason;
    }

    private static String sanitizedStackTrace(Throwable exception) {
        StringWriter buffer = new StringWriter();
        exception.printStackTrace(new PrintWriter(buffer));
        return redact(buffer.toString());
    }

    private static String redact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String redacted = value.replaceAll("(?i)(password|passwd|pwd|token|secret|api[_-]?key|credential)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2[redacted]");
        redacted = redacted.replaceAll("(sk-[A-Za-z0-9_-]{8,}|sk_live_[A-Za-z0-9_-]{8,}|AIza[A-Za-z0-9_-]{12,}|ghp_[A-Za-z0-9_]{12,}|github_pat_[A-Za-z0-9_]{12,}|xox[baprs]-[A-Za-z0-9-]{12,}|ya29\\.[A-Za-z0-9_-]{12,})", "[redacted]");
        return redacted;
    }
}
