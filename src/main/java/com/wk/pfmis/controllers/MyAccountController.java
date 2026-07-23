package com.wk.pfmis.controllers;

import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

public class MyAccountController {
    @FXML private Label fullNameLabel;
    @FXML private Label usernameLabel;
    @FXML private Label emailLabel;
    @FXML private Label roleLabel;
    @FXML private Label passwordStatusLabel;
    @FXML private Label workspaceLabel;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label messageLabel;

    private final AuthDatabase authDatabase = AuthDatabase.getInstance();

    @FXML
    public void initialize() {
        SystemUser user = UserSession.getAuthenticatedUser();
        fullNameLabel.setText(user.getDisplayName());
        usernameLabel.setText(user.getUsername());
        emailLabel.setText(user.getEmail().isBlank() ? "Not provided" : user.getEmail());
        roleLabel.setText(user.getRoleDisplay());
        passwordStatusLabel.setText(user.isMustChangePassword()
                ? "Password change required. Set a new password before continuing normal work."
                : "Password status: current.");
        workspaceLabel.setText(UserSession.isViewingOwnWorkspace()
                ? "You are using your own private workspace."
                : "You are administering the workspace of " + UserSession.getWorkspaceUser().getDisplayName() + ".");
    }

    @FXML
    private void changePassword() {
        messageLabel.setText("");
        if (!newPasswordField.getText().equals(confirmPasswordField.getText())) {
            messageLabel.setText("The new passwords do not match.");
            confirmPasswordField.clear();
            return;
        }
        try {
            authDatabase.changeOwnPassword(
                    UserSession.getAuthenticatedUser().getId(),
                    currentPasswordField.getText(),
                    newPasswordField.getText()
            );
            SystemUser refreshedUser = authDatabase.findUserById(UserSession.getAuthenticatedUser().getId());
            UserSession.refreshAuthenticatedUser(refreshedUser);
            currentPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
            passwordStatusLabel.setText("Password status: current.");
            messageLabel.setText("Password changed successfully.");
        } catch (RuntimeException exception) {
            messageLabel.setText(rootMessage(exception));
        }
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
