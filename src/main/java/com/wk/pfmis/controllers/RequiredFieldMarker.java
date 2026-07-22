package com.wk.pfmis.controllers;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;

final class RequiredFieldMarker {
    private RequiredFieldMarker() {
    }

    static void apply(Node root) {
        if (root == null) {
            return;
        }
        if (root instanceof Label label) {
            String text = label.getText();
            if (text != null && text.trim().endsWith("*")) {
                setRequired(label, true);
            }
        }
        if (root instanceof ScrollPane scrollPane) {
            apply(scrollPane.getContent());
        }
        if (root instanceof TitledPane titledPane) {
            apply(titledPane.getContent());
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                apply(child);
            }
        }
    }

    static void setRequired(Label label, boolean required) {
        if (label == null) {
            return;
        }
        label.setText(stripMarker(label.getText()));
        if (required) {
            Label star = new Label("*");
            star.getStyleClass().add("required-star");
            label.setGraphic(star);
            label.setContentDisplay(ContentDisplay.RIGHT);
            label.setGraphicTextGap(3);
            if (!label.getStyleClass().contains("required-field-label")) {
                label.getStyleClass().add("required-field-label");
            }
            String accessibleText = label.getText() == null || label.getText().isBlank()
                    ? "Required field"
                    : label.getText() + " required";
            label.setAccessibleText(accessibleText);
        } else {
            label.setGraphic(null);
            label.getStyleClass().remove("required-field-label");
            label.setAccessibleText(label.getText());
        }
    }

    private static String stripMarker(String text) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        while (value.endsWith("*")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }
}
