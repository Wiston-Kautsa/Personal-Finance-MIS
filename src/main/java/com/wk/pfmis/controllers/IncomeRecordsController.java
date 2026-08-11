package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Person;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.SystemLogRecord;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class IncomeRecordsController {
    @FXML private ComboBox<String> periodBox;
    @FXML private ComboBox<String> accountFilterBox;
    @FXML private ComboBox<String> sourceFilterBox;
    @FXML private ComboBox<String> statusFilterBox;
    @FXML private TextField searchField;
    @FXML private TableView<IncomeRecordRow> incomeTable;
    @FXML private TableColumn<IncomeRecordRow, String> dateColumn;
    @FXML private TableColumn<IncomeRecordRow, String> descriptionColumn;
    @FXML private TableColumn<IncomeRecordRow, String> sourceColumn;
    @FXML private TableColumn<IncomeRecordRow, String> accountColumn;
    @FXML private TableColumn<IncomeRecordRow, String> amountColumn;
    @FXML private TableColumn<IncomeRecordRow, String> statusColumn;
    @FXML private TextArea detailsArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final List<IncomeRecordRow> allRows = new ArrayList<>();

    @FXML
    public void initialize() {
        periodBox.setItems(FXCollections.observableArrayList("This month", "Last month", "This year", "All periods"));
        periodBox.getSelectionModel().select("This month");
        statusFilterBox.setItems(FXCollections.observableArrayList("All statuses", "Draft", "Posted", "Cancelled", "Reversed"));
        statusFilterBox.getSelectionModel().select("All statuses");
        dateColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDate()));
        descriptionColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDescription()));
        sourceColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getSource()));
        accountColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAccount()));
        amountColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmount())));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));
        TableActions.configureScrollableTable(incomeTable);
        loadFilterOptions();
        search();
    }

    @FXML
    private void search() {
        loadRows();
        String account = selected(accountFilterBox);
        String source = selected(sourceFilterBox);
        String status = selected(statusFilterBox);
        String query = value(searchField).toLowerCase(Locale.ENGLISH);
        List<IncomeRecordRow> filtered = allRows.stream()
                .filter(row -> matchesPeriod(row.getDate()))
                .filter(row -> "All accounts".equals(account) || row.getAccount().equals(account))
                .filter(row -> "All sources".equals(source) || row.getSource().equals(source))
                .filter(row -> "All statuses".equals(status) || row.getStatus().equals(status))
                .filter(row -> query.isBlank()
                        || row.getDescription().toLowerCase(Locale.ENGLISH).contains(query)
                        || row.getReference().toLowerCase(Locale.ENGLISH).contains(query)
                        || String.valueOf(row.getRecordId()).contains(query))
                .toList();
        incomeTable.setItems(FXCollections.observableArrayList(filtered));
        detailsArea.setText(filtered.size() + " income record(s) found.");
    }

    @FXML
    private void openRecord() {
        IncomeRecordRow row = selectedRow("open");
        if (row == null) {
            return;
        }
        detailsArea.setText(row.isDraft() ? draftDetails(row.draft()) : transactionDetails(row.transaction()));
    }

    @FXML
    private void showMoreActions() {
        IncomeRecordRow row = selectedRow("manage");
        if (row == null) {
            return;
        }
        List<String> actions = actionsFor(row);
        ChoiceDialog<String> dialog = new ChoiceDialog<>(actions.get(0), actions);
        dialog.setTitle("Income Actions");
        dialog.setHeaderText(row.getDescription());
        dialog.setContentText("Action:");
        dialog.showAndWait().ifPresent(action -> handleAction(row, action));
    }

    private void loadFilterOptions() {
        List<String> accounts = new ArrayList<>();
        accounts.add("All accounts");
        accounts.addAll(database.listAccounts().stream().map(Account::getAccountName).distinct().sorted().toList());
        accountFilterBox.setItems(FXCollections.observableArrayList(accounts));
        accountFilterBox.getSelectionModel().select("All accounts");

        List<String> sources = new ArrayList<>();
        sources.add("All sources");
        sources.addAll(database.listCategories().stream()
                .filter(category -> "INCOME".equals(category.getCategoryType()) || "BOTH".equals(category.getCategoryType()))
                .map(Category::getCategoryName)
                .distinct()
                .sorted()
                .toList());
        sourceFilterBox.setItems(FXCollections.observableArrayList(sources));
        sourceFilterBox.getSelectionModel().select("All sources");
    }

    private void loadRows() {
        allRows.clear();
        for (DatabaseHandler.IncomeDraftRecord draft : database.listIncomeDrafts(500)) {
            allRows.add(IncomeRecordRow.fromDraft(draft));
        }
        for (FinanceTransaction transaction : database.listRecentTransactions(1000)) {
            if ("INCOME".equals(transaction.getTransactionType())) {
                allRows.add(IncomeRecordRow.fromTransaction(transaction));
            }
        }
        allRows.sort(Comparator.comparing(IncomeRecordRow::getDate, Comparator.nullsLast(String::compareTo)).reversed()
                .thenComparing(Comparator.comparingInt(IncomeRecordRow::getRecordId).reversed()));
    }

    private List<String> actionsFor(IncomeRecordRow row) {
        if (row.isDraft()) {
            return List.of("Edit Draft", "Post Income");
        }
        if ("Posted".equals(row.getStatus())) {
            return List.of("Request Correction", "Cancel Income", "Create Reversal", "Copy as New");
        }
        if ("Cancelled".equals(row.getStatus()) || "Reversed".equals(row.getStatus())) {
            return List.of("View History", "Create Corrected Record");
        }
        return List.of("View History");
    }

    private void handleAction(IncomeRecordRow row, String action) {
        try {
            switch (action) {
                case "Edit Draft" -> editDraft(row);
                case "Post Income" -> postDraft(row);
                case "Request Correction" -> requestCorrection(row);
                case "Cancel Income" -> cancelIncome(row);
                case "Create Reversal" -> createReversal(row);
                case "Copy as New" -> copyAsNew(row, "Copied from posted income");
                case "Create Corrected Record" -> createCorrectedRecord(row);
                case "View History" -> viewHistory(row);
                default -> detailsArea.setText("No action selected.");
            }
        } catch (RuntimeException exception) {
            UiAlerts.error("Income action failed", exception);
        }
    }

    private void editDraft(IncomeRecordRow row) {
        DatabaseHandler.IncomeDraftRecord draft = database.findIncomeDraft(row.getRecordId());
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Income Draft");
        ButtonType saveButton = new ButtonType("Save Correction", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveButton);

        ComboBox<Account> accountBox = new ComboBox<>(FXCollections.observableArrayList(activeAccounts()));
        ComboBox<Category> categoryBox = new ComboBox<>();
        ComboBox<String> paymentBox = new ComboBox<>(FXCollections.observableArrayList(database.listPaymentMethodSuggestions()));
        ComboBox<Person> personBox = new ComboBox<>(FXCollections.observableArrayList(database.listPeople()));
        ComboBox<Project> projectBox = new ComboBox<>(FXCollections.observableArrayList(database.listProjects()));
        TextField amountField = new TextField(String.format(Locale.ENGLISH, "%.2f", draft.amount()));
        DatePicker datePicker = new DatePicker(LocalDate.parse(draft.incomeDate()));
        TextField referenceField = new TextField(safe(draft.referenceNumber()));
        TextField attachmentField = new TextField(safe(draft.attachmentPath()));
        TextArea descriptionArea = new TextArea(safe(draft.description()));
        descriptionArea.setPrefRowCount(3);

        CategoryInput.configure(categoryBox);
        CategoryInput.setItems(categoryBox, incomeCategories());
        CategoryInput.selectByName(categoryBox, draft.categoryName());
        configurePersonBox(personBox);
        selectAccount(accountBox, draft.accountId());
        selectPerson(personBox, draft.personId());
        selectProject(projectBox, draft.projectId());
        paymentBox.setEditable(true);
        paymentBox.setValue(safe(draft.paymentMethod()).isBlank() ? "Bank Transfer" : draft.paymentMethod());

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        addField(grid, "Receiving Account", accountBox, 0);
        addField(grid, "Income Source", categoryBox, 1);
        addField(grid, "Amount", amountField, 2);
        addField(grid, "Income Date", datePicker, 3);
        addField(grid, "Payment Method", paymentBox, 4);
        addField(grid, "Reference", referenceField, 5);
        addField(grid, "Person or Organisation", personBox, 6);
        addField(grid, "Project Link", projectBox, 7);
        addField(grid, "Attachment or Receipt", attachmentField, 8);
        addField(grid, "Description", descriptionArea, 9);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || !saveButton.equals(result.get())) {
            return;
        }
        Account account = accountBox.getValue();
        if (account == null) {
            throw new IllegalArgumentException("Select a receiving account.");
        }
        int draftId = database.saveIncomeDraft(
                draft.id(),
                account.getId(),
                CategoryInput.resolveCategoryId(database, categoryBox, "INCOME"),
                projectBox.getValue() == null ? null : projectBox.getValue().getId(),
                resolvePersonId(personBox),
                draft.expectedIncomeId(),
                parseAmount(amountField),
                account.getCurrency(),
                datePicker.getValue(),
                paymentMethod(paymentBox),
                value(referenceField),
                value(descriptionArea),
                value(attachmentField)
        );
        search();
        detailsArea.setText("Income draft #" + draftId + " updated.");
    }

    private void postDraft(IncomeRecordRow row) {
        DatabaseHandler.IncomeDraftRecord draft = database.findIncomeDraft(row.getRecordId());
        if (draft.categoryId() == null) {
            throw new IllegalArgumentException("Open and fix the draft before posting. Income source is required.");
        }
        if (safe(draft.paymentMethod()).isBlank()) {
            throw new IllegalArgumentException("Open and fix the draft before posting. Payment method is required.");
        }
        LocalDate incomeDate = LocalDate.parse(draft.incomeDate());
        if (database.hasSimilarIncomeTransaction(draft.accountId(), draft.amount(), incomeDate, draft.description(), draft.referenceNumber())
                && !UiAlerts.confirm("Possible duplicate income", "A similar income record already exists. Post this draft anyway?")) {
            return;
        }
        int transactionId = database.recordIncomeTransaction(
                draft.accountId(),
                draft.categoryId(),
                draft.projectId(),
                draft.personId(),
                draft.expectedIncomeId(),
                draft.amount(),
                draft.currency(),
                incomeDate,
                descriptionWithAttachment(draft.description(), draft.attachmentPath()),
                draft.paymentMethod(),
                draft.referenceNumber()
        );
        database.markIncomeDraftPosted(draft.id(), transactionId);
        DataRefreshBus.notifyDataChanged();
        search();
        detailsArea.setText("Income draft #" + draft.id() + " posted as transaction #" + transactionId + ".");
    }

    private void requestCorrection(IncomeRecordRow row) {
        String reason = askReason("Request Correction", "Reason for the correction request");
        if (reason == null) {
            return;
        }
        database.recordSystemLog(
                "Income",
                "Correction Requested",
                "WARNING",
                "Correction requested for income transaction " + row.getRecordId() + ". Reason: " + reason
        );
        detailsArea.setText("Correction request recorded for income transaction #" + row.getRecordId() + ".");
    }

    private void cancelIncome(IncomeRecordRow row) {
        String reason = askReason("Cancel Income", "Reason for cancellation");
        if (reason == null) {
            return;
        }
        if (UserSession.isAdminOrSuperAdmin()) {
            database.updateRecordLifecycleStatus("Transaction", row.getRecordId(), "CANCELLED", reason);
            DataRefreshBus.notifyDataChanged();
            search();
            detailsArea.setText("Income transaction #" + row.getRecordId() + " was cancelled.");
            return;
        }
        database.recordSystemLog(
                "Income",
                "Income Cancellation Requested",
                "WARNING",
                "Cancellation requested for income transaction " + row.getRecordId() + ". Reason: " + reason
        );
        detailsArea.setText("Cancellation request recorded for income transaction #" + row.getRecordId() + ".");
    }

    private void createReversal(IncomeRecordRow row) {
        String reason = askReason("Create Reversal", "Reason for reversal");
        if (reason == null) {
            return;
        }
        if (UserSession.isSuperAdmin()) {
            int reversalId = database.createIncomeReversal(row.getRecordId(), reason);
            DataRefreshBus.notifyDataChanged();
            search();
            detailsArea.setText("Income transaction #" + row.getRecordId() + " was reversed by transaction #" + reversalId + ".");
            return;
        }
        database.recordSystemLog(
                "Income",
                "Income Reversal Requested",
                "WARNING",
                "Reversal requested for income transaction " + row.getRecordId() + ". Reason: " + reason
        );
        detailsArea.setText("Reversal request recorded for income transaction #" + row.getRecordId() + ".");
    }

    private void copyAsNew(IncomeRecordRow row, String defaultReason) {
        String reason = askOptionalReason("Copy Income", "Reason");
        if (reason == null) {
            return;
        }
        if (reason.isBlank()) {
            reason = defaultReason;
        }
        int draftId = database.createIncomeDraftFromTransaction(row.getRecordId(), reason);
        search();
        detailsArea.setText("Income draft #" + draftId + " was created from transaction #" + row.getRecordId() + ".");
    }

    private void createCorrectedRecord(IncomeRecordRow row) {
        String reason = askReason("Create Corrected Record", "Reason for corrected record");
        if (reason == null) {
            return;
        }
        if (UserSession.isAdminOrSuperAdmin()) {
            int draftId = database.createIncomeDraftFromTransaction(row.getRecordId(), reason);
            search();
            detailsArea.setText("Corrected income draft #" + draftId + " was created.");
            return;
        }
        database.recordSystemLog(
                "Income",
                "Corrected Income Requested",
                "WARNING",
                "Corrected record requested for income transaction " + row.getRecordId() + ". Reason: " + reason
        );
        detailsArea.setText("Corrected-record request logged for income transaction #" + row.getRecordId() + ".");
    }

    private void viewHistory(IncomeRecordRow row) {
        String id = String.valueOf(row.getRecordId());
        List<SystemLogRecord> logs = database.listSystemLogHistory(100).stream()
                .filter(log -> safe(log.getModuleName()).equals("Income") || safe(log.getModuleName()).equals("Data And Records"))
                .filter(log -> safe(log.getDetails()).contains(id))
                .limit(12)
                .toList();
        if (logs.isEmpty()) {
            detailsArea.setText("No recent audit entries found for record #" + row.getRecordId() + ".");
            return;
        }
        StringBuilder history = new StringBuilder("Recent audit history for record #").append(row.getRecordId()).append("\n\n");
        for (SystemLogRecord log : logs) {
            history.append(log.getCreatedAt())
                    .append(" | ")
                    .append(log.getActionName())
                    .append(" | ")
                    .append(log.getDetails())
                    .append('\n');
        }
        detailsArea.setText(history.toString());
    }

    private String draftDetails(DatabaseHandler.IncomeDraftRecord draft) {
        return """
                Income Draft #%d

                Amount: %s
                Date: %s
                Receiving account: %s
                Income source: %s
                Payment method: %s
                Reference: %s
                Description: %s
                Person or organisation: %s
                Project: %s
                Expected-income link: %s
                Attachment: %s
                Status: %s
                Created: %s
                Updated: %s
                """.formatted(
                draft.id(),
                MoneyUtil.mwk(draft.amount()),
                dash(draft.incomeDate()),
                dash(draft.accountName()),
                dash(draft.categoryName()),
                dash(draft.paymentMethod()),
                dash(draft.referenceNumber()),
                dash(draft.description()),
                dash(draft.personName()),
                dash(draft.projectName()),
                draft.expectedIncomeId() == null ? "-" : "#" + draft.expectedIncomeId(),
                dash(draft.attachmentPath()),
                dash(draft.status()),
                dash(draft.createdAt()),
                dash(draft.updatedAt())
        );
    }

    private String transactionDetails(FinanceTransaction transaction) {
        return """
                Income Transaction #%d

                Amount: %s
                Date: %s
                Receiving account: %s
                Income source: %s
                Payment method: %s
                Reference: %s
                Description: %s
                Person or organisation: %s
                Project: %s
                Status: %s
                Creation and modification history: See Audit and History.
                """.formatted(
                transaction.getId(),
                MoneyUtil.mwk(transaction.getAmount()),
                dash(transaction.getTransactionDate()),
                dash(transaction.getAccountName()),
                dash(transaction.getCategoryName()),
                dash(transaction.getPaymentMethod()),
                dash(transaction.getReferenceNumber()),
                dash(transaction.getDescription()),
                dash(transaction.getPersonName()),
                dash(transaction.getProjectName()),
                statusLabel(transaction.getTransactionStatus())
        );
    }

    private boolean matchesPeriod(String dateValue) {
        LocalDate date = parseDate(dateValue);
        if (date == null) {
            return true;
        }
        return switch (selected(periodBox)) {
            case "Last month" -> YearMonth.from(date).equals(YearMonth.now().minusMonths(1));
            case "This year" -> date.getYear() == LocalDate.now().getYear();
            case "All periods" -> true;
            default -> YearMonth.from(date).equals(YearMonth.now());
        };
    }

    private IncomeRecordRow selectedRow(String action) {
        IncomeRecordRow selected = incomeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select an income record to " + action + ".");
        }
        return selected;
    }

    private String askReason(String title, String prompt) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt + ":");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }
        String reason = result.get() == null ? "" : result.get().trim();
        if (reason.isBlank()) {
            throw new IllegalArgumentException("Reason is required.");
        }
        return reason;
    }

    private String askOptionalReason(String title, String prompt) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt + ":");
        Optional<String> result = dialog.showAndWait();
        return result.map(value -> value == null ? "" : value.trim()).orElse(null);
    }

    private void addField(GridPane grid, String label, javafx.scene.Node control, int row) {
        javafx.scene.control.Label fieldLabel = new javafx.scene.control.Label(label);
        fieldLabel.getStyleClass().add("field-label");
        grid.add(fieldLabel, 0, row);
        grid.add(control, 1, row);
    }

    private List<Account> activeAccounts() {
        return database.listAccounts().stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(safe(account.getStatus())))
                .toList();
    }

    private List<Category> incomeCategories() {
        return database.listCategories().stream()
                .filter(category -> "INCOME".equals(category.getCategoryType()) || "BOTH".equals(category.getCategoryType()))
                .toList();
    }

    private void configurePersonBox(ComboBox<Person> box) {
        box.setEditable(true);
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(Person person) {
                return person == null ? "" : person.getFullName();
            }

            @Override
            public Person fromString(String value) {
                String name = value == null ? "" : value.trim();
                if (name.isBlank()) {
                    return null;
                }
                return box.getItems().stream()
                        .filter(person -> person.getFullName().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(new Person(-1, name, "", "", ""));
            }
        });
    }

    private Integer resolvePersonId(ComboBox<Person> box) {
        String typedName = box.getEditor().getText();
        Person selected = box.getValue();
        String name = typedName == null || typedName.isBlank()
                ? selected == null ? "" : selected.getFullName()
                : typedName;
        return database.findOrCreatePerson(name);
    }

    private void selectAccount(ComboBox<Account> box, int accountId) {
        box.getItems().stream()
                .filter(account -> account.getId() == accountId)
                .findFirst()
                .ifPresent(box::setValue);
    }

    private void selectPerson(ComboBox<Person> box, Integer personId) {
        if (personId == null) {
            return;
        }
        box.getItems().stream()
                .filter(person -> person.getId() == personId)
                .findFirst()
                .ifPresent(box::setValue);
    }

    private void selectProject(ComboBox<Project> box, Integer projectId) {
        if (projectId == null) {
            return;
        }
        box.getItems().stream()
                .filter(project -> project.getId() == projectId)
                .findFirst()
                .ifPresent(box::setValue);
    }

    private double parseAmount(TextField field) {
        String text = value(field).replace(",", "");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Amount is required.");
        }
        try {
            double amount = Double.parseDouble(text);
            if (amount <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero.");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid amount.");
        }
    }

    private String paymentMethod(ComboBox<String> box) {
        String value = box.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = box.getValue();
        }
        return value == null ? "" : value.trim();
    }

    private String descriptionWithAttachment(String description, String attachmentPath) {
        if (safe(attachmentPath).isBlank()) {
            return safe(description);
        }
        String base = safe(description).isBlank() ? "" : safe(description) + System.lineSeparator();
        return base + "Attachment or receipt: " + attachmentPath.trim();
    }

    private String statusLabel(String status) {
        return switch (safe(status).toUpperCase(Locale.ENGLISH)) {
            case "OPEN" -> "Draft";
            case "CANCELLED" -> "Cancelled";
            case "REVERSED" -> "Reversed";
            default -> "Posted";
        };
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String selected(ComboBox<String> box) {
        return box.getValue() == null ? "" : box.getValue();
    }

    private String value(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String value(TextArea area) {
        return area.getText() == null ? "" : area.getText().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String dash(String value) {
        return safe(value).isBlank() ? "-" : value.trim();
    }

    public static class IncomeRecordRow {
        private final int recordId;
        private final String date;
        private final String description;
        private final String source;
        private final String account;
        private final double amount;
        private final String status;
        private final String reference;
        private final DatabaseHandler.IncomeDraftRecord draft;
        private final FinanceTransaction transaction;

        private IncomeRecordRow(
                int recordId,
                String date,
                String description,
                String source,
                String account,
                double amount,
                String status,
                String reference,
                DatabaseHandler.IncomeDraftRecord draft,
                FinanceTransaction transaction
        ) {
            this.recordId = recordId;
            this.date = date;
            this.description = description;
            this.source = source;
            this.account = account;
            this.amount = amount;
            this.status = status;
            this.reference = reference;
            this.draft = draft;
            this.transaction = transaction;
        }

        static IncomeRecordRow fromDraft(DatabaseHandler.IncomeDraftRecord draft) {
            return new IncomeRecordRow(
                    draft.id(),
                    safeStatic(draft.incomeDate()),
                    fallback(draft.description(), "Income draft #" + draft.id()),
                    fallback(draft.categoryName(), "Uncategorized"),
                    fallback(draft.accountName(), "Unknown account"),
                    draft.amount(),
                    "Draft",
                    safeStatic(draft.referenceNumber()),
                    draft,
                    null
            );
        }

        static IncomeRecordRow fromTransaction(FinanceTransaction transaction) {
            return new IncomeRecordRow(
                    transaction.getId(),
                    safeStatic(transaction.getTransactionDate()),
                    fallback(transaction.getDescription(), "Income transaction #" + transaction.getId()),
                    fallback(transaction.getCategoryName(), "Uncategorized"),
                    fallback(transaction.getAccountName(), "Unknown account"),
                    transaction.getAmount(),
                    statusLabelStatic(transaction.getTransactionStatus()),
                    safeStatic(transaction.getReferenceNumber()),
                    null,
                    transaction
            );
        }

        public int getRecordId() {
            return recordId;
        }

        public String getDate() {
            return date;
        }

        public String getDescription() {
            return description;
        }

        public String getSource() {
            return source;
        }

        public String getAccount() {
            return account;
        }

        public double getAmount() {
            return amount;
        }

        public String getStatus() {
            return status;
        }

        public String getReference() {
            return reference;
        }

        public DatabaseHandler.IncomeDraftRecord draft() {
            return draft;
        }

        public FinanceTransaction transaction() {
            return transaction;
        }

        public boolean isDraft() {
            return draft != null;
        }

        private static String fallback(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        private static String safeStatic(String value) {
            return value == null ? "" : value.trim();
        }

        private static String statusLabelStatic(String status) {
            return switch (safeStatic(status).toUpperCase(Locale.ENGLISH)) {
                case "OPEN" -> "Draft";
                case "CANCELLED" -> "Cancelled";
                case "REVERSED" -> "Reversed";
                default -> "Posted";
            };
        }
    }
}
