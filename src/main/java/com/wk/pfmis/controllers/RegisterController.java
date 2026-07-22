package com.wk.pfmis.controllers;

import com.wk.pfmis.MainApp;
import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.models.SystemUser;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController {
    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label headingLabel;
    @FXML private Label roleHintLabel;
    @FXML private Label messageLabel;

    private final AuthDatabase authDatabase = AuthDatabase.getInstance();

    @FXML
    public void initialize() {
        boolean firstUser = !authDatabase.hasUsers();
        headingLabel.setText(firstUser ? "Create the First Super Administrator" : "Registration Is Controlled by the Super Administrator");
        roleHintLabel.setText(firstUser
                ? "The first account controls user administration and can open every user's workspace."
                : "Return to sign in. Additional users must be created from Manage Users by a Super Administrator.");
        if (firstUser) {
            fullNameField.setText(AuthDatabase.DEFAULT_SUPER_ADMIN_FULL_NAME);
            usernameField.setText(AuthDatabase.DEFAULT_SUPER_ADMIN_USERNAME);
            emailField.setText(AuthDatabase.DEFAULT_SUPER_ADMIN_EMAIL);
            Platform.runLater(passwordField::requestFocus);
        } else {
            Platform.runLater(MainApp::showLogin);
        }
    }

    @FXML
    private void register() {
        clearMessage();
        if (authDatabase.hasUsers()) {
            showError("Additional users must be created by a Super Administrator.");
            return;
        }
        if (!passwordField.getText().equals(confirmPasswordField.getText())) {
            showError("The two passwords do not match.");
            confirmPasswordField.clear();
            confirmPasswordField.requestFocus();
            return;
        }
        try {
            SystemUser user = authDatabase.registerUser(
                    fullNameField.getText(),
                    usernameField.getText(),
                    emailField.getText(),
                    passwordField.getText()
            );
            passwordField.clear();
            confirmPasswordField.clear();
            MainApp.completeLogin(user);
        } catch (RuntimeException exception) {
            showError(rootMessage(exception));
        }
    }

    @FXML
    private void backToLogin() {
        MainApp.showLogin();
    }

    private void showError(String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().remove("auth-message-success");
        if (!messageLabel.getStyleClass().contains("auth-message-error")) {
            messageLabel.getStyleClass().add("auth-message-error");
        }
    }

    private void clearMessage() {
        messageLabel.setText("");
        messageLabel.getStyleClass().removeAll("auth-message-error", "auth-message-success");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
