package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.DashboardStats;
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
    @FXML private Label monthlySummaryLabel;
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
        reportTypeBox.setOnAction(event -> refresh());
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
        DashboardStats stats = database.getDashboardStats();
        double rate = stats.getMonthlyIncome() == 0 ? 0 : (stats.getMonthlySavings() / stats.getMonthlyIncome()) * 100;
        monthlySummaryLabel.setText(
                "Income: " + MoneyUtil.mwk(stats.getMonthlyIncome()) +
                "    Expenses: " + MoneyUtil.mwk(stats.getMonthlyExpenses()) +
                "    Savings: " + MoneyUtil.mwk(stats.getMonthlySavings()) +
                "    Savings Rate: " + String.format("%.2f%%", rate)
        );
    }

    private void refreshMonthlySummary() {
        setCategoryReport("Expense Categories", "Category", database.categorySpendingReport());
        setProjectReport("Project Spending", database.projectSpendingReport());
        setAccountReport("Account Balances", database.accountBalanceReport());
        showReportPanes(true, true, true);
    }

    private void refreshIncomeReport() {
        setCategoryReport("Income Sources", "Source", true, database.incomeSourceByAccountReport());
        projectTable.getItems().clear();
        accountTable.getItems().clear();
        showReportPanes(true, false, false);
    }

    private void refreshExpenseReport() {
        setCategoryReport("Expense Categories", "Category", true, database.categorySpendingByAccountReport());
        setProjectReport("Project Expense Spending", database.projectSpendingReport());
        accountTable.getItems().clear();
        showReportPanes(true, true, false);
    }

    private void refreshProjectReport() {
        categoryTable.getItems().clear();
        setProjectReport("Project Spending", database.projectSpendingReport());
        accountTable.getItems().clear();
        showReportPanes(false, true, false);
    }

    private void refreshAccountReport() {
        categoryTable.getItems().clear();
        projectTable.getItems().clear();
        setAccountReport("Account Balances", database.accountBalanceReport());
        showReportPanes(false, false, true);
    }

    private void refreshLendingReport() {
        setCategoryReport("Lending By Person", "Person", database.lendingByPersonReport());
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
