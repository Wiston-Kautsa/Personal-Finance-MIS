package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanInstallmentRecord;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanPaymentRecord;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanRecord;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class LoanLedgerController {
    private static final String ALL_TYPES = "All Sources";
    private static final String ALL_STATUSES = "All Statuses";
    private static final String ALL_LENDERS = "All Lenders";
    private static final String ALL_DUE = "All Due States";
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    @FXML private ComboBox<String> typeFilterBox;
    @FXML private ComboBox<String> statusFilterBox;
    @FXML private ComboBox<String> personFilterBox;
    @FXML private ComboBox<String> dueStatusFilterBox;
    @FXML private TextField searchField;
    @FXML private TableView<LoanLedgerRow> loanTable;
    @FXML private TableColumn<LoanLedgerRow, String> loanColumn;
    @FXML private TableColumn<LoanLedgerRow, String> personColumn;
    @FXML private TableColumn<LoanLedgerRow, String> typeColumn;
    @FXML private TableColumn<LoanLedgerRow, String> principalColumn;
    @FXML private TableColumn<LoanLedgerRow, String> outstandingColumn;
    @FXML private TableColumn<LoanLedgerRow, String> nextPaymentColumn;
    @FXML private TableColumn<LoanLedgerRow, String> statusColumn;
    @FXML private TextArea detailsArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<CentralLoanRecord> loans = List.of();

    @FXML
    public void initialize() {
        MONEY_FORMAT.setMinimumFractionDigits(2);
        MONEY_FORMAT.setMaximumFractionDigits(2);
        configureFilters();
        configureTable();
        refresh();
    }

    @FXML
    private void openLoan() {
        CentralLoanRecord loan = selectedLoan();
        if (loan == null) {
            UiAlerts.info("Select a loan to open.");
            return;
        }
        detailsArea.setText(loanProfile(loan));
    }

    @FXML
    private void moreActions() {
        CentralLoanRecord loan = selectedLoan();
        if (loan == null) {
            UiAlerts.info("Select a loan first.");
            return;
        }
        List<String> actions = actionsFor(loan);
        ChoiceDialog<String> dialog = new ChoiceDialog<>(actions.get(0), actions);
        dialog.setTitle("Loan Actions");
        dialog.setHeaderText(loan.loanNumber() + " - " + loan.loanName());
        dialog.setContentText("Action");
        Optional<String> selected = dialog.showAndWait();
        selected.ifPresent(action -> handleAction(loan, action));
    }

    private void configureFilters() {
        typeFilterBox.setItems(FXCollections.observableArrayList(
                ALL_TYPES,
                "Commercial Bank",
                "Microfinance Institution",
                "Village Bank / Bank Nkhonde",
                "Savings Group / Chipeleganyu",
                "Employer",
                "Individual",
                "Friend / Family",
                "Other"
        ));
        typeFilterBox.getSelectionModel().select(ALL_TYPES);
        statusFilterBox.setItems(FXCollections.observableArrayList(
                ALL_STATUSES,
                "Active",
                "Overdue",
                "Completed",
                "Closed",
                "Defaulted",
                "Cancelled"
        ));
        statusFilterBox.getSelectionModel().select(ALL_STATUSES);
        dueStatusFilterBox.setItems(FXCollections.observableArrayList(ALL_DUE, "Current", "Due soon", "Overdue", "Completed"));
        dueStatusFilterBox.getSelectionModel().select(ALL_DUE);
        typeFilterBox.setOnAction(event -> applyFilters());
        statusFilterBox.setOnAction(event -> applyFilters());
        personFilterBox.setOnAction(event -> applyFilters());
        dueStatusFilterBox.setOnAction(event -> applyFilters());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
    }

    private void configureTable() {
        loanColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().loan().loanNumber()));
        personColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().loan().lenderName()));
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayToken(cell.getValue().loan().lenderType())));
        principalColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(cell.getValue().loan().currency(), cell.getValue().loan().principalAmount())));
        outstandingColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(cell.getValue().loan().currency(), cell.getValue().loan().outstandingBalance())));
        nextPaymentColumn.setCellValueFactory(cell -> new SimpleStringProperty(nextPaymentText(cell.getValue().loan())));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayToken(cell.getValue().loan().status())));
        loanTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                detailsArea.setText(summaryText(newValue.loan()));
            }
        });
    }

    private void refresh() {
        loans = database.listCentralLoans();
        List<String> lenders = new ArrayList<>();
        lenders.add(ALL_LENDERS);
        loans.stream()
                .map(CentralLoanRecord::lenderName)
                .filter(name -> !safe(name).isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(lenders::add);
        personFilterBox.setItems(FXCollections.observableArrayList(lenders));
        personFilterBox.getSelectionModel().select(ALL_LENDERS);
        applyFilters();
        detailsArea.setText(loans.isEmpty()
                ? "No central loans are recorded. Use Add Loan to register borrowed money and generate its repayment schedule."
                : "Select a loan to review its overview, repayment schedule, payment history, interest and charges, and linked Savings Group.");
    }

    private void applyFilters() {
        if (loanTable == null) {
            return;
        }
        List<LoanLedgerRow> rows = loans.stream()
                .filter(this::matchesType)
                .filter(this::matchesStatus)
                .filter(this::matchesLender)
                .filter(this::matchesDueStatus)
                .filter(this::matchesSearch)
                .sorted(Comparator.comparing((CentralLoanRecord loan) -> parseDate(loan.nextPaymentDate()), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CentralLoanRecord::loanNumber))
                .map(LoanLedgerRow::new)
                .toList();
        loanTable.setItems(FXCollections.observableArrayList(rows));
    }

    private boolean matchesType(CentralLoanRecord loan) {
        String selected = safe(typeFilterBox.getValue());
        return selected.isBlank()
                || ALL_TYPES.equals(selected)
                || displayToken(loan.lenderType()).equalsIgnoreCase(selected);
    }

    private boolean matchesStatus(CentralLoanRecord loan) {
        String selected = safe(statusFilterBox.getValue());
        return selected.isBlank()
                || ALL_STATUSES.equals(selected)
                || displayToken(loan.status()).equalsIgnoreCase(selected);
    }

    private boolean matchesLender(CentralLoanRecord loan) {
        String selected = selectedLender();
        return selected.isBlank() || ALL_LENDERS.equals(selected) || selected.equalsIgnoreCase(safe(loan.lenderName()));
    }

    private boolean matchesDueStatus(CentralLoanRecord loan) {
        String selected = safe(dueStatusFilterBox.getValue());
        return selected.isBlank() || ALL_DUE.equals(selected) || selected.equalsIgnoreCase(dueStatus(loan));
    }

    private boolean matchesSearch(CentralLoanRecord loan) {
        String query = safe(searchField.getText()).toLowerCase(Locale.ENGLISH);
        if (query.isBlank()) {
            return true;
        }
        String haystack = (loan.loanNumber() + " " + loan.loanName() + " "
                + safe(loan.lenderName()) + " "
                + safe(loan.lenderType()) + " "
                + safe(loan.savingsGroupName()) + " "
                + safe(loan.status()) + " "
                + safe(loan.notes())).toLowerCase(Locale.ENGLISH);
        return haystack.contains(query);
    }

    private String loanProfile(CentralLoanRecord loan) {
        StringBuilder builder = new StringBuilder();
        builder.append(summaryText(loan)).append(System.lineSeparator());
        builder.append("Overview").append(System.lineSeparator());
        builder.append("Start date: ").append(dash(loan.startDate())).append(System.lineSeparator());
        builder.append("Expected completion: ").append(dash(loan.expectedEndDate())).append(System.lineSeparator());
        builder.append("Proceeds account: ").append(dash(loan.proceedsAccountName())).append(System.lineSeparator());
        builder.append("Repayment account: ").append(dash(loan.repaymentAccountName())).append(System.lineSeparator());
        builder.append("Repayment mode: ").append(displayToken(loan.repaymentMode())).append(System.lineSeparator());
        builder.append("Repayment frequency: ").append(displayToken(loan.repaymentFrequency())).append(System.lineSeparator()).append(System.lineSeparator());

        builder.append("Repayment Schedule").append(System.lineSeparator());
        List<CentralLoanInstallmentRecord> installments = database.listCentralLoanInstallments(loan.id());
        if (installments.isEmpty()) {
            builder.append("- No instalments generated.").append(System.lineSeparator());
        } else {
            for (CentralLoanInstallmentRecord installment : installments) {
                builder.append("#").append(installment.installmentNumber())
                        .append(" | Due ").append(installment.dueDate())
                        .append(" | Principal ").append(money(loan.currency(), installment.principalDue()))
                        .append(" | Interest ").append(money(loan.currency(), installment.interestDue()))
                        .append(" | Paid ").append(money(loan.currency(), installment.amountPaid()))
                        .append(" | Remaining ").append(money(loan.currency(), installment.remainingDue()))
                        .append(" | ").append(displayToken(installment.status()))
                        .append(System.lineSeparator());
            }
        }

        builder.append(System.lineSeparator()).append("Payment History").append(System.lineSeparator());
        List<CentralLoanPaymentRecord> payments = database.listCentralLoanPayments(loan.id());
        if (payments.isEmpty()) {
            builder.append("- No repayments posted yet.").append(System.lineSeparator());
        } else {
            for (CentralLoanPaymentRecord payment : payments) {
                builder.append(payment.paymentDate())
                        .append(" | Total ").append(money(loan.currency(), payment.totalPaid()))
                        .append(" | Principal ").append(money(loan.currency(), payment.principalPaid()))
                        .append(" | Interest ").append(money(loan.currency(), payment.interestPaid()))
                        .append(" | Account ").append(dash(payment.accountName()))
                        .append(" | Ref ").append(dash(payment.reference()))
                        .append(System.lineSeparator());
            }
        }

        builder.append(System.lineSeparator()).append("Interest and Charges").append(System.lineSeparator());
        builder.append("Method: ").append(displayToken(loan.interestMethod())).append(System.lineSeparator());
        builder.append("Rate: ").append(String.format(Locale.ENGLISH, "%.2f%%", loan.interestRate())).append(System.lineSeparator());
        builder.append("Fixed interest: ").append(money(loan.currency(), loan.fixedInterestAmount())).append(System.lineSeparator());
        builder.append("Total interest: ").append(money(loan.currency(), loan.totalInterest())).append(System.lineSeparator());
        builder.append("Fees: ").append(money(loan.currency(), loan.fees())).append(System.lineSeparator());
        builder.append("Penalties outstanding: ").append(money(loan.currency(), loan.penaltiesOutstanding())).append(System.lineSeparator());

        if (loan.savingsGroupId() != null) {
            builder.append(System.lineSeparator()).append("Linked Savings Group").append(System.lineSeparator());
            builder.append("Group: ").append(dash(loan.savingsGroupName())).append(System.lineSeparator());
            builder.append("Relationship: ").append(displayToken(loan.lenderType())).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator()).append("Notes").append(System.lineSeparator()).append(dash(loan.notes()));
        return builder.toString();
    }

    private String summaryText(CentralLoanRecord loan) {
        return """
                %s
                Loan: %s
                Lender: %s
                Source: %s
                Principal: %s
                Total repayable: %s
                Amount paid: %s
                Outstanding: %s
                Next repayment: %s
                Status: %s
                """.formatted(
                loan.loanNumber(),
                loan.loanName(),
                loan.lenderName(),
                displayToken(loan.lenderType()),
                money(loan.currency(), loan.principalAmount()),
                money(loan.currency(), loan.totalRepayable()),
                money(loan.currency(), loan.totalPaid()),
                money(loan.currency(), loan.outstandingBalance()),
                nextPaymentText(loan),
                displayToken(loan.status())
        );
    }

    private List<String> actionsFor(CentralLoanRecord loan) {
        String status = safe(loan.status()).toUpperCase(Locale.ENGLISH);
        if (List.of("COMPLETED", "CLOSED").contains(status)) {
            return List.of("View Statement");
        }
        if ("CANCELLED".equals(status)) {
            return List.of("View Statement");
        }
        return List.of("Record Repayment", "View Schedule", "View Statement");
    }

    private void handleAction(CentralLoanRecord loan, String action) {
        switch (action) {
            case "Record Repayment" -> NavigationBus.requestLoanRepayment(loan.id(), "BORROWED", loan.lenderName());
            case "View Schedule", "View Statement" -> detailsArea.setText(loanProfile(loan));
            default -> UiAlerts.info("Action is not available.");
        }
    }

    private CentralLoanRecord selectedLoan() {
        LoanLedgerRow row = loanTable.getSelectionModel().getSelectedItem();
        return row == null ? null : row.loan();
    }

    private String dueStatus(CentralLoanRecord loan) {
        if (loan.outstandingBalance() <= 0.005) {
            return "Completed";
        }
        LocalDate dueDate = parseDate(loan.nextPaymentDate());
        if ("OVERDUE".equalsIgnoreCase(safe(loan.status())) || (dueDate != null && dueDate.isBefore(LocalDate.now()))) {
            return "Overdue";
        }
        if (dueDate != null && !dueDate.isAfter(LocalDate.now().plusDays(30))) {
            return "Due soon";
        }
        return "Current";
    }

    private String nextPaymentText(CentralLoanRecord loan) {
        if (loan.outstandingBalance() <= 0.005 || safe(loan.nextPaymentDate()).isBlank()) {
            return "-";
        }
        return loan.nextPaymentDate() + " | " + money(loan.currency(), loan.nextPaymentAmount());
    }

    private String selectedLender() {
        String editorText = personFilterBox.getEditor() == null ? "" : safe(personFilterBox.getEditor().getText());
        if (!editorText.isBlank()) {
            return editorText;
        }
        return safe(personFilterBox.getValue());
    }

    private LocalDate parseDate(String value) {
        try {
            return safe(value).isBlank() ? null : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
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

    private String money(String currency, double amount) {
        String cleanCurrency = safe(currency).isBlank() ? "MWK" : currency;
        return cleanCurrency + " " + MONEY_FORMAT.format(amount);
    }

    private String dash(String value) {
        return safe(value).isBlank() ? "-" : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record LoanLedgerRow(CentralLoanRecord loan) {
    }
}
