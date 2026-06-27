package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.ReportRow;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.time.Year;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

public class ReportsController {
    @FXML private ComboBox<String> reportTypeBox;
    @FXML private ComboBox<String> monthBox;
    @FXML private ComboBox<Integer> yearBox;
    @FXML private Label summaryIncomeLabel;
    @FXML private Label summaryExpenseLabel;
    @FXML private Label summarySavingsLabel;
    @FXML private Label summarySavingsRateLabel;
    @FXML private VBox categoryReportPane;
    @FXML private Label categoryReportTitle;
    @FXML private TableView<ReportRow> categoryTable;
    @FXML private TableColumn<ReportRow, String> categoryLabelColumn;
    @FXML private TableColumn<ReportRow, String> categoryAccountColumn;
    @FXML private TableColumn<ReportRow, Double> categoryAmountColumn;
    @FXML private VBox projectReportPane;
    @FXML private Label projectReportTitle;
    @FXML private TableView<ReportRow> projectTable;
    @FXML private TableColumn<ReportRow, String> projectLabelColumn;
    @FXML private TableColumn<ReportRow, Double> projectAmountColumn;
    @FXML private VBox accountReportPane;
    @FXML private Label accountReportTitle;
    @FXML private TableView<ReportRow> accountTable;
    @FXML private TableColumn<ReportRow, String> accountLabelColumn;
    @FXML private TableColumn<ReportRow, Double> accountAmountColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        reportTypeBox.setItems(FXCollections.observableArrayList(
                "Monthly Summary", "Income Report", "Expense Report", "Project Report",
                "Account Balance Report", "Lending Report"));
        String requestedReportType = NavigationBus.consumeRequestedReportType();
        reportTypeBox.getSelectionModel().select(
                reportTypeBox.getItems().contains(requestedReportType) ? requestedReportType : "Monthly Summary");
        monthBox.setItems(FXCollections.observableArrayList(IntStream.rangeClosed(1, 12)
                .mapToObj(month -> java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .toList()));
        monthBox.getSelectionModel().select(java.time.LocalDate.now().getMonthValue() - 1);
        int year = Year.now().getValue();
        yearBox.setItems(FXCollections.observableArrayList(IntStream.rangeClosed(year - 5, year + 1).boxed().toList()));
        yearBox.getSelectionModel().select(Integer.valueOf(year));
        categoryLabelColumn.setCellValueFactory(new PropertyValueFactory<>("label"));
        categoryAccountColumn.setCellValueFactory(new PropertyValueFactory<>("account"));
        categoryAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        projectLabelColumn.setCellValueFactory(new PropertyValueFactory<>("label"));
        projectAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        accountLabelColumn.setCellValueFactory(new PropertyValueFactory<>("label"));
        accountAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        reportTypeBox.setOnAction(event -> refresh());
        monthBox.setOnAction(event -> refresh());
        yearBox.setOnAction(event -> refresh());
        refresh();
    }

    @FXML
    private void refresh() {
        refreshSummaryLine();
        switch (reportTypeBox.getValue()) {
            case "Income Report" -> refreshIncomeReport();
            case "Expense Report" -> refreshExpenseReport();
            case "Project Report" -> refreshProjectReport();
            case "Account Balance Report" -> refreshAccountReport();
            case "Lending Report" -> refreshLendingReport();
            default -> refreshMonthlySummary();
        }
    }

    private void refreshSummaryLine() {
        String month = selectedMonthKey();
        double income = database.transactionTotalByTypeForMonth("INCOME", month);
        double expenses = database.transactionTotalByTypeForMonth("EXPENSE", month);
        double savings = income - expenses;
        double rate = income == 0 ? 0 : (savings / income) * 100;
        summaryIncomeLabel.setText(MoneyUtil.mwk(income));
        summaryExpenseLabel.setText(MoneyUtil.mwk(expenses));
        summarySavingsLabel.setText(MoneyUtil.mwk(savings));
        summarySavingsRateLabel.setText(String.format("%.2f%%", rate));
        NavigationBus.updateReportTitle(reportTypeBox.getValue());
    }

    private void refreshMonthlySummary() {
        String month = selectedMonthKey();
        setCategoryReport("Expense Categories", "Category", database.categorySpendingReport(month));
        setProjectReport("Project Spending", database.projectSpendingReport(month));
        setAccountReport("Account Balances", database.accountBalanceReportThroughMonth(month));
        showReportPanes(true, true, true);
    }

    private void refreshIncomeReport() {
        setCategoryReport("Income Sources", "Source", true, database.incomeSourceByAccountReport(selectedMonthKey()));
        projectTable.getItems().clear();
        accountTable.getItems().clear();
        showReportPanes(true, false, false);
    }

    private void refreshExpenseReport() {
        String month = selectedMonthKey();
        setCategoryReport("Expense Categories", "Category", true, database.categorySpendingByAccountReport(month));
        setProjectReport("Project Expense Spending", database.projectSpendingReport(month));
        accountTable.getItems().clear();
        showReportPanes(true, true, false);
    }

    private void refreshProjectReport() {
        categoryTable.getItems().clear();
        setProjectReport("Project Spending", database.projectSpendingReport(selectedMonthKey()));
        accountTable.getItems().clear();
        showReportPanes(false, true, false);
    }

    private void refreshAccountReport() {
        categoryTable.getItems().clear();
        projectTable.getItems().clear();
        setAccountReport("Account Balances", database.accountBalanceReportThroughMonth(selectedMonthKey()));
        showReportPanes(false, false, true);
    }

    private void refreshLendingReport() {
        setCategoryReport("Lending By Person", "Person", database.lendingByPersonReport(selectedMonthKey()));
        projectTable.getItems().clear();
        accountTable.getItems().clear();
        showReportPanes(true, false, false);
    }

    private void setCategoryReport(String title, String labelColumn, List<ReportRow> rows) {
        setCategoryReport(title, labelColumn, false, rows);
    }

    private void setCategoryReport(String title, String labelColumn, boolean showAccount, List<ReportRow> rows) {
        categoryReportTitle.setText(title);
        categoryLabelColumn.setText(labelColumn);
        categoryAccountColumn.setVisible(showAccount);
        categoryTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void setProjectReport(String title, List<ReportRow> rows) {
        projectReportTitle.setText(title);
        projectLabelColumn.setText("Project");
        projectTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void setAccountReport(String title, List<ReportRow> rows) {
        accountReportTitle.setText(title);
        accountLabelColumn.setText("Account");
        accountTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void showReportPanes(boolean showCategory, boolean showProject, boolean showAccount) {
        setVisible(categoryReportPane, showCategory);
        setVisible(projectReportPane, showProject);
        setVisible(accountReportPane, showAccount);
    }

    private void setVisible(VBox pane, boolean visible) {
        pane.setVisible(visible);
        pane.setManaged(visible);
    }

    private String selectedMonthKey() {
        int selectedMonth = monthBox.getSelectionModel().getSelectedIndex() + 1;
        Integer selectedYear = yearBox.getValue();
        if (selectedMonth <= 0) {
            selectedMonth = java.time.LocalDate.now().getMonthValue();
        }
        if (selectedYear == null) {
            selectedYear = Year.now().getValue();
        }
        return String.format("%04d-%02d", selectedYear, selectedMonth);
    }

    @FXML
    private void exportPdf() {
        UiAlerts.info("PDF export is not implemented yet.");
    }

    @FXML
    private void exportExcel() {
        UiAlerts.info("Excel export is not implemented yet.");
    }

    @FXML
    private void printReport() {
        UiAlerts.info("Printing is not implemented yet.");
    }
}
