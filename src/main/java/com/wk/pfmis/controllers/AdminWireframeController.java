package com.wk.pfmis.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class AdminWireframeController {
    @FXML private Label statusLabel;

    @FXML
    private void showWireframeStatus(ActionEvent event) {
        String action = "Action";
        if (event.getSource() instanceof Button button && button.getText() != null && !button.getText().isBlank()) {
            action = button.getText();
        }
        if (statusLabel != null) {
            statusLabel.setText(action + " selected. Database logic can now be connected to this screen.");
        }
    }
}
