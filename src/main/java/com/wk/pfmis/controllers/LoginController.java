package com.wk.pfmis.controllers;

import com.wk.pfmis.MainApp;
import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.mail.EmailService;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.LoginCredentialStore;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Arrays;

public class LoginController {
    private static final System.Logger LOGGER = System.getLogger(LoginController.class.getName());
    private static final String INPUT_FOCUSED_CLASS = "auth-input-focused";
    private static final String FIELD_ERROR_CLASS = "field-error";
    private static final String GENERIC_AUTH_ERROR = "Invalid email/username or password.";

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private TextField resetEmailField;

    @FXML private Label messageLabel;
    @FXML private Label savedLoginStatusLabel;
    @FXML private Label loginUsernameErrorLabel;
    @FXML private Label loginPasswordErrorLabel;
    @FXML private Label resetEmailErrorLabel;

    @FXML private CheckBox rememberMeCheckBox;
    @FXML private Button signInButton;
    @FXML private Button passwordVisibilityButton;
    @FXML private Button forgotPasswordButton;
    @FXML private Button forgetSavedLoginButton;
    @FXML private Button sendResetButton;

    @FXML private VBox signInPanel;
    @FXML private VBox resetPanel;
    @FXML private VBox savedLoginPanel;
    @FXML private HBox loginUsernameShell;
    @FXML private HBox loginPasswordShell;
    @FXML private HBox resetEmailShell;

    private final AuthDatabase authDatabase = AuthDatabase.getInstance();
    private final EmailService emailService = EmailService.getInstance();
    private final LoginCredentialStore credentialStore = LoginCredentialStore.getInstance();

    private boolean passwordVisible;
    private boolean restoredPasswordFromStore;
    private boolean suppressPasswordChangeTracking;
    private boolean suppressRememberMeListener;
    private String restoredCredentialAccount = "";

    @FXML
    public void initialize() {
        LOGGER.log(System.Logger.Level.INFO, "Authentication screen initialized");
        LOGGER.log(System.Logger.Level.INFO, "Authentication mode: LOGIN");
        configurePasswordVisibility();
        configureInputShells();
        configureValidationClearance();
        configureCredentialMirroring();
        configureRememberMeRemoval();
        configureKeyboardActions();
        setLoginControlsInteractive();
        restoreSavedCredentials();
        updateForgetSavedLoginButtonState();
        if (!authDatabase.hasActiveSuperAdministrator()) {
            showError("PFMIS is not fully configured. Check the local .env and restart PFMIS.");
        }
        Platform.runLater(() -> {
            if (rememberMeCheckBox.isSelected() && !usernameField.getText().isBlank()) {
                activeLoginPasswordField().requestFocus();
            } else {
                usernameField.requestFocus();
            }
        });
    }

    @FXML
    private void signIn() {
        if (!validateLoginForm()) {
            return;
        }
        authenticateAndOpen();
    }

    @FXML
    private void focusPasswordReset() {
        clearMessage();
        clearResetValidation();
        setVisibleManaged(signInPanel, false);
        setVisibleManaged(resetPanel, true);
        resetEmailField.setDisable(false);
        resetEmailField.setEditable(true);
        resetEmailField.setMouseTransparent(false);
        resetEmailField.setFocusTraversable(true);
        if (resetEmailField.getText().isBlank()) {
            resetEmailField.setText(usernameField.getText());
        }
        Platform.runLater(resetEmailField::requestFocus);
    }

    @FXML
    private void backToSignIn() {
        clearMessage();
        clearResetValidation();
        setVisibleManaged(resetPanel, false);
        setVisibleManaged(signInPanel, true);
        Platform.runLater(() -> {
            if (!usernameField.getText().isBlank()) {
                activeLoginPasswordField().requestFocus();
            } else {
                usernameField.requestFocus();
            }
        });
    }

    @FXML
    private void resetPasswordByEmail() {
        clearMessage();
        clearResetValidation();
        String account = text(resetEmailField);
        if (account.isBlank()) {
            markInvalid(resetEmailShell, resetEmailErrorLabel, "Email address is required.");
            requestFocus(resetEmailField);
            return;
        }
        if (!emailService.isSendConfigured()) {
            showError(emailService.sendConfigurationStatus());
            return;
        }

        setResetLoading(true);
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
            setResetLoading(false);
            showSuccess("If an account matches the information provided, password reset instructions have been sent.");
        });
        resetTask.setOnFailed(event -> {
            setResetLoading(false);
            showError(rootMessage(resetTask.getException()));
        });
        Thread worker = new Thread(resetTask, "pfmis-password-reset");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void forgetSavedLogin() {
        clearMessage();
        clearRememberedCredentials(true, true);
        usernameField.requestFocus();
    }

    @FXML
    private void togglePasswordVisibility() {
        boolean passwordHadFocus = passwordField.isFocused() || visiblePasswordField.isFocused();
        int caret = activeLoginPasswordField().getCaretPosition();
        passwordVisible = !passwordVisible;
        applyPasswordVisibility();
        passwordVisibilityButton.setText(passwordVisible ? "Hide" : "Show");
        if (passwordHadFocus) {
            focusWithCaret(activeLoginPasswordField(), caret);
        }
    }

    private boolean validateLoginForm() {
        clearLoginValidation();
        Node firstInvalid = null;
        if (text(usernameField).isBlank()) {
            markInvalid(loginUsernameShell, loginUsernameErrorLabel, "Email or username is required.");
            firstInvalid = usernameField;
        }
        if (activePasswordText().isBlank()) {
            markInvalid(loginPasswordShell, loginPasswordErrorLabel, "Password is required.");
            if (firstInvalid == null) {
                firstInvalid = activeLoginPasswordField();
            }
        }
        if (firstInvalid != null) {
            requestFocus(firstInvalid);
            return false;
        }
        return true;
    }

    private void authenticateAndOpen() {
        clearMessage();
        String login = text(usernameField);
        char[] passwordChars = activePasswordChars();
        String passwordText = new String(passwordChars);
        setSignInLoading(true);
        Task<SystemUser> authTask = new Task<>() {
            @Override
            protected SystemUser call() {
                return authDatabase.authenticate(login, passwordText);
            }
        };
        authTask.setOnSucceeded(event -> {
            try {
                SystemUser user = authTask.getValue();
                saveCredentialsAfterSuccessfulLogin(user, passwordChars);
                clearPasswordFields();
                MainApp.completeLogin(user);
            } finally {
                Arrays.fill(passwordChars, '\0');
                setSignInLoading(false);
            }
        });
        authTask.setOnFailed(event -> {
            try {
                if (shouldClearRestoredCredentials(login)) {
                    clearRememberedCredentials(false, true);
                    showError("The saved password is no longer valid. Please enter your current password.");
                } else {
                    showError(GENERIC_AUTH_ERROR);
                }
                markInvalid(loginPasswordShell, loginPasswordErrorLabel, GENERIC_AUTH_ERROR);
                clearPasswordFields();
                passwordField.requestFocus();
            } finally {
                Arrays.fill(passwordChars, '\0');
                setSignInLoading(false);
            }
        });
        Thread worker = new Thread(authTask, "pfmis-authenticate");
        worker.setDaemon(true);
        worker.start();
    }

    private void configurePasswordVisibility() {
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        passwordVisible = false;
        applyPasswordVisibility();
        passwordVisibilityButton.setText("Show");
        passwordVisibilityButton.setFocusTraversable(false);
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressPasswordChangeTracking) {
                restoredPasswordFromStore = false;
            }
        });
        visiblePasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressPasswordChangeTracking) {
                restoredPasswordFromStore = false;
            }
        });
    }

    private void configureInputShells() {
        usernameField.focusedProperty().addListener((observable, oldValue, focused) ->
                setStyleClass(loginUsernameShell, INPUT_FOCUSED_CLASS, focused));
        passwordField.focusedProperty().addListener((observable, oldValue, focused) ->
                updatePasswordShellFocus());
        visiblePasswordField.focusedProperty().addListener((observable, oldValue, focused) ->
                updatePasswordShellFocus());
        resetEmailField.focusedProperty().addListener((observable, oldValue, focused) ->
                setStyleClass(resetEmailShell, INPUT_FOCUSED_CLASS, focused));
    }

    private void configureValidationClearance() {
        usernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!text(usernameField).isBlank()) {
                clearInvalid(loginUsernameShell, loginUsernameErrorLabel);
            }
        });
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!activePasswordText().isBlank()) {
                clearInvalid(loginPasswordShell, loginPasswordErrorLabel);
            }
        });
        visiblePasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!activePasswordText().isBlank()) {
                clearInvalid(loginPasswordShell, loginPasswordErrorLabel);
            }
        });
        resetEmailField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!text(resetEmailField).isBlank()) {
                clearInvalid(resetEmailShell, resetEmailErrorLabel);
            }
        });
    }

    private void configureCredentialMirroring() {
        usernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (resetEmailField.getText().isBlank() || resetEmailField.getText().equals(oldValue)) {
                resetEmailField.setText(newValue);
            }
            if (!sameAccount(newValue, restoredCredentialAccount)) {
                restoredPasswordFromStore = false;
            }
        });
    }

    private void configureRememberMeRemoval() {
        rememberMeCheckBox.selectedProperty().addListener((observable, oldValue, selected) -> {
            if (!selected && !suppressRememberMeListener) {
                clearRememberedCredentials(true, false);
            }
        });
    }

    private void configureKeyboardActions() {
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                activeLoginPasswordField().requestFocus();
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
        resetEmailField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                resetPasswordByEmail();
            }
        });
    }

    private void restoreSavedCredentials() {
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

    private void saveCredentialsAfterSuccessfulLogin(SystemUser user, char[] password) {
        if (rememberMeCheckBox.isSelected()) {
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

    private String canonicalLoginIdentifier(SystemUser user) {
        if (user == null) {
            return usernameField.getText();
        }
        return user.getUsername() == null || user.getUsername().isBlank()
                ? user.getEmail()
                : user.getUsername();
    }

    private void setLoginControlsInteractive() {
        setTextControlInteractive(usernameField, true);
        setTextControlInteractive(passwordField, true);
        setTextControlInteractive(visiblePasswordField, true);
        applyPasswordVisibility();
        rememberMeCheckBox.setDisable(false);
        rememberMeCheckBox.setFocusTraversable(true);
        forgotPasswordButton.setDisable(false);
        forgotPasswordButton.setFocusTraversable(true);
        signInButton.setDisable(false);
        signInButton.setFocusTraversable(true);
    }

    private void setTextControlInteractive(TextInputControl field, boolean focusable) {
        field.setDisable(false);
        field.setEditable(true);
        field.setMouseTransparent(false);
        field.setFocusTraversable(focusable);
    }

    private void applyPasswordVisibility() {
        passwordField.setVisible(!passwordVisible);
        passwordField.setManaged(!passwordVisible);
        passwordField.setMouseTransparent(passwordVisible);
        passwordField.setFocusTraversable(!passwordVisible);
        passwordField.setDisable(false);
        passwordField.setEditable(true);

        visiblePasswordField.setVisible(passwordVisible);
        visiblePasswordField.setManaged(passwordVisible);
        visiblePasswordField.setMouseTransparent(!passwordVisible);
        visiblePasswordField.setFocusTraversable(passwordVisible);
        visiblePasswordField.setDisable(false);
        visiblePasswordField.setEditable(true);
        updatePasswordShellFocus();
    }

    private void updatePasswordShellFocus() {
        setStyleClass(loginPasswordShell, INPUT_FOCUSED_CLASS,
                passwordField.isFocused() || visiblePasswordField.isFocused());
    }

    private void setSignInLoading(boolean loading) {
        signInButton.setDisable(loading);
        signInButton.setText(loading ? "Signing in..." : "Sign In");
    }

    private void setResetLoading(boolean loading) {
        sendResetButton.setDisable(loading);
        sendResetButton.setText(loading ? "Sending..." : "Send Reset Instructions");
    }

    private void markInvalid(HBox shell, Label label, String message) {
        setStyleClass(shell, FIELD_ERROR_CLASS, true);
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void clearInvalid(HBox shell, Label label) {
        setStyleClass(shell, FIELD_ERROR_CLASS, false);
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    private void clearLoginValidation() {
        clearInvalid(loginUsernameShell, loginUsernameErrorLabel);
        clearInvalid(loginPasswordShell, loginPasswordErrorLabel);
    }

    private void clearResetValidation() {
        clearInvalid(resetEmailShell, resetEmailErrorLabel);
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

    private String activePasswordText() {
        return passwordVisible ? visiblePasswordField.getText() : passwordField.getText();
    }

    private char[] activePasswordChars() {
        String password = activePasswordText();
        return password == null ? new char[0] : password.toCharArray();
    }

    private TextInputControl activeLoginPasswordField() {
        return passwordVisible ? visiblePasswordField : passwordField;
    }

    private void setPasswordFields(char[] password) {
        suppressPasswordChangeTracking = true;
        try {
            passwordField.setText(new String(password));
        } finally {
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
        if (updateCheckbox && rememberMeCheckBox.isSelected()) {
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

    private void setSavedLoginStatus(String message) {
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
        boolean available = credentialStore.hasSavedCredentials();
        savedLoginPanel.setVisible(available);
        savedLoginPanel.setManaged(available);
        if (available && savedLoginStatusLabel.getText().isBlank()) {
            savedLoginStatusLabel.setText("Saved securely on this computer.");
        }
        forgetSavedLoginButton.setVisible(available);
        forgetSavedLoginButton.setManaged(available);
        forgetSavedLoginButton.setDisable(!available);
    }

    private void setVisibleManaged(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void setStyleClass(Node node, String styleClass, boolean present) {
        if (present) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }

    private void requestFocus(Node node) {
        Platform.runLater(node::requestFocus);
    }

    private void focusWithCaret(TextInputControl field, int caret) {
        Platform.runLater(() -> {
            field.requestFocus();
            field.positionCaret(Math.min(Math.max(caret, 0), field.getText() == null ? 0 : field.getText().length()));
        });
    }

    private String text(TextInputControl field) {
        return field.getText() == null ? "" : field.getText().trim();
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
