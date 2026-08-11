package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanInstallmentRecord;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanRecord;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class LoanRepaymentScheduleController {
    private static final String ALL = "All";
    private static final String ALL_LENDERS = "All lenders";
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    @FXML private ComboBox<String> viewFilterBox;
    @FXML private ComboBox<String> periodFilterBox;
    @FXML private ComboBox<String> statusFilterBox;
    @FXML private ComboBox<String> personFilterBox;
    @FXML private TextField searchField;
    @FXML private TableView<ScheduleRow> scheduleTable;
    @FXML private TableColumn<ScheduleRow, String> dueDateColumn;
    @FXML private TableColumn<ScheduleRow, String> loanColumn;
    @FXML private TableColumn<ScheduleRow, String> personColumn;
    @FXML private TableColumn<ScheduleRow, String> directionColumn;
    @FXML private TableColumn<ScheduleRow, String> expectedAmountColumn;
    @FXML private TableColumn<ScheduleRow, String> statusColumn;
    @FXML private TextArea detailsArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private List<CentralLoanRecord> loans = List.of();
    private List<CentralLoanInstallmentRecord> installments = List.of();
    private Map<Integer, CentralLoanRecord> loansById = Map.of();

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
        ScheduleRow row = selectedRow();
        if (row == null) {
            UiAlerts.info("Select a scheduled repayment.");
            return;
        }
        detailsArea.setText(installmentDetails(row.loan(), row.installment()));
    }

    @FXML
    private void recordRepayment() {
        ScheduleRow row = selectedRow();
        if (row == null) {
            UiAlerts.info("Select a scheduled repayment.");
            return;
        }
        CentralLoanRecord loan = row.loan();
        if (loan == null || !canRecordRepayment(row.installment())) {
            UiAlerts.info("Select an unpaid scheduled, due, overdue or partially paid instalment.");
            return;
        }
        NavigationBus.requestLoanRepayment(loan.id(), "BORROWED", loan.lenderName());
    }

    private void configureFilters() {
        viewFilterBox.setItems(FXCollections.observableArrayList(ALL, "Commercial Bank", "Microfinance Institution",
                "Village Bank / Bank Nkhonde", "Savings Group / Chipeleganyu", "Employer", "Individual", "Friend / Family", "Other"));
        viewFilterBox.getSelectionModel().select(ALL);
        periodFilterBox.setItems(FXCollections.observableArrayList("Next 30 days", "This month", "Last month", "This year", ALL));
        periodFilterBox.getSelectionModel().select("Next 30 days");
        statusFilterBox.setItems(FXCollections.observableArrayList(ALL, "Scheduled", "Due", "Overdue", "Partially Paid", "Paid", "Skipped", "Failed", "Cancelled"));
        statusFilterBox.getSelectionModel().select(ALL);
        viewFilterBox.setOnAction(event -> applyFilters());
        periodFilterBox.setOnAction(event -> applyFilters());
        statusFilterBox.setOnAction(event -> applyFilters());
        personFilterBox.setOnAction(event -> applyFilters());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
    }

    private void configureTable() {
        dueDateColumn.setCellValueFactory(cell -> new SimpleStringProperty(dash(cell.getValue().installment().dueDate())));
        loanColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().loan() == null ? cell.getValue().installment().loanNumber() : cell.getValue().loan().loanNumber()));
        personColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().loan() == null ? "-" : cell.getValue().loan().lenderName()));
        directionColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().loan() == null ? "Loan" : displayToken(cell.getValue().loan().lenderType())));
        expectedAmountColumn.setCellValueFactory(cell -> new SimpleStringProperty(money(currency(cell.getValue()), cell.getValue().installment().remainingDue())));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(displayToken(cell.getValue().installment().status())));
        scheduleTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                detailsArea.setText(installmentDetails(newValue.loan(), newValue.installment()));
            }
        });
    }

    private void refresh() {
        loans = database.listCentralLoans();
        loansById = loans.stream().collect(Collectors.toMap(CentralLoanRecord::id, loan -> loan));
        installments = database.listCentralLoanInstallments(null);
        List<String> lenders = loans.stream()
                .map(CentralLoanRecord::lenderName)
                .filter(name -> !safe(name).isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        lenders.add(0, ALL_LENDERS);
        personFilterBox.setItems(FXCollections.observableArrayList(lenders));
        personFilterBox.getSelectionModel().select(ALL_LENDERS);
        applyFilters();
        detailsArea.setText(warningSummary());
    }

    private void applyFilters() {
        if (scheduleTable == null) {
            return;
        }
        List<ScheduleRow> rows = installments.stream()
                .map(installment -> new ScheduleRow(loansById.get(installment.loanId()), installment))
                .filter(this::matchesView)
                .filter(this::matchesPeriod)
                .filter(this::matchesStatus)
                .filter(this::matchesPerson)
                .filter(this::matchesSearch)
                .sorted(Comparator.comparing((ScheduleRow row) -> parseDate(row.installment().dueDate()), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(row -> row.installment().loanNumber()))
                .toList();
        scheduleTable.setItems(FXCollections.observableArrayList(rows));
    }

    private boolean matchesView(ScheduleRow row) {
        String selected = safe(viewFilterBox.getValue());
        return selected.isBlank()
                || ALL.equals(selected)
                || (row.loan() != null && displayToken(row.loan().lenderType()).equalsIgnoreCase(selected));
    }

    private boolean matchesPeriod(ScheduleRow row) {
        String selected = safe(periodFilterBox.getValue());
        if (selected.isBlank() || ALL.equals(selected)) {
            return true;
        }
        LocalDate dueDate = parseDate(row.installment().dueDate());
        if (dueDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        if ("This month".equals(selected)) {
            return YearMonth.from(today).equals(YearMonth.from(dueDate));
        }
        if ("Last month".equals(selected)) {
            return YearMonth.from(today).minusMonths(1).equals(YearMonth.from(dueDate));
        }
        if ("This year".equals(selected)) {
            return dueDate.getYear() == today.getYear();
        }
        return !dueDate.isBefore(today) && !dueDate.isAfter(today.plusDays(30));
    }

    private boolean matchesStatus(ScheduleRow row) {
        String selected = safe(statusFilterBox.getValue());
        return selected.isBlank()
                || ALL.equals(selected)
                || displayToken(row.installment().status()).equalsIgnoreCase(selected);
    }

    private boolean matchesPerson(ScheduleRow row) {
        String selected = selectedLender();
        return selected.isBlank()
                || ALL_LENDERS.equals(selected)
                || (row.loan() != null && selected.equalsIgnoreCase(safe(row.loan().lenderName())));
    }

    private boolean matchesSearch(ScheduleRow row) {
        String query = safe(searchField.getText()).toLowerCase(Locale.ENGLISH);
        if (query.isBlank()) {
            return true;
        }
        CentralLoanRecord loan = row.loan();
        String haystack = (row.installment().loanNumber() + " "
                + (loan == null ? "" : loan.loanName() + " " + loan.lenderName() + " " + loan.lenderType())
                + " " + row.installment().status()
                + " " + row.installment().dueDate()).toLowerCase(Locale.ENGLISH);
        return haystack.contains(query);
    }

    private String warningSummary() {
        LocalDate today = LocalDate.now();
        List<CentralLoanInstallmentRecord> open = installments.stream()
                .filter(this::canRecordRepayment)
                .toList();
        List<CentralLoanInstallmentRecord> overdue = open.stream()
                .filter(installment -> "OVERDUE".equalsIgnoreCase(safe(installment.status()))
                        || before(parseDate(installment.dueDate()), today))
                .toList();
        double overdueTotal = overdue.stream().mapToDouble(CentralLoanInstallmentRecord::remainingDue).sum();
        double dueThirty = open.stream()
                .filter(installment -> between(parseDate(installment.dueDate()), today, today.plusDays(30)))
                .mapToDouble(CentralLoanInstallmentRecord::remainingDue)
                .sum();
        return """
                Repayment schedule summary

                Open instalments: %d
                Due within 30 days: %s
                Overdue instalments: %d totalling %s

                Future instalments remain scheduled obligations only. They are posted to account history when a repayment is actually recorded.
                """.formatted(
                open.size(),
                money(database.getBaseCurrencyCode(), dueThirty),
                overdue.size(),
                money(database.getBaseCurrencyCode(), overdueTotal)
        );
    }

    private String installmentDetails(CentralLoanRecord loan, CentralLoanInstallmentRecord installment) {
        String currency = loan == null ? database.getBaseCurrencyCode() : loan.currency();
        return """
                Loan instalment #%d
                Loan: %s
                Lender: %s
                Due date: %s
                Principal due: %s
                Interest due: %s
                Fees due: %s
                Penalty due: %s
                Total due: %s
                Paid: %s
                Remaining: %s
                Paid date: %s
                Linked transaction: %s
                Status: %s
                """.formatted(
                installment.installmentNumber(),
                loan == null ? installment.loanNumber() : loan.loanNumber() + " - " + loan.loanName(),
                loan == null ? "-" : loan.lenderName(),
                dash(installment.dueDate()),
                money(currency, installment.principalDue()),
                money(currency, installment.interestDue()),
                money(currency, installment.feesDue()),
                money(currency, installment.penaltyDue()),
                money(currency, installment.totalDue()),
                money(currency, installment.amountPaid()),
                money(currency, installment.remainingDue()),
                dash(installment.paidDate()),
                installment.transactionId() == null ? "-" : "#" + installment.transactionId(),
                displayToken(installment.status())
        );
    }

    private boolean canRecordRepayment(CentralLoanInstallmentRecord installment) {
        return installment.remainingDue() > 0.005
                && !List.of("PAID", "CANCELLED", "SKIPPED").contains(safe(installment.status()).toUpperCase(Locale.ENGLISH));
    }

    private ScheduleRow selectedRow() {
        return scheduleTable.getSelectionModel().getSelectedItem();
    }

    private String selectedLender() {
        String editorText = personFilterBox.getEditor() == null ? "" : safe(personFilterBox.getEditor().getText());
        if (!editorText.isBlank()) {
            return editorText;
        }
        return safe(personFilterBox.getValue());
    }

    private String currency(ScheduleRow row) {
        return row.loan() == null ? database.getBaseCurrencyCode() : row.loan().currency();
    }

    private boolean between(LocalDate value, LocalDate startInclusive, LocalDate endInclusive) {
        return value != null && !value.isBefore(startInclusive) && !value.isAfter(endInclusive);
    }

    private boolean before(LocalDate value, LocalDate other) {
        return value != null && value.isBefore(other);
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

    private record ScheduleRow(CentralLoanRecord loan, CentralLoanInstallmentRecord installment) {
    }
}
