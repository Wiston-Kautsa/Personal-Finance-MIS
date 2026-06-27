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

import java.time.Year;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.stream.IntStream;

public class ReportsController {
    @FXML private ComboBox<String> reportTypeBox;
    @FXML private ComboBox<String> monthBox;
    @FXML private ComboBox<Integer> yearBox;
    @FXML private Label monthlySummaryLabel;
    @FXML private TableView<ReportRow> categoryTable;
    @FXML private TableColumn<ReportRow, String> categoryLabelColumn;
    @FXML private TableColumn<ReportRow, Double> categoryAmountColumn;
    @FXML private TableView<ReportRow> projectTable;
    @FXML private TableColumn<ReportRow, String> projectLabelColumn;
    @FXML private TableColumn<ReportRow, Double> projectAmountColumn;
    @FXML private TableView<ReportRow> accountTable;
    @FXML private TableColumn<ReportRow, String> accountLabelColumn;
    @FXML private TableColumn<ReportRow, Double> accountAmountColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        reportTypeBox.setItems(FXCollections.observableArrayList(
                "Monthly Summary", "Income Report", "Expense Report", "Project Report",
                "Account Balance Report", "Lending Report"));
        reportTypeBox.getSelectionModel().select("Monthly Summary");
        monthBox.setItems(FXCollections.observableArrayList(IntStream.rangeClosed(1, 12)
                .mapToObj(month -> java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .toList()));
        monthBox.getSelectionModel().select(java.time.LocalDate.now().getMonthValue() - 1);
        int year = Year.now().getValue();
        yearBox.setItems(FXCollections.observableArrayList(IntStream.rangeClosed(year - 5, year + 1).boxed().toList()));
        yearBox.getSelectionModel().select(Integer.valueOf(year));
        categoryLabelColumn.setCellValueFactory(new PropertyValueFactory<>("label"));
        categoryAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        projectLabelColumn.setCellValueFactory(new PropertyValueFactory<>("label"));
        projectAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        accountLabelColumn.setCellValueFactory(new PropertyValueFactory<>("label"));
        accountAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        refresh();
    }

    @FXML
    private void refresh() {
        DashboardStats stats = database.getDashboardStats();
        double rate = stats.getMonthlyIncome() == 0 ? 0 : (stats.getMonthlySavings() / stats.getMonthlyIncome()) * 100;
        monthlySummaryLabel.setText(
                "Income: " + MoneyUtil.mwk(stats.getMonthlyIncome()) +
                "    Expenses: " + MoneyUtil.mwk(stats.getMonthlyExpenses()) +
                "    Savings: " + MoneyUtil.mwk(stats.getMonthlySavings()) +
                "    Savings Rate: " + String.format("%.2f%%", rate)
        );
        categoryTable.setItems(FXCollections.observableArrayList(database.categorySpendingReport()));
        projectTable.setItems(FXCollections.observableArrayList(database.projectSpendingReport()));
        accountTable.setItems(FXCollections.observableArrayList(database.accountBalanceReport()));
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
