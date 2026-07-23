package com.wk.pfmis.controllers;

import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.models.AuthenticationEventRecord;
import com.wk.pfmis.security.UserSession;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class SecurityHistoryController {
    @FXML private Label scopeLabel;
    @FXML private Label statusLabel;
    @FXML private TableView<AuthenticationEventRecord> authenticationTable;
    @FXML private TableColumn<AuthenticationEventRecord, String> authDateColumn;
    @FXML private TableColumn<AuthenticationEventRecord, String> authUsernameColumn;
    @FXML private TableColumn<AuthenticationEventRecord, String> authEventColumn;
    @FXML private TableColumn<AuthenticationEventRecord, String> authResultColumn;
    @FXML private TableColumn<AuthenticationEventRecord, String> authDetailsColumn;

    private final AuthDatabase authDatabase = AuthDatabase.getInstance();

    @FXML
    public void initialize() {
        authDateColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        authUsernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        authEventColumn.setCellValueFactory(new PropertyValueFactory<>("eventType"));
        authResultColumn.setCellValueFactory(new PropertyValueFactory<>("result"));
        authDetailsColumn.setCellValueFactory(new PropertyValueFactory<>("details"));
        configureContextMenu();
        refresh();
    }

    @FXML
    private void refresh() {
        try {
            int userId = UserSession.getAuthenticatedUser().getId();
            List<AuthenticationEventRecord> events = UserSession.isSuperAdmin()
                    ? authDatabase.listAuthenticationEvents(userId, 500)
                    : authDatabase.listOwnAuthenticationEvents(userId, 250);
            authenticationTable.setItems(FXCollections.observableArrayList(events));
            scopeLabel.setText(UserSession.isSuperAdmin()
                    ? "Showing central sign-in, reset and workspace-access events."
                    : "Showing sign-in and password activity connected to your account.");
            statusLabel.setText(events.size() + " security event(s) loaded.");
        } catch (RuntimeException exception) {
            statusLabel.setText(rootMessage(exception));
        }
    }

    private void configureContextMenu() {
        TableActions.installRowContextMenu(authenticationTable, event -> List.of(
                TableActions.copyRowItem(authenticationTable, event),
                TableActions.exportTableItem(authenticationTable, "Login and Security History"),
                TableActions.printTableItem(authenticationTable, "Login and Security History"),
                TableActions.refreshItem(this::refresh)
        ));
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
