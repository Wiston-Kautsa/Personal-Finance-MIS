package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanInstallmentRecord;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanPaymentCommand;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanPaymentRecord;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanRecord;
import com.wk.pfmis.models.Account;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoanRepaymentController {
    private static final String EVENT_MANUAL = "Manual repayment";
    private static final String EVENT_AUTO_RETRY = "Automatic repayment retry";
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final Logger LOGGER = Logger.getLogger(LoanRepaymentController.class.getName());

    @FXML private ComboBox<String> eventBox;
    @FXML private Label personLabel;
    @FXML private ComboBox<String> personBox;
    @FXML private ComboBox<CentralLoanRecord> loanBox;
    @FXML private Label loanBalanceLabel;
    @FXML private DatePicker paymentDatePicker;
    @FXML private Label accountLabel;
    @FXML private ComboBox<Account> accountBox;
    @FXML private ComboBox<String> paymentMethodBox;
    @FXML private Label accountBalanceLabel;
    @FXML private TextField totalAmountField;
    @FXML private TextField principalField;
    @FXML private Label interestLabel;
    @FXML private TextField interestField;
    @FXML private Label penaltyLabel;
    @FXML private TextField penaltyField;
    @FXML private TextField referenceField;
    @FXML private TextArea notesArea;
    @FXML private Label allocationLabel;
    @FXML private TextArea resultArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<Account> accounts = List.of();
    private List<CentralLoanRecord> loans = List.of();

    @FXML
    public void initialize() {
        MONEY_FORMAT.setMinimumFractionDigits(2);
        MONEY_FORMAT.setMaximumFractionDigits(2);
        eventBox.setItems(FXCollections.observableArrayList(EVENT_MANUAL, EVENT_AUTO_RETRY));
        eventBox.getSelectionModel().select(EVENT_MANUAL);
        paymentDatePicker.setValue(LocalDate.now());
        principalField.setEditable(false);
        interestField.setEditable(false);
        penaltyField.setEditable(false);
        personLabel.setText("Lender");
        accountLabel.setText("Payment account");
        interestLabel.setText("Interest allocation");
        penaltyLabel.setText("Fees / penalty allocation");
        allocationLabel.setText("The service allocates the payment across penalties, fees, interest and principal, then updates the loan and account ledger together.");
        configureConverters();
        eventBox.valueProperty().addListener((observable, oldValue, newValue) -> updateLabels());
        personBox.setOnAction(event -> applyLoanFilter());
        loanBox.valueProperty().addListener((observable, oldValue, newValue) -> populateLoan(newValue));
        accountBox.valueProperty().addListener((observable, oldValue, newValue) -> updateAccountState());
        refresh();
        applyNavigationRequest();
    }

    @FXML
    private void recordRepayment() {
        CentralLoanRecord loan;
        Account account;
        LocalDate paymentDate;
        double amount;
        try {
            loan = loanBox.getValue();
            if (loan == null) {
                throw new IllegalArgumentException("Select the loan being repaid.");
            }
            account = accountBox.getValue();
            if (account == null) {
                throw new IllegalArgumentException("Select the payment account.");
            }
            paymentDate = paymentDatePicker.getValue();
            if (paymentDate == null) {
                throw new IllegalArgumentException("Enter the payment date.");
            }
            amount = parsePositiveAmount(totalAmountField, "Enter the amount being paid.");
        } catch (IllegalArgumentException exception) {
            resultArea.setText(exception.getMessage());
            return;
        }

        int paymentId;
        try {
            paymentId = database.recordCentralLoanPayment(new CentralLoanPaymentCommand(
                    loan.id(),
                    null,
                    account.getId(),
                    paymentDate,
                    amount,
                    safe(referenceField.getText()),
                    safe(notesArea.getText()),
                    EVENT_AUTO_RETRY.equals(eventBox.getValue())
            ));
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Loan repayment database posting failed before commit", exception);
            UiAlerts.info("Loan repayment could not be recorded. Please check the entered information and try again.");
            return;
        }

        try {
            CentralLoanRecord refreshed = database.getCentralLoan(loan.id());
            CentralLoanPaymentRecord payment = database.listCentralLoanPayments(loan.id()).stream()
                    .filter(row -> row.id() == paymentId)
                    .findFirst()
                    .orElse(null);
            resultArea.setText(resultText(refreshed, payment, amount));
            DataRefreshBus.notifyDataChanged();
            refresh();
            selectLoan(refreshed.id());
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Loan repayment recorded, but post-save UI refresh failed for payment " + paymentId, exception);
            UiAlerts.info("Loan repayment was recorded successfully, but the screen could not be refreshed. Do not record it again. Please refresh or reopen this page.");
        }
    }

    @FXML
    private void clearForm() {
        loanBox.setValue(null);
        totalAmountField.clear();
        principalField.clear();
        interestField.clear();
        penaltyField.clear();
        referenceField.clear();
        notesArea.clear();
        paymentDatePicker.setValue(LocalDate.now());
        resultArea.setText("Form cleared.");
        updateLoanLabels(null);
    }

    private void refresh() {
        accounts = database.listAccounts().stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(safe(account.getStatus())))
                .toList();
        loans = database.listCentralLoans().stream()
                .filter(this::canRecordRepayment)
                .toList();
        accountBox.setItems(FXCollections.observableArrayList(accounts));
        paymentMethodBox.setItems(FXCollections.observableArrayList(database.listPaymentMethodSuggestions()));
        List<String> lenders = loans.stream()
                .map(CentralLoanRecord::lenderName)
                .filter(name -> !safe(name).isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        personBox.setItems(FXCollections.observableArrayList(lenders));
        applyLoanFilter();
        updateLabels();
        updateAccountState();
    }

    private void applyNavigationRequest() {
        Integer requestedLoanId = NavigationBus.consumeRequestedLoanScheduleId();
        NavigationBus.consumeRequestedLoanDirection();
        String requestedLender = NavigationBus.consumeRequestedLoanPersonName();
        if (!safe(requestedLender).isBlank()) {
            personBox.setValue(requestedLender);
        }
        applyLoanFilter();
        if (requestedLoanId != null) {
            selectLoan(requestedLoanId);
        }
    }

    private void applyLoanFilter() {
        String lender = safe(personBox.getValue());
        CentralLoanRecord selected = loanBox.getValue();
        List<CentralLoanRecord> filtered = loans.stream()
                .filter(loan -> lender.isBlank() || safe(loan.lenderName()).equalsIgnoreCase(lender))
                .toList();
        loanBox.setItems(FXCollections.observableArrayList(filtered));
        if (selected != null) {
            filtered.stream()
                    .filter(loan -> loan.id() == selected.id())
                    .findFirst()
                    .ifPresentOrElse(loanBox::setValue, () -> loanBox.setValue(null));
        }
    }

    private void selectLoan(int loanId) {
        loanBox.getItems().stream()
                .filter(loan -> loan.id() == loanId)
                .findFirst()
                .ifPresent(loanBox::setValue);
    }

    private void populateLoan(CentralLoanRecord loan) {
        updateLoanLabels(loan);
        if (loan == null) {
            return;
        }
        if (loan.repaymentAccountId() != null) {
            accounts.stream()
                    .filter(account -> account.getId() == loan.repaymentAccountId())
                    .findFirst()
                    .ifPresent(accountBox::setValue);
        }
        if (!safe(loan.repaymentMethod()).isBlank()) {
            paymentMethodBox.setValue(loan.repaymentMethod());
        }
        double suggested = loan.nextPaymentAmount() > 0 ? loan.nextPaymentAmount() : loan.outstandingBalance();
        totalAmountField.setText(amountText(Math.min(suggested, loan.outstandingBalance())));
        CentralLoanInstallmentRecord next = database.listCentralLoanInstallments(loan.id()).stream()
                .filter(installment -> installment.remainingDue() > 0.005)
                .findFirst()
                .orElse(null);
        if (next != null) {
            principalField.setText(amountText(next.principalDue()));
            interestField.setText(amountText(next.interestDue()));
            penaltyField.setText(amountText(next.feesDue() + next.penaltyDue()));
        } else {
            principalField.clear();
            interestField.clear();
            penaltyField.clear();
        }
    }

    private void updateLabels() {
        boolean automatic = EVENT_AUTO_RETRY.equals(eventBox.getValue());
        allocationLabel.setText(automatic
                ? "Retry posts through the same loan repayment service. If funds are insufficient, the instalment remains outstanding and the failed attempt is logged."
                : "Principal reduces the loan liability. Interest, fees and penalties are recorded separately from principal.");
    }

    private void updateLoanLabels(CentralLoanRecord loan) {
        if (loan == null) {
            loanBalanceLabel.setText("Outstanding: -");
            return;
        }
        loanBalanceLabel.setText("Outstanding: " + money(loan.currency(), loan.outstandingBalance())
                + " | Next due: " + dash(loan.nextPaymentDate())
                + " | Status: " + displayToken(loan.status()));
    }

    private void updateAccountState() {
        Account account = accountBox.getValue();
        if (account == null) {
            accountBalanceLabel.setText("Account balance: -");
            return;
        }
        accountBalanceLabel.setText("Account balance: " + money(account.getCurrency(), account.getCurrentBalance()));
    }

    private void configureConverters() {
        loanBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CentralLoanRecord loan) {
                if (loan == null) {
                    return "";
                }
                return loan.loanNumber() + " - " + loan.loanName() + " - outstanding "
                        + money(loan.currency(), loan.outstandingBalance());
            }

            @Override
            public CentralLoanRecord fromString(String value) {
                return null;
            }
        });
        accountBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Account account) {
                return account == null ? "" : account.getAccountName() + " (" + safe(account.getCurrency()) + ")";
            }

            @Override
            public Account fromString(String value) {
                return null;
            }
        });
    }

    private boolean canRecordRepayment(CentralLoanRecord loan) {
        return loan.outstandingBalance() > 0.005
                && !List.of("DRAFT", "CANCELLED", "COMPLETED", "CLOSED").contains(safe(loan.status()).toUpperCase(Locale.ENGLISH));
    }

    private String resultText(CentralLoanRecord loan, CentralLoanPaymentRecord payment, double requestedAmount) {
        if (payment == null) {
            return "Repayment posted. Outstanding balance: " + money(loan.currency(), loan.outstandingBalance());
        }
        return """
                Repayment recorded.

                Loan: %s
                Amount requested: %s
                Principal paid: %s
                Interest paid: %s
                Fees paid: %s
                Penalties paid: %s
                Total posted: %s
                Remaining outstanding: %s
                Transaction ID: %s
                Status: %s
                """.formatted(
                loan.loanNumber(),
                money(loan.currency(), requestedAmount),
                money(loan.currency(), payment.principalPaid()),
                money(loan.currency(), payment.interestPaid()),
                money(loan.currency(), payment.feesPaid()),
                money(loan.currency(), payment.penaltyPaid()),
                money(loan.currency(), payment.totalPaid()),
                money(loan.currency(), loan.outstandingBalance()),
                payment.transactionId() == null ? "-" : "#" + payment.transactionId(),
                displayToken(loan.status())
        );
    }

    private double parsePositiveAmount(TextField field, String message) {
        String text = safe(field.getText()).replace(",", "");
        if (text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        try {
            double amount = Double.parseDouble(text);
            if (amount <= 0) {
                throw new IllegalArgumentException(message);
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid repayment amount.");
        }
    }

    private String amountText(double amount) {
        return String.format(Locale.ENGLISH, "%.2f", amount);
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
