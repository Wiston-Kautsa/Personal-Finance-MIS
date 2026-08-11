package com.wk.pfmis.controllers;

import com.wk.pfmis.MainApp;
import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.mail.EmailService;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.LoginCredentialStore;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import java.util.Arrays;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private TextField resetEmailField;
    @FXML private Label messageLabel;
    @FXML private Label savedLoginStatusLabel;
    @FXML private Label setupHintLabel;
    @FXML private Label systemEmailLabel;
    @FXML private CheckBox rememberMeCheckBox;
    @FXML private Button signInButton;
    @FXML private Button forgetSavedLoginButton;
    @FXML private Button passwordVisibilityButton;
    @FXML private VBox resetPanel;
    @FXML private VBox savedLoginPanel;

    private final AuthDatabase authDatabase = AuthDatabase.getInstance();
    private final EmailService emailService = EmailService.getInstance();
    private final LoginCredentialStore credentialStore = LoginCredentialStore.getInstance();

    private boolean firstRun;
    private boolean passwordVisible;
    private boolean restoredPasswordFromStore;
    private boolean suppressPasswordChangeTracking;
    private boolean suppressRememberMeListener;
    private String restoredCredentialAccount = "";

    @FXML
    public void initialize() {
        firstRun = !authDatabase.hasUsers();
        setupHintLabel.setText(firstRun
                ? "Create the first Super Administrator account to open this private PFMIS workspace."
                : "Sign in to open your private PFMIS workspace. New accounts are created by the Super Administrator.");
        systemEmailLabel.setText(emailService.isSendConfigured()
                ? "Password reset email is configured."
                : "Password reset email is not configured.");
        signInButton.setText(firstRun ? "Create First Administrator" : "Sign In");
        usernameField.setDisable(firstRun);
        passwordField.setDisable(firstRun);
        visiblePasswordField.setDisable(firstRun);
        rememberMeCheckBox.setDisable(firstRun);
        resetPanel.setVisible(!firstRun);
        resetPanel.setManaged(!firstRun);
        configurePasswordVisibility();
        restoreSavedCredentials();
        configureRememberMeRemoval();
        if (!firstRun && resetEmailField.getText().isBlank()) {
            resetEmailField.setText(usernameField.getText());
        }
        usernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (resetEmailField.getText().isBlank() || resetEmailField.getText().equals(oldValue)) {
                resetEmailField.setText(newValue);
            }
            if (!sameAccount(newValue, restoredCredentialAccount)) {
                restoredPasswordFromStore = false;
            }
        });
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                signIn();
            }
        });
        visiblePasswordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                signIn();
            }
        });
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                signIn();
            }
        });
        resetEmailField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                resetPasswordByEmail();
            }
        });
        Platform.runLater(() -> {
            if (firstRun) {
                signInButton.requestFocus();
            } else if (restoredPasswordFromStore) {
                signInButton.requestFocus();
            } else if (rememberMeCheckBox.isSelected()) {
                passwordField.requestFocus();
            } else {
                usernameField.requestFocus();
            }
        });
    }

    @FXML
    private void signIn() {
        authenticateAndOpen(false);
    }

    private void authenticateAndOpen(boolean requireSuperAdmin) {
        clearMessage();
        if (!authDatabase.hasUsers()) {
            createAccount();
            return;
        }

        String login = usernameField.getText();
        char[] passwordChars = activePasswordChars();
        String password = new String(passwordChars);
        try {
            SystemUser user = authDatabase.authenticate(login, password);
            if (requireSuperAdmin && !user.isSuperAdmin()) {
                clearPasswordFields();
                showError("This shortcut requires a Super Administrator account.");
                usernameField.requestFocus();
                return;
            }
            saveCredentialsAfterSuccessfulLogin(user, passwordChars);
            clearPasswordFields();
            MainApp.completeLogin(user);
        } catch (RuntimeException exception) {
            if (shouldClearRestoredCredentials(login)) {
                clearRememberedCredentials(false, true);
                showError("The saved password is no longer valid. Please enter your current password.");
            } else {
                showError(rootMessage(exception));
            }
            clearPasswordFields();
            passwordField.requestFocus();
        } finally {
            Arrays.fill(passwordChars, '\0');
            password = "";
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
        resetPanel.setDisable(true);
        Task<Boolean> resetTask = new Task<>() {
            @Override
            protected Boolean call() {
                try {
                    AuthDatabase.PasswordResetDelivery reset = authDatabase.prepareEmailPasswordReset(account, null);
                    emailService.sendPasswordResetEmail(reset.email(), reset.displayName(), reset.temporaryPassword());
                    authDatabase.completeEmailPasswordReset(reset, null);
                    return true;
                } catch (IllegalArgumentException | SecurityException exception) {
                    return false;
                }
            }
        };
        resetTask.setOnSucceeded(event -> {
            resetPanel.setDisable(false);
            showSuccess("If the account can receive password resets, an email has been sent.");
        });
        resetTask.setOnFailed(event -> {
            resetPanel.setDisable(false);
            showError(rootMessage(resetTask.getException()));
        });
        Thread worker = new Thread(resetTask, "pfmis-password-reset");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void signInAsSuperAdmin() {
        clearMessage();
        if (!authDatabase.hasUsers()) {
            createAccount();
            return;
        }
        if (usernameField.getText().isBlank() || activePasswordText().isBlank()) {
            showSuccess("Enter Super Administrator credentials, then use this button again.");
            usernameField.requestFocus();
            return;
        }
        authenticateAndOpen(true);
    }

    @FXML
    private void focusPasswordReset() {
        if (!resetPanel.isVisible()) {
            showError("Create the first Super Administrator account before password reset is available.");
            return;
        }
        if (resetEmailField.getText().isBlank()) {
            resetEmailField.setText(usernameField.getText());
        }
        resetEmailField.requestFocus();
    }

    @FXML
    private void forgetSavedLogin() {
        clearMessage();
        clearRememberedCredentials(true, true);
        usernameField.requestFocus();
    }

    @FXML
    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        suppressPasswordChangeTracking = true;
        try {
            if (passwordVisible) {
                visiblePasswordField.setText(passwordField.getText());
            } else {
                passwordField.setText(visiblePasswordField.getText());
            }
        } finally {
            suppressPasswordChangeTracking = false;
        }
        passwordField.setVisible(!passwordVisible);
        passwordField.setManaged(!passwordVisible);
        visiblePasswordField.setVisible(passwordVisible);
        visiblePasswordField.setManaged(passwordVisible);
        passwordVisibilityButton.setText(passwordVisible ? "Hide" : "Show");
        Platform.runLater((passwordVisible ? visiblePasswordField : passwordField)::requestFocus);
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

    private void restoreSavedCredentials() {
        if (rememberMeCheckBox == null) {
            return;
        }
        if (firstRun) {
            updateForgetSavedLoginButtonState();
            return;
        }

        try (LoginCredentialStore.LoadedCredentials credentials = credentialStore.load()) {
            updateForgetSavedLoginButtonState();
            if (credentials.status() == LoginCredentialStore.LoadStatus.NONE) {
                return;
            }
            if (credentials.status() == LoginCredentialStore.LoadStatus.CORRUPTED) {
                clearPasswordFields();
                showError(messageOrDefault(
                        credentials.userMessage(),
                        "The saved password could not be restored. Please enter it again."
                ));
                updateForgetSavedLoginButtonState();
                return;
            }
            if (!credentials.usernameOrEmail().isBlank()) {
                usernameField.setText(credentials.usernameOrEmail());
                rememberMeCheckBox.setSelected(true);
            }
            if (credentials.hasPassword()) {
                setPasswordFields(credentials.password());
                restoredPasswordFromStore = true;
                restoredCredentialAccount = credentials.usernameOrEmail();
                setSavedLoginStatus("Saved login restored securely.");
            }
            if (!credentials.userMessage().isBlank()) {
                setSavedLoginStatus(credentials.userMessage());
                if (credentials.status() == LoginCredentialStore.LoadStatus.USERNAME_ONLY) {
                    showError(credentials.userMessage());
                } else {
                    showSuccess(credentials.userMessage());
                }
            } else if (credentials.status() == LoginCredentialStore.LoadStatus.USERNAME_ONLY) {
                setSavedLoginStatus("Saved username restored. Enter your password to sign in.");
            }
        }
    }

    private void configurePasswordVisibility() {
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        passwordVisible = false;
        passwordField.setVisible(true);
        passwordField.setManaged(true);
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setManaged(false);
        passwordVisibilityButton.setText("Show");
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressPasswordChangeTracking) {
                restoredPasswordFromStore = false;
            }
        });
    }

    private void configureRememberMeRemoval() {
        if (rememberMeCheckBox == null || firstRun) {
            return;
        }
        rememberMeCheckBox.selectedProperty().addListener((observable, oldValue, selected) -> {
            if (!selected && !suppressRememberMeListener) {
                clearRememberedCredentials(true, false);
            }
        });
    }

    private void saveCredentialsAfterSuccessfulLogin(SystemUser user, char[] password) {
        if (rememberMeCheckBox != null && rememberMeCheckBox.isSelected()) {
            LoginCredentialStore.SaveResult result = credentialStore.save(canonicalLoginIdentifier(user), password);
            updateForgetSavedLoginButtonState();
            if (!result.userMessage().isBlank()) {
                setSavedLoginStatus(result.userMessage());
                if (result.status() == LoginCredentialStore.SaveStatus.USERNAME_ONLY) {
                    showError(result.userMessage());
                } else {
                    showSuccess(result.userMessage());
                }
            }
        } else if (credentialStore.hasSavedCredentials()) {
            clearRememberedCredentials(false, false);
        }
    }

    private String activePasswordText() {
        return passwordVisible ? visiblePasswordField.getText() : passwordField.getText();
    }

    private char[] activePasswordChars() {
        String password = activePasswordText();
        return password == null ? new char[0] : password.toCharArray();
    }

    private void setPasswordFields(char[] password) {
        suppressPasswordChangeTracking = true;
        String passwordText = new String(password);
        try {
            passwordField.setText(passwordText);
        } finally {
            passwordText = "";
            suppressPasswordChangeTracking = false;
        }
    }

    private void clearPasswordFields() {
        suppressPasswordChangeTracking = true;
        try {
            passwordField.clear();
            visiblePasswordField.clear();
        } finally {
            suppressPasswordChangeTracking = false;
            restoredPasswordFromStore = false;
            restoredCredentialAccount = "";
        }
    }

    private void clearRememberedCredentials(boolean showMessage, boolean updateCheckbox) {
        boolean hadSavedCredentials = credentialStore.hasSavedCredentials();
        credentialStore.clear();
        clearPasswordFields();
        if (updateCheckbox && rememberMeCheckBox != null && rememberMeCheckBox.isSelected()) {
            suppressRememberMeListener = true;
            try {
                rememberMeCheckBox.setSelected(false);
            } finally {
                suppressRememberMeListener = false;
            }
        }
        updateForgetSavedLoginButtonState();
        if (showMessage && hadSavedCredentials) {
            showSuccess("Saved login credentials removed.");
        }
    }

    private String canonicalLoginIdentifier(SystemUser user) {
        if (user == null) {
            return usernameField.getText();
        }
        return user.getUsername() == null || user.getUsername().isBlank()
                ? user.getEmail()
                : user.getUsername();
    }

    private void setSavedLoginStatus(String message) {
        if (savedLoginStatusLabel == null) {
            return;
        }
        savedLoginStatusLabel.setText(message == null || message.isBlank()
                ? "Saved securely on this computer."
                : message);
    }

    private boolean shouldClearRestoredCredentials(String login) {
        return restoredPasswordFromStore
                && !restoredCredentialAccount.isBlank()
                && sameAccount(login, restoredCredentialAccount);
    }

    private boolean sameAccount(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        return !left.isBlank() && left.equalsIgnoreCase(right);
    }

    private void updateForgetSavedLoginButtonState() {
        if (forgetSavedLoginButton == null) {
            return;
        }
        boolean available = !firstRun && credentialStore.hasSavedCredentials();
        if (savedLoginPanel != null) {
            savedLoginPanel.setVisible(available);
            savedLoginPanel.setManaged(available);
        }
        if (available && savedLoginStatusLabel != null && savedLoginStatusLabel.getText().isBlank()) {
            savedLoginStatusLabel.setText("Saved securely on this computer.");
        }
        forgetSavedLoginButton.setVisible(available);
        forgetSavedLoginButton.setManaged(available);
        forgetSavedLoginButton.setDisable(!available);
    }

    private String messageOrDefault(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
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
