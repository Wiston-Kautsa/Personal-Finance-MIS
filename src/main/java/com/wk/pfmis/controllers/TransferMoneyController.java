package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.TransferPostingResult;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransferMoneyController {
    private static final String STATUS_DRAFT = "Draft";
    private static final String STATUS_POSTED = "Posted";
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    @FXML private DatePicker transferDatePicker;
    @FXML private ComboBox<String> statusBox;
    @FXML private ComboBox<Account> fromAccountBox;
    @FXML private ComboBox<Account> toAccountBox;
    @FXML private Label fromBalanceLabel;
    @FXML private Label toBalanceLabel;
    @FXML private TextField amountField;
    @FXML private TextField currencyField;
    @FXML private TextField feeField;
    @FXML private ComboBox<Category> feeCategoryBox;
    @FXML private TextField exchangeRateField;
    @FXML private ComboBox<String> paymentMethodBox;
    @FXML private TextField referenceField;
    @FXML private TextArea descriptionArea;
    @FXML private Label transferCheckLabel;
    @FXML private TextArea resultArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        MONEY_FORMAT.setMinimumFractionDigits(2);
        MONEY_FORMAT.setMaximumFractionDigits(2);

        transferDatePicker.setValue(LocalDate.now());
        statusBox.setItems(FXCollections.observableArrayList(STATUS_DRAFT, STATUS_POSTED));
        statusBox.getSelectionModel().select(STATUS_POSTED);
        feeField.setText("0");
        resultArea.setText("Posting result will appear here after the transfer is completed.");

        CategoryInput.configure(feeCategoryBox);
        fromAccountBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshAccountState());
        toAccountBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshAccountState());
        refresh();
    }

    @FXML
    private void saveDraft() {
        try {
            TransferForm form = readForm(false);
            int draftId = database.saveTransferDraft(
                    null,
                    form.fromAccount().getId(),
                    form.toAccount().getId(),
                    form.amountSent(),
                    form.amountReceived(),
                    form.currency(),
                    form.exchangeRate(),
                    form.transferFee(),
                    form.feeCategoryId(),
                    form.transferDate(),
                    form.paymentMethod(),
                    form.referenceNumber(),
                    form.description()
            );
            statusBox.getSelectionModel().select(STATUS_DRAFT);
            resultArea.setText("Transfer draft saved.\n\nDraft ID: " + draftId
                    + "\nFrom: " + form.fromAccount().getAccountName()
                    + "\nTo: " + form.toAccount().getAccountName()
                    + "\nAmount: " + money(form.currency(), form.amountSent())
                    + "\nStatus: Draft");
            DataRefreshBus.notifyDataChanged();
        } catch (IllegalArgumentException exception) {
            UiAlerts.info(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save transfer draft", exception);
        }
    }

    @FXML
    private void transferMoney() {
        try {
            TransferForm form = readForm(true);
            if (database.hasSimilarTransfer(
                    form.fromAccount().getId(),
                    form.toAccount().getId(),
                    form.amountSent(),
                    form.transferDate(),
                    form.referenceNumber()
            ) && !UiAlerts.confirm(
                    "Possible duplicate transfer",
                    "A similar transfer between these accounts already exists for this date. Continue posting?"
            )) {
                return;
            }

            TransferPostingResult result = database.recordTransferWithFee(
                    form.fromAccount().getId(),
                    form.toAccount().getId(),
                    form.amountSent(),
                    form.amountReceived(),
                    form.transferFee(),
                    form.feeCategoryId(),
                    form.transferDate(),
                    form.description(),
                    form.paymentMethod(),
                    form.referenceNumber()
            );
            statusBox.getSelectionModel().select(STATUS_POSTED);
            resultArea.setText("""
                    Transfer completed successfully.

                    From: %s
                    To: %s
                    Amount transferred: %s
                    Amount received: %s
                    Transfer fee: %s

                    Source balance: %s
                    Destination balance: %s
                    Reference: %s
                    """.formatted(
                    form.fromAccount().getAccountName(),
                    form.toAccount().getAccountName(),
                    money(form.fromAccount().getCurrency(), form.amountSent()),
                    money(form.toAccount().getCurrency(), form.amountReceived()),
                    money(form.fromAccount().getCurrency(), form.transferFee()),
                    money(form.fromAccount().getCurrency(), result.sourceBalance()),
                    money(form.toAccount().getCurrency(), result.destinationBalance()),
                    result.transferReference()
            ));
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (IllegalArgumentException exception) {
            UiAlerts.info(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to transfer money", exception);
        }
    }

    @FXML
    private void clearForm() {
        fromAccountBox.setValue(null);
        toAccountBox.setValue(null);
        amountField.clear();
        feeField.setText("0");
        feeCategoryBox.setValue(null);
        feeCategoryBox.getEditor().clear();
        exchangeRateField.clear();
        paymentMethodBox.setValue(null);
        paymentMethodBox.getEditor().clear();
        referenceField.clear();
        descriptionArea.clear();
        transferDatePicker.setValue(LocalDate.now());
        statusBox.getSelectionModel().select(STATUS_POSTED);
        resultArea.setText("Posting result will appear here after the transfer is completed.");
        refreshAccountState();
    }

    private void refresh() {
        String fromName = fromAccountBox.getValue() == null ? "" : fromAccountBox.getValue().getAccountName();
        String toName = toAccountBox.getValue() == null ? "" : toAccountBox.getValue().getAccountName();
        List<Account> activeAccounts = database.listAccounts().stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(account.getStatus()))
                .toList();
        fromAccountBox.setItems(FXCollections.observableArrayList(activeAccounts));
        toAccountBox.setItems(FXCollections.observableArrayList(activeAccounts));
        selectAccount(fromAccountBox, fromName);
        selectAccount(toAccountBox, toName);

        paymentMethodBox.setItems(FXCollections.observableArrayList(paymentMethodSuggestions()));
        CategoryInput.setItemsForType(feeCategoryBox, database.listCategories(), "EXPENSE");
        if (categoryNameText().isBlank()) {
            CategoryInput.selectByName(feeCategoryBox, "Transaction Fees");
        }
        refreshAccountState();
    }

    private TransferForm readForm(boolean posting) {
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
        if (!"ACTIVE".equalsIgnoreCase(fromAccount.getStatus()) || !"ACTIVE".equalsIgnoreCase(toAccount.getStatus())) {
            throw new IllegalArgumentException("Both accounts must be active.");
        }

        LocalDate transferDate = transferDatePicker.getValue();
        if (transferDate == null) {
            throw new IllegalArgumentException("Select the transfer date.");
        }
        double amount = parsePositiveAmount(amountField.getText(), "Enter the transfer amount.");
        double fee = parseOptionalAmount(feeField.getText(), "Transfer fee cannot be negative.");
        if (posting && fromAccount.getCurrentBalance() + 0.005 < amount + fee) {
            throw new IllegalArgumentException("The source account does not have enough available funds to complete the transfer and fee.");
        }

        boolean sameCurrency = sameCurrency(fromAccount, toAccount);
        Double exchangeRate = null;
        double amountReceived = amount;
        if (!sameCurrency) {
            exchangeRate = parsePositiveAmount(exchangeRateField.getText(), "Enter the exchange rate for different currencies.");
            amountReceived = amount * exchangeRate;
        }

        Integer feeCategoryId = null;
        if (fee > 0) {
            feeCategoryId = CategoryInput.resolveCategoryId(database, feeCategoryBox, "EXPENSE");
        }

        return new TransferForm(
                fromAccount,
                toAccount,
                amount,
                amountReceived,
                fromAccount.getCurrency(),
                exchangeRate,
                fee,
                feeCategoryId,
                transferDate,
                paymentMethodValue(),
                clean(referenceField.getText()),
                descriptionWithExchangeRate(sameCurrency, exchangeRate)
        );
    }

    private void refreshAccountState() {
        Account fromAccount = fromAccountBox.getValue();
        Account toAccount = toAccountBox.getValue();
        fromBalanceLabel.setText(balanceText(fromAccount));
        toBalanceLabel.setText(balanceText(toAccount));
        currencyField.setText(fromAccount == null ? "" : clean(fromAccount.getCurrency()));

        boolean differentCurrency = fromAccount != null && toAccount != null && !sameCurrency(fromAccount, toAccount);
        exchangeRateField.setDisable(!differentCurrency);
        exchangeRateField.setPromptText(differentCurrency
                ? "Required for " + clean(fromAccount.getCurrency()) + " to " + clean(toAccount.getCurrency())
                : "Not required");
        if (!differentCurrency) {
            exchangeRateField.clear();
        }
        transferCheckLabel.setText(differentCurrency
                ? "The accounts use different currencies. Enter a reviewed exchange rate before posting."
                : "A transfer moves money between your own accounts; it is not income or an expense.");
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

    private String descriptionWithExchangeRate(boolean sameCurrency, Double exchangeRate) {
        List<String> parts = new ArrayList<>();
        String description = clean(descriptionArea.getText());
        if (!description.isBlank()) {
            parts.add(description);
        }
        if (!sameCurrency && exchangeRate != null) {
            parts.add("Exchange rate: " + exchangeRate);
        }
        return String.join("\n", parts);
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

    private String balanceText(Account account) {
        if (account == null) {
            return "Balance: -";
        }
        return "Balance: " + money(account.getCurrency(), account.getCurrentBalance());
    }

    private String money(String currency, double amount) {
        return clean(currency).isBlank() ? MONEY_FORMAT.format(amount) : clean(currency) + " " + MONEY_FORMAT.format(amount);
    }

    private boolean sameCurrency(Account first, Account second) {
        return clean(first.getCurrency()).equalsIgnoreCase(clean(second.getCurrency()));
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

    private String paymentMethodValue() {
        String value = paymentMethodBox.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = paymentMethodBox.getValue();
        }
        return clean(value);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record TransferForm(
            Account fromAccount,
            Account toAccount,
            double amountSent,
            double amountReceived,
            String currency,
            Double exchangeRate,
            double transferFee,
            Integer feeCategoryId,
            LocalDate transferDate,
            String paymentMethod,
            String referenceNumber,
            String description
    ) {
    }
}
