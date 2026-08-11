package com.wk.pfmis.utils;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class FormValidator {
    private static final String FIELD_ERROR_CLASS = "field-error";
    private final List<String> errors = new ArrayList<>();
    private Node firstInvalid;

    private FormValidator() {
    }

    public static FormValidator create() {
        return new FormValidator();
    }

    public static HBox requiredLabel(String labelText) {
        Label label = new Label(labelText);
        Label asterisk = new Label("*");
        asterisk.getStyleClass().add("required-indicator");
        HBox container = new HBox(3, label, asterisk);
        container.setAlignment(Pos.CENTER_LEFT);
        return container;
    }

    public boolean requireText(TextInputControl field, Label errorLabel, String message) {
        if (field == null || field.isDisabled() || !field.isVisible()) {
            clearInvalid(field, errorLabel);
            return true;
        }
        if (field.getText() == null || field.getText().isBlank()) {
            markInvalid(field, errorLabel, message);
            return false;
        }
        clearInvalid(field, errorLabel);
        return true;
    }

    public boolean requireSelection(ComboBox<?> field, Label errorLabel, String message) {
        if (field == null || field.isDisabled() || !field.isVisible()) {
            clearInvalid(field, errorLabel);
            return true;
        }
        Object value = field.getValue();
        String editorText = field.isEditable() && field.getEditor() != null ? field.getEditor().getText() : "";
        if (value == null && (editorText == null || editorText.isBlank())) {
            markInvalid(field, errorLabel, message);
            return false;
        }
        clearInvalid(field, errorLabel);
        return true;
    }

    public boolean requireDate(DatePicker field, Label errorLabel, String message) {
        if (field == null || field.isDisabled() || !field.isVisible()) {
            clearInvalid(field, errorLabel);
            return true;
        }
        if (field.getValue() == null) {
            markInvalid(field, errorLabel, message);
            return false;
        }
        clearInvalid(field, errorLabel);
        return true;
    }

    public boolean requirePositiveAmount(TextInputControl field, Label errorLabel, String message) {
        if (!requireText(field, errorLabel, message)) {
            return false;
        }
        try {
            double amount = Double.parseDouble(field.getText().trim().replace(",", ""));
            if (amount <= 0) {
                markInvalid(field, errorLabel, message);
                return false;
            }
        } catch (NumberFormatException exception) {
            markInvalid(field, errorLabel, message);
            return false;
        }
        clearInvalid(field, errorLabel);
        return true;
    }

    public boolean requireYearMonth(TextInputControl field, Label errorLabel, String message) {
        if (!requireText(field, errorLabel, message)) {
            return false;
        }
        try {
            YearMonth.parse(field.getText().trim());
        } catch (DateTimeParseException exception) {
            markInvalid(field, errorLabel, message);
            return false;
        }
        clearInvalid(field, errorLabel);
        return true;
    }

    public boolean requireDateRange(DatePicker start, Label startError, DatePicker end, Label endError, String message) {
        LocalDate startDate = start == null ? null : start.getValue();
        LocalDate endDate = end == null ? null : end.getValue();
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            markInvalid(end, endError, message);
            return false;
        }
        clearInvalid(start, startError);
        clearInvalid(end, endError);
        return true;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public String summary() {
        int count = errors.size();
        return count == 0 ? "" : "Please complete the " + count + " required field" + (count == 1 ? "" : "s") + " highlighted below.";
    }

    public String messages() {
        return String.join("\n", errors);
    }

    public void focusFirstInvalid() {
        if (firstInvalid != null) {
            Platform.runLater(firstInvalid::requestFocus);
        }
    }

    public static void clearInvalid(Node field, Label errorLabel) {
        if (field != null) {
            field.getStyleClass().remove(FIELD_ERROR_CLASS);
        }
        if (errorLabel != null) {
            errorLabel.setText("");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    private void markInvalid(Node field, Label errorLabel, String message) {
        if (field != null && !field.getStyleClass().contains(FIELD_ERROR_CLASS)) {
            field.getStyleClass().add(FIELD_ERROR_CLASS);
        }
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
        if (firstInvalid == null) {
            firstInvalid = field;
        }
        errors.add(message);
    }
}
