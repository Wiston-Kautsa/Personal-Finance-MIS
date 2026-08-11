package com.wk.pfmis.controllers;

import com.wk.pfmis.MainApp;
import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.mail.EmailService;
import com.wk.pfmis.models.AuthenticationEventRecord;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.ArrayList;
import java.util.List;

public class UserManagementController {
    @FXML private TableView<SystemUser> usersTable;
    @FXML private TableColumn<SystemUser, Integer> idColumn;
    @FXML private TableColumn<SystemUser, String> fullNameColumn;
    @FXML private TableColumn<SystemUser, String> usernameColumn;
    @FXML private TableColumn<SystemUser, String> emailColumn;
    @FXML private TableColumn<SystemUser, String> roleColumn;
    @FXML private TableColumn<SystemUser, String> statusColumn;
    @FXML private TableColumn<SystemUser, String> passwordStatusColumn;
    @FXML private TableColumn<SystemUser, String> lastLoginColumn;
    @FXML private Label workspaceLabel;
    @FXML private Label messageLabel;
    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField temporaryPasswordField;
    @FXML private ComboBox<String> roleCombo;
    @FXML private TableView<AuthenticationEventRecord> authenticationTable;
    @FXML private TableColumn<AuthenticationEventRecord, String> authDateColumn;
    @FXML private TableColumn<AuthenticationEventRecord, String> authUsernameColumn;
    @FXML private TableColumn<AuthenticationEventRecord, String> authEventColumn;
    @FXML private TableColumn<AuthenticationEventRecord, String> authResultColumn;
    @FXML private TableColumn<AuthenticationEventRecord, String> authDetailsColumn;

    private final AuthDatabase authDatabase = AuthDatabase.getInstance();
    private final EmailService emailService = EmailService.getInstance();

    @FXML
    public void initialize() {
        requireSuperAdmin();
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        fullNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        roleColumn.setCellValueFactory(new PropertyValueFactory<>("roleDisplay"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        passwordStatusColumn.setCellValueFactory(new PropertyValueFactory<>("passwordStatus"));
        lastLoginColumn.setCellValueFactory(new PropertyValueFactory<>("lastLoginAt"));
        authDateColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        authUsernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        authEventColumn.setCellValueFactory(new PropertyValueFactory<>("eventType"));
        authResultColumn.setCellValueFactory(new PropertyValueFactory<>("result"));
        authDetailsColumn.setCellValueFactory(new PropertyValueFactory<>("details"));
        roleCombo.setItems(FXCollections.observableArrayList(
                SystemUser.ROLE_USER,
                SystemUser.ROLE_ADMIN,
                SystemUser.ROLE_SUPER_ADMIN
        ));
        roleCombo.setValue(SystemUser.ROLE_USER);
        configureContextMenus();
        refresh();
    }

    @FXML
    private void refresh() {
        int actingUserId = UserSession.getAuthenticatedUser().getId();
        usersTable.setItems(FXCollections.observableArrayList(authDatabase.listUsers(actingUserId)));
        authenticationTable.setItems(FXCollections.observableArrayList(authDatabase.listAuthenticationEvents(actingUserId, 250)));
        workspaceLabel.setText("Current workspace: " + UserSession.getWorkspaceUser().getDisplayName()
                + " (" + UserSession.getWorkspaceUser().getUsername() + ")");
        messageLabel.setText("");
    }

    @FXML
    private void backupUserRegistry() {
        try {
            java.nio.file.Path backup = authDatabase.ensureDailySecurityBackup();
            messageLabel.setText("User registry backup created: " + backup.getFileName());
        } catch (RuntimeException exception) {
            showError(rootMessage(exception));
        }
    }

    @FXML
    private void createUser() {
        try {
            SystemUser user = authDatabase.registerUserByAdmin(
                    fullNameField.getText(),
                    usernameField.getText(),
                    emailField.getText(),
                    temporaryPasswordField.getText(),
                    roleCombo.getValue(),
                    UserSession.getAuthenticatedUser().getId()
            );
            clearForm();
            refresh();
            messageLabel.setText("Created " + user.getDisplayName() + " with a private workspace.");
        } catch (RuntimeException exception) {
            showError(rootMessage(exception));
        }
    }

    @FXML
    private void openSelectedWorkspace() {
        try {
            SystemUser selected = requireSelection();
            if (!selected.isActive()) {
                showError("Activate the user before opening the workspace.");
                return;
            }
            MainApp.switchWorkspace(selected);
        } catch (RuntimeException exception) {
            showError(rootMessage(exception));
        }
    }

    @FXML
    private void returnToMyWorkspace() {
        MainApp.returnToOwnWorkspace();
    }

    @FXML
    private void activateSelected() {
        updateStatus(SystemUser.STATUS_ACTIVE);
    }

    @FXML
    private void deactivateSelected() {
        updateStatus(SystemUser.STATUS_INACTIVE);
    }

    @FXML
    private void resetSelectedPassword() {
        try {
            SystemUser selected = requireSelection();
            if (!emailService.isSendConfigured()) {
                showError(emailService.sendConfigurationStatus());
                return;
            }
            AuthDatabase.PasswordResetDelivery reset = authDatabase.prepareEmailPasswordReset(
                    selected.getEmail().isBlank() ? selected.getUsername() : selected.getEmail(),
                    UserSession.getAuthenticatedUser().getId()
            );
            emailService.sendPasswordResetEmail(reset.email(), reset.displayName(), reset.temporaryPassword());
            authDatabase.completeEmailPasswordReset(reset, UserSession.getAuthenticatedUser().getId());
            refresh();
            messageLabel.setText("Password reset email sent to " + reset.email() + ".");
        } catch (RuntimeException exception) {
            showError(rootMessage(exception));
        }
    }

    private void updateStatus(String status) {
        try {
            SystemUser selected = requireSelection();
            authDatabase.updateUserStatus(
                    selected.getId(),
                    status,
                    UserSession.getAuthenticatedUser().getId()
            );
            refresh();
            messageLabel.setText(selected.getUsername() + " is now " + status + ".");
            if (SystemUser.STATUS_INACTIVE.equals(status)
                    && UserSession.getWorkspaceUser().getId() == selected.getId()
                    && !UserSession.isViewingOwnWorkspace()) {
                MainApp.returnToOwnWorkspace();
            }
        } catch (RuntimeException exception) {
            showError(rootMessage(exception));
        }
    }

    private SystemUser requireSelection() {
        SystemUser selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException("Select a user first.");
        }
        return selected;
    }

    private void clearForm() {
        fullNameField.clear();
        usernameField.clear();
        emailField.clear();
        temporaryPasswordField.clear();
        roleCombo.setValue(SystemUser.ROLE_USER);
    }

    private void configureContextMenus() {
        TableActions.installRowContextMenu(usersTable, this::userMenuItems);
        TableActions.installRowContextMenu(authenticationTable, this::authenticationMenuItems);
    }

    private List<javafx.scene.control.MenuItem> userMenuItems(SystemUser user) {
        List<javafx.scene.control.MenuItem> items = new ArrayList<>();
        items.add(TableActions.menuItem("Open Workspace", this::openSelectedWorkspace));
        if (user.isActive()) {
            items.add(TableActions.menuItem("Deactivate Account", this::deactivateSelected));
        } else {
            items.add(TableActions.menuItem("Activate Account", this::activateSelected));
        }
        items.add(TableActions.menuItem("Email Password Reset", this::resetSelectedPassword));
        items.add(TableActions.separator());
        items.add(TableActions.copyRowItem(usersTable, user));
        items.add(TableActions.exportTableItem(usersTable, "System Users"));
        items.add(TableActions.printTableItem(usersTable, "System Users"));
        items.add(TableActions.refreshItem(this::refresh));
        return items;
    }

    private List<javafx.scene.control.MenuItem> authenticationMenuItems(AuthenticationEventRecord event) {
        List<javafx.scene.control.MenuItem> items = new ArrayList<>();
        items.add(TableActions.menuItem("View Authentication Event", () -> viewAuthenticationEvent(event)));
        items.add(TableActions.separator());
        items.add(TableActions.copyRowItem(authenticationTable, event));
        items.add(TableActions.exportTableItem(authenticationTable, "Authentication Activity"));
        items.add(TableActions.printTableItem(authenticationTable, "Authentication Activity"));
        items.add(TableActions.refreshItem(this::refresh));
        return items;
    }

    private void viewAuthenticationEvent(AuthenticationEventRecord event) {
        if (event == null) {
            return;
        }
        UiAlerts.info(
                "Date: " + event.getCreatedAt()
                        + "\nUser: " + event.getUsername()
                        + "\nEvent: " + event.getEventType()
                        + "\nResult: " + event.getResult()
                        + "\nDetails: " + (event.getDetails() == null || event.getDetails().isBlank() ? "-" : event.getDetails())
        );
    }

    private void showError(String message) {
        messageLabel.setText(message);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("User Management");
        alert.setHeaderText("The requested action could not be completed");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void requireSuperAdmin() {
        if (!UserSession.isSuperAdmin()) {
            throw new SecurityException("Only a super administrator can manage users.");
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
