package com.wk.pfmis.controllers;

import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.RiskLevel;
import com.wk.pfmis.security.UserSession;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Arrays;

public final class SecurityVerificationPane extends VBox {
    private final PasswordField passwordField = new PasswordField();
    private final TextField visiblePasswordField = new TextField();
    private final TextField phraseField = new TextField();
    private final TextArea reasonArea = new TextArea();
    private final CheckBox understandingCheckBox = new CheckBox("I understand the impact of this action.");
    private final Label statusLabel = new Label();
    private final VBox passwordBlock;
    private final VBox phraseBlock;
    private final VBox reasonBlock;
    private final String requiredPhrase;
    private final boolean passwordRequired;
    private final boolean reasonRequired;

    private boolean passwordVisible;

    public SecurityVerificationPane(
            String actionName,
            String impact,
            RiskLevel riskLevel,
            String requiredPhrase,
            boolean passwordRequired,
            boolean reasonRequired,
            String existingSessionStatus
    ) {
        this.requiredPhrase = requiredPhrase == null ? "" : requiredPhrase.trim();
        this.passwordRequired = passwordRequired;
        this.reasonRequired = reasonRequired;
        getStyleClass().add("security-verification-pane");
        setSpacing(14);
        setMaxWidth(Double.MAX_VALUE);

        Label heading = new Label("Security Verification");
        heading.getStyleClass().add("security-heading");
        Label caption = new Label("This action requires verification of your current account.");
        caption.setWrapText(true);
        caption.getStyleClass().add("security-caption");

        VBox identity = identityCard(actionName, impact, riskLevel);
        passwordBlock = fieldBlock("Password", passwordInput());
        phraseBlock = fieldBlock("Confirmation phrase", phraseField);
        reasonBlock = fieldBlock("Reason", reasonArea);

        passwordField.setPromptText("Enter your current password");
        visiblePasswordField.setPromptText("Enter your current password");
        passwordField.getStyleClass().addAll("security-input", "security-password-field");
        visiblePasswordField.getStyleClass().addAll("security-input", "security-password-field");
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setManaged(false);
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        phraseField.setPromptText(this.requiredPhrase.isBlank() ? "Confirmation phrase" : this.requiredPhrase);
        phraseField.getStyleClass().addAll("security-input", "security-confirmation-field");
        reasonArea.setPromptText("Why is this action required?");
        reasonArea.setPrefRowCount(3);
        reasonArea.setWrapText(true);
        reasonArea.getStyleClass().add("security-input");
        understandingCheckBox.getStyleClass().add("security-understanding-check");
        statusLabel.getStyleClass().add("inline-validation-error");

        passwordBlock.setVisible(passwordRequired);
        passwordBlock.setManaged(passwordRequired);
        phraseBlock.setVisible(!this.requiredPhrase.isBlank());
        phraseBlock.setManaged(!this.requiredPhrase.isBlank());

        if (!passwordRequired && existingSessionStatus != null && !existingSessionStatus.isBlank()) {
            markVerified(existingSessionStatus);
        }

        getChildren().addAll(heading, caption, identity);
        getChildren().add(passwordBlock);
        if (!this.requiredPhrase.isBlank()) {
            getChildren().add(phraseBlock);
        }
        getChildren().addAll(reasonBlock, understandingCheckBox, statusLabel);
    }

    public boolean validateInput() {
        clearValidation();
        if (passwordRequired && passwordField.getText().isBlank()) {
            setError("Enter your current password.");
            passwordField.requestFocus();
            return false;
        }
        if (!requiredPhrase.isBlank() && !requiredPhrase.equals(phraseField.getText().trim())) {
            setError("Type the confirmation phrase exactly as shown.");
            phraseField.requestFocus();
            return false;
        }
        if (reasonRequired && reasonArea.getText().trim().isBlank()) {
            setError("Enter a reason before continuing.");
            reasonArea.requestFocus();
            return false;
        }
        if (!understandingCheckBox.isSelected()) {
            setError("Confirm that you understand the impact of this action.");
            understandingCheckBox.requestFocus();
            return false;
        }
        return true;
    }

    public boolean isPasswordRequired() {
        return passwordRequired;
    }

    public char[] passwordChars() {
        String value = passwordField.getText();
        return value == null ? new char[0] : value.toCharArray();
    }

    public String reason() {
        return reasonArea.getText() == null ? "" : reasonArea.getText().trim();
    }

    public void clearPassword() {
        char[] password = passwordChars();
        try {
            passwordField.clear();
            visiblePasswordField.clear();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public void setError(String message) {
        statusLabel.getStyleClass().remove("security-verified-badge");
        if (!statusLabel.getStyleClass().contains("inline-validation-error")) {
            statusLabel.getStyleClass().add("inline-validation-error");
        }
        statusLabel.setText(message == null ? "" : message);
    }

    public void markVerified(String message) {
        statusLabel.getStyleClass().remove("inline-validation-error");
        if (!statusLabel.getStyleClass().contains("security-verified-badge")) {
            statusLabel.getStyleClass().add("security-verified-badge");
        }
        statusLabel.setText(message == null || message.isBlank()
                ? "Identity verified for this high-risk action."
                : message);
        passwordBlock.setVisible(false);
        passwordBlock.setManaged(false);
    }

    private VBox identityCard(String actionName, String impact, RiskLevel riskLevel) {
        SystemUser signedIn = safeAuthenticatedUser();
        SystemUser workspace = safeWorkspaceUser();
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        grid.setMaxWidth(Double.MAX_VALUE);
        addRow(grid, 0, "Signed in as", signedIn == null ? "No signed-in user" : signedIn.getDisplayName() + " - " + signedIn.getRoleDisplay());
        addRow(grid, 1, "Target workspace", workspace == null ? "No active workspace" : workspace.getDisplayName() + " (" + workspace.getUsername() + ")");
        addRow(grid, 2, "Action", actionName);
        addRow(grid, 3, "Risk level", riskLevel == null ? RiskLevel.NORMAL.name() : riskLevel.name());
        addRow(grid, 4, "Impact", impact);

        VBox box = new VBox(10, grid);
        box.getStyleClass().add("security-identity-card");
        return box;
    }

    private void addRow(GridPane grid, int row, String label, String value) {
        Label name = new Label(label);
        name.getStyleClass().add("security-row-label");
        Label details = new Label(value == null ? "" : value);
        details.setWrapText(true);
        details.getStyleClass().add("security-row-value");
        grid.add(name, 0, row);
        grid.add(details, 1, row);
    }

    private Node passwordInput() {
        Button toggle = new Button("Show");
        toggle.getStyleClass().add("secondary-button");
        toggle.setOnAction(event -> {
            passwordVisible = !passwordVisible;
            passwordField.setVisible(!passwordVisible);
            passwordField.setManaged(!passwordVisible);
            visiblePasswordField.setVisible(passwordVisible);
            visiblePasswordField.setManaged(passwordVisible);
            toggle.setText(passwordVisible ? "Hide" : "Show");
        });
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox fieldStack = new VBox(passwordField, visiblePasswordField);
        HBox.setHgrow(fieldStack, Priority.ALWAYS);
        row.getChildren().addAll(fieldStack, toggle);
        return row;
    }

    private VBox fieldBlock(String labelText, Node field) {
        VBox box = new VBox(6);
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        field.getStyleClass().add("security-input");
        box.getChildren().addAll(label, field);
        return box;
    }

    private void clearValidation() {
        statusLabel.setText("");
        statusLabel.getStyleClass().remove("security-verified-badge");
        if (!statusLabel.getStyleClass().contains("inline-validation-error")) {
            statusLabel.getStyleClass().add("inline-validation-error");
        }
    }

    private SystemUser safeAuthenticatedUser() {
        try {
            return UserSession.getAuthenticatedUser();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private SystemUser safeWorkspaceUser() {
        try {
            return UserSession.getWorkspaceUser();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
