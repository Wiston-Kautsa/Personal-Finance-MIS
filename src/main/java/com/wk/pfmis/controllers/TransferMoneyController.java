package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Person;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransferMoneyController {
    private static final String SOURCE_MANUAL = "Manual entry";
    private static final String SOURCE_MOBILE_MONEY = "Mobile money SMS";
    private static final String SOURCE_BANK_SMS = "Bank SMS";
    private static final String SOURCE_API = "Phone/API import";

    private static final String DIRECTION_INTERNAL = "Between own accounts";
    private static final String DIRECTION_INCOMING = "Money received";
    private static final String DIRECTION_OUTGOING = "Money sent";
    private static final String DIRECTION_REVIEW = "Needs review";

    private static final String CLASS_INTERNAL_TRANSFER = "Internal transfer";
    private static final String CLASS_INCOME = "Income received";
    private static final String CLASS_BORROWED_RECEIVED = "Borrowed money received";
    private static final String CLASS_LENT_REPAID = "Repayment from borrower";
    private static final String CLASS_MONEY_LENT = "Money lent out";
    private static final String CLASS_BORROWED_REPAID = "Repay borrowed loan";

    private static final String REVIEW_READY = "Ready to post";
    private static final String REVIEW_NEEDS = "Needs review";
    private static final String REVIEW_DUPLICATE = "Duplicate suspected";

    private static final String DEFAULT_LOAN_CATEGORY = "Personal Loan";
    private static final String DEFAULT_LOAN_REPAYMENT_CATEGORY = "Loan Repayment";
    private static final String DEFAULT_INCOME_CATEGORY = "Other";

    @FXML private ComboBox<String> sourceBox;
    @FXML private ComboBox<String> directionBox;
    @FXML private ComboBox<String> classificationBox;
    @FXML private ComboBox<String> reviewStatusBox;
    @FXML private TextArea rawMessageArea;
    @FXML private ComboBox<Account> fromAccountBox;
    @FXML private ComboBox<Account> toAccountBox;
    @FXML private Label fromBalanceLabel;
    @FXML private Label toBalanceLabel;
    @FXML private TextField amountSentField;
    @FXML private TextField amountReceivedField;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> paymentMethodBox;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private ComboBox<Person> personBox;
    @FXML private TextField referenceField;
    @FXML private TextArea descriptionArea;
    @FXML private TableView<FinanceTransaction> transfersTable;
    @FXML private TableColumn<FinanceTransaction, String> dateColumn;
    @FXML private TableColumn<FinanceTransaction, String> accountColumn;
    @FXML private TableColumn<FinanceTransaction, String> purposeColumn;
    @FXML private TableColumn<FinanceTransaction, String> counterpartyColumn;
    @FXML private TableColumn<FinanceTransaction, String> amountColumn;
    @FXML private TableColumn<FinanceTransaction, String> methodColumn;
    @FXML private TableColumn<FinanceTransaction, String> referenceColumn;

    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        MONEY_FORMAT.setMinimumFractionDigits(2);
        MONEY_FORMAT.setMaximumFractionDigits(2);

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        accountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        purposeColumn.setCellValueFactory(cell -> new SimpleStringProperty(classificationLabel(cell.getValue())));
        counterpartyColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getPersonName())));
        amountColumn.setCellValueFactory(cell -> new SimpleStringProperty(MONEY_FORMAT.format(cell.getValue().getAmount())));
        methodColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getPaymentMethod())));
        referenceColumn.setCellValueFactory(cell -> new SimpleStringProperty(blankToDash(cell.getValue().getReferenceNumber())));
        TableActions.configureScrollableTable(transfersTable);

        sourceBox.setItems(FXCollections.observableArrayList(
                SOURCE_MANUAL,
                SOURCE_MOBILE_MONEY,
                SOURCE_BANK_SMS,
                SOURCE_API
        ));
        directionBox.setItems(FXCollections.observableArrayList(
                DIRECTION_INTERNAL,
                DIRECTION_INCOMING,
                DIRECTION_OUTGOING,
                DIRECTION_REVIEW
        ));
        classificationBox.setItems(FXCollections.observableArrayList(
                CLASS_INTERNAL_TRANSFER,
                CLASS_INCOME,
                CLASS_BORROWED_RECEIVED,
                CLASS_LENT_REPAID,
                CLASS_MONEY_LENT,
                CLASS_BORROWED_REPAID
        ));
        reviewStatusBox.setItems(FXCollections.observableArrayList(
                REVIEW_READY,
                REVIEW_NEEDS,
                REVIEW_DUPLICATE
        ));

        sourceBox.getSelectionModel().select(SOURCE_MANUAL);
        classificationBox.getSelectionModel().select(CLASS_INTERNAL_TRANSFER);
        reviewStatusBox.getSelectionModel().select(REVIEW_READY);
        directionBox.getSelectionModel().select(DIRECTION_INTERNAL);
        datePicker.setValue(LocalDate.now());

        CategoryInput.configure(categoryBox);
        configurePersonInput();
        classificationBox.valueProperty().addListener((observable, oldValue, newValue) -> configureClassificationState());
        fromAccountBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshBalanceLabels());
        toAccountBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshBalanceLabels());
        configureTransferRowActions();
        refresh();
        configureClassificationState();
    }

    @FXML
    private void saveTransfer() {
        try {
            String message = isInternalTransfer()
                    ? recordInternalTransfer()
                    : recordClassifiedMovement();
            clearForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info(message);
        } catch (IllegalArgumentException exception) {
            UiAlerts.info(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save money movement", exception);
        }
    }

    @FXML
    private void clearForm() {
        sourceBox.getSelectionModel().select(SOURCE_MANUAL);
        classificationBox.getSelectionModel().select(CLASS_INTERNAL_TRANSFER);
        reviewStatusBox.getSelectionModel().select(REVIEW_READY);
        directionBox.getSelectionModel().select(DIRECTION_INTERNAL);
        rawMessageArea.clear();
        fromAccountBox.setValue(null);
        toAccountBox.setValue(null);
        amountSentField.clear();
        amountReceivedField.clear();
        datePicker.setValue(LocalDate.now());
        paymentMethodBox.setValue(null);
        paymentMethodBox.getEditor().clear();
        categoryBox.setValue(null);
        categoryBox.getEditor().clear();
        personBox.setValue(null);
        personBox.getEditor().clear();
        referenceField.clear();
        descriptionArea.clear();
        configureClassificationState();
        refreshBalanceLabels();
    }

    @FXML
    private void refresh() {
        String selectedFrom = fromAccountBox.getValue() == null ? null : fromAccountBox.getValue().getAccountName();
        String selectedTo = toAccountBox.getValue() == null ? null : toAccountBox.getValue().getAccountName();
        String selectedPerson = personNameText();
        List<Account> activeAccounts = database.listAccounts().stream()
                .filter(account -> "ACTIVE".equals(account.getStatus()))
                .toList();
        fromAccountBox.setItems(FXCollections.observableArrayList(activeAccounts));
        toAccountBox.setItems(FXCollections.observableArrayList(activeAccounts));
        selectAccountByName(fromAccountBox, selectedFrom);
        selectAccountByName(toAccountBox, selectedTo);

        paymentMethodBox.setItems(FXCollections.observableArrayList(paymentMethodSuggestions()));
        personBox.setItems(FXCollections.observableArrayList(database.listPeople()));
        selectPersonByName(selectedPerson);
        refreshCategoriesForClassification();

        List<FinanceTransaction> movements = database.listRecentTransactions(200).stream()
                .filter(this::isVisibleMoneyMovement)
                .toList();
        transfersTable.setItems(FXCollections.observableArrayList(movements));
        refreshBalanceLabels();
    }

    private String recordInternalTransfer() {
        Account fromAccount = fromAccountBox.getValue();
        Account toAccount = toAccountBox.getValue();
        if (fromAccount == null) {
            throw new IllegalArgumentException("Select the account money is leaving.");
        }
        if (toAccount == null) {
            throw new IllegalArgumentException("Select the account money is going to.");
        }
        if (fromAccount.getId() == toAccount.getId()) {
            throw new IllegalArgumentException("Choose two different accounts.");
        }

        double amountSent = parseAmount(amountSentField.getText(), "Enter the amount sent.");
        double amountReceived = amountReceivedField.getText() == null || amountReceivedField.getText().isBlank()
                ? amountSent
                : parseAmount(amountReceivedField.getText(), "Enter the amount received.");
        if (!sameCurrency(fromAccount, toAccount)
                && (amountReceivedField.getText() == null || amountReceivedField.getText().isBlank())) {
            throw new IllegalArgumentException("Enter the amount received for transfers between different currencies.");
        }

        database.recordTransfer(
                fromAccount.getId(),
                toAccount.getId(),
                amountSent,
                amountReceived,
                requiredDate(),
                composedDescription("Internal transfer"),
                paymentMethodValue(),
                clean(referenceField.getText())
        );
        return "Transfer saved and both account balances updated.";
    }

    private String recordClassifiedMovement() {
        String classification = classificationValue();
        String transactionType = transactionTypeForClassification(classification);
        String purpose = purposeForClassification(classification);
        Account postingAccount = postingAccountForClassification(classification);
        double amount = incomingClassification(classification)
                ? incomingAmount()
                : outgoingAmount();

        Integer personId = resolvePersonId(requiresPerson(classification));
        Integer categoryId = resolveCategoryId(classification, transactionType);
        database.recordTransaction(
                postingAccount.getId(),
                categoryId,
                null,
                null,
                personId,
                transactionType,
                purpose,
                transactionStatusForClassification(classification),
                amount,
                requiredDate(),
                composedDescription(classification),
                paymentMethodValue(),
                clean(referenceField.getText())
        );
        return "Money movement posted to the ledger.";
    }

    private void configureClassificationState() {
        String classification = classificationValue();
        directionBox.getSelectionModel().select(directionForClassification(classification));
        boolean internalTransfer = CLASS_INTERNAL_TRANSFER.equals(classification);
        boolean incoming = incomingClassification(classification);
        boolean outgoing = outgoingClassification(classification);

        fromAccountBox.setDisable(incoming && !internalTransfer);
        toAccountBox.setDisable(outgoing && !internalTransfer);
        categoryBox.setDisable(internalTransfer);
        personBox.setDisable(!requiresPerson(classification));
        amountReceivedField.setDisable(outgoing && !internalTransfer);
        amountSentField.setDisable(incoming && !internalTransfer);

        if (internalTransfer) {
            clearCategorySelection();
        }
        if (!requiresPerson(classification)) {
            clearPersonSelection();
        }
        refreshCategoriesForClassification();
        refreshBalanceLabels();
    }

    private void refreshCategoriesForClassification() {
        if (categoryBox == null || categoryBox.isDisabled()) {
            return;
        }
        String classification = classificationValue();
        String currentCategory = categoryNameText();
        CategoryInput.setItemsForType(categoryBox, database.listCategories(), categoryTypeForClassification(classification));
        if (currentCategory.isBlank() || isDefaultCategory(currentCategory)) {
            CategoryInput.selectByName(categoryBox, defaultCategoryName(classification));
        } else {
            CategoryInput.selectByName(categoryBox, currentCategory);
        }
    }

    private void configurePersonInput() {
        personBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Person person) {
                return person == null ? "" : person.getFullName();
            }

            @Override
            public Person fromString(String value) {
                String name = clean(value);
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

    private void configureTransferRowActions() {
        TableActions.installRowContextMenu(transfersTable, this::movementMenuItems);
    }

    private List<javafx.scene.control.MenuItem> movementMenuItems(FinanceTransaction movement) {
        return List.of(
                TableActions.menuItem("View Movement", this::viewSelected),
                TableActions.menuItem("Void Movement", this::deleteSelected),
                TableActions.separator(),
                TableActions.copyRowItem(transfersTable, movement),
                TableActions.exportTableItem(transfersTable, "Money Movements"),
                TableActions.printTableItem(transfersTable, "Money Movements"),
                TableActions.refreshItem(this::refresh)
        );
    }

    private void viewSelected() {
        FinanceTransaction selected = transfersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a money movement to view.");
            return;
        }
        UiAlerts.info(
                "Date: " + selected.getTransactionDate()
                        + "\nClassification: " + classificationLabel(selected)
                        + "\nAccount: " + selected.getAccountName()
                        + "\nContact: " + blankToDash(selected.getPersonName())
                        + "\nAmount: " + MONEY_FORMAT.format(selected.getAmount())
                        + "\nStatus: " + selected.getTransactionStatus()
                        + "\nMethod: " + blankToDash(selected.getPaymentMethod())
                        + "\nReference: " + blankToDash(selected.getReferenceNumber())
                        + "\nDescription: " + blankToDash(selected.getDescription())
        );
    }

    private void deleteSelected() {
        FinanceTransaction selected = transfersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a money movement to delete.");
            return;
        }
        try {
            database.deleteTransaction(selected.getId());
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to delete money movement", exception);
        }
    }

    private Account postingAccountForClassification(String classification) {
        Account account = incomingClassification(classification) ? toAccountBox.getValue() : fromAccountBox.getValue();
        if (account == null) {
            throw new IllegalArgumentException(incomingClassification(classification)
                    ? "Select the account receiving this money."
                    : "Select the account money is leaving.");
        }
        return account;
    }

    private Integer resolveCategoryId(String classification, String transactionType) {
        Integer categoryId = CategoryInput.resolveCategoryId(database, categoryBox, categoryTypeForTransaction(transactionType));
        if (categoryId != null) {
            return categoryId;
        }
        return database.findOrCreateCategory(defaultCategoryName(classification), categoryTypeForTransaction(transactionType)).getId();
    }

    private Integer resolvePersonId(boolean required) {
        String personName = personNameText();
        if (personName.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("Select or enter the borrower, lender, sender, or institution.");
            }
            return null;
        }
        return database.listPeople().stream()
                .filter(person -> person.getFullName().equalsIgnoreCase(personName))
                .findFirst()
                .map(Person::getId)
                .orElseGet(() -> {
                    database.addPerson(personName, "", "", "Added from transfer intake");
                    return database.listPeople().stream()
                            .filter(person -> person.getFullName().equalsIgnoreCase(personName))
                            .findFirst()
                            .map(Person::getId)
                            .orElseThrow(() -> new IllegalStateException("Failed to create contact"));
                });
    }

    private boolean isVisibleMoneyMovement(FinanceTransaction transaction) {
        String purpose = transaction.getTransactionPurpose();
        if ("TRANSFER".equals(transaction.getTransactionType())) {
            return "TRANSFER_OUT".equals(purpose);
        }
        return "NORMAL".equals(purpose)
                || "MONEY_BORROWED".equals(purpose)
                || "LENT_REPAID".equals(purpose)
                || "MONEY_LENT".equals(purpose)
                || "BORROWED_REPAID".equals(purpose);
    }

    private String classificationLabel(FinanceTransaction transaction) {
        return switch (transaction.getTransactionPurpose()) {
            case "TRANSFER_OUT", "TRANSFER_IN" -> CLASS_INTERNAL_TRANSFER;
            case "MONEY_BORROWED" -> CLASS_BORROWED_RECEIVED;
            case "LENT_REPAID" -> CLASS_LENT_REPAID;
            case "MONEY_LENT" -> CLASS_MONEY_LENT;
            case "BORROWED_REPAID" -> CLASS_BORROWED_REPAID;
            default -> "INCOME".equals(transaction.getTransactionType()) ? CLASS_INCOME : transaction.getTransactionPurpose();
        };
    }

    private String transactionTypeForClassification(String classification) {
        return switch (classification) {
            case CLASS_INCOME, CLASS_BORROWED_RECEIVED, CLASS_LENT_REPAID -> "INCOME";
            case CLASS_MONEY_LENT, CLASS_BORROWED_REPAID -> "EXPENSE";
            default -> "TRANSFER";
        };
    }

    private String purposeForClassification(String classification) {
        return switch (classification) {
            case CLASS_BORROWED_RECEIVED -> "MONEY_BORROWED";
            case CLASS_LENT_REPAID -> "LENT_REPAID";
            case CLASS_MONEY_LENT -> "MONEY_LENT";
            case CLASS_BORROWED_REPAID -> "BORROWED_REPAID";
            default -> "NORMAL";
        };
    }

    private String transactionStatusForClassification(String classification) {
        if (REVIEW_DUPLICATE.equals(reviewStatusBox.getValue())) {
            return "CANCELLED";
        }
        if (REVIEW_NEEDS.equals(reviewStatusBox.getValue())
                || CLASS_MONEY_LENT.equals(classification)
                || CLASS_BORROWED_RECEIVED.equals(classification)) {
            return "OPEN";
        }
        return "COMPLETED";
    }

    private String directionForClassification(String classification) {
        return switch (classification) {
            case CLASS_INCOME, CLASS_BORROWED_RECEIVED, CLASS_LENT_REPAID -> DIRECTION_INCOMING;
            case CLASS_MONEY_LENT, CLASS_BORROWED_REPAID -> DIRECTION_OUTGOING;
            case CLASS_INTERNAL_TRANSFER -> DIRECTION_INTERNAL;
            default -> DIRECTION_REVIEW;
        };
    }

    private boolean incomingClassification(String classification) {
        return CLASS_INCOME.equals(classification)
                || CLASS_BORROWED_RECEIVED.equals(classification)
                || CLASS_LENT_REPAID.equals(classification);
    }

    private boolean outgoingClassification(String classification) {
        return CLASS_MONEY_LENT.equals(classification)
                || CLASS_BORROWED_REPAID.equals(classification);
    }

    private boolean requiresPerson(String classification) {
        return CLASS_BORROWED_RECEIVED.equals(classification)
                || CLASS_LENT_REPAID.equals(classification)
                || CLASS_MONEY_LENT.equals(classification)
                || CLASS_BORROWED_REPAID.equals(classification);
    }

    private String categoryTypeForClassification(String classification) {
        return categoryTypeForTransaction(transactionTypeForClassification(classification));
    }

    private String categoryTypeForTransaction(String transactionType) {
        return "EXPENSE".equals(transactionType) ? "EXPENSE" : "INCOME";
    }

    private String defaultCategoryName(String classification) {
        return switch (classification) {
            case CLASS_BORROWED_RECEIVED, CLASS_MONEY_LENT -> DEFAULT_LOAN_CATEGORY;
            case CLASS_LENT_REPAID, CLASS_BORROWED_REPAID -> DEFAULT_LOAN_REPAYMENT_CATEGORY;
            default -> DEFAULT_INCOME_CATEGORY;
        };
    }

    private boolean isDefaultCategory(String categoryName) {
        return DEFAULT_INCOME_CATEGORY.equalsIgnoreCase(categoryName)
                || DEFAULT_LOAN_CATEGORY.equalsIgnoreCase(categoryName)
                || DEFAULT_LOAN_REPAYMENT_CATEGORY.equalsIgnoreCase(categoryName);
    }

    private boolean isInternalTransfer() {
        return CLASS_INTERNAL_TRANSFER.equals(classificationValue());
    }

    private String classificationValue() {
        String value = classificationBox.getValue();
        return value == null || value.isBlank() ? CLASS_INTERNAL_TRANSFER : value;
    }

    private LocalDate requiredDate() {
        LocalDate date = datePicker.getValue();
        if (date == null) {
            throw new IllegalArgumentException("Select the transaction date.");
        }
        return date;
    }

    private double incomingAmount() {
        String received = clean(amountReceivedField.getText());
        if (!received.isBlank()) {
            return parseAmount(received, "Enter the amount received.");
        }
        return parseAmount(amountSentField.getText(), "Enter the amount received.");
    }

    private double outgoingAmount() {
        String sent = clean(amountSentField.getText());
        if (!sent.isBlank()) {
            return parseAmount(sent, "Enter the amount sent.");
        }
        return parseAmount(amountReceivedField.getText(), "Enter the amount sent.");
    }

    private String composedDescription(String classification) {
        List<String> parts = new ArrayList<>();
        parts.add("Classification: " + classification);
        parts.add("Source: " + blankToDash(sourceBox.getValue()));
        if (!blankToDash(directionBox.getValue()).equals("-")) {
            parts.add("Direction: " + directionBox.getValue());
        }
        String note = clean(descriptionArea.getText());
        if (!note.isBlank()) {
            parts.add(note);
        }
        String rawMessage = clean(rawMessageArea.getText());
        if (!rawMessage.isBlank()) {
            parts.add("Raw message: " + rawMessage);
        }
        return String.join("\n", parts);
    }

    private List<String> paymentMethodSuggestions() {
        List<String> methods = new ArrayList<>(List.of("Mobile Money", "Bank Transfer", "Cash", "Card"));
        for (String method : database.listPaymentMethodSuggestions()) {
            if (!method.isBlank() && methods.stream().noneMatch(existing -> existing.equalsIgnoreCase(method))) {
                methods.add(method);
            }
        }
        return methods;
    }

    private void refreshBalanceLabels() {
        fromBalanceLabel.setText(balanceText(fromAccountBox.getValue()));
        toBalanceLabel.setText(balanceText(toAccountBox.getValue()));
    }

    private String balanceText(Account account) {
        if (account == null) {
            return "Balance: -";
        }
        return "Balance: " + account.getCurrency() + " " + MONEY_FORMAT.format(account.getCurrentBalance());
    }

    private boolean sameCurrency(Account first, Account second) {
        return clean(first.getCurrency()).equalsIgnoreCase(clean(second.getCurrency()));
    }

    private double parseAmount(String value, String emptyMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        double amount;
        try {
            amount = Double.parseDouble(value.replace(",", "").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter amounts using numbers only.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return amount;
    }

    private void selectAccountByName(ComboBox<Account> box, String accountName) {
        if (accountName == null || accountName.isBlank()) {
            return;
        }
        box.getItems().stream()
                .filter(account -> account.getAccountName().equals(accountName))
                .findFirst()
                .ifPresent(box::setValue);
    }

    private void selectPersonByName(String personName) {
        String cleanName = clean(personName);
        if (cleanName.isBlank()) {
            return;
        }
        personBox.getItems().stream()
                .filter(person -> person.getFullName().equalsIgnoreCase(cleanName))
                .findFirst()
                .ifPresentOrElse(personBox::setValue, () -> personBox.getEditor().setText(cleanName));
    }

    private void clearCategorySelection() {
        categoryBox.setValue(null);
        categoryBox.getEditor().clear();
    }

    private void clearPersonSelection() {
        personBox.setValue(null);
        personBox.getEditor().clear();
    }

    private String categoryNameText() {
        String typedName = clean(categoryBox.getEditor().getText());
        if (!typedName.isBlank()) {
            return typedName;
        }
        Category selected = categoryBox.getValue();
        return selected == null ? "" : clean(selected.getCategoryName());
    }

    private String personNameText() {
        String typedName = clean(personBox.getEditor().getText());
        if (!typedName.isBlank()) {
            return typedName;
        }
        Person selected = personBox.getValue();
        return selected == null ? "" : clean(selected.getFullName());
    }

    private String paymentMethodValue() {
        String value = paymentMethodBox.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = paymentMethodBox.getValue();
        }
        return value == null ? "" : value.trim();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
