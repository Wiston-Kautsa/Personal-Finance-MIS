package com.wk.pfmis.controllers;

import com.wk.pfmis.MainApp;
import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.mail.EmailService;
import com.wk.pfmis.models.SystemUser;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField resetEmailField;
    @FXML private Label messageLabel;
    @FXML private Label setupHintLabel;
    @FXML private Label systemEmailLabel;
    @FXML private Button registerButton;
    @FXML private VBox resetPanel;

    private final AuthDatabase authDatabase = AuthDatabase.getInstance();
    private final EmailService emailService = EmailService.getInstance();

    @FXML
    public void initialize() {
        boolean firstRun = !authDatabase.hasUsers();
        setupHintLabel.setText(firstRun
                ? "No users are registered. Create the first account; it will become the Super Administrator."
                : "Sign in to open your private PFMIS workspace. New accounts are created by the Super Administrator.");
        systemEmailLabel.setText("System email: " + emailService.systemEmailAddress()
                + "\n" + emailService.sendConfigurationStatus()
                + "\n" + emailService.receiveConfigurationStatus());
        registerButton.setText("Create First Administrator");
        registerButton.setVisible(firstRun);
        registerButton.setManaged(firstRun);
        resetPanel.setVisible(!firstRun);
        resetPanel.setManaged(!firstRun);
        if (!firstRun && usernameField.getText().isBlank()) {
            usernameField.setText(AuthDatabase.DEFAULT_SUPER_ADMIN_EMAIL);
        }
        if (!firstRun && resetEmailField.getText().isBlank()) {
            resetEmailField.setText(usernameField.getText());
        }
        usernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (resetEmailField.getText().isBlank() || resetEmailField.getText().equals(oldValue)) {
                resetEmailField.setText(newValue);
            }
        });
        if (!firstRun && passwordField.getText().isBlank()) {
            passwordField.setText(AuthDatabase.DEFAULT_SUPER_ADMIN_PASSWORD);
        }
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                signIn();
            }
        });
        resetEmailField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                resetPasswordByEmail();
            }
        });
        Platform.runLater(usernameField::requestFocus);
    }

    @FXML
    private void signIn() {
        clearMessage();
        try {
            SystemUser user = authDatabase.authenticate(usernameField.getText(), passwordField.getText());
            passwordField.clear();
            MainApp.completeLogin(user);
        } catch (RuntimeException exception) {
            showError(rootMessage(exception));
            passwordField.clear();
            passwordField.requestFocus();
        }
    }

    @FXML
    private void createAccount() {
        MainApp.showRegistration();
    }

    @FXML
    private void resetPasswordByEmail() {
        clearMessage();
        if (!emailService.isSendConfigured()) {
            showError(emailService.sendConfigurationStatus());
            return;
        }
        String account = resetEmailField.getText().isBlank() ? usernameField.getText() : resetEmailField.getText();
        if (account == null || account.trim().isEmpty()) {
            showError("Enter the registered email or username to receive a reset email.");
            resetEmailField.requestFocus();
            return;
        }
        try {
            AuthDatabase.PasswordResetDelivery reset = authDatabase.prepareEmailPasswordReset(account, null);
            emailService.sendPasswordResetEmail(reset.email(), reset.displayName(), reset.temporaryPassword());
            authDatabase.completeEmailPasswordReset(reset, null);
            showSuccess("A temporary password was sent to " + reset.email() + ".");
        } catch (RuntimeException exception) {
            showError(rootMessage(exception));
        }
    }

    private void showError(String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().remove("auth-message-success");
        if (!messageLabel.getStyleClass().contains("auth-message-error")) {
            messageLabel.getStyleClass().add("auth-message-error");
        }
    }

    private void showSuccess(String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().remove("auth-message-error");
        if (!messageLabel.getStyleClass().contains("auth-message-success")) {
            messageLabel.getStyleClass().add("auth-message-success");
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
