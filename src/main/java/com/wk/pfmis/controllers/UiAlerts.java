package com.wk.pfmis.controllers;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

final class UiAlerts {
    private UiAlerts() {
    }

    static void error(String message, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("PFMIS");
        alert.setHeaderText(message);
        alert.setContentText(exception == null ? "" : exception.getMessage());
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
}
