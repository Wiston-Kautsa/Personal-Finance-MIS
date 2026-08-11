package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.TransferFxMetadata;
import com.wk.pfmis.db.DatabaseHandler.TransferPostingResult;
import com.wk.pfmis.fx.ExchangeRateQuote;
import com.wk.pfmis.fx.ExchangeRateService;
import com.wk.pfmis.fx.ExchangeRateSource;
import com.wk.pfmis.fx.FxMath;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TransferMoneyController {
    private static final String STATUS_DRAFT = "Draft";
    private static final String STATUS_POSTED = "Posted";
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final Logger LOGGER = Logger.getLogger(TransferMoneyController.class.getName());

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
    @FXML private VBox exchangeRateCard;
    @FXML private Label exchangeRateDisplayLabel;
    @FXML private Label exchangeRateSourceLabel;
    @FXML private Label exchangeRateUpdatedLabel;
    @FXML private Label estimatedReceivedLabel;
    @FXML private Button refreshRateButton;
    @FXML private Button manualRateButton;
    @FXML private Label transferCheckLabel;
    @FXML private TextArea resultArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final ExchangeRateService exchangeRateService = ExchangeRateService.getInstance();
    private ExchangeRateQuote selectedQuote;
    private CompletableFuture<?> pendingRateLoad;

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
        amountField.textProperty().addListener((observable, oldValue, newValue) -> updateEstimatedReceived());
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
        TransferForm form;
        try {
            form = readForm(true);
            if (form.exchangeRateQuote() != null && !UiAlerts.confirm("Transfer Summary", transferSummary(form))) {
                return;
            }
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
        } catch (IllegalArgumentException exception) {
            UiAlerts.info(exception.getMessage());
            return;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Transfer validation or duplicate check failed before posting", exception);
            UiAlerts.info("Transfer could not be posted. Please check the entered information and try again.");
            return;
        }

        TransferPostingResult result;
        try {
            result = database.recordTransferWithFee(
                    form.fromAccount().getId(),
                    form.toAccount().getId(),
                    form.amountSent(),
                    form.amountReceived(),
                    form.transferFee(),
                    form.feeCategoryId(),
                    form.transferDate(),
                    form.description(),
                    form.paymentMethod(),
                    form.referenceNumber(),
                    fxMetadata(form)
            );
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Transfer database posting failed before commit", exception);
            UiAlerts.info("Transfer could not be posted. Please check the entered information and try again.");
            return;
        }

        try {
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
                    Exchange rate: %s
                    Reference: %s
                    """.formatted(
                    form.fromAccount().getAccountName(),
                    form.toAccount().getAccountName(),
                    money(form.fromAccount().getCurrency(), form.amountSent()),
                    money(form.toAccount().getCurrency(), form.amountReceived()),
                    money(form.fromAccount().getCurrency(), form.transferFee()),
                    money(form.fromAccount().getCurrency(), result.sourceBalance()),
                    money(form.toAccount().getCurrency(), result.destinationBalance()),
                    form.exchangeRateQuote() == null ? "1" : rateText(form.exchangeRateQuote()),
                    result.transferReference()
            ));
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Transfer posted, but post-save UI refresh failed for reference " + result.transferReference(), exception);
            UiAlerts.info("Transfer was posted successfully, but the screen could not be refreshed. Do not post it again. Please refresh or reopen this page.");
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
        selectedQuote = null;
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
        ExchangeRateQuote quote = null;
        if (!sameCurrency) {
            quote = selectedQuote;
            if (quote == null) {
                throw new IllegalArgumentException("Exchange rate unavailable. Connect to the internet to obtain the latest rate or enter a manual rate.");
            }
            exchangeRate = quote.rate().doubleValue();
            amountReceived = FxMath.convert(BigDecimal.valueOf(amount), quote.rate()).doubleValue();
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
                descriptionWithExchangeRate(sameCurrency, quote),
                quote
        );
    }

    private void refreshAccountState() {
        Account fromAccount = fromAccountBox.getValue();
        Account toAccount = toAccountBox.getValue();
        fromBalanceLabel.setText(balanceText(fromAccount));
        toBalanceLabel.setText(balanceText(toAccount));
        currencyField.setText(fromAccount == null ? "" : clean(fromAccount.getCurrency()));

        boolean differentCurrency = fromAccount != null && toAccount != null && !sameCurrency(fromAccount, toAccount);
        exchangeRateField.setDisable(true);
        exchangeRateField.setPromptText(differentCurrency
                ? "Automatic rate for " + clean(fromAccount.getCurrency()) + " to " + clean(toAccount.getCurrency())
                : "Not required");
        if (!differentCurrency) {
            exchangeRateField.clear();
            selectedQuote = null;
            showExchangeRateCard(false);
        } else {
            showExchangeRateCard(true);
            loadExchangeRate();
        }
        transferCheckLabel.setText(differentCurrency
                ? "The accounts use different currencies. Review the exchange-rate card before posting."
                : "A transfer moves money between your own accounts; it is not income or an expense.");
    }

    @FXML
    private void refreshExchangeRate() {
        loadExchangeRate();
    }

    @FXML
    private void useManualRate() {
        Account fromAccount = fromAccountBox.getValue();
        Account toAccount = toAccountBox.getValue();
        if (fromAccount == null || toAccount == null || sameCurrency(fromAccount, toAccount)) {
            UiAlerts.info("Select two accounts with different currencies first.");
            return;
        }
        Dialog<ExchangeRateQuote> dialog = new Dialog<>();
        dialog.setTitle("Enter Manual Exchange Rate");
        dialog.setHeaderText(clean(fromAccount.getCurrency()) + " to " + clean(toAccount.getCurrency()));
        TextField rateField = new TextField();
        rateField.setPromptText("Exchange rate");
        TextField notesField = new TextField();
        notesField.setPromptText("Reason or notes");
        Label preview = new Label("Enter a rate greater than 0.");
        preview.setWrapText(true);
        rateField.textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                BigDecimal rate = parsePositiveDecimal(newValue, "Exchange rate must be greater than 0.");
                preview.setText("1 " + clean(fromAccount.getCurrency()) + " = " + clean(toAccount.getCurrency()) + " " + formatNumber(rate));
            } catch (RuntimeException exception) {
                preview.setText("Exchange rate must be greater than 0.");
            }
        });
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("From Currency *"), 0, 0);
        grid.add(new Label(clean(fromAccount.getCurrency())), 1, 0);
        grid.add(new Label("To Currency *"), 0, 1);
        grid.add(new Label(clean(toAccount.getCurrency())), 1, 1);
        grid.add(new Label("Exchange Rate *"), 0, 2);
        grid.add(rateField, 1, 2);
        grid.add(new Label("Reason / Notes"), 0, 3);
        grid.add(notesField, 1, 3);
        grid.add(preview, 1, 4);
        dialog.getDialogPane().setContent(grid);
        ButtonType save = new ButtonType("Save Manual Rate", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (!save.equals(button)) {
                return null;
            }
            BigDecimal rate = parsePositiveDecimal(rateField.getText(), "Exchange rate must be greater than 0.");
            return new ExchangeRateQuote(
                    clean(fromAccount.getCurrency()),
                    clean(toAccount.getCurrency()),
                    rate,
                    transferDatePicker.getValue() == null ? LocalDate.now() : transferDatePicker.getValue(),
                    Instant.now(),
                    "PFMIS Manual Rate",
                    ExchangeRateSource.MANUAL,
                    "MANUAL",
                    true,
                    false,
                    notesField.getText()
            );
        });
        Optional<ExchangeRateQuote> result = dialog.showAndWait();
        result.ifPresent(quote -> {
            exchangeRateService.saveManualRate(
                    quote.fromCurrency(),
                    quote.toCurrency(),
                    quote.rate(),
                    quote.effectiveDate(),
                    null,
                    quote.notes()
            );
            selectedQuote = quote;
            applyQuote(quote);
        });
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

    private String descriptionWithExchangeRate(boolean sameCurrency, ExchangeRateQuote quote) {
        List<String> parts = new ArrayList<>();
        String description = clean(descriptionArea.getText());
        if (!description.isBlank()) {
            parts.add(description);
        }
        if (!sameCurrency && quote != null) {
            parts.add("Exchange rate locked: " + rateText(quote)
                    + " | Source: " + quote.source()
                    + " | Retrieved: " + quote.retrievedAt());
        }
        return String.join("\n", parts);
    }

    private void loadExchangeRate() {
        Account fromAccount = fromAccountBox.getValue();
        Account toAccount = toAccountBox.getValue();
        if (fromAccount == null || toAccount == null || sameCurrency(fromAccount, toAccount)) {
            return;
        }
        if (pendingRateLoad != null && !pendingRateLoad.isDone()) {
            pendingRateLoad.cancel(true);
        }
        selectedQuote = null;
        exchangeRateField.setText("");
        exchangeRateDisplayLabel.setText("Loading...");
        exchangeRateSourceLabel.setText("Updating exchange rate...");
        exchangeRateUpdatedLabel.setText("");
        estimatedReceivedLabel.setText("Estimated amount received: Loading...");
        if (refreshRateButton != null) {
            refreshRateButton.setDisable(true);
            refreshRateButton.setText("Updating...");
        }
        pendingRateLoad = CompletableFuture
                .supplyAsync(() -> exchangeRateService.getLatestRate(fromAccount.getCurrency(), toAccount.getCurrency()))
                .whenComplete((quote, throwable) -> Platform.runLater(() -> {
                    if (refreshRateButton != null) {
                        refreshRateButton.setDisable(false);
                        refreshRateButton.setText("Refresh Rate");
                    }
                    if (throwable == null) {
                        selectedQuote = quote;
                        applyQuote(quote);
                    } else {
                        selectedQuote = null;
                        exchangeRateDisplayLabel.setText("Exchange rate unavailable");
                        exchangeRateSourceLabel.setText("Connect to the internet or enter a manual rate.");
                        exchangeRateUpdatedLabel.setText("");
                        estimatedReceivedLabel.setText("Estimated amount received: Unavailable");
                    }
                }));
    }

    private void applyQuote(ExchangeRateQuote quote) {
        exchangeRateField.setText(quote.rate().toPlainString());
        exchangeRateDisplayLabel.setText(rateText(quote));
        exchangeRateSourceLabel.setText("Source: " + quote.source() + " | Provider: " + quote.providerName());
        exchangeRateUpdatedLabel.setText("Updated: " + timestampText(quote.retrievedAt()));
        updateEstimatedReceived();
    }

    private void updateEstimatedReceived() {
        if (selectedQuote == null || estimatedReceivedLabel == null) {
            return;
        }
        try {
            BigDecimal amount = parsePositiveDecimal(amountField.getText(), "Enter the transfer amount.");
            BigDecimal converted = FxMath.convert(amount, selectedQuote.rate());
            estimatedReceivedLabel.setText("Estimated amount received: " + money(selectedQuote.toCurrency(), converted.doubleValue()));
        } catch (RuntimeException exception) {
            estimatedReceivedLabel.setText("Estimated amount received: Enter an amount.");
        }
    }

    private void showExchangeRateCard(boolean visible) {
        if (exchangeRateCard != null) {
            exchangeRateCard.setVisible(visible);
            exchangeRateCard.setManaged(visible);
        }
    }

    private TransferFxMetadata fxMetadata(TransferForm form) {
        ExchangeRateQuote quote = form.exchangeRateQuote();
        if (quote == null) {
            return null;
        }
        return new TransferFxMetadata(
                BigDecimal.valueOf(form.amountSent()),
                form.fromAccount().getCurrency(),
                quote.rate(),
                BigDecimal.valueOf(form.amountReceived()),
                form.toAccount().getCurrency(),
                quote.source().name(),
                quote.retrievedAt(),
                quote.providerName(),
                quote.rateType(),
                quote.effectiveDate()
        );
    }

    private String transferSummary(TransferForm form) {
        ExchangeRateQuote quote = form.exchangeRateQuote();
        return """
                From:
                %s

                Amount:
                %s

                Exchange Rate:
                %s

                Rate Source:
                %s

                Destination:
                %s

                Amount Received:
                %s
                """.formatted(
                form.fromAccount().getAccountName(),
                money(form.fromAccount().getCurrency(), form.amountSent()),
                quote == null ? "1" : rateText(quote),
                quote == null ? "Same currency" : quote.source(),
                form.toAccount().getAccountName(),
                money(form.toAccount().getCurrency(), form.amountReceived())
        );
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

    private BigDecimal parsePositiveDecimal(String value, String emptyMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        try {
            BigDecimal amount = new BigDecimal(value.replace(",", "").trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Exchange rate must be greater than 0.");
            }
            return amount;
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

    private String rateText(ExchangeRateQuote quote) {
        return "1 " + quote.fromCurrency() + " = " + quote.toCurrency() + " " + formatNumber(quote.rate());
    }

    private String formatNumber(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(6);
        return format.format(amount);
    }

    private String timestampText(Instant instant) {
        return instant == null
                ? "-"
                : DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault())
                .format(instant);
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
            String description,
            ExchangeRateQuote exchangeRateQuote
    ) {
    }
}
