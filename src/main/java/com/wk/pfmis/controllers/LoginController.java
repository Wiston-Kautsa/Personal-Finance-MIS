package com.wk.pfmis.controllers;

import com.wk.pfmis.MainApp;
import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.mail.EmailService;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.LoginCredentialStore;
import com.wk.pfmis.security.PasswordSecurity;
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
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class LoginController {
    private static final System.Logger LOGGER = System.getLogger(LoginController.class.getName());
    private static final String INPUT_FOCUSED_CLASS = "auth-input-focused";
    private static final String FIELD_ERROR_CLASS = "field-error";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,40}$");

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private TextField resetEmailField;
    @FXML private TextField bootstrapFullNameField;
    @FXML private TextField bootstrapUsernameField;
    @FXML private TextField bootstrapEmailField;
    @FXML private PasswordField bootstrapPasswordField;
    @FXML private TextField bootstrapVisiblePasswordField;
    @FXML private PasswordField bootstrapConfirmPasswordField;
    @FXML private TextField bootstrapVisibleConfirmPasswordField;

    @FXML private Label authModeTitleLabel;
    @FXML private Label messageLabel;
    @FXML private Label savedLoginStatusLabel;
    @FXML private Label setupHintLabel;
    @FXML private Label systemEmailLabel;
    @FXML private Label loginUsernameErrorLabel;
    @FXML private Label loginPasswordErrorLabel;
    @FXML private Label bootstrapFullNameErrorLabel;
    @FXML private Label bootstrapUsernameErrorLabel;
    @FXML private Label bootstrapEmailErrorLabel;
    @FXML private Label bootstrapPasswordErrorLabel;
    @FXML private Label bootstrapConfirmPasswordErrorLabel;

    @FXML private CheckBox rememberMeCheckBox;
    @FXML private Button signInButton;
    @FXML private Button createAdministratorButton;
    @FXML private Button retryModeButton;
    @FXML private Button forgetSavedLoginButton;
    @FXML private Button passwordVisibilityButton;
    @FXML private Button bootstrapPasswordVisibilityButton;
    @FXML private Button forgotPasswordButton;

    @FXML private VBox loginForm;
    @FXML private VBox bootstrapForm;
    @FXML private VBox resetPanel;
    @FXML private VBox savedLoginPanel;
    @FXML private HBox loginOptionsRow;
    @FXML private HBox loginUsernameShell;
    @FXML private HBox loginPasswordShell;
    @FXML private HBox bootstrapFullNameShell;
    @FXML private HBox bootstrapUsernameShell;
    @FXML private HBox bootstrapEmailShell;
    @FXML private HBox bootstrapPasswordShell;
    @FXML private HBox bootstrapConfirmPasswordShell;

    private final AuthDatabase authDatabase = AuthDatabase.getInstance();
    private final EmailService emailService = EmailService.getInstance();
    private final LoginCredentialStore credentialStore = LoginCredentialStore.getInstance();

    private AuthenticationMode authenticationMode = AuthenticationMode.CHECKING;
    private boolean passwordVisible;
    private boolean bootstrapPasswordVisible;
    private boolean restoredPasswordFromStore;
    private boolean savedCredentialsChecked;
    private boolean suppressPasswordChangeTracking;
    private boolean suppressRememberMeListener;
    private String restoredCredentialAccount = "";

    @FXML
    public void initialize() {
        LOGGER.log(System.Logger.Level.INFO, "Authentication screen initialized");
        systemEmailLabel.setText(emailService.isSendConfigured()
                ? "Password reset email is configured."
                : "Password reset email is not configured.");
        setupHintLabel.setText("Checking workspace security...");
        configurePasswordVisibility();
        configureBootstrapPasswordVisibility();
        configureInputShells();
        configureValidationClearance();
        configureCredentialMirroring();
        configureRememberMeRemoval();
        configureKeyboardActions();
        refreshAuthenticationMode();
    }

    @FXML
    private void retryAuthenticationMode() {
        refreshAuthenticationMode();
    }

    @FXML
    private void signIn() {
        if (authenticationMode != AuthenticationMode.LOGIN) {
            refreshAuthenticationMode();
            return;
        }
        if (!validateLoginForm()) {
            return;
        }
        authenticateAndOpen();
    }

    @FXML
    private void createAdministrator() {
        if (authenticationMode != AuthenticationMode.BOOTSTRAP) {
            refreshAuthenticationMode();
            return;
        }
        clearMessage();
        boolean valid;
        try {
            valid = validateBootstrapForm();
        } catch (RuntimeException exception) {
            showError(rootMessage(exception));
            return;
        }
        if (!valid) {
            return;
        }

        createAdministratorButton.setDisable(true);
        try {
            SystemUser user = authDatabase.registerUser(
                    bootstrapFullNameField.getText(),
                    bootstrapUsernameField.getText(),
                    bootstrapEmailField.getText(),
                    activeBootstrapPasswordText()
            );
            LOGGER.log(System.Logger.Level.INFO, "First Super Administrator created");
            if (!authDatabase.hasActiveSuperAdministrator()) {
                throw new IllegalStateException("PFMIS could not verify the new Super Administrator account.");
            }
            clearBootstrapFields();
            switchMode(AuthenticationMode.LOGIN, false);
            usernameField.setText(user.getUsername());
            resetEmailField.setText(user.getUsername());
            clearPasswordFields();
            showSuccess("Super Administrator created successfully. Sign in to continue.");
            Platform.runLater(passwordField::requestFocus);
        } catch (RuntimeException exception) {
            showError(rootMessage(exception));
            focusLikelyBootstrapFailure(exception);
        } finally {
            createAdministratorButton.setDisable(authenticationMode != AuthenticationMode.BOOTSTRAP);
        }
    }

    @FXML
    private void resetPasswordByEmail() {
        clearMessage();
        if (authenticationMode != AuthenticationMode.LOGIN) {
            showError("Password reset is available after a Super Administrator account exists.");
            return;
        }
        if (!emailService.isSendConfigured()) {
            showError(emailService.sendConfigurationStatus());
            return;
        }
        String account = resetEmailField.getText().isBlank() ? usernameField.getText() : resetEmailField.getText();
        if (account == null || account.trim().isEmpty()) {
            showError("Enter the registered email or username to receive a reset email.");
            setVisibleManaged(resetPanel, true);
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
    private void focusPasswordReset() {
        if (authenticationMode != AuthenticationMode.LOGIN) {
            showError("Create the first Super Administrator account before password reset is available.");
            return;
        }
        setVisibleManaged(resetPanel, true);
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
        int caret = activeLoginPasswordField().getCaretPosition();
        passwordVisible = !passwordVisible;
        applyPasswordVisibility(passwordField, visiblePasswordField, passwordVisible);
        passwordVisibilityButton.setText(passwordVisible ? "Hide" : "Show");
        focusWithCaret(activeLoginPasswordField(), caret);
    }

    @FXML
    private void toggleBootstrapPasswordVisibility() {
        boolean passwordHadFocus = bootstrapPasswordField.isFocused() || bootstrapVisiblePasswordField.isFocused();
        boolean confirmHadFocus = bootstrapConfirmPasswordField.isFocused() || bootstrapVisibleConfirmPasswordField.isFocused();
        int passwordCaret = activeBootstrapPasswordField().getCaretPosition();
        int confirmCaret = activeBootstrapConfirmPasswordField().getCaretPosition();
        bootstrapPasswordVisible = !bootstrapPasswordVisible;
        applyPasswordVisibility(bootstrapPasswordField, bootstrapVisiblePasswordField, bootstrapPasswordVisible);
        applyPasswordVisibility(bootstrapConfirmPasswordField, bootstrapVisibleConfirmPasswordField, bootstrapPasswordVisible);
        bootstrapPasswordVisibilityButton.setText(bootstrapPasswordVisible ? "Hide" : "Show");
        if (confirmHadFocus) {
            focusWithCaret(activeBootstrapConfirmPasswordField(), confirmCaret);
        } else if (passwordHadFocus) {
            focusWithCaret(activeBootstrapPasswordField(), passwordCaret);
        } else {
            focusWithCaret(activeBootstrapPasswordField(), passwordCaret);
        }
    }

    private void refreshAuthenticationMode() {
        LOGGER.log(System.Logger.Level.INFO, "Checking first administrator status");
        authenticationMode = AuthenticationMode.CHECKING;
        authModeTitleLabel.setText("Checking workspace security");
        setupHintLabel.setText("Checking workspace security...");
        clearMessage();
        clearAllValidation();
        setVisibleManaged(loginForm, false);
        setVisibleManaged(bootstrapForm, false);
        setVisibleManaged(resetPanel, false);
        setVisibleManaged(savedLoginPanel, false);
        setVisibleManaged(retryModeButton, false);
        try {
            boolean firstAdministratorExists = authDatabase.hasActiveSuperAdministrator();
            LOGGER.log(System.Logger.Level.INFO, "First administrator exists: " + firstAdministratorExists);
            switchMode(firstAdministratorExists ? AuthenticationMode.LOGIN : AuthenticationMode.BOOTSTRAP, true);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "First administrator check failed", exception);
            authenticationMode = AuthenticationMode.ERROR;
            authModeTitleLabel.setText("Workspace security check failed");
            setupHintLabel.setText("PFMIS could not verify the administrator account. Check the database connection and try again.");
            setVisibleManaged(retryModeButton, true);
            showError("PFMIS could not verify the administrator account. Check the database connection and try again.");
        }
    }

    private void switchMode(AuthenticationMode mode, boolean restoreSavedCredentials) {
        authenticationMode = mode;
        LOGGER.log(System.Logger.Level.INFO, "Authentication mode: " + mode);
        clearAllValidation();
        setVisibleManaged(retryModeButton, false);
        setVisibleManaged(resetPanel, false);
        if (mode == AuthenticationMode.BOOTSTRAP) {
            authModeTitleLabel.setText("Create First Administrator");
            setupHintLabel.setText("Create the first Super Administrator account to secure and open this PFMIS workspace.");
            setVisibleManaged(bootstrapForm, true);
            setVisibleManaged(loginForm, false);
            setVisibleManaged(savedLoginPanel, false);
            setBootstrapControlsInteractive(true);
            setLoginControlsInteractive(false);
            updateForgetSavedLoginButtonState();
            Platform.runLater(bootstrapFullNameField::requestFocus);
            return;
        }

        authModeTitleLabel.setText("Sign in");
        setupHintLabel.setText("Sign in to open your private PFMIS workspace. New accounts are created by the Super Administrator.");
        setVisibleManaged(bootstrapForm, false);
        setVisibleManaged(loginForm, true);
        setBootstrapControlsInteractive(false);
        setLoginControlsInteractive(true);
        if (restoreSavedCredentials && !savedCredentialsChecked) {
            restoreSavedCredentials();
            savedCredentialsChecked = true;
        }
        if (resetEmailField.getText().isBlank()) {
            resetEmailField.setText(usernameField.getText());
        }
        updateForgetSavedLoginButtonState();
        Platform.runLater(() -> {
            if (restoredPasswordFromStore) {
                signInButton.requestFocus();
            } else if (rememberMeCheckBox.isSelected() && !usernameField.getText().isBlank()) {
                passwordField.requestFocus();
            } else {
                usernameField.requestFocus();
            }
        });
    }

    private boolean validateLoginForm() {
        clearLoginValidation();
        Node firstInvalid = null;
        if (usernameField.getText() == null || usernameField.getText().isBlank()) {
            markInvalid(loginUsernameShell, loginUsernameErrorLabel, "Username or email is required.");
            firstInvalid = usernameField;
        }
        if (activePasswordText().isBlank()) {
            markInvalid(loginPasswordShell, loginPasswordErrorLabel, "Password is required.");
            if (firstInvalid == null) {
                firstInvalid = activeLoginPasswordField();
            }
        }
        if (firstInvalid != null) {
            showError("Complete the required fields highlighted below.");
            requestFocus(firstInvalid);
            return false;
        }
        return true;
    }

    private boolean validateBootstrapForm() {
        clearBootstrapValidation();
        Node firstInvalid = null;
        String fullName = text(bootstrapFullNameField);
        String username = text(bootstrapUsernameField).toLowerCase(Locale.ENGLISH);
        String email = text(bootstrapEmailField).toLowerCase(Locale.ENGLISH);
        String password = activeBootstrapPasswordText();
        String confirmPassword = activeBootstrapConfirmPasswordText();

        if (fullName.isBlank()) {
            markInvalid(bootstrapFullNameShell, bootstrapFullNameErrorLabel, "Full name is required.");
            firstInvalid = firstInvalid == null ? bootstrapFullNameField : firstInvalid;
        }
        if (username.isBlank()) {
            markInvalid(bootstrapUsernameShell, bootstrapUsernameErrorLabel, "Username is required.");
            firstInvalid = firstInvalid == null ? bootstrapUsernameField : firstInvalid;
        } else if (!USERNAME_PATTERN.matcher(username).matches()) {
            markInvalid(bootstrapUsernameShell, bootstrapUsernameErrorLabel,
                    "Username must be 3-40 characters and use only letters, numbers, dot, underscore, or hyphen.");
            firstInvalid = firstInvalid == null ? bootstrapUsernameField : firstInvalid;
        } else if (authDatabase.usernameExists(username)) {
            markInvalid(bootstrapUsernameShell, bootstrapUsernameErrorLabel, "That username is already registered.");
            firstInvalid = firstInvalid == null ? bootstrapUsernameField : firstInvalid;
        }
        if (email.isBlank()) {
            markInvalid(bootstrapEmailShell, bootstrapEmailErrorLabel, "Email is required.");
            firstInvalid = firstInvalid == null ? bootstrapEmailField : firstInvalid;
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            markInvalid(bootstrapEmailShell, bootstrapEmailErrorLabel, "Enter a valid email address.");
            firstInvalid = firstInvalid == null ? bootstrapEmailField : firstInvalid;
        } else if (authDatabase.emailExists(email)) {
            markInvalid(bootstrapEmailShell, bootstrapEmailErrorLabel, "That email address is already registered.");
            firstInvalid = firstInvalid == null ? bootstrapEmailField : firstInvalid;
        }
        if (password.isBlank()) {
            markInvalid(bootstrapPasswordShell, bootstrapPasswordErrorLabel, "Password is required.");
            firstInvalid = firstInvalid == null ? activeBootstrapPasswordField() : firstInvalid;
        } else {
            try {
                PasswordSecurity.validatePassword(password);
            } catch (IllegalArgumentException exception) {
                markInvalid(bootstrapPasswordShell, bootstrapPasswordErrorLabel, exception.getMessage());
                firstInvalid = firstInvalid == null ? activeBootstrapPasswordField() : firstInvalid;
            }
        }
        if (confirmPassword.isBlank()) {
            markInvalid(bootstrapConfirmPasswordShell, bootstrapConfirmPasswordErrorLabel, "Confirm password is required.");
            firstInvalid = firstInvalid == null ? activeBootstrapConfirmPasswordField() : firstInvalid;
        } else if (!password.equals(confirmPassword)) {
            markInvalid(bootstrapConfirmPasswordShell, bootstrapConfirmPasswordErrorLabel, "Password and confirmation do not match.");
            firstInvalid = firstInvalid == null ? activeBootstrapConfirmPasswordField() : firstInvalid;
        }

        if (firstInvalid != null) {
            showError("Complete the required fields highlighted below.");
            requestFocus(firstInvalid);
            return false;
        }
        return true;
    }

    private void authenticateAndOpen() {
        clearMessage();
        String login = usernameField.getText();
        char[] passwordChars = activePasswordChars();
        String passwordText = new String(passwordChars);
        try {
            if (!authDatabase.hasActiveSuperAdministrator()) {
                clearPasswordFields();
                switchMode(AuthenticationMode.BOOTSTRAP, false);
                showError("Create the first Super Administrator account before signing in.");
                return;
            }
            SystemUser user = authDatabase.authenticate(login, passwordText);
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
            markInvalid(loginPasswordShell, loginPasswordErrorLabel, "Invalid username/email or password.");
            clearPasswordFields();
            passwordField.requestFocus();
        } finally {
            Arrays.fill(passwordChars, '\0');
            passwordText = "";
        }
    }

    private void configurePasswordVisibility() {
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        passwordVisible = false;
        applyPasswordVisibility(passwordField, visiblePasswordField, false);
        passwordVisibilityButton.setText("Show");
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressPasswordChangeTracking) {
                restoredPasswordFromStore = false;
            }
        });
    }

    private void configureBootstrapPasswordVisibility() {
        bootstrapVisiblePasswordField.textProperty().bindBidirectional(bootstrapPasswordField.textProperty());
        bootstrapVisibleConfirmPasswordField.textProperty().bindBidirectional(bootstrapConfirmPasswordField.textProperty());
        bootstrapPasswordVisible = false;
        applyPasswordVisibility(bootstrapPasswordField, bootstrapVisiblePasswordField, false);
        applyPasswordVisibility(bootstrapConfirmPasswordField, bootstrapVisibleConfirmPasswordField, false);
        bootstrapPasswordVisibilityButton.setText("Show");
    }

    private void configureInputShells() {
        configureInputShell(loginUsernameShell, () -> usernameField, usernameField);
        configureInputShell(loginPasswordShell, this::activeLoginPasswordField, passwordField, visiblePasswordField);
        configureInputShell(bootstrapFullNameShell, () -> bootstrapFullNameField, bootstrapFullNameField);
        configureInputShell(bootstrapUsernameShell, () -> bootstrapUsernameField, bootstrapUsernameField);
        configureInputShell(bootstrapEmailShell, () -> bootstrapEmailField, bootstrapEmailField);
        configureInputShell(bootstrapPasswordShell, this::activeBootstrapPasswordField, bootstrapPasswordField, bootstrapVisiblePasswordField);
        configureInputShell(bootstrapConfirmPasswordShell, this::activeBootstrapConfirmPasswordField,
                bootstrapConfirmPasswordField, bootstrapVisibleConfirmPasswordField);
    }

    private void configureInputShell(HBox shell, java.util.function.Supplier<TextInputControl> activeField,
                                     TextInputControl... fields) {
        shell.setOnMouseClicked(event -> activeField.get().requestFocus());
        for (TextInputControl field : fields) {
            field.focusedProperty().addListener((observable, oldValue, focused) -> updateInputShellFocus(shell, fields));
        }
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
        bootstrapFullNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!text(bootstrapFullNameField).isBlank()) {
                clearInvalid(bootstrapFullNameShell, bootstrapFullNameErrorLabel);
            }
        });
        bootstrapUsernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            String username = text(bootstrapUsernameField).toLowerCase(Locale.ENGLISH);
            if (USERNAME_PATTERN.matcher(username).matches()) {
                clearInvalid(bootstrapUsernameShell, bootstrapUsernameErrorLabel);
            }
        });
        bootstrapEmailField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (EMAIL_PATTERN.matcher(text(bootstrapEmailField).toLowerCase(Locale.ENGLISH)).matches()) {
                clearInvalid(bootstrapEmailShell, bootstrapEmailErrorLabel);
            }
        });
        bootstrapPasswordField.textProperty().addListener((observable, oldValue, newValue) -> clearValidBootstrapPasswords());
        bootstrapConfirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> clearValidBootstrapPasswords());
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
            if (authenticationMode == AuthenticationMode.LOGIN && !selected && !suppressRememberMeListener) {
                clearRememberedCredentials(true, false);
            }
        });
    }

    private void configureKeyboardActions() {
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                signIn();
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
        bootstrapConfirmPasswordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                createAdministrator();
            }
        });
        bootstrapVisibleConfirmPasswordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                createAdministrator();
            }
        });
        resetEmailField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                resetPasswordByEmail();
            }
        });
    }

    private void restoreSavedCredentials() {
        if (rememberMeCheckBox == null || authenticationMode != AuthenticationMode.LOGIN) {
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

    private void setLoginControlsInteractive(boolean active) {
        setTextControlInteractive(usernameField, active);
        setTextControlInteractive(passwordField, active && !passwordVisible);
        setTextControlInteractive(visiblePasswordField, active && passwordVisible);
        rememberMeCheckBox.setDisable(!active);
        rememberMeCheckBox.setFocusTraversable(active);
        forgotPasswordButton.setDisable(!active);
        forgotPasswordButton.setFocusTraversable(active);
        signInButton.setDisable(!active);
        signInButton.setFocusTraversable(active);
        passwordVisibilityButton.setDisable(!active);
        passwordVisibilityButton.setFocusTraversable(active);
    }

    private void setBootstrapControlsInteractive(boolean active) {
        for (TextInputControl field : List.of(
                bootstrapFullNameField,
                bootstrapUsernameField,
                bootstrapEmailField,
                bootstrapPasswordField,
                bootstrapVisiblePasswordField,
                bootstrapConfirmPasswordField,
                bootstrapVisibleConfirmPasswordField
        )) {
            setTextControlInteractive(field, active && (field.isVisible() || !field.isManaged()));
        }
        applyPasswordVisibility(bootstrapPasswordField, bootstrapVisiblePasswordField, bootstrapPasswordVisible);
        applyPasswordVisibility(bootstrapConfirmPasswordField, bootstrapVisibleConfirmPasswordField, bootstrapPasswordVisible);
        createAdministratorButton.setDisable(!active);
        createAdministratorButton.setFocusTraversable(active);
        bootstrapPasswordVisibilityButton.setDisable(!active);
        bootstrapPasswordVisibilityButton.setFocusTraversable(active);
    }

    private void setTextControlInteractive(TextInputControl field, boolean focusable) {
        field.setDisable(!focusable && field.isVisible());
        field.setEditable(true);
        field.setMouseTransparent(!focusable && field.isVisible());
        field.setFocusTraversable(focusable && field.isVisible());
    }

    private void applyPasswordVisibility(PasswordField hiddenField, TextField visibleField, boolean visible) {
        hiddenField.setVisible(!visible);
        hiddenField.setManaged(!visible);
        hiddenField.setMouseTransparent(visible);
        hiddenField.setFocusTraversable(!visible && parentFormVisible(hiddenField));
        hiddenField.setDisable(false);
        hiddenField.setEditable(true);

        visibleField.setVisible(visible);
        visibleField.setManaged(visible);
        visibleField.setMouseTransparent(!visible);
        visibleField.setFocusTraversable(visible && parentFormVisible(visibleField));
        visibleField.setDisable(false);
        visibleField.setEditable(true);
        updateInputShellFocus(passwordShellFor(hiddenField), hiddenField, visibleField);
    }

    private boolean parentFormVisible(Node field) {
        if (field == passwordField || field == visiblePasswordField) {
            return authenticationMode == AuthenticationMode.LOGIN;
        }
        return authenticationMode == AuthenticationMode.BOOTSTRAP;
    }

    private HBox passwordShellFor(Node field) {
        if (field == passwordField || field == visiblePasswordField) {
            return loginPasswordShell;
        }
        if (field == bootstrapConfirmPasswordField || field == bootstrapVisibleConfirmPasswordField) {
            return bootstrapConfirmPasswordShell;
        }
        return bootstrapPasswordShell;
    }

    private void updateInputShellFocus(HBox shell, TextInputControl... fields) {
        if (shell == null) {
            return;
        }
        boolean focused = Arrays.stream(fields).anyMatch(TextInputControl::isFocused);
        setStyleClass(shell, INPUT_FOCUSED_CLASS, focused);
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

    private void clearBootstrapValidation() {
        clearInvalid(bootstrapFullNameShell, bootstrapFullNameErrorLabel);
        clearInvalid(bootstrapUsernameShell, bootstrapUsernameErrorLabel);
        clearInvalid(bootstrapEmailShell, bootstrapEmailErrorLabel);
        clearInvalid(bootstrapPasswordShell, bootstrapPasswordErrorLabel);
        clearInvalid(bootstrapConfirmPasswordShell, bootstrapConfirmPasswordErrorLabel);
    }

    private void clearAllValidation() {
        clearLoginValidation();
        clearBootstrapValidation();
    }

    private void clearValidBootstrapPasswords() {
        String password = activeBootstrapPasswordText();
        String confirm = activeBootstrapConfirmPasswordText();
        if (!password.isBlank()) {
            try {
                PasswordSecurity.validatePassword(password);
                clearInvalid(bootstrapPasswordShell, bootstrapPasswordErrorLabel);
            } catch (IllegalArgumentException ignored) {
                // Keep the current inline validation message until the password satisfies policy.
            }
        }
        if (!confirm.isBlank() && confirm.equals(password)) {
            clearInvalid(bootstrapConfirmPasswordShell, bootstrapConfirmPasswordErrorLabel);
        }
    }

    private void focusLikelyBootstrapFailure(RuntimeException exception) {
        String message = rootMessage(exception).toLowerCase(Locale.ENGLISH);
        if (message.contains("email")) {
            markInvalid(bootstrapEmailShell, bootstrapEmailErrorLabel, rootMessage(exception));
            bootstrapEmailField.requestFocus();
        } else if (message.contains("username")) {
            markInvalid(bootstrapUsernameShell, bootstrapUsernameErrorLabel, rootMessage(exception));
            bootstrapUsernameField.requestFocus();
        } else if (message.contains("password")) {
            markInvalid(bootstrapPasswordShell, bootstrapPasswordErrorLabel, rootMessage(exception));
            activeBootstrapPasswordField().requestFocus();
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

    private String activePasswordText() {
        return passwordVisible ? visiblePasswordField.getText() : passwordField.getText();
    }

    private char[] activePasswordChars() {
        String password = activePasswordText();
        return password == null ? new char[0] : password.toCharArray();
    }

    private String activeBootstrapPasswordText() {
        return bootstrapPasswordVisible ? bootstrapVisiblePasswordField.getText() : bootstrapPasswordField.getText();
    }

    private String activeBootstrapConfirmPasswordText() {
        return bootstrapPasswordVisible ? bootstrapVisibleConfirmPasswordField.getText() : bootstrapConfirmPasswordField.getText();
    }

    private TextInputControl activeLoginPasswordField() {
        return passwordVisible ? visiblePasswordField : passwordField;
    }

    private TextInputControl activeBootstrapPasswordField() {
        return bootstrapPasswordVisible ? bootstrapVisiblePasswordField : bootstrapPasswordField;
    }

    private TextInputControl activeBootstrapConfirmPasswordField() {
        return bootstrapPasswordVisible ? bootstrapVisibleConfirmPasswordField : bootstrapConfirmPasswordField;
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

    private void clearBootstrapFields() {
        bootstrapFullNameField.clear();
        bootstrapUsernameField.clear();
        bootstrapEmailField.clear();
        bootstrapPasswordField.clear();
        bootstrapVisiblePasswordField.clear();
        bootstrapConfirmPasswordField.clear();
        bootstrapVisibleConfirmPasswordField.clear();
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
        boolean available = authenticationMode == AuthenticationMode.LOGIN && credentialStore.hasSavedCredentials();
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

    private enum AuthenticationMode {
        CHECKING,
        BOOTSTRAP,
        LOGIN,
        ERROR
    }
}
