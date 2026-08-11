package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Person;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ExpectedIncomeController {
    @FXML private Label upcomingLabel;
    @FXML private Label dueTodayLabel;
    @FXML private Label overdueLabel;
    @FXML private Label expectedMonthLabel;
    @FXML private Label receivedMonthLabel;
    @FXML private DatePicker expectedDatePicker;
    @FXML private TextField amountField;
    @FXML private TextField currencyField;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private ComboBox<Account> accountBox;
    @FXML private ComboBox<Person> personBox;
    @FXML private TextField referenceField;
    @FXML private ComboBox<String> frequencyBox;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<String> confidenceBox;
    @FXML private TextArea notesArea;
    @FXML private TableView<DatabaseHandler.ExpectedIncomeRecord> expectedIncomeTable;
    @FXML private TableColumn<DatabaseHandler.ExpectedIncomeRecord, String> dateColumn;
    @FXML private TableColumn<DatabaseHandler.ExpectedIncomeRecord, String> sourceColumn;
    @FXML private TableColumn<DatabaseHandler.ExpectedIncomeRecord, String> accountColumn;
    @FXML private TableColumn<DatabaseHandler.ExpectedIncomeRecord, String> amountColumn;
    @FXML private TableColumn<DatabaseHandler.ExpectedIncomeRecord, String> frequencyColumn;
    @FXML private TableColumn<DatabaseHandler.ExpectedIncomeRecord, String> statusColumn;
    @FXML private TextArea detailsArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        expectedDatePicker.setValue(LocalDate.now().plusDays(1));
        frequencyBox.setItems(FXCollections.observableArrayList("One time", "Weekly", "Monthly", "Quarterly", "Yearly", "Custom"));
        frequencyBox.getSelectionModel().select("One time");
        confidenceBox.setItems(FXCollections.observableArrayList("Certain", "Likely", "Possible", "Uncertain"));
        confidenceBox.getSelectionModel().select("Likely");
        CategoryInput.configure(categoryBox);
        configurePersonBox();
        accountBox.valueProperty().addListener((observable, oldValue, newValue) -> updateCurrency());
        configureTable();
        refresh();
    }

    @FXML
    private void saveExpectedIncome() {
        try {
            Account account = accountBox.getValue();
            if (account == null) {
                throw new IllegalArgumentException("Select the expected account.");
            }
            Integer categoryId = CategoryInput.resolveCategoryId(database, categoryBox, "INCOME");
            if (categoryId == null) {
                throw new IllegalArgumentException("Select or enter the income source.");
            }
            int expectedId = database.saveExpectedIncome(
                    null,
                    expectedDate(),
                    parseAmount(amountField),
                    account.getCurrency(),
                    account.getId(),
                    categoryId,
                    resolvePersonId(),
                    value(referenceField),
                    selected(frequencyBox, "One time"),
                    endDatePicker.getValue(),
                    selected(confidenceBox, "Likely"),
                    value(notesArea),
                    "Upcoming"
            );
            resetForm();
            refresh();
            detailsArea.setText("Expected income #" + expectedId + " saved. It does not change account balances until marked received.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save expected income", exception);
        }
    }

    @FXML
    private void markAsReceived() {
        DatabaseHandler.ExpectedIncomeRecord selected = expectedIncomeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select expected income to mark as received.");
            return;
        }
        if ("Received".equalsIgnoreCase(safe(selected.status())) || "Cancelled".equalsIgnoreCase(safe(selected.status()))) {
            UiAlerts.info("Select an upcoming, due, overdue or partially received expected income record.");
            return;
        }
        Optional<ReceivedIncome> result = receivedIncomeDialog(selected);
        if (result.isEmpty()) {
            return;
        }
        try {
            ReceivedIncome received = result.get();
            Integer categoryId = selected.categoryId() == null
                    ? database.findOrCreateCategory("Other", "INCOME").getId()
                    : selected.categoryId();
            String description = "Received expected income #" + selected.id()
                    + " - " + dash(selected.categoryName())
                    + (safe(selected.notes()).isBlank() ? "" : System.lineSeparator() + selected.notes());
            if (database.hasSimilarIncomeTransaction(
                    selected.accountId(),
                    received.amount(),
                    received.receivedDate(),
                    description,
                    received.referenceNumber()
            ) && !UiAlerts.confirm(
                    "Possible duplicate income",
                    "A similar income record already exists. Mark this expected income as received anyway?"
            )) {
                return;
            }
            int transactionId = database.recordIncomeTransaction(
                    selected.accountId(),
                    categoryId,
                    null,
                    selected.personId(),
                    selected.id(),
                    received.amount(),
                    selected.currency(),
                    received.receivedDate(),
                    description,
                    received.paymentMethod(),
                    received.referenceNumber()
            );
            DataRefreshBus.notifyDataChanged();
            refresh();
            detailsArea.setText("""
                    Expected income marked as received.

                    Expected income: #%d
                    Transaction: #%d
                    Actual amount: %s
                    Received date: %s
                    """.formatted(selected.id(), transactionId, MoneyUtil.mwk(received.amount()), received.receivedDate()));
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to mark expected income received", exception);
        }
    }

    private void configureTable() {
        dateColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().expectedDate()));
        sourceColumn.setCellValueFactory(cell -> new SimpleStringProperty(dash(cell.getValue().categoryName())));
        accountColumn.setCellValueFactory(cell -> new SimpleStringProperty(dash(cell.getValue().accountName())));
        amountColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().expectedAmount())));
        frequencyColumn.setCellValueFactory(cell -> new SimpleStringProperty(dash(cell.getValue().repeatFrequency())));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(computedStatus(cell.getValue())));
        expectedIncomeTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                detailsArea.setText(recordDetails(newValue));
            }
        });
        TableActions.configureScrollableTable(expectedIncomeTable);
    }

    private void refresh() {
        Account selectedAccount = accountBox.getValue();
        accountBox.setItems(FXCollections.observableArrayList(database.listAccounts().stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(safe(account.getStatus())))
                .toList()));
        if (selectedAccount != null) {
            accountBox.getItems().stream()
                    .filter(account -> account.getId() == selectedAccount.getId())
                    .findFirst()
                    .ifPresent(accountBox::setValue);
        }
        CategoryInput.setItems(categoryBox, database.listCategories().stream()
                .filter(category -> "INCOME".equals(category.getCategoryType()) || "BOTH".equals(category.getCategoryType()))
                .toList());
        personBox.setItems(FXCollections.observableArrayList(database.listPeople()));
        updateCurrency();
        List<DatabaseHandler.ExpectedIncomeRecord> records = database.listExpectedIncomeRecords(500);
        expectedIncomeTable.setItems(FXCollections.observableArrayList(records));
        refreshSummary(records);
    }

    private void refreshSummary(List<DatabaseHandler.ExpectedIncomeRecord> records) {
        long upcoming = records.stream().filter(record -> "Upcoming".equals(computedStatus(record))).count();
        long dueToday = records.stream().filter(record -> "Due Today".equals(computedStatus(record))).count();
        long overdue = records.stream().filter(record -> "Overdue".equals(computedStatus(record))).count();
        YearMonth thisMonth = YearMonth.now();
        double expectedThisMonth = records.stream()
                .filter(record -> !"Cancelled".equalsIgnoreCase(safe(record.status())))
                .filter(record -> monthOf(record.expectedDate()).equals(thisMonth))
                .mapToDouble(DatabaseHandler.ExpectedIncomeRecord::expectedAmount)
                .sum();
        double receivedThisMonth = database.listRecentTransactions(500).stream()
                .filter(transaction -> "INCOME".equals(transaction.getTransactionType()))
                .filter(transaction -> !"CANCELLED".equalsIgnoreCase(safe(transaction.getTransactionStatus())))
                .filter(transaction -> !"REVERSED".equalsIgnoreCase(safe(transaction.getTransactionStatus())))
                .filter(transaction -> monthOf(transaction.getTransactionDate()).equals(thisMonth))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();

        upcomingLabel.setText(String.valueOf(upcoming));
        dueTodayLabel.setText(String.valueOf(dueToday));
        overdueLabel.setText(String.valueOf(overdue));
        expectedMonthLabel.setText(MoneyUtil.mwk(expectedThisMonth));
        receivedMonthLabel.setText(MoneyUtil.mwk(receivedThisMonth));
        if (expectedIncomeTable.getSelectionModel().getSelectedItem() == null) {
            detailsArea.setText(smartSummary(records, expectedThisMonth, receivedThisMonth, overdue, dueToday));
        }
    }

    private Optional<ReceivedIncome> receivedIncomeDialog(DatabaseHandler.ExpectedIncomeRecord record) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Mark Expected Income as Received");
        ButtonType saveButton = new ButtonType("Save Received Income", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveButton);

        TextField amountField = new TextField(String.format(Locale.ENGLISH, "%.2f", record.expectedAmount()));
        DatePicker receivedDatePicker = new DatePicker(LocalDate.now());
        ComboBox<String> paymentMethodBox = new ComboBox<>(FXCollections.observableArrayList(database.listPaymentMethodSuggestions()));
        paymentMethodBox.setEditable(true);
        paymentMethodBox.setValue("Bank Transfer");
        TextField referenceField = new TextField(safe(record.referenceNumber()));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        addField(grid, "Actual Amount", amountField, 0);
        addField(grid, "Received Date", receivedDatePicker, 1);
        addField(grid, "Payment Method", paymentMethodBox, 2);
        addField(grid, "Reference", referenceField, 3);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || !saveButton.equals(result.get())) {
            return Optional.empty();
        }
        LocalDate receivedDate = receivedDatePicker.getValue();
        if (receivedDate == null) {
            throw new IllegalArgumentException("Received date is required.");
        }
        String paymentMethod = paymentMethod(paymentMethodBox);
        if (paymentMethod.isBlank()) {
            throw new IllegalArgumentException("Payment method is required.");
        }
        return Optional.of(new ReceivedIncome(
                parseAmount(amountField),
                receivedDate,
                paymentMethod,
                value(referenceField)
        ));
    }

    private String smartSummary(
            List<DatabaseHandler.ExpectedIncomeRecord> records,
            double expectedThisMonth,
            double receivedThisMonth,
            long overdue,
            long dueToday
    ) {
        StringBuilder summary = new StringBuilder();
        if (overdue > 0) {
            double overdueTotal = records.stream()
                    .filter(record -> "Overdue".equals(computedStatus(record)))
                    .mapToDouble(DatabaseHandler.ExpectedIncomeRecord::expectedAmount)
                    .sum();
            summary.append(overdue)
                    .append(" expected income item(s) are overdue, with a combined value of ")
                    .append(MoneyUtil.mwk(overdueTotal))
                    .append(".\n");
        } else if (dueToday > 0) {
            summary.append(dueToday).append(" expected income item(s) are due today.\n");
        } else {
            summary.append("No expected income is overdue.\n");
        }
        if (expectedThisMonth > 0) {
            summary.append("Expected income for this month is ")
                    .append(MoneyUtil.mwk(expectedThisMonth))
                    .append(", while confirmed income is ")
                    .append(MoneyUtil.mwk(receivedThisMonth))
                    .append(".");
        } else {
            summary.append("No expected income has been scheduled for this month.");
        }
        return summary.toString();
    }

    private String recordDetails(DatabaseHandler.ExpectedIncomeRecord record) {
        return """
                Expected Income #%d

                Expected date: %s
                Expected amount: %s
                Account: %s
                Income source: %s
                Person or organisation: %s
                Reference: %s
                Repeat frequency: %s
                End date: %s
                Confidence: %s
                Status: %s
                Linked transaction: %s
                Notes: %s
                """.formatted(
                record.id(),
                dash(record.expectedDate()),
                MoneyUtil.mwk(record.expectedAmount()),
                dash(record.accountName()),
                dash(record.categoryName()),
                dash(record.personName()),
                dash(record.referenceNumber()),
                dash(record.repeatFrequency()),
                dash(record.endDate()),
                dash(record.confidence()),
                computedStatus(record),
                record.linkedTransactionId() == null ? "-" : "#" + record.linkedTransactionId(),
                dash(record.notes())
        );
    }

    private LocalDate expectedDate() {
        LocalDate date = expectedDatePicker.getValue();
        if (date == null) {
            throw new IllegalArgumentException("Expected date is required.");
        }
        return date;
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

    private Integer resolvePersonId() {
        String typedName = personBox.getEditor().getText();
        Person selected = personBox.getValue();
        String name = typedName == null || typedName.isBlank()
                ? selected == null ? "" : selected.getFullName()
                : typedName;
        return database.findOrCreatePerson(name);
    }

    private void configurePersonBox() {
        personBox.setConverter(new StringConverter<>() {
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
                return personBox.getItems().stream()
                        .filter(person -> person.getFullName().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(new Person(-1, name, "", "", ""));
            }
        });
    }

    private void updateCurrency() {
        Account account = accountBox.getValue();
        currencyField.setText(account == null || safe(account.getCurrency()).isBlank()
                ? "MWK"
                : account.getCurrency().trim().toUpperCase(Locale.ENGLISH));
    }

    private void resetForm() {
        expectedDatePicker.setValue(LocalDate.now().plusDays(1));
        amountField.clear();
        referenceField.clear();
        notesArea.clear();
        personBox.setValue(null);
        personBox.getEditor().clear();
        frequencyBox.getSelectionModel().select("One time");
        confidenceBox.getSelectionModel().select("Likely");
        endDatePicker.setValue(null);
        updateCurrency();
    }

    private void addField(GridPane grid, String label, javafx.scene.Node control, int row) {
        javafx.scene.control.Label fieldLabel = new javafx.scene.control.Label(label);
        fieldLabel.getStyleClass().add("field-label");
        grid.add(fieldLabel, 0, row);
        grid.add(control, 1, row);
    }

    private String computedStatus(DatabaseHandler.ExpectedIncomeRecord record) {
        String status = safe(record.status());
        if ("Received".equalsIgnoreCase(status)
                || "Cancelled".equalsIgnoreCase(status)
                || "Partially Received".equalsIgnoreCase(status)) {
            return status;
        }
        LocalDate date = parseDate(record.expectedDate());
        if (date == null) {
            return status.isBlank() ? "Upcoming" : status;
        }
        if (date.isBefore(LocalDate.now())) {
            return "Overdue";
        }
        if (date.equals(LocalDate.now())) {
            return "Due Today";
        }
        return "Upcoming";
    }

    private YearMonth monthOf(String dateValue) {
        LocalDate date = parseDate(dateValue);
        return date == null ? YearMonth.of(1900, 1) : YearMonth.from(date);
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String paymentMethod(ComboBox<String> box) {
        String value = box.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = box.getValue();
        }
        return value == null ? "" : value.trim();
    }

    private String selected(ComboBox<String> box, String fallback) {
        return box.getValue() == null || box.getValue().isBlank() ? fallback : box.getValue();
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

    private record ReceivedIncome(double amount, LocalDate receivedDate, String paymentMethod, String referenceNumber) {
    }
}
