package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanRecord;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanRegistrationCommand;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupProfileRecord;
import com.wk.pfmis.models.Account;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class NewLoanController {
    private static final List<String> LENDER_TYPES = List.of(
            "Commercial Bank",
            "Microfinance Institution",
            "Village Bank / Bank Nkhonde",
            "Savings Group / Chipeleganyu",
            "Employer",
            "Individual",
            "Friend / Family",
            "Other"
    );
    private static final List<String> INTEREST_METHODS = List.of(
            "No Interest",
            "Fixed / Flat Interest Amount",
            "Flat Percentage Interest",
            "Reducing Balance Interest"
    );
    private static final List<String> FREQUENCIES = List.of("Weekly", "Fortnightly", "Monthly", "Quarterly", "Yearly", "One Time");
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    @FXML private TextField loanNameField;
    @FXML private ComboBox<String> lenderTypeBox;
    @FXML private TextField lenderField;
    @FXML private ComboBox<SavingsGroupProfileRecord> savingsGroupBox;
    @FXML private TextField principalField;
    @FXML private TextField currencyField;
    @FXML private DatePicker startDatePicker;
    @FXML private ComboBox<String> frequencyBox;
    @FXML private TextField repaymentMethodField;
    @FXML private ComboBox<String> interestMethodBox;
    @FXML private TextField interestRateField;
    @FXML private TextField fixedInterestField;
    @FXML private TextField feesField;
    @FXML private TextField installmentsField;
    @FXML private DatePicker firstRepaymentDatePicker;
    @FXML private ComboBox<Account> proceedsAccountBox;
    @FXML private ComboBox<Account> repaymentAccountBox;
    @FXML private ComboBox<String> repaymentModeBox;
    @FXML private TextField automaticDayField;
    @FXML private TextField automaticAmountField;
    @FXML private DatePicker automaticStartDatePicker;
    @FXML private DatePicker automaticEndDatePicker;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextField referenceField;
    @FXML private TextArea notesArea;
    @FXML private Label proceedsBalanceLabel;
    @FXML private Label repaymentBalanceLabel;
    @FXML private Label validationSummaryLabel;
    @FXML private Label loanNameError;
    @FXML private Label lenderTypeError;
    @FXML private Label lenderError;
    @FXML private Label savingsGroupError;
    @FXML private Label principalError;
    @FXML private Label startDateError;
    @FXML private Label firstRepaymentDateError;
    @FXML private Label proceedsAccountError;
    @FXML private Label repaymentAccountError;
    @FXML private Label interestError;
    @FXML private Label installmentsError;
    @FXML private Label automaticError;
    @FXML private TextArea resultArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Account> accounts = List.of();
    private List<SavingsGroupProfileRecord> savingsGroups = List.of();

    @FXML
    public void initialize() {
        MONEY_FORMAT.setMinimumFractionDigits(2);
        MONEY_FORMAT.setMaximumFractionDigits(2);
        configureConverters();
        configureChoiceLists();
        bindState();
        refresh();
        resultArea.setText("Register borrowed money once under Loans. Loan proceeds increase the selected account as borrowed money, not normal income.");
    }

    @FXML
    private void registerLoan() {
        clearValidation();
        try {
            CentralLoanRegistrationCommand command = readCommand();
            int loanId = database.registerBorrowedLoan(command);
            CentralLoanRecord loan = database.getCentralLoan(loanId);
            resultArea.setText("""
                    Loan registered.

                    Loan number: %s
                    Lender: %s
                    Source type: %s
                    Principal received: %s
                    Total repayable: %s
                    Outstanding: %s
                    First repayment: %s
                    Linked Savings Group: %s
                    Status: %s
                    """.formatted(
                    loan.loanNumber(),
                    loan.lenderName(),
                    displayToken(loan.lenderType()),
                    money(loan.currency(), loan.principalAmount()),
                    money(loan.currency(), loan.totalRepayable()),
                    money(loan.currency(), loan.outstandingBalance()),
                    dash(loan.firstPaymentDate()),
                    dash(loan.savingsGroupName()),
                    displayToken(loan.status())
            ));
            DataRefreshBus.notifyDataChanged();
            refresh();
        } catch (IllegalArgumentException exception) {
            validationSummaryLabel.setText(exception.getMessage());
        } catch (RuntimeException exception) {
            validationSummaryLabel.setText("Failed to register the loan. Review the fields and try again.");
            UiAlerts.error("Failed to register loan", exception);
        }
    }

    @FXML
    private void clearForm() {
        clearValidation();
        loanNameField.clear();
        lenderField.clear();
        savingsGroupBox.setValue(null);
        principalField.clear();
        currencyField.setText("MWK");
        startDatePicker.setValue(LocalDate.now());
        frequencyBox.getSelectionModel().select("Monthly");
        repaymentMethodField.setText("Bank transfer");
        interestMethodBox.getSelectionModel().select("No Interest");
        interestRateField.setText("0");
        fixedInterestField.setText("0");
        feesField.setText("0");
        installmentsField.setText("1");
        firstRepaymentDatePicker.setValue(LocalDate.now().plusMonths(1));
        proceedsAccountBox.setValue(null);
        repaymentAccountBox.setValue(null);
        repaymentModeBox.getSelectionModel().select("Manual");
        automaticDayField.clear();
        automaticAmountField.clear();
        automaticStartDatePicker.setValue(null);
        automaticEndDatePicker.setValue(null);
        statusBox.getSelectionModel().select("Active");
        referenceField.clear();
        notesArea.clear();
        updateDependentState();
        resultArea.setText("Form cleared.");
    }

    private void refresh() {
        accounts = database.listAccounts().stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(safe(account.getStatus())))
                .toList();
        savingsGroups = database.listSavingsGroupProfiles().stream()
                .filter(group -> !"INACTIVE".equalsIgnoreCase(safe(group.status())))
                .toList();
        proceedsAccountBox.setItems(FXCollections.observableArrayList(accounts));
        repaymentAccountBox.setItems(FXCollections.observableArrayList(accounts));
        savingsGroupBox.setItems(FXCollections.observableArrayList(savingsGroups));
        if (startDatePicker.getValue() == null) {
            startDatePicker.setValue(LocalDate.now());
        }
        if (firstRepaymentDatePicker.getValue() == null) {
            firstRepaymentDatePicker.setValue(LocalDate.now().plusMonths(1));
        }
        if (currencyField.getText() == null || currencyField.getText().isBlank()) {
            currencyField.setText("MWK");
        }
        updateDependentState();
    }

    private void configureChoiceLists() {
        lenderTypeBox.setItems(FXCollections.observableArrayList(LENDER_TYPES));
        lenderTypeBox.getSelectionModel().select("Commercial Bank");
        frequencyBox.setItems(FXCollections.observableArrayList(FREQUENCIES));
        frequencyBox.getSelectionModel().select("Monthly");
        interestMethodBox.setItems(FXCollections.observableArrayList(INTEREST_METHODS));
        interestMethodBox.getSelectionModel().select("No Interest");
        repaymentModeBox.setItems(FXCollections.observableArrayList("Manual", "Automatic"));
        repaymentModeBox.getSelectionModel().select("Manual");
        statusBox.setItems(FXCollections.observableArrayList("Active"));
        statusBox.getSelectionModel().select("Active");
        repaymentMethodField.setText("Bank transfer");
        interestRateField.setText("0");
        fixedInterestField.setText("0");
        feesField.setText("0");
        installmentsField.setText("1");
        currencyField.setText("MWK");
    }

    private void configureConverters() {
        StringConverter<Account> accountConverter = new StringConverter<>() {
            @Override
            public String toString(Account account) {
                return account == null ? "" : account.getAccountName() + " (" + safe(account.getCurrency()) + ")";
            }

            @Override
            public Account fromString(String value) {
                String clean = safe(value);
                return accounts.stream()
                        .filter(account -> clean.equalsIgnoreCase(account.getAccountName())
                                || clean.startsWith(account.getAccountName()))
                        .findFirst()
                        .orElse(null);
            }
        };
        proceedsAccountBox.setConverter(accountConverter);
        repaymentAccountBox.setConverter(accountConverter);
        savingsGroupBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(SavingsGroupProfileRecord group) {
                return group == null ? "" : group.groupName() + " - " + group.groupType();
            }

            @Override
            public SavingsGroupProfileRecord fromString(String value) {
                String clean = safe(value);
                return savingsGroups.stream()
                        .filter(group -> clean.equalsIgnoreCase(group.groupName())
                                || clean.startsWith(group.groupName()))
                        .findFirst()
                        .orElse(null);
            }
        });
    }

    private void bindState() {
        lenderTypeBox.setOnAction(event -> updateDependentState());
        savingsGroupBox.setOnAction(event -> copySavingsGroupLender());
        proceedsAccountBox.setOnAction(event -> {
            Account account = proceedsAccountBox.getValue();
            if (account != null) {
                currencyField.setText(safe(account.getCurrency()).isBlank() ? "MWK" : account.getCurrency());
            }
            updateDependentState();
        });
        repaymentAccountBox.setOnAction(event -> updateDependentState());
        interestMethodBox.setOnAction(event -> updateDependentState());
        repaymentModeBox.setOnAction(event -> updateDependentState());
    }

    private void updateDependentState() {
        boolean savingsOrigin = savingsGroupRequired();
        savingsGroupBox.setDisable(!savingsOrigin);
        if (!savingsOrigin) {
            savingsGroupBox.setValue(null);
        }
        String interestMethod = safe(interestMethodBox.getValue());
        boolean fixedInterest = "Fixed / Flat Interest Amount".equals(interestMethod);
        boolean rateInterest = "Flat Percentage Interest".equals(interestMethod) || "Reducing Balance Interest".equals(interestMethod);
        fixedInterestField.setDisable(!fixedInterest);
        interestRateField.setDisable(!rateInterest);
        boolean automatic = "Automatic".equalsIgnoreCase(safe(repaymentModeBox.getValue()));
        for (Node node : List.of(automaticDayField, automaticAmountField, automaticStartDatePicker, automaticEndDatePicker)) {
            node.setDisable(!automatic);
        }
        updateAccountLabel(proceedsAccountBox.getValue(), proceedsBalanceLabel, "Proceeds account balance");
        updateAccountLabel(repaymentAccountBox.getValue(), repaymentBalanceLabel, "Repayment account balance");
    }

    private void copySavingsGroupLender() {
        SavingsGroupProfileRecord group = savingsGroupBox.getValue();
        if (group != null) {
            lenderField.setText(group.groupName());
        }
    }

    private CentralLoanRegistrationCommand readCommand() {
        String loanName = requiredText(loanNameField, loanNameError, "Loan Name is required.");
        String lenderType = requiredCombo(lenderTypeBox, lenderTypeError, "Lender Type is required.");
        SavingsGroupProfileRecord savingsGroup = savingsGroupRequired()
                ? requiredSavingsGroup()
                : savingsGroupBox.getValue();
        String lender = requiredText(lenderField, lenderError, "Lender is required.");
        double principal = requiredPositiveMoney(principalField, principalError, "Principal Amount must be greater than zero.");
        LocalDate startDate = requiredDate(startDatePicker, startDateError, "Loan Start Date is required.");
        LocalDate firstRepayment = requiredDate(firstRepaymentDatePicker, firstRepaymentDateError, "First Repayment Date is required.");
        if (firstRepayment.isBefore(startDate)) {
            fail(firstRepaymentDatePicker, firstRepaymentDateError, "First Repayment Date cannot be before Loan Start Date.");
        }
        Account proceeds = requiredAccount(proceedsAccountBox, proceedsAccountError, "Account where loan proceeds were received is required.");
        Account repayment = requiredAccount(repaymentAccountBox, repaymentAccountError, "Repayment Account is required.");
        String currency = requiredText(currencyField, principalError, "Currency is required.").toUpperCase(Locale.ENGLISH);
        if (!currency.equalsIgnoreCase(safe(proceeds.getCurrency()))) {
            fail(proceedsAccountBox, proceedsAccountError, "Loan currency must match the proceeds account currency.");
        }
        if (!currency.equalsIgnoreCase(safe(repayment.getCurrency()))) {
            fail(repaymentAccountBox, repaymentAccountError, "Loan currency must match the repayment account currency.");
        }
        String interestMethod = requiredCombo(interestMethodBox, interestError, "Interest Calculation Method is required.");
        double interestRate = parseNonNegative(interestRateField, interestError, "Interest Rate cannot be negative.");
        double fixedInterest = parseNonNegative(fixedInterestField, interestError, "Fixed Interest Amount cannot be negative.");
        double fees = parseNonNegative(feesField, interestError, "Fees cannot be negative.");
        if (interestMethod.equals("Flat Percentage Interest") || interestMethod.equals("Reducing Balance Interest")) {
            if (interestRate <= 0) {
                fail(interestRateField, interestError, "Interest Rate must be greater than zero for the selected method.");
            }
        }
        if (interestMethod.equals("Fixed / Flat Interest Amount") && fixedInterest <= 0) {
            fail(fixedInterestField, interestError, "Fixed Interest Amount must be greater than zero for the selected method.");
        }
        int installments = parsePositiveInt(installmentsField, installmentsError, "Number of Instalments must be greater than zero.");
        String repaymentMode = requiredCombo(repaymentModeBox, automaticError, "Repayment Mode is required.");
        Integer automaticDay = null;
        Double automaticAmount = null;
        LocalDate automaticStart = automaticStartDatePicker.getValue();
        LocalDate automaticEnd = automaticEndDatePicker.getValue();
        if ("Automatic".equalsIgnoreCase(repaymentMode)) {
            automaticDay = parseDayOfMonth(automaticDayField, automaticError);
            automaticAmount = parsePositiveOptional(automaticAmountField, automaticError, "Automatic repayment amount must be greater than zero.");
            if (automaticStart == null || automaticEnd == null) {
                fail(automaticStartDatePicker, automaticError, "Automatic repayment start and end dates are required.");
            }
            if (automaticEnd.isBefore(automaticStart)) {
                fail(automaticEndDatePicker, automaticError, "Automatic repayment end date cannot be before start date.");
            }
        }
        return new CentralLoanRegistrationCommand(
                loanName,
                lenderType,
                lender,
                savingsGroup == null ? null : savingsGroup.id(),
                principal,
                currency,
                startDate,
                requiredCombo(frequencyBox, installmentsError, "Repayment Frequency is required."),
                requiredText(repaymentMethodField, repaymentAccountError, "Repayment Method is required."),
                interestMethod,
                interestRate,
                fixedInterest,
                fees,
                installments,
                firstRepayment,
                proceeds.getId(),
                repayment.getId(),
                repaymentMode,
                automaticDay,
                automaticAmount,
                automaticStart,
                automaticEnd,
                requiredCombo(statusBox, automaticError, "Loan Status is required."),
                safe(referenceField.getText()),
                safe(notesArea.getText())
        );
    }

    private boolean savingsGroupRequired() {
        String lenderType = safe(lenderTypeBox.getValue());
        return lenderType.equals("Village Bank / Bank Nkhonde") || lenderType.equals("Savings Group / Chipeleganyu");
    }

    private SavingsGroupProfileRecord requiredSavingsGroup() {
        SavingsGroupProfileRecord group = savingsGroupBox.getValue();
        if (group == null) {
            fail(savingsGroupBox, savingsGroupError, "Select the registered Savings Group that originated this loan.");
        }
        return group;
    }

    private String requiredText(TextField field, Label errorLabel, String message) {
        String value = safe(field.getText());
        if (value.isBlank()) {
            fail(field, errorLabel, message);
        }
        return value;
    }

    private String requiredCombo(ComboBox<String> box, Label errorLabel, String message) {
        String value = safe(box.getValue());
        if (value.isBlank()) {
            fail(box, errorLabel, message);
        }
        return value;
    }

    private LocalDate requiredDate(DatePicker picker, Label errorLabel, String message) {
        LocalDate date = picker.getValue();
        if (date == null) {
            fail(picker, errorLabel, message);
        }
        return date;
    }

    private Account requiredAccount(ComboBox<Account> box, Label errorLabel, String message) {
        Account account = box.getValue();
        if (account == null) {
            fail(box, errorLabel, message);
        }
        return account;
    }

    private double requiredPositiveMoney(TextField field, Label errorLabel, String message) {
        double amount = parseNonNegative(field, errorLabel, message);
        if (amount <= 0) {
            fail(field, errorLabel, message);
        }
        return amount;
    }

    private double parseNonNegative(TextField field, Label errorLabel, String message) {
        String text = safe(field.getText()).replace(",", "");
        if (text.isBlank()) {
            return 0;
        }
        try {
            double value = Double.parseDouble(text);
            if (value < 0) {
                fail(field, errorLabel, message);
            }
            return value;
        } catch (NumberFormatException exception) {
            fail(field, errorLabel, "Enter a valid number.");
            return 0;
        }
    }

    private int parsePositiveInt(TextField field, Label errorLabel, String message) {
        String text = safe(field.getText()).replace(",", "");
        try {
            int value = Integer.parseInt(text);
            if (value <= 0) {
                fail(field, errorLabel, message);
            }
            return value;
        } catch (NumberFormatException exception) {
            fail(field, errorLabel, "Enter a valid number of instalments.");
            return 0;
        }
    }

    private int parseDayOfMonth(TextField field, Label errorLabel) {
        try {
            int day = Integer.parseInt(safe(field.getText()));
            if (day < 1 || day > 31) {
                fail(field, errorLabel, "Automatic repayment day must be between 1 and 31.");
            }
            return day;
        } catch (NumberFormatException exception) {
            fail(field, errorLabel, "Automatic repayment day must be between 1 and 31.");
            return 1;
        }
    }

    private double parsePositiveOptional(TextField field, Label errorLabel, String message) {
        double amount = parseNonNegative(field, errorLabel, message);
        if (amount <= 0) {
            fail(field, errorLabel, message);
        }
        return amount;
    }

    private void fail(Control control, Label errorLabel, String message) {
        markInvalid(control);
        if (errorLabel != null) {
            errorLabel.setText(message);
        }
        throw new IllegalArgumentException(message);
    }

    private void clearValidation() {
        validationSummaryLabel.setText("");
        for (Label label : List.of(
                loanNameError,
                lenderTypeError,
                lenderError,
                savingsGroupError,
                principalError,
                startDateError,
                firstRepaymentDateError,
                proceedsAccountError,
                repaymentAccountError,
                interestError,
                installmentsError,
                automaticError
        )) {
            label.setText("");
        }
        for (Control control : List.of(
                loanNameField,
                lenderTypeBox,
                lenderField,
                savingsGroupBox,
                principalField,
                currencyField,
                startDatePicker,
                firstRepaymentDatePicker,
                proceedsAccountBox,
                repaymentAccountBox,
                interestMethodBox,
                interestRateField,
                fixedInterestField,
                feesField,
                installmentsField,
                repaymentModeBox,
                automaticDayField,
                automaticAmountField,
                automaticStartDatePicker,
                automaticEndDatePicker
        )) {
            control.getStyleClass().remove("field-error");
        }
    }

    private void markInvalid(Control control) {
        if (control != null && !control.getStyleClass().contains("field-error")) {
            control.getStyleClass().add("field-error");
        }
    }

    private void updateAccountLabel(Account account, Label label, String prefix) {
        if (account == null) {
            label.setText(prefix + ": -");
            return;
        }
        label.setText(prefix + ": " + money(account.getCurrency(), account.getCurrentBalance()));
    }

    private String money(String currency, double amount) {
        String cleanCurrency = safe(currency).isBlank() ? "MWK" : currency;
        return cleanCurrency + " " + MONEY_FORMAT.format(amount);
    }

    private String displayToken(String value) {
        String clean = safe(value).replace('_', ' ').toLowerCase(Locale.ENGLISH);
        if (clean.isBlank()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : clean.split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ENGLISH)).append(part.substring(1));
        }
        return builder.toString();
    }

    private String dash(String value) {
        return safe(value).isBlank() ? "-" : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
