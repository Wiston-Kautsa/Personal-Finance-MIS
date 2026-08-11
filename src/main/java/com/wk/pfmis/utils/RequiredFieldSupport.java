package com.wk.pfmis.utils;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;

public final class RequiredFieldSupport {
    private RequiredFieldSupport() {
    }

    public static void apply(Node root) {
        if (root == null) {
            return;
        }
        applyToNode(root);
    }

    private static void applyToNode(Node node) {
        if (node instanceof Label label) {
            applyToLabel(label);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyToNode(child);
            }
        }
    }

    private static void applyToLabel(Label label) {
        String text = label.getText();
        if (text == null || !text.trim().endsWith("*") || label.getGraphic() != null) {
            return;
        }
        String clean = text.trim();
        label.setText(clean.substring(0, clean.length() - 1).stripTrailing());
        Label indicator = new Label("*");
        indicator.getStyleClass().add("required-indicator");
        indicator.setAccessibleText("required");
        label.setGraphic(indicator);
        label.setGraphicTextGap(3);
        label.setContentDisplay(ContentDisplay.RIGHT);
        if (label.getAccessibleHelp() == null || label.getAccessibleHelp().isBlank()) {
            label.setAccessibleHelp("Required field.");
        }
    }
}
