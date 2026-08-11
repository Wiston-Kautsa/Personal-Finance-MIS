package com.wk.pfmis.controllers;

import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.AiInteractionRecord;
import com.wk.pfmis.models.AuthenticationEventRecord;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.SystemLogRecord;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.utils.ExportPathService;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public class AuditHistoryTaskController {
    private static final int PAGE_LIMIT = 100;

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label guidanceLabel;
    @FXML private VBox contentContainer;
    @FXML private TextArea resultArea;
    @FXML private Button supportingActionButton;
    @FXML private Button mainActionButton;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final AuthDatabase authDatabase = AuthDatabase.getInstance();

    private String currentArea = "Activity Audit";
    private DatePicker fromPicker;
    private DatePicker toPicker;
    private ComboBox<String> userBox;
    private ComboBox<String> moduleBox;
    private ComboBox<String> actionBox;
    private ComboBox<String> eventBox;
    private ComboBox<String> resultBox;
    private ComboBox<String> accountBox;
    private ComboBox<String> recordTypeBox;
    private ComboBox<String> administratorBox;
    private ComboBox<String> workspaceBox;
    private ComboBox<String> operationBox;
    private ComboBox<String> statusBox;
    private TextField searchField;
    private TextField recordIdField;
    private TableView<AuditRow> table;
    private List<AuditRow> currentRows = List.of();

    @FXML
    public void initialize() {
        selectArea(currentArea);
    }

    public void selectArea(String area) {
        currentArea = area == null || area.isBlank() ? "Activity Audit" : area.trim();
        render();
    }

    public void refresh() {
        search();
    }

    @FXML
    private void runSupportingAction() {
        if ("Financial Record History".equals(currentArea)) {
            viewFullRecord();
            return;
        }
        viewDetails();
    }

    @FXML
    private void runMainAction() {
        if ("Data Disposal History".equals(currentArea)) {
            downloadCertificate();
            return;
        }
        exportRows(mainActionButton.getText());
    }

    private void render() {
        contentContainer.getChildren().clear();
        currentRows = List.of();
        table = null;
        configureTitle();
        renderFilters();
        search();
    }

    private void configureTitle() {
        switch (currentArea) {
            case "Financial Record History" -> {
                titleLabel.setText("Financial Record History");
                subtitleLabel.setText("Review the available history for one financial record.");
                guidanceLabel.setText("This page is read-only. Find a record, review its timeline and export the evidence.");
                supportingActionButton.setText("View Full Record");
                mainActionButton.setText("Export History");
            }
            case "Authentication History" -> {
                titleLabel.setText("Authentication History");
                subtitleLabel.setText("Review sign-in, password and account-security activity.");
                guidanceLabel.setText("Sensitive credentials are never displayed in audit details or exports.");
                supportingActionButton.setText("View Details");
                mainActionButton.setText("Export");
            }
            case "Administrative Actions" -> {
                titleLabel.setText("Administrative Actions");
                subtitleLabel.setText("Review actions performed by Administrators and Super Administrators.");
                guidanceLabel.setText("Administrative audit history is read-only and newest records are shown first.");
                supportingActionButton.setText("View Details");
                mainActionButton.setText("Export");
            }
            case "Data Disposal History" -> {
                titleLabel.setText("Data Disposal History");
                subtitleLabel.setText("Review destructive maintenance operations and related backup evidence.");
                guidanceLabel.setText("This page links to Data Maintenance evidence but cannot change financial records.");
                supportingActionButton.setText("View Details");
                mainActionButton.setText("Download Certificate");
            }
            default -> {
                titleLabel.setText("Activity Audit");
                subtitleLabel.setText("Review general actions performed throughout the system.");
                guidanceLabel.setText("Audit evidence is read-only and newest records are shown first.");
                supportingActionButton.setText("View Details");
                mainActionButton.setText("Export");
            }
        }
        supportingActionButton.getStyleClass().setAll("secondary-button");
        mainActionButton.getStyleClass().setAll("primary-button");
    }

    private void renderFilters() {
        switch (currentArea) {
            case "Financial Record History" -> renderFinancialFilters();
            case "Authentication History" -> renderAuthenticationFilters();
            case "Administrative Actions" -> renderAdministrativeFilters();
            case "Data Disposal History" -> renderDisposalFilters();
            default -> renderActivityFilters();
        }
    }

    private void renderActivityFilters() {
        fromPicker = datePicker(LocalDate.now().minusDays(30));
        toPicker = datePicker(LocalDate.now());
        userBox = combo(List.of("All users", signedInUserText(), "System"), "All users");
        moduleBox = combo(activityModuleOptions(), "All modules");
        actionBox = combo(List.of("All actions", "Created", "Updated", "Deleted", "Backup", "Restore", "Opened", "Generated", "Maintenance"), "All actions");
        searchField = textField("Record ID or description");
        contentContainer.getChildren().add(filters(
                field("From", fromPicker),
                field("To", toPicker),
                field("User", userBox),
                field("Module", moduleBox),
                field("Action", actionBox),
                wideField("Search", searchField),
                searchButton("Search")
        ));
        table = table("No activity audit records were found for the selected period.",
                List.of(column("Date and time", 175), column("User", 160), column("Module", 170), column("Action", 230), column("Record", 160), column("Result", 130)));
        contentContainer.getChildren().add(table);
    }

    private void renderFinancialFilters() {
        recordTypeBox = combo(List.of("Transaction"), "Transaction");
        recordIdField = textField("Enter ID or reference");
        accountBox = combo(accountOptions(), "All accounts");
        contentContainer.getChildren().add(filters(
                field("Record type", recordTypeBox),
                wideField("Record ID", recordIdField),
                field("Account", accountBox),
                searchButton("Find Record")
        ));
        table = table("No financial record history was found for the selected record.",
                List.of(column("Date and time", 175), column("Change", 260), column("Previous value", 220), column("New value", 360)));
        contentContainer.getChildren().add(table);
    }

    private void renderAuthenticationFilters() {
        fromPicker = datePicker(LocalDate.now().minusDays(30));
        toPicker = datePicker(LocalDate.now());
        userBox = combo(authenticationUserOptions(), "All users");
        eventBox = combo(List.of("All events", "LOGIN", "LOGOUT", "PASSWORD_CHANGE", "PASSWORD_RESET", "PASSWORD_RESET_EMAIL", "REGISTER", "WORKSPACE_ACCESS"), "All events");
        resultBox = combo(List.of("All results", "SUCCESS", "FAILED"), "All results");
        contentContainer.getChildren().add(filters(
                field("User", userBox),
                field("Event", eventBox),
                field("Result", resultBox),
                field("From", fromPicker),
                field("To", toPicker),
                searchButton("Search")
        ));
        table = table("No authentication events were found for the selected period.",
                List.of(column("Date and time", 175), column("User", 170), column("Event", 190), column("Result", 120), column("Device", 150), column("Details", 360)));
        contentContainer.getChildren().add(table);
    }

    private void renderAdministrativeFilters() {
        fromPicker = datePicker(LocalDate.now().minusDays(30));
        toPicker = datePicker(LocalDate.now());
        administratorBox = combo(List.of("All administrators", signedInUserText(), "System"), "All administrators");
        actionBox = combo(List.of("All actions", "User", "Role", "Password reset", "Workspace", "Category", "Currency", "Setup", "Backup", "Maintenance"), "All actions");
        workspaceBox = combo(List.of("All workspaces", workspaceText()), "All workspaces");
        contentContainer.getChildren().add(filters(
                field("Administrator", administratorBox),
                field("Action", actionBox),
                field("Workspace", workspaceBox),
                field("From", fromPicker),
                field("To", toPicker),
                searchButton("Search")
        ));
        table = table("No administrative actions were found for the selected period.",
                List.of(column("Date", 175), column("Administrator", 180), column("Action", 260), column("Target", 240), column("Workspace", 180), column("Result", 130)));
        contentContainer.getChildren().add(table);
    }

    private void renderDisposalFilters() {
        fromPicker = datePicker(LocalDate.now().minusDays(30));
        toPicker = datePicker(LocalDate.now());
        operationBox = combo(List.of("All operations", "Record Disposal", "Clear Test Data", "Purge Archived Records", "Reset Workspace", "Delete Workspace", "Restore"), "All operations");
        workspaceBox = combo(List.of("All workspaces", workspaceText()), "All workspaces");
        statusBox = combo(List.of("All statuses", "INFO", "WARNING", "ERROR", "Completed", "Failed"), "All statuses");
        contentContainer.getChildren().add(filters(
                field("Operation", operationBox),
                field("Workspace", workspaceBox),
                field("Status", statusBox),
                field("From", fromPicker),
                field("To", toPicker),
                searchButton("Search")
        ));
        table = table("No data disposal history was found for the selected period.",
                List.of(column("Date", 175), column("Operation", 260), column("Workspace", 180), column("Records affected", 140), column("Status", 130), column("Performed by", 170)));
        contentContainer.getChildren().add(table);
    }

    private void search() {
        if (table == null) {
            return;
        }
        currentRows = switch (currentArea) {
            case "Financial Record History" -> financialRows();
            case "Authentication History" -> authenticationRows();
            case "Administrative Actions" -> administrativeRows();
            case "Data Disposal History" -> disposalRows();
            default -> activityRows();
        };
        table.getItems().setAll(currentRows);
        if (!currentRows.isEmpty()) {
            table.getSelectionModel().selectFirst();
        }
        supportingActionButton.setDisable(currentRows.isEmpty());
        mainActionButton.setDisable(currentRows.isEmpty());
        resultArea.setText(resultSummary());
    }

    private List<AuditRow> activityRows() {
        List<AuditRow> rows = new ArrayList<>();
        for (SystemLogRecord record : database.listSystemLogHistory(300)) {
            AuditRow row = systemActivityRow(record);
            if (matchesActivity(row)) {
                rows.add(row);
            }
        }
        for (AiInteractionRecord record : database.listAiInteractionHistory(200)) {
            AuditRow row = aiActivityRow(record);
            if (matchesActivity(row)) {
                rows.add(row);
            }
        }
        return newest(rows);
    }

    private List<AuditRow> financialRows() {
        List<AuditRow> rows = new ArrayList<>();
        String idOrReference = text(recordIdField).toLowerCase(Locale.ENGLISH);
        String selectedAccount = selected(accountBox);
        for (FinanceTransaction transaction : database.listRecentTransactions(300)) {
            if (!idOrReference.isBlank()
                    && !String.valueOf(transaction.getId()).equals(idOrReference)
                    && !contains(transaction.getReferenceNumber(), idOrReference)
                    && !contains(transaction.getDescription(), idOrReference)) {
                continue;
            }
            if (!isAll(selectedAccount) && !selectedAccount.equals(transaction.getAccountName())) {
                continue;
            }
            rows.add(transactionHistoryRow(transaction));
        }
        return newest(rows);
    }

    private List<AuditRow> authenticationRows() {
        List<AuthenticationEventRecord> events = authenticationEvents();
        List<AuditRow> rows = new ArrayList<>();
        for (AuthenticationEventRecord event : events) {
            AuditRow row = authenticationRow(event);
            if (matchesAuthentication(row, event)) {
                rows.add(row);
            }
        }
        return newest(rows);
    }

    private List<AuditRow> administrativeRows() {
        if (!UserSession.isAdminOrSuperAdmin()) {
            return List.of();
        }
        List<AuditRow> rows = new ArrayList<>();
        for (SystemLogRecord record : database.listSystemLogHistory(300)) {
            if (!isAdministrativeLog(record)) {
                continue;
            }
            AuditRow row = administrativeRow(record);
            if (withinDateRange(record.getCreatedAt()) && matchesCombo(row.value(2), selected(actionBox))) {
                rows.add(row);
            }
        }
        return newest(rows);
    }

    private List<AuditRow> disposalRows() {
        if (!UserSession.isAdminOrSuperAdmin()) {
            return List.of();
        }
        List<AuditRow> rows = new ArrayList<>();
        for (SystemLogRecord record : database.listSystemLogHistory(300)) {
            if (!isDisposalLog(record)) {
                continue;
            }
            AuditRow row = disposalRow(record);
            if (withinDateRange(record.getCreatedAt())
                    && matchesCombo(row.value(1), selected(operationBox))
                    && matchesCombo(row.value(4), selected(statusBox))) {
                rows.add(row);
            }
        }
        return newest(rows);
    }

    private AuditRow systemActivityRow(SystemLogRecord record) {
        String detail = lines(
                "User: " + signedInUserText(),
                "Role: " + roleText(),
                "Workspace: " + workspaceText(),
                "Module: " + record.getModuleName(),
                "Action: " + record.getActionName(),
                "Record type: System event",
                "Record ID: " + record.getId(),
                "Date and time: " + record.getCreatedAt(),
                "Reason: " + record.getDetails(),
                "Result: " + record.getSeverity(),
                "Device/session: Local desktop",
                "Related record: Not recorded"
        );
        return new AuditRow(row(record.getCreatedAt(), "System", record.getModuleName(), record.getActionName(), "#" + record.getId(), record.getSeverity()), detail);
    }

    private AuditRow aiActivityRow(AiInteractionRecord record) {
        String detail = lines(
                "User: " + signedInUserText(),
                "Role: " + roleText(),
                "Workspace: " + workspaceText(),
                "Module: " + record.getModuleName(),
                "Action: " + record.getActionName(),
                "Record type: Smart Analysis",
                "Record ID: " + record.getId(),
                "Date and time: " + record.getCreatedAt(),
                "Reason: Smart Analysis request",
                "Result: " + record.getStatus(),
                "Device/session: Local desktop",
                "Related record: Provider " + record.getProviderName()
        );
        return new AuditRow(row(record.getCreatedAt(), signedInUserText(), record.getModuleName(), record.getActionName(), "#" + record.getId(), record.getStatus()), detail);
    }

    private AuditRow transactionHistoryRow(FinanceTransaction transaction) {
        String newValue = transaction.getTransactionType() + " | " + MoneyUtil.mwk(transaction.getAmount())
                + " | " + nullToDash(transaction.getTransactionStatus())
                + " | " + nullToDash(transaction.getCategoryName());
        String detail = fullTransactionDetail(transaction);
        return new AuditRow(row(transaction.getTransactionDate(), "Transaction created", "-", newValue), detail);
    }

    private AuditRow authenticationRow(AuthenticationEventRecord event) {
        String details = sanitizeAuthenticationDetails(event.getDetails());
        String detail = lines(
                "Username: " + event.getUsername(),
                "Event: " + event.getEventType(),
                "Result: " + event.getResult(),
                "Date and time: " + event.getCreatedAt(),
                "Device or computer: Local desktop",
                "Session ID: Authentication event " + event.getId(),
                "Failed-attempt count: Stored in security database where available",
                "Lockout expiry: Stored in security database where available",
                "Administrator involved: " + administratorFromDetails(details),
                "Error reason: " + details
        );
        return new AuditRow(row(event.getCreatedAt(), event.getUsername(), event.getEventType(), event.getResult(), "Local desktop", details), detail);
    }

    private AuditRow administrativeRow(SystemLogRecord record) {
        String detail = lines(
                "Administrator: " + signedInUserText(),
                "Role: " + roleText(),
                "Target user or record: " + record.getActionName(),
                "Previous state: Not recorded",
                "New state: " + record.getSeverity(),
                "Reason: " + record.getDetails(),
                "Approval reference: System event " + record.getId(),
                "Date and time: " + record.getCreatedAt(),
                "Result: " + record.getSeverity()
        );
        return new AuditRow(row(record.getCreatedAt(), "System", record.getActionName(), record.getDetails(), workspaceText(), record.getSeverity()), detail);
    }

    private AuditRow disposalRow(SystemLogRecord record) {
        String detail = lines(
                "Operation ID: " + record.getId(),
                "Operation type: " + record.getActionName(),
                "Workspace: " + workspaceText(),
                "Reason: " + record.getDetails(),
                "Records selected: See operation details",
                "Records removed: See operation details",
                "Records blocked: See operation details",
                "Financial amount affected: See operation details",
                "Backup reference: " + latestBackupText(),
                "Backup verification: Recorded during maintenance where available",
                "Performed by: System",
                "Date and time: " + record.getCreatedAt(),
                "Database verification result: Recorded during maintenance where available",
                "Final status: " + record.getSeverity()
        );
        return new AuditRow(row(record.getCreatedAt(), record.getActionName(), workspaceText(), recordsAffected(record.getDetails()), record.getSeverity(), "System"), detail);
    }

    private void viewDetails() {
        AuditRow row = selectedRow();
        if (row == null) {
            resultArea.setText("Select an audit entry first.");
            return;
        }
        resultArea.setText(row.detail());
    }

    private void viewFullRecord() {
        viewDetails();
    }

    private void exportRows(String actionName) {
        try {
            Path exportFile = exportFile(slug(currentArea) + "-" + slug(actionName), actionName);
            resultArea.setText(ExportPathService.successMessage(exportFile));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export audit history", exception);
        }
    }

    private void downloadCertificate() {
        try {
            Path exportFile = exportFile("data-disposal-certificate", "Download Certificate");
            resultArea.setText("Certificate downloaded." + System.lineSeparator()
                    + System.lineSeparator()
                    + "Saved to:" + System.lineSeparator()
                    + exportFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to download disposal certificate", exception);
        }
    }

    private Path exportFile(String name, String actionName) throws IOException {
        return ExportPathService.writeTextExport(
                ExportPathService.defaultFileName("Audit History " + name, "txt"),
                exportBody(actionName)
        );
    }

    private String exportBody(String actionName) {
        StringBuilder builder = new StringBuilder();
        builder.append(titleLabel.getText()).append(System.lineSeparator());
        builder.append("Action: ").append(actionName).append(System.lineSeparator());
        builder.append("Workspace: ").append(workspaceText()).append(System.lineSeparator());
        builder.append("Generated: ").append(LocalDateTime.now()).append(System.lineSeparator());
        builder.append("Scope: Current filtered results only").append(System.lineSeparator()).append(System.lineSeparator());
        if (currentRows.isEmpty()) {
            builder.append("No records matched the selected filters.").append(System.lineSeparator());
            return builder.toString();
        }
        for (AuditRow row : currentRows) {
            builder.append(String.join(" | ", row.values())).append(System.lineSeparator());
            builder.append(row.detail()).append(System.lineSeparator()).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String resultSummary() {
        if (!UserSession.isAdminOrSuperAdmin()
                && ("Administrative Actions".equals(currentArea) || "Data Disposal History".equals(currentArea))) {
            return titleLabel.getText() + " is hidden for normal users.";
        }
        if (currentRows.isEmpty()) {
            return switch (currentArea) {
                case "Authentication History" -> "No authentication events were found for the selected period.";
                case "Financial Record History" -> "No financial record history was found. Enter a transaction ID, reference or select an account.";
                case "Data Disposal History" -> "No data disposal history was found for the selected period.";
                case "Administrative Actions" -> "No administrative actions were found for the selected period.";
                default -> "No activity audit records were found for the selected period.";
            };
        }
        return "Showing " + currentRows.size() + " newest matching record(s).";
    }

    private TableView<AuditRow> table(String emptyMessage, List<ColumnSpec> columns) {
        TableView<AuditRow> view = new TableView<>();
        view.setPlaceholder(new Label(emptyMessage));
        view.setPrefHeight(360);
        for (int index = 0; index < columns.size(); index++) {
            ColumnSpec spec = columns.get(index);
            int valueIndex = index;
            TableColumn<AuditRow, String> column = new TableColumn<>(spec.title());
            column.setPrefWidth(spec.width());
            column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().value(valueIndex)));
            view.getColumns().add(column);
        }
        TableActions.configureScrollableTable(view);
        return view;
    }

    private FlowPane filters(Node... nodes) {
        FlowPane pane = new FlowPane(10, 10, nodes);
        pane.setPrefWrapLength(1120);
        return pane;
    }

    private VBox field(String label, Node node) {
        VBox box = new VBox(5);
        box.getStyleClass().add("maintenance-simple-field");
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("field-label");
        box.getChildren().addAll(fieldLabel, node);
        return box;
    }

    private VBox wideField(String label, Node node) {
        VBox box = field(label, node);
        box.getStyleClass().setAll("maintenance-simple-field-wide");
        return box;
    }

    private Node searchButton(String text) {
        Button button = new Button(text);
        button.setMinWidth(120);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(event -> search());
        VBox box = new VBox(5);
        box.getStyleClass().add("maintenance-simple-field");
        Label label = new Label(" ");
        label.getStyleClass().add("field-label");
        box.getChildren().addAll(label, button);
        return box;
    }

    private ComboBox<String> combo(List<String> values, String selected) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(values);
        comboBox.setValue(selected);
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.getStyleClass().add("maintenance-input");
        return comboBox;
    }

    private TextField textField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("maintenance-input");
        field.setOnAction(event -> search());
        return field;
    }

    private DatePicker datePicker(LocalDate value) {
        DatePicker picker = new DatePicker(value);
        picker.getStyleClass().add("maintenance-input");
        return picker;
    }

    private List<String> activityModuleOptions() {
        Set<String> modules = new LinkedHashSet<>();
        modules.add("All modules");
        for (SystemLogRecord record : database.listSystemLogHistory(100)) {
            if (record.getModuleName() != null && !record.getModuleName().isBlank()) {
                modules.add(record.getModuleName());
            }
        }
        modules.add("Smart Analysis");
        return List.copyOf(modules);
    }

    private List<String> accountOptions() {
        List<String> options = new ArrayList<>();
        options.add("All accounts");
        for (Account account : database.listAccounts()) {
            options.add(account.getAccountName());
        }
        return options;
    }

    private List<String> authenticationUserOptions() {
        Set<String> users = new LinkedHashSet<>();
        users.add("All users");
        for (AuthenticationEventRecord event : authenticationEvents()) {
            users.add(event.getUsername());
        }
        return List.copyOf(users);
    }

    private List<AuthenticationEventRecord> authenticationEvents() {
        int userId = UserSession.getAuthenticatedUser().getId();
        return UserSession.isSuperAdmin()
                ? authDatabase.listAuthenticationEvents(userId, 500)
                : authDatabase.listOwnAuthenticationEvents(userId, 250);
    }

    private boolean matchesActivity(AuditRow row) {
        return withinDateRange(row.value(0))
                && matchesCombo(row.value(1), selected(userBox))
                && matchesCombo(row.value(2), selected(moduleBox))
                && matchesCombo(row.value(3), selected(actionBox))
                && matchesSearch(row);
    }

    private boolean matchesAuthentication(AuditRow row, AuthenticationEventRecord event) {
        return withinDateRange(row.value(0))
                && matchesCombo(row.value(1), selected(userBox))
                && matchesCombo(row.value(2), selected(eventBox))
                && matchesCombo(row.value(3), selected(resultBox))
                && matchesSearch(row);
    }

    private boolean matchesSearch(AuditRow row) {
        String search = text(searchField).toLowerCase(Locale.ENGLISH);
        return search.isBlank() || row.exportLine().toLowerCase(Locale.ENGLISH).contains(search);
    }

    private boolean matchesCombo(String value, String selected) {
        if (isAll(selected)) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ENGLISH).contains(selected.toLowerCase(Locale.ENGLISH));
    }

    private boolean withinDateRange(String timestamp) {
        LocalDate date = dateFrom(timestamp);
        if (date == null) {
            return true;
        }
        LocalDate from = fromPicker == null ? null : fromPicker.getValue();
        LocalDate to = toPicker == null ? null : toPicker.getValue();
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private LocalDate dateFrom(String value) {
        if (value == null || value.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private boolean isAdministrativeLog(SystemLogRecord record) {
        String text = (record.getModuleName() + " " + record.getActionName() + " " + record.getDetails()).toLowerCase(Locale.ENGLISH);
        return containsAny(text, "administration", "user", "role", "password reset", "workspace", "setup", "category", "currency", "backup", "restore", "maintenance approved", "super administrator");
    }

    private boolean isDisposalLog(SystemLogRecord record) {
        String text = (record.getModuleName() + " " + record.getActionName() + " " + record.getDetails()).toLowerCase(Locale.ENGLISH);
        return containsAny(text, "record disposal", "disposal", "clear test", "purge", "archived", "reset workspace", "delete workspace", "maintenance", "backup restored", "restore latest local recovery backup");
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private List<AuditRow> newest(List<AuditRow> rows) {
        return rows.stream()
                .sorted(Comparator.comparing((AuditRow row) -> row.value(0)).reversed())
                .limit(PAGE_LIMIT)
                .toList();
    }

    private AuditRow selectedRow() {
        if (table == null) {
            return null;
        }
        AuditRow row = table.getSelectionModel().getSelectedItem();
        return row == null && !currentRows.isEmpty() ? currentRows.getFirst() : row;
    }

    private String fullTransactionDetail(FinanceTransaction transaction) {
        return lines(
                "Record type: Transaction",
                "Record ID: " + transaction.getId(),
                "Date and time: " + transaction.getTransactionDate(),
                "Account: " + nullToDash(transaction.getAccountName()),
                "Type: " + nullToDash(transaction.getTransactionType()),
                "Purpose: " + nullToDash(transaction.getTransactionPurpose()),
                "Status: " + nullToDash(transaction.getTransactionStatus()),
                "Category: " + nullToDash(transaction.getCategoryName()),
                "Amount: " + MoneyUtil.mwk(transaction.getAmount()),
                "Payment method: " + nullToDash(transaction.getPaymentMethod()),
                "Reference: " + nullToDash(transaction.getReferenceNumber()),
                "Description: " + nullToDash(transaction.getDescription()),
                "Workspace: " + workspaceText(),
                "Result: Read-only record history"
        );
    }

    private String sanitizeAuthenticationDetails(String details) {
        String clean = details == null ? "" : details;
        String lower = clean.toLowerCase(Locale.ENGLISH);
        if (lower.contains("temporary password")
                || lower.contains("password_hash")
                || lower.contains("password salt")
                || lower.contains("api key")
                || lower.contains("credential")
                || lower.contains("secret")
                || lower.contains("token")) {
            return "Sensitive credential detail hidden.";
        }
        return clean;
    }

    private String administratorFromDetails(String details) {
        String clean = details == null ? "" : details;
        int index = clean.toLowerCase(Locale.ENGLISH).indexOf("by user ");
        return index >= 0 ? clean.substring(index).trim() : "Not recorded";
    }

    private String recordsAffected(String details) {
        if (details == null || details.isBlank()) {
            return "-";
        }
        String lower = details.toLowerCase(Locale.ENGLISH);
        int index = lower.indexOf("records affected:");
        if (index < 0) {
            return "-";
        }
        String value = details.substring(index + "records affected:".length()).trim();
        int end = value.indexOf('\n');
        return end >= 0 ? value.substring(0, end).trim() : value;
    }

    private String latestBackupText() {
        try {
            BackupRecord backup = database.latestDailyBackupRecord();
            return backup == null ? "No backup recorded" : backup.getBackupFile();
        } catch (RuntimeException exception) {
            return "Unavailable";
        }
    }

    private String signedInUserText() {
        try {
            return UserSession.getAuthenticatedUser().getDisplayName();
        } catch (RuntimeException exception) {
            return "No signed-in user";
        }
    }

    private String roleText() {
        try {
            return UserSession.getAuthenticatedUser().getRoleDisplay();
        } catch (RuntimeException exception) {
            return "Not signed in";
        }
    }

    private String workspaceText() {
        try {
            SystemUser user = UserSession.getWorkspaceUser();
            return user.getDisplayName() + " - Workspace " + user.getId();
        } catch (RuntimeException exception) {
            return "No active workspace";
        }
    }

    private String selected(ComboBox<String> comboBox) {
        return comboBox == null || comboBox.getValue() == null ? "" : comboBox.getValue();
    }

    private boolean isAll(String value) {
        return value == null || value.isBlank() || value.toLowerCase(Locale.ENGLISH).startsWith("all ");
    }

    private String text(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ENGLISH).contains(search);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String slug(String value) {
        String clean = value == null ? "audit" : value.toLowerCase(Locale.ENGLISH);
        clean = clean.replaceAll("[^a-z0-9]+", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        return clean.isBlank() ? "audit" : clean;
    }

    private String lines(String... values) {
        return String.join(System.lineSeparator(), values);
    }

    private List<String> row(String... values) {
        List<String> row = new ArrayList<>();
        for (String value : values) {
            row.add(value == null ? "" : value);
        }
        return row;
    }

    private ColumnSpec column(String title, double width) {
        return new ColumnSpec(title, width);
    }

    private record ColumnSpec(String title, double width) {
    }

    private record AuditRow(List<String> values, String detail) {
        private String value(int index) {
            return index >= 0 && index < values.size() ? values.get(index) : "";
        }

        private String exportLine() {
            return String.join(" | ", values);
        }
    }
}
