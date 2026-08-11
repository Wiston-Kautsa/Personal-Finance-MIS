package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.Person;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IncomeController {
    private static final Logger LOGGER = Logger.getLogger(IncomeController.class.getName());

    @FXML private Label todayIncomeLabel;
    @FXML private Label monthIncomeLabel;
    @FXML private Label selectedAccountLabel;
    @FXML private Label selectedCurrencyLabel;
    @FXML private ComboBox<Account> accountBox;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private ComboBox<String> paymentMethodBox;
    @FXML private ComboBox<Person> personBox;
    @FXML private ComboBox<Project> projectBox;
    @FXML private ComboBox<DatabaseHandler.ExpectedIncomeRecord> expectedIncomeBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextField amountField;
    @FXML private TextField currencyField;
    @FXML private TextField referenceField;
    @FXML private TextField attachmentField;
    @FXML private DatePicker datePicker;
    @FXML private TextArea descriptionArea;
    @FXML private TextArea resultArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        datePicker.setValue(LocalDate.now());
        statusBox.setItems(FXCollections.observableArrayList("Draft", "Posted"));
        statusBox.getSelectionModel().select("Draft");
        CategoryInput.configure(categoryBox);
        configurePersonBox();
        configureExpectedIncomeBox();
        accountBox.valueProperty().addListener((observable, oldValue, newValue) -> updateSelectedAccount());
        expectedIncomeBox.valueProperty().addListener((observable, oldValue, newValue) -> applyExpectedIncome(newValue));
        refresh();
    }

    @FXML
    private void saveDraft() {
        try {
            IncomeForm form = readForm(false);
            int draftId = database.saveIncomeDraft(
                    null,
                    form.account().getId(),
                    form.categoryId(),
                    form.projectId(),
                    form.personId(),
                    form.expectedIncomeId(),
                    form.amount(),
                    form.currency(),
                    form.incomeDate(),
                    form.paymentMethod(),
                    form.referenceNumber(),
                    form.description(),
                    form.attachmentPath()
            );
            statusBox.getSelectionModel().select("Draft");
            refresh();
            resultArea.setText("""
                    Income draft saved.

                    Draft: #%d
                    Amount: %s
                    Account: %s

                    Drafts do not change account balances or final reports.
                    """.formatted(draftId, MoneyUtil.mwk(form.amount()), form.account().getAccountName()));
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save income draft", exception);
        }
    }

    @FXML
    private void postIncome() {
        IncomeForm form;
        try {
            form = readForm(true);
            if (database.hasSimilarIncomeTransaction(
                    form.account().getId(),
                    form.amount(),
                    form.incomeDate(),
                    form.description(),
                    form.referenceNumber()
            ) && !UiAlerts.confirm(
                    "Possible duplicate income",
                    "A similar income record already exists for this account, date and amount. Post this income anyway?"
            )) {
                return;
            }
        } catch (IllegalArgumentException exception) {
            UiAlerts.info(exception.getMessage());
            return;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Income validation or duplicate check failed before posting", exception);
            UiAlerts.info("Income could not be posted. Please check the entered information and try again.");
            return;
        }

        int transactionId;
        try {
            transactionId = database.recordIncomeTransaction(
                    form.account().getId(),
                    form.categoryId(),
                    form.projectId(),
                    form.personId(),
                    form.expectedIncomeId(),
                    form.amount(),
                    form.currency(),
                    form.incomeDate(),
                    form.descriptionWithAttachment(),
                    form.paymentMethod(),
                    form.referenceNumber()
            );
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Income database posting failed before commit", exception);
            UiAlerts.info("Income could not be posted. Please check the entered information and try again.");
            return;
        }

        try {
            DataRefreshBus.notifyDataChanged();
            refresh();
            Account updatedAccount = accountById(form.account().getId());
            resultArea.setText("""
                    Income posted successfully.

                    Transaction: #%d
                    Amount: %s
                    Account: %s
                    New account balance: %s
                    """.formatted(
                    transactionId,
                    MoneyUtil.mwk(form.amount()),
                    form.account().getAccountName(),
                    updatedAccount == null ? "Refresh required" : MoneyUtil.mwk(updatedAccount.getCurrentBalance())
            ));
            resetEntryFields();
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Income posted, but post-save UI refresh failed for transaction " + transactionId, exception);
            UiAlerts.info("Income was posted successfully, but the screen could not be refreshed. Do not post it again. Please refresh or reopen this page.");
        }
    }

    @FXML
    private void clearForm() {
        resetEntryFields();
        resultArea.clear();
    }

    @FXML
    private void browseAttachment() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Income Receipt");
        File file = chooser.showOpenDialog(attachmentField.getScene() == null ? null : attachmentField.getScene().getWindow());
        if (file != null) {
            attachmentField.setText(file.getAbsolutePath());
        }
    }

    private void refresh() {
        Account selectedAccount = accountBox.getValue();
        accountBox.setItems(FXCollections.observableArrayList(database.listAccounts().stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(safe(account.getStatus())))
                .toList()));
        if (selectedAccount != null) {
            selectAccountById(selectedAccount.getId());
        }
        String selectedPaymentMethod = paymentMethodValue();
        paymentMethodBox.setItems(FXCollections.observableArrayList(database.listPaymentMethodSuggestions()));
        paymentMethodBox.setValue(selectedPaymentMethod.isBlank() ? "Bank Transfer" : selectedPaymentMethod);
        CategoryInput.setItems(categoryBox, database.listCategories().stream()
                .filter(category -> "INCOME".equals(category.getCategoryType()) || "BOTH".equals(category.getCategoryType()))
                .toList());
        personBox.setItems(FXCollections.observableArrayList(database.listPeople()));
        projectBox.setItems(FXCollections.observableArrayList(database.listProjects().stream()
                .filter(project -> !"CANCELLED".equalsIgnoreCase(safe(project.getStatus()))
                        && !"CLOSED".equalsIgnoreCase(safe(project.getStatus())))
                .toList()));
        expectedIncomeBox.setItems(FXCollections.observableArrayList(database.listExpectedIncomeRecords(200).stream()
                .filter(record -> !"Received".equalsIgnoreCase(safe(record.status()))
                        && !"Cancelled".equalsIgnoreCase(safe(record.status())))
                .toList()));
        refreshSummary();
        updateSelectedAccount();
    }

    private void refreshSummary() {
        List<com.wk.pfmis.models.FinanceTransaction> incomeTransactions = database.listRecentTransactions(500).stream()
                .filter(transaction -> "INCOME".equals(transaction.getTransactionType()))
                .filter(transaction -> !"CANCELLED".equalsIgnoreCase(safe(transaction.getTransactionStatus())))
                .filter(transaction -> !"REVERSED".equalsIgnoreCase(safe(transaction.getTransactionStatus())))
                .toList();
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.now();
        double todayIncome = incomeTransactions.stream()
                .filter(transaction -> today.toString().equals(transaction.getTransactionDate()))
                .mapToDouble(com.wk.pfmis.models.FinanceTransaction::getAmount)
                .sum();
        double monthIncome = incomeTransactions.stream()
                .filter(transaction -> transaction.getTransactionDate() != null)
                .filter(transaction -> YearMonth.from(LocalDate.parse(transaction.getTransactionDate())).equals(thisMonth))
                .mapToDouble(com.wk.pfmis.models.FinanceTransaction::getAmount)
                .sum();
        todayIncomeLabel.setText(MoneyUtil.mwk(todayIncome));
        monthIncomeLabel.setText(MoneyUtil.mwk(monthIncome));
    }

    private IncomeForm readForm(boolean posting) {
        Account account = accountBox.getValue();
        if (account == null) {
            throw new IllegalArgumentException("Select a receiving account.");
        }
        if (!"ACTIVE".equalsIgnoreCase(safe(account.getStatus()))) {
            throw new IllegalArgumentException("The receiving account must be active.");
        }
        double amount = parseAmount();
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        LocalDate incomeDate = datePicker.getValue();
        if (incomeDate == null) {
            throw new IllegalArgumentException("Income date is required.");
        }
        Integer categoryId = CategoryInput.resolveCategoryId(database, categoryBox, "INCOME");
        String paymentMethod = paymentMethodValue();
        if (posting && categoryId == null) {
            throw new IllegalArgumentException("Select or enter an income source.");
        }
        if (posting && paymentMethod.isBlank()) {
            throw new IllegalArgumentException("Enter the payment method.");
        }
        String accountCurrency = safe(account.getCurrency()).isBlank() ? "MWK" : account.getCurrency().trim().toUpperCase(Locale.ENGLISH);
        String formCurrency = safe(currencyField.getText()).isBlank() ? accountCurrency : currencyField.getText().trim().toUpperCase(Locale.ENGLISH);
        if (!accountCurrency.equals(formCurrency)) {
            throw new IllegalArgumentException("Income currency must match the receiving account currency.");
        }
        return new IncomeForm(
                account,
                categoryId,
                projectBox.getValue() == null ? null : projectBox.getValue().getId(),
                resolvePersonId(),
                expectedIncomeBox.getValue() == null ? null : expectedIncomeBox.getValue().id(),
                amount,
                formCurrency,
                incomeDate,
                paymentMethod,
                value(referenceField),
                value(descriptionArea),
                value(attachmentField)
        );
    }

    private Integer resolvePersonId() {
        String typedName = personBox.getEditor().getText();
        Person selected = personBox.getValue();
        String name = typedName == null || typedName.isBlank()
                ? selected == null ? "" : selected.getFullName()
                : typedName;
        return database.findOrCreatePerson(name);
    }

    private double parseAmount() {
        String amountText = value(amountField).replace(",", "");
        if (amountText.isBlank()) {
            throw new IllegalArgumentException("Enter the income amount.");
        }
        try {
            return Double.parseDouble(amountText);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid income amount.");
        }
    }

    private void updateSelectedAccount() {
        Account account = accountBox.getValue();
        selectedAccountLabel.setText(account == null ? "None" : account.getAccountName());
        String currency = account == null || safe(account.getCurrency()).isBlank()
                ? "MWK"
                : account.getCurrency().trim().toUpperCase(Locale.ENGLISH);
        selectedCurrencyLabel.setText(currency);
        currencyField.setText(currency);
    }

    private void applyExpectedIncome(DatabaseHandler.ExpectedIncomeRecord record) {
        if (record == null) {
            return;
        }
        selectAccountById(record.accountId());
        CategoryInput.selectByName(categoryBox, record.categoryName());
        selectPersonById(record.personId());
        amountField.setText(String.format(Locale.ENGLISH, "%.2f", record.expectedAmount()));
        if (record.expectedDate() != null && !record.expectedDate().isBlank()) {
            datePicker.setValue(LocalDate.parse(record.expectedDate()));
        }
        if (value(referenceField).isBlank() && record.referenceNumber() != null) {
            referenceField.setText(record.referenceNumber());
        }
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
                if (name.isEmpty()) {
                    return null;
                }
                return personBox.getItems().stream()
                        .filter(person -> person.getFullName().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(new Person(-1, name, "", "", ""));
            }
        });
    }

    private void configureExpectedIncomeBox() {
        expectedIncomeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(DatabaseHandler.ExpectedIncomeRecord record) {
                return record == null ? "" : record.toString();
            }

            @Override
            public DatabaseHandler.ExpectedIncomeRecord fromString(String value) {
                return expectedIncomeBox.getItems().stream()
                        .filter(record -> record.toString().equals(value))
                        .findFirst()
                        .orElse(null);
            }
        });
    }

    private void selectAccountById(int accountId) {
        accountBox.getItems().stream()
                .filter(account -> account.getId() == accountId)
                .findFirst()
                .ifPresent(accountBox::setValue);
    }

    private void selectPersonById(Integer personId) {
        if (personId == null) {
            personBox.setValue(null);
            personBox.getEditor().clear();
            return;
        }
        personBox.getItems().stream()
                .filter(person -> person.getId() == personId)
                .findFirst()
                .ifPresent(personBox::setValue);
    }

    private Account accountById(int accountId) {
        return database.listAccounts().stream()
                .filter(account -> account.getId() == accountId)
                .findFirst()
                .orElse(null);
    }

    private void resetEntryFields() {
        amountField.clear();
        referenceField.clear();
        descriptionArea.clear();
        attachmentField.clear();
        personBox.setValue(null);
        personBox.getEditor().clear();
        projectBox.setValue(null);
        expectedIncomeBox.setValue(null);
        paymentMethodBox.setValue("Bank Transfer");
        statusBox.getSelectionModel().select("Draft");
        datePicker.setValue(LocalDate.now());
        updateSelectedAccount();
    }

    private String paymentMethodValue() {
        String value = paymentMethodBox.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = paymentMethodBox.getValue();
        }
        return value == null ? "" : value.trim();
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

    private record IncomeForm(
            Account account,
            Integer categoryId,
            Integer projectId,
            Integer personId,
            Integer expectedIncomeId,
            double amount,
            String currency,
            LocalDate incomeDate,
            String paymentMethod,
            String referenceNumber,
            String description,
            String attachmentPath
    ) {
        String descriptionWithAttachment() {
            if (attachmentPath == null || attachmentPath.isBlank()) {
                return description;
            }
            String base = description == null || description.isBlank() ? "" : description + System.lineSeparator();
            return base + "Attachment or receipt: " + attachmentPath;
        }
    }
}
