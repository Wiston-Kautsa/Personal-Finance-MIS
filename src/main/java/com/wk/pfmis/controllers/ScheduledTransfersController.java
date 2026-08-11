package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.ScheduledTransferRecord;
import com.wk.pfmis.db.DatabaseHandler.TransferPostingResult;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScheduledTransfersController {
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    @FXML private TextField transferNameField;
    @FXML private ComboBox<String> statusBox;
    @FXML private ComboBox<Account> fromAccountBox;
    @FXML private ComboBox<Account> toAccountBox;
    @FXML private Label fromBalanceLabel;
    @FXML private Label toBalanceLabel;
    @FXML private TextField amountField;
    @FXML private TextField currencyField;
    @FXML private TextField feeField;
    @FXML private ComboBox<Category> feeCategoryBox;
    @FXML private DatePicker firstTransferDatePicker;
    @FXML private ComboBox<String> frequencyBox;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<String> reminderPeriodBox;
    @FXML private ComboBox<String> transferMethodBox;
    @FXML private CheckBox confirmationRequiredBox;
    @FXML private TextArea notesArea;
    @FXML private Label scheduleCheckLabel;
    @FXML private TableView<ScheduledTransferRecord> schedulesTable;
    @FXML private TableColumn<ScheduledTransferRecord, String> nameColumn;
    @FXML private TableColumn<ScheduledTransferRecord, String> nextDueColumn;
    @FXML private TableColumn<ScheduledTransferRecord, String> fromColumn;
    @FXML private TableColumn<ScheduledTransferRecord, String> toColumn;
    @FXML private TableColumn<ScheduledTransferRecord, String> amountColumn;
    @FXML private TableColumn<ScheduledTransferRecord, String> frequencyColumn;
    @FXML private TableColumn<ScheduledTransferRecord, String> statusColumn;
    @FXML private TextArea detailsArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Account> accounts = List.of();
    private Integer editingScheduleId;
    private Integer editingLastTransactionId;

    @FXML
    public void initialize() {
        MONEY_FORMAT.setMinimumFractionDigits(2);
        MONEY_FORMAT.setMaximumFractionDigits(2);

        statusBox.setItems(FXCollections.observableArrayList("Upcoming", "Due Today", "Overdue", "Completed", "Paused", "Cancelled"));
        frequencyBox.setItems(FXCollections.observableArrayList("One time", "Weekly", "Monthly", "Quarterly", "Half-yearly", "Yearly", "Custom"));
        reminderPeriodBox.setItems(FXCollections.observableArrayList("Same day", "1 day before", "3 days before", "1 week before", "No reminder"));
        statusBox.getSelectionModel().select("Upcoming");
        frequencyBox.getSelectionModel().select("Monthly");
        reminderPeriodBox.getSelectionModel().select("Same day");
        firstTransferDatePicker.setValue(LocalDate.now());
        feeField.setText("0");

        CategoryInput.configure(feeCategoryBox);
        fromAccountBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshAccountState());
        toAccountBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshAccountState());

        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().transferName()));
        nextDueColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().nextDueDate()));
        fromColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().fromAccountName()));
        toColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().toAccountName()));
        amountColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(cell.getValue().currency(), cell.getValue().amount())));
        frequencyColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().frequency()));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(statusForDisplay(cell.getValue())));
        TableActions.configureScrollableTable(schedulesTable);
        schedulesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> loadSelectedSchedule(newValue));

        refresh();
    }

    @FXML
    private void saveSchedule() {
        try {
            ScheduleForm form = readForm();
            int id = database.saveScheduledTransfer(
                    editingScheduleId,
                    form.transferName(),
                    form.fromAccount().getId(),
                    form.toAccount().getId(),
                    form.amount(),
                    form.currency(),
                    form.transferFee(),
                    form.feeCategoryId(),
                    form.firstTransferDate(),
                    form.nextDueDate(),
                    form.frequency(),
                    form.endDate(),
                    form.reminderPeriod(),
                    form.transferMethod(),
                    form.confirmationRequired(),
                    form.notes(),
                    form.status(),
                    editingLastTransactionId
            );
            editingScheduleId = id;
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Scheduled transfer saved.");
        } catch (IllegalArgumentException exception) {
            UiAlerts.info(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save scheduled transfer", exception);
        }
    }

    @FXML
    private void transferNow() {
        ScheduledTransferRecord selected = schedulesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a scheduled transfer to post.");
            return;
        }
        if ("Cancelled".equals(selected.status()) || "Paused".equals(selected.status()) || "Completed".equals(selected.status())) {
            UiAlerts.info("Only active scheduled transfers can be posted.");
            return;
        }
        if (!sameCurrency(selected.fromCurrency(), selected.toCurrency())) {
            UiAlerts.info("This schedule uses different account currencies. Open Transfer Money to review the exchange rate before posting.");
            return;
        }
        if (!UiAlerts.confirm(
                "Transfer Now",
                "Post " + money(selected.currency(), selected.amount()) + " from "
                        + selected.fromAccountName() + " to " + selected.toAccountName() + "?"
        )) {
            return;
        }

        try {
            TransferPostingResult result = database.recordTransferWithFee(
                    selected.fromAccountId(),
                    selected.toAccountId(),
                    selected.amount(),
                    selected.amount(),
                    selected.transferFee(),
                    selected.feeCategoryId(),
                    LocalDate.now(),
                    "Posted from scheduled transfer: " + selected.transferName()
                            + (selected.notes() == null || selected.notes().isBlank() ? "" : "\n" + selected.notes()),
                    selected.transferMethod(),
                    ""
            );
            LocalDate nextDueDate = nextDueDate(selected);
            database.updateScheduledTransferAfterPost(
                    selected.id(),
                    result.outgoingTransactionId(),
                    nextDueDate,
                    nextDueDate == null ? "Completed" : dueStatus(nextDueDate)
            );
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Scheduled transfer posted. Reference: " + result.transferReference());
        } catch (IllegalArgumentException exception) {
            UiAlerts.info(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to post scheduled transfer", exception);
        }
    }

    @FXML
    private void clearForm() {
        editingScheduleId = null;
        editingLastTransactionId = null;
        transferNameField.clear();
        fromAccountBox.setValue(null);
        toAccountBox.setValue(null);
        amountField.clear();
        currencyField.clear();
        feeField.setText("0");
        feeCategoryBox.setValue(null);
        feeCategoryBox.getEditor().clear();
        firstTransferDatePicker.setValue(LocalDate.now());
        frequencyBox.getSelectionModel().select("Monthly");
        endDatePicker.setValue(null);
        reminderPeriodBox.getSelectionModel().select("Same day");
        transferMethodBox.setValue(null);
        transferMethodBox.getEditor().clear();
        confirmationRequiredBox.setSelected(true);
        notesArea.clear();
        statusBox.getSelectionModel().select("Upcoming");
        schedulesTable.getSelectionModel().clearSelection();
        detailsArea.clear();
        refreshAccountState();
    }

    private void refresh() {
        String fromName = fromAccountBox.getValue() == null ? "" : fromAccountBox.getValue().getAccountName();
        String toName = toAccountBox.getValue() == null ? "" : toAccountBox.getValue().getAccountName();
        accounts = database.listAccounts().stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(account.getStatus()))
                .toList();
        fromAccountBox.setItems(FXCollections.observableArrayList(accounts));
        toAccountBox.setItems(FXCollections.observableArrayList(accounts));
        selectAccount(fromAccountBox, fromName);
        selectAccount(toAccountBox, toName);

        transferMethodBox.setItems(FXCollections.observableArrayList(paymentMethodSuggestions()));
        CategoryInput.setItemsForType(feeCategoryBox, database.listCategories(), "EXPENSE");
        if (categoryNameText().isBlank()) {
            CategoryInput.selectByName(feeCategoryBox, "Transaction Fees");
        }

        List<ScheduledTransferRecord> records = database.listScheduledTransfers(500);
        schedulesTable.setItems(FXCollections.observableArrayList(records));
        if (editingScheduleId != null) {
            records.stream()
                    .filter(record -> record.id() == editingScheduleId)
                    .findFirst()
                    .ifPresent(schedulesTable.getSelectionModel()::select);
        }
        refreshAccountState();
    }

    private ScheduleForm readForm() {
        String transferName = clean(transferNameField.getText());
        if (transferName.isBlank()) {
            throw new IllegalArgumentException("Enter the transfer name.");
        }
        Account fromAccount = fromAccountBox.getValue();
        Account toAccount = toAccountBox.getValue();
        if (fromAccount == null || toAccount == null) {
            throw new IllegalArgumentException("Select both transfer accounts.");
        }
        if (fromAccount.getId() == toAccount.getId()) {
            throw new IllegalArgumentException("Choose two different accounts.");
        }
        double amount = parsePositiveAmount(amountField.getText(), "Enter the scheduled transfer amount.");
        double fee = parseOptionalAmount(feeField.getText(), "Transfer fee cannot be negative.");
        Integer feeCategoryId = fee > 0 ? CategoryInput.resolveCategoryId(database, feeCategoryBox, "EXPENSE") : null;
        LocalDate firstDate = firstTransferDatePicker.getValue();
        if (firstDate == null) {
            throw new IllegalArgumentException("Select the first transfer date.");
        }
        LocalDate endDate = endDatePicker.getValue();
        if (endDate != null && endDate.isBefore(firstDate)) {
            throw new IllegalArgumentException("End date cannot be before the first transfer date.");
        }
        LocalDate nextDueDate = editingScheduleId == null ? firstDate : selectedNextDueOr(firstDate);
        return new ScheduleForm(
                transferName,
                fromAccount,
                toAccount,
                amount,
                fromAccount.getCurrency(),
                fee,
                feeCategoryId,
                firstDate,
                nextDueDate,
                valueOrDefault(frequencyBox.getValue(), "Monthly"),
                endDate,
                valueOrDefault(reminderPeriodBox.getValue(), "Same day"),
                transferMethodValue(),
                confirmationRequiredBox.isSelected(),
                clean(notesArea.getText()),
                valueOrDefault(statusBox.getValue(), dueStatus(nextDueDate))
        );
    }

    private void loadSelectedSchedule(ScheduledTransferRecord record) {
        if (record == null) {
            return;
        }
        editingScheduleId = record.id();
        editingLastTransactionId = record.lastTransactionId();
        transferNameField.setText(record.transferName());
        selectAccount(fromAccountBox, record.fromAccountName());
        selectAccount(toAccountBox, record.toAccountName());
        amountField.setText(numberText(record.amount()));
        currencyField.setText(record.currency());
        feeField.setText(numberText(record.transferFee()));
        CategoryInput.selectByName(feeCategoryBox, record.feeCategoryName());
        firstTransferDatePicker.setValue(parseDate(record.firstTransferDate(), LocalDate.now()));
        endDatePicker.setValue(record.endDate() == null || record.endDate().isBlank() ? null : parseDate(record.endDate(), null));
        frequencyBox.getSelectionModel().select(valueOrDefault(record.frequency(), "Monthly"));
        reminderPeriodBox.getSelectionModel().select(valueOrDefault(record.reminderPeriod(), "Same day"));
        transferMethodBox.setValue(record.transferMethod());
        confirmationRequiredBox.setSelected(record.confirmationRequired());
        notesArea.setText(record.notes() == null ? "" : record.notes());
        statusBox.getSelectionModel().select(statusForDisplay(record));
        detailsArea.setText(detailsText(record));
        refreshAccountState();
    }

    private void refreshAccountState() {
        Account fromAccount = fromAccountBox.getValue();
        Account toAccount = toAccountBox.getValue();
        fromBalanceLabel.setText(balanceText(fromAccount));
        toBalanceLabel.setText(balanceText(toAccount));
        currencyField.setText(fromAccount == null ? "" : clean(fromAccount.getCurrency()));
        boolean differentCurrency = fromAccount != null && toAccount != null && !sameCurrency(fromAccount.getCurrency(), toAccount.getCurrency());
        scheduleCheckLabel.setText(differentCurrency
                ? "Different-currency schedules must be posted through Transfer Money so the exchange rate can be reviewed."
                : "Scheduled transfers do not change balances until Transfer Now is confirmed.");
    }

    private String detailsText(ScheduledTransferRecord record) {
        return """
                Transfer name: %s
                From: %s
                To: %s
                Amount: %s
                Fee: %s
                Frequency: %s
                First transfer date: %s
                Next due date: %s
                End date: %s
                Reminder: %s
                Method: %s
                Confirmation required: %s
                Status: %s
                Last transaction: %s

                Notes:
                %s
                """.formatted(
                record.transferName(),
                record.fromAccountName(),
                record.toAccountName(),
                money(record.currency(), record.amount()),
                money(record.fromCurrency(), record.transferFee()),
                record.frequency(),
                record.firstTransferDate(),
                record.nextDueDate(),
                blankToDash(record.endDate()),
                blankToDash(record.reminderPeriod()),
                blankToDash(record.transferMethod()),
                record.confirmationRequired() ? "Yes" : "No",
                statusForDisplay(record),
                record.lastTransactionId() == null ? "-" : "#" + record.lastTransactionId(),
                blankToDash(record.notes())
        );
    }

    private LocalDate nextDueDate(ScheduledTransferRecord record) {
        LocalDate currentDue = parseDate(record.nextDueDate(), LocalDate.now());
        LocalDate next = switch (valueOrDefault(record.frequency(), "One time")) {
            case "Weekly" -> currentDue.plusWeeks(1);
            case "Monthly" -> currentDue.plusMonths(1);
            case "Quarterly" -> currentDue.plusMonths(3);
            case "Half-yearly" -> currentDue.plusMonths(6);
            case "Yearly" -> currentDue.plusYears(1);
            case "Custom" -> currentDue.plusMonths(1);
            default -> null;
        };
        if (next == null) {
            return null;
        }
        LocalDate endDate = record.endDate() == null || record.endDate().isBlank() ? null : parseDate(record.endDate(), null);
        return endDate != null && next.isAfter(endDate) ? null : next;
    }

    private LocalDate selectedNextDueOr(LocalDate fallback) {
        ScheduledTransferRecord selected = schedulesTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.nextDueDate() == null || selected.nextDueDate().isBlank()) {
            return fallback;
        }
        return parseDate(selected.nextDueDate(), fallback);
    }

    private String dueStatus(LocalDate dueDate) {
        LocalDate today = LocalDate.now();
        if (dueDate == null) {
            return "Completed";
        }
        if (dueDate.isBefore(today)) {
            return "Overdue";
        }
        if (dueDate.equals(today)) {
            return "Due Today";
        }
        return "Upcoming";
    }

    private String statusForDisplay(ScheduledTransferRecord record) {
        if (List.of("Completed", "Paused", "Cancelled").contains(record.status())) {
            return record.status();
        }
        return dueStatus(parseDate(record.nextDueDate(), LocalDate.now()));
    }

    private List<String> paymentMethodSuggestions() {
        List<String> methods = new ArrayList<>(List.of("Bank Transfer", "Mobile Money", "Cash", "Card"));
        for (String method : database.listPaymentMethodSuggestions()) {
            if (!method.isBlank() && methods.stream().noneMatch(existing -> existing.equalsIgnoreCase(method))) {
                methods.add(method);
            }
        }
        return methods;
    }

    private double parsePositiveAmount(String value, String emptyMessage) {
        double amount = parseAmount(value, emptyMessage);
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        return amount;
    }

    private double parseOptionalAmount(String value, String negativeMessage) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        double amount = parseAmount(value, negativeMessage);
        if (amount < 0) {
            throw new IllegalArgumentException(negativeMessage);
        }
        return amount;
    }

    private double parseAmount(String value, String emptyMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter amounts using numbers only.");
        }
    }

    private void selectAccount(ComboBox<Account> box, String accountName) {
        if (accountName == null || accountName.isBlank()) {
            return;
        }
        box.getItems().stream()
                .filter(account -> account.getAccountName().equals(accountName))
                .findFirst()
                .ifPresent(box::setValue);
    }

    private String categoryNameText() {
        String typedName = clean(feeCategoryBox.getEditor().getText());
        if (!typedName.isBlank()) {
            return typedName;
        }
        Category selected = feeCategoryBox.getValue();
        return selected == null ? "" : clean(selected.getCategoryName());
    }

    private String transferMethodValue() {
        String value = transferMethodBox.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = transferMethodBox.getValue();
        }
        return clean(value);
    }

    private String balanceText(Account account) {
        if (account == null) {
            return "Balance: -";
        }
        return "Balance: " + money(account.getCurrency(), account.getCurrentBalance());
    }

    private String money(String currency, double amount) {
        String cleanCurrency = clean(currency);
        return (cleanCurrency.isBlank() ? "" : cleanCurrency + " ") + MONEY_FORMAT.format(amount);
    }

    private String numberText(double amount) {
        return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }

    private LocalDate parseDate(String date, LocalDate fallback) {
        try {
            return date == null || date.isBlank() ? fallback : LocalDate.parse(date);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private boolean sameCurrency(String first, String second) {
        return clean(first).equalsIgnoreCase(clean(second));
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record ScheduleForm(
            String transferName,
            Account fromAccount,
            Account toAccount,
            double amount,
            String currency,
            double transferFee,
            Integer feeCategoryId,
            LocalDate firstTransferDate,
            LocalDate nextDueDate,
            String frequency,
            LocalDate endDate,
            String reminderPeriod,
            String transferMethod,
            boolean confirmationRequired,
            String notes,
            String status
    ) {
    }
}
