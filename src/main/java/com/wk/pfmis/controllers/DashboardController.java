package com.wk.pfmis.controllers;

import com.wk.pfmis.MainApp;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.DashboardStats;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ReportRow;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardController {
    @FXML private Label totalBalanceLabel;
    @FXML private Label incomeLabel;
    @FXML private Label expensesLabel;
    @FXML private Label savingsLabel;
    @FXML private Label balanceDetailLabel;
    @FXML private Label incomeDetailLabel;
    @FXML private Label expensesDetailLabel;
    @FXML private Label savingsDetailLabel;
    @FXML private Label activeAccountsLabel;
    @FXML private Label activeProjectsLabel;
    @FXML private Label activeGoalsLabel;
    @FXML private Label moneyGivenLabel;
    @FXML private Label sectionTitleLabel;
    @FXML private Label signedInUserLabel;
    @FXML private Label activeWorkspaceLabel;
    @FXML private Button userManagementSidebarButton;
    @FXML private Button returnWorkspaceButton;
    @FXML private VBox dashboardSummaryPane;
    @FXML private StackPane contentPane;
    @FXML private LineChart<String, Number> cashFlowChart;
    @FXML private PieChart expenseDistributionChart;
    @FXML private PieChart accountBalanceChart;
    @FXML private PieChart incomeSourceChart;
    @FXML private PieChart moneyPositionChart;
    @FXML private BarChart<String, Number> projectSpendingChart;
    @FXML private TableView<FinanceTransaction> dashboardTransactionsTable;
    @FXML private TableColumn<FinanceTransaction, String> dashboardDateColumn;
    @FXML private TableColumn<FinanceTransaction, String> dashboardTypeColumn;
    @FXML private TableColumn<FinanceTransaction, String> dashboardAccountColumn;
    @FXML private TableColumn<FinanceTransaction, String> dashboardCategoryColumn;
    @FXML private TableColumn<FinanceTransaction, Double> dashboardAmountColumn;
    @FXML private VBox alertsBox;
    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private static final DateTimeFormatter MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private String currentViewFileName;
    private String currentViewTitle = "Dashboard";
    private boolean currentViewIsDashboard = true;
    private String previousViewFileName;
    private String previousViewTitle = "Dashboard";
    private boolean previousViewIsDashboard = true;
    private boolean navigatingBack;

    @FXML
    public void initialize() {
        configureUserSecurityHeader();
        DataRefreshBus.addListener(this::refreshDashboard);
        NavigationBus.onAccountHistoryRequested(this::showAccountHistory);
        NavigationBus.onBackRequested(this::goBack);
        NavigationBus.onReportTitleChanged(reportType -> sectionTitleLabel.setText(reportTitle(reportType)));
        NavigationBus.onTransactionEntryRequested(title -> loadView("Expenses.fxml", title));
        configureDashboardTable();
        showHome();
    }

    @FXML
    private void showHome() {
        rememberCurrentView();
        sectionTitleLabel.setText("Dashboard");
        setDashboardSummaryVisible(true);
        contentPane.getChildren().clear();
        currentViewFileName = null;
        currentViewTitle = "Dashboard";
        currentViewIsDashboard = true;
        refreshDashboard();
    }

    @FXML
    private void showDashboardSummary() {
        showHome();
    }

    @FXML
    private void showDashboardRecentTransactions() {
        loadView("Transactions.fxml", "Recent Transactions");
    }

    @FXML
    private void showAiCenter() {
        loadView("AiCenter.fxml", "Smart Analysis");
    }

    @FXML
    private void showMyAccount() {
        loadView("MyAccount.fxml", "My Account");
    }

    @FXML
    private void showUserManagement() {
        if (!UserSession.isSuperAdmin()) {
            UiAlerts.info("Only a super administrator can manage users.");
            return;
        }
        loadView("UserManagement.fxml", "User Management");
    }

    @FXML
    private void returnToMyWorkspace() {
        MainApp.returnToOwnWorkspace();
    }

    @FXML
    private void logout() {
        MainApp.logout();
    }

    @FXML
    private void showCashFlowChart() {
        loadPlaceholder("Cash Flow Chart", "Cash flow chart visualization is part of the wireframe and can be added on top of transaction totals.");
    }

    @FXML
    private void showDashboardProjectSpending() {
        showReport("Project Report", "Project Spending Summary");
    }

    @FXML
    private void showAlerts() {
        loadPlaceholder("Alerts", "Alerts can highlight overdue goals, open repayments, low balances, and projects over budget.");
    }

    @FXML
    private void showAccounts() {
        loadView("Accounts.fxml", "Accounts");
    }

    @FXML
    private void showAccountHistory() {
        loadView("AccountHistory.fxml", "Account History");
    }

    @FXML
    private void showIncome() {
        loadView("Income.fxml", "Add Income");
    }

    @FXML
    private void showIncomeReport() {
        showReport("Income Report", "Income Report");
    }

    @FXML
    private void showCategories() {
        loadView("Categories.fxml", "Categories");
    }

    @FXML
    private void showTransactions() {
        loadView("Transactions.fxml", "Transaction Ledger");
    }

    @FXML
    private void showExpenses() {
        NavigationBus.requestTransactionType("EXPENSE");
        loadView("Expenses.fxml", "Record Expense");
    }

    @FXML
    private void showExpenseReport() {
        showReport("Expense Report", "Expense Report");
    }

    @FXML
    private void showTransferMoney() {
        loadView("TransferMoney.fxml", "Transfer Money");
    }

    @FXML
    private void showProjects() {
        loadView("Projects.fxml", "Add Project");
    }

    @FXML
    private void showViewProjects() {
        loadView("ViewProjects.fxml", "View Projects");
    }

    @FXML
    private void showProjectList() {
        loadView("ProjectList.fxml", "Project List");
    }

    @FXML
    private void showProjectActivities() {
        loadView("ProjectActivities.fxml", "Register Project Activity");
    }

    @FXML
    private void showPeople() {
        loadView("People.fxml", "Loan Contacts");
    }

    @FXML
    private void showLendMoney() {
        showLoanTransaction("Lend Money", "EXPENSE", "MONEY_LENT");
    }

    @FXML
    private void showBorrowMoney() {
        showLoanTransaction("Borrow Money", "INCOME", "MONEY_BORROWED");
    }

    @FXML
    private void showReceiveRepayment() {
        showLoanTransaction("Receive Repayment", "INCOME", "LENT_REPAID");
    }

    @FXML
    private void showRepayBorrowedMoney() {
        showLoanTransaction("Repay Borrowed Money", "EXPENSE", "BORROWED_REPAID");
    }

    @FXML
    private void showLoanLedger() {
        showReport("Loan Report", "Loan Ledger");
    }

    @FXML
    private void showGoals() {
        loadView("Goals.fxml", "Add Goal");
    }

    @FXML
    private void showGoalProgress() {
        loadView("Goals.fxml", "Goal Progress");
    }

    @FXML
    private void showGoalStatus() {
        loadView("Goals.fxml", "Goal Status");
    }

    @FXML
    private void showGoalProject() {
        loadView("GoalProject.fxml", "Turn Goal Into Project");
    }

    @FXML
    private void showGoalSteps() {
        loadView("GoalSteps.fxml", "Goal Steps");
    }

    @FXML
    private void showBudgets() {
        loadView("Budgets.fxml", "Budgets");
    }

    @FXML
    private void showSettings() {
        showAdministration();
    }

    @FXML
    private void showAdministration() {
        loadView("Administration.fxml", "Administration");
    }

    @FXML
    private void showReports() {
        showMonthlySummaryReport();
    }

    @FXML
    private void showMonthlySummaryReport() {
        showReport("Monthly Summary", "Monthly Summary");
    }

    @FXML
    private void showProjectReport() {
        showReport("Project Report", "Project Report");
    }

    @FXML
    private void showAccountReport() {
        showReport("Account Balance Report", "Account Report");
    }

    @FXML
    private void showLendingReport() {
        showReport("Loan Report", "Loan Report");
    }

    @FXML
    private void showPaymentMethods() {
        loadView("PaymentMethods.fxml", "Payment Methods");
    }

    @FXML
    private void showCurrencies() {
        loadView("Currencies.fxml", "Currencies");
    }

    @FXML
    private void showBackupRestore() {
        showAdministration();
    }

    @FXML
    private void showAuditLogs() {
        loadView("AuditLogs.fxml", "Audit Logs");
    }

    @FXML
    private void showSyncCenter() {
        loadView("SyncCenter.fxml", "Sync Center");
    }

    @FXML
    private void showMaintenance() {
        loadView("Maintenance.fxml", "Maintenance");
    }

    private void configureUserSecurityHeader() {
        SystemUser signedIn = UserSession.getAuthenticatedUser();
        SystemUser workspace = UserSession.getWorkspaceUser();
        signedInUserLabel.setText("Signed in: " + signedIn.getDisplayName() + " · " + signedIn.getRoleDisplay());
        activeWorkspaceLabel.setText("Workspace: " + workspace.getDisplayName() + " (" + workspace.getUsername() + ")");
        boolean superAdmin = signedIn.isSuperAdmin();
        userManagementSidebarButton.setVisible(superAdmin);
        userManagementSidebarButton.setManaged(superAdmin);
        boolean anotherWorkspace = superAdmin && !UserSession.isViewingOwnWorkspace();
        returnWorkspaceButton.setVisible(anotherWorkspace);
        returnWorkspaceButton.setManaged(anotherWorkspace);
    }

    @FXML
    private void refreshDashboard() {
        DashboardStats stats = database.getDashboardStats();
        List<FinanceTransaction> transactions = database.listRecentTransactions(500);
        List<FinanceTransaction> currentMonthTransactions = transactions.stream()
                .filter(this::isCurrentMonth)
                .toList();
        long incomeRecords = currentMonthTransactions.stream()
                .filter(transaction -> "INCOME".equals(transaction.getTransactionType()))
                .count();
        long expenseRecords = currentMonthTransactions.stream()
                .filter(transaction -> "EXPENSE".equals(transaction.getTransactionType()))
                .count();
        double savingsRate = stats.getMonthlyIncome() == 0 ? 0 : (stats.getMonthlySavings() / stats.getMonthlyIncome()) * 100;
        totalBalanceLabel.setText(MoneyUtil.mwk(stats.getTotalBalance()));
        incomeLabel.setText(MoneyUtil.mwk(stats.getMonthlyIncome()));
        expensesLabel.setText(MoneyUtil.mwk(stats.getMonthlyExpenses()));
        savingsLabel.setText(MoneyUtil.mwk(stats.getMonthlySavings()));
        balanceDetailLabel.setText("Across " + stats.getActiveAccounts() + " accounts");
        incomeDetailLabel.setText(incomeRecords + " income records");
        expensesDetailLabel.setText(expenseRecords + " expense records");
        savingsDetailLabel.setText(String.format("%.1f%% savings rate", savingsRate));
        activeAccountsLabel.setText(String.valueOf(stats.getActiveAccounts()));
        activeProjectsLabel.setText(String.valueOf(stats.getActiveProjects()));
        activeGoalsLabel.setText(String.valueOf(stats.getActiveGoals()));
        moneyGivenLabel.setText(MoneyUtil.mwk(stats.getMoneyGivenOut()));
        refreshDashboardCharts(stats, transactions);
        refreshDashboardTable(transactions);
        refreshAlerts(stats);
    }

    private void configureDashboardTable() {
        dashboardDateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        dashboardTypeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionType"));
        dashboardAccountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        dashboardCategoryColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        dashboardAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        TableActions.configureScrollableTable(dashboardTransactionsTable);
        TableActions.installRowContextMenu(dashboardTransactionsTable, this::dashboardTransactionMenuItems);
    }

    private List<javafx.scene.control.MenuItem> dashboardTransactionMenuItems(FinanceTransaction transaction) {
        return List.of(
                TableActions.menuItem("View Transaction", () -> viewDashboardTransaction(transaction)),
                TableActions.menuItem("Open Full Ledger", this::showTransactions),
                TableActions.separator(),
                TableActions.copyRowItem(dashboardTransactionsTable, transaction),
                TableActions.exportTableItem(dashboardTransactionsTable, "Dashboard Recent Transactions"),
                TableActions.printTableItem(dashboardTransactionsTable, "Dashboard Recent Transactions"),
                TableActions.refreshItem(this::refreshDashboard)
        );
    }

    private void viewDashboardTransaction(FinanceTransaction transaction) {
        if (transaction == null) {
            return;
        }
        UiAlerts.info(
                "Date: " + transaction.getTransactionDate()
                        + "\nType: " + transaction.getTransactionType()
                        + "\nAccount: " + transaction.getAccountName()
                        + "\nCategory: " + labelOrDefault(transaction.getCategoryName(), "-")
                        + "\nAmount: " + MoneyUtil.mwk(transaction.getAmount())
                        + "\nStatus: " + labelOrDefault(transaction.getTransactionStatus(), "-")
                        + "\nDescription: " + labelOrDefault(transaction.getDescription(), "-")
        );
    }

    private void refreshDashboardCharts(DashboardStats stats, List<FinanceTransaction> transactions) {
        setPieData(expenseDistributionChart, database.categorySpendingReport(), "No expenses");
        setPieData(accountBalanceChart, database.accountBalanceReport(), "No balances");
        setPieData(incomeSourceChart, incomeSourceRows(transactions), "No income");
        setPieData(moneyPositionChart, moneyPositionRows(stats), "No money position");
        refreshCashFlowChart(transactions);
        refreshProjectSpendingChart();
    }

    private void setPieData(PieChart chart, List<ReportRow> rows, String emptyLabel) {
        List<PieChart.Data> data = rows.stream()
                .filter(row -> row.getAmount() > 0)
                .map(row -> new PieChart.Data(row.getLabel(), row.getAmount()))
                .toList();
        if (data.isEmpty()) {
            data = List.of(new PieChart.Data(emptyLabel, 1));
        }
        chart.setData(FXCollections.observableArrayList(data));
    }

    private List<ReportRow> incomeSourceRows(List<FinanceTransaction> transactions) {
        return transactions.stream()
                .filter(transaction -> "INCOME".equals(transaction.getTransactionType()))
                .collect(Collectors.groupingBy(
                        transaction -> labelOrDefault(transaction.getCategoryName(), "Other"),
                        LinkedHashMap::new,
                        Collectors.summingDouble(FinanceTransaction::getAmount)
                ))
                .entrySet()
                .stream()
                .map(entry -> new ReportRow(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<ReportRow> moneyPositionRows(DashboardStats stats) {
        List<ReportRow> rows = new ArrayList<>();
        rows.add(new ReportRow("Available Balance", Math.max(stats.getTotalBalance(), 0)));
        rows.add(new ReportRow("Expenses", Math.max(stats.getMonthlyExpenses(), 0)));
        rows.add(new ReportRow("Savings", Math.max(stats.getMonthlySavings(), 0)));
        rows.add(new ReportRow("Loans Receivable", Math.max(stats.getMoneyGivenOut(), 0)));
        return rows;
    }

    private void refreshCashFlowChart(List<FinanceTransaction> transactions) {
        cashFlowChart.getData().clear();
        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("Income");
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Expenses");
        Map<YearMonth, double[]> totals = new LinkedHashMap<>();
        YearMonth start = YearMonth.now().minusMonths(5);
        for (int index = 0; index < 6; index++) {
            totals.put(start.plusMonths(index), new double[]{0, 0});
        }
        for (FinanceTransaction transaction : transactions) {
            YearMonth month = parseMonth(transaction.getTransactionDate());
            if (month == null || !totals.containsKey(month)) {
                continue;
            }
            double[] monthTotals = totals.get(month);
            if ("INCOME".equals(transaction.getTransactionType())) {
                monthTotals[0] += transaction.getAmount();
            } else if ("EXPENSE".equals(transaction.getTransactionType())) {
                monthTotals[1] += transaction.getAmount();
            }
        }
        for (Map.Entry<YearMonth, double[]> entry : totals.entrySet()) {
            String label = entry.getKey().atDay(1).format(MONTH_LABEL_FORMAT);
            incomeSeries.getData().add(new XYChart.Data<>(label, entry.getValue()[0]));
            expenseSeries.getData().add(new XYChart.Data<>(label, entry.getValue()[1]));
        }
        cashFlowChart.getData().addAll(incomeSeries, expenseSeries);
    }

    private void refreshProjectSpendingChart() {
        projectSpendingChart.getData().clear();
        XYChart.Series<String, Number> budgetSeries = new XYChart.Series<>();
        budgetSeries.setName("Budget");
        XYChart.Series<String, Number> spentSeries = new XYChart.Series<>();
        spentSeries.setName("Spent");
        for (Project project : database.listProjects()) {
            budgetSeries.getData().add(new XYChart.Data<>(project.getProjectName(), project.getPlannedBudget()));
            spentSeries.getData().add(new XYChart.Data<>(project.getProjectName(), project.getAmountSpent()));
        }
        projectSpendingChart.getData().addAll(budgetSeries, spentSeries);
    }

    private void refreshDashboardTable(List<FinanceTransaction> transactions) {
        dashboardTransactionsTable.setItems(FXCollections.observableArrayList(transactions.stream().limit(10).toList()));
    }

    private void refreshAlerts(DashboardStats stats) {
        alertsBox.getChildren().clear();
        if (stats.getActiveGoals() > 0 && stats.getMonthlySavings() <= 0) {
            alertsBox.getChildren().add(alertLabel("No positive savings recorded this month."));
        }
        for (Project project : database.listProjects()) {
            if (project.getPlannedBudget() > 0 && project.getAmountSpent() / project.getPlannedBudget() >= 0.7) {
                alertsBox.getChildren().add(alertLabel(project.getProjectName() + " has used at least 70% of budget."));
            }
        }
        if (stats.getMoneyGivenOut() > 0) {
            alertsBox.getChildren().add(alertLabel(MoneyUtil.mwk(stats.getMoneyGivenOut()) + " is still owed to you."));
        } else if (stats.getMoneyGivenOut() < 0) {
            alertsBox.getChildren().add(alertLabel(MoneyUtil.mwk(Math.abs(stats.getMoneyGivenOut())) + " is still owed by you."));
        }
        if (alertsBox.getChildren().isEmpty()) {
            alertsBox.getChildren().add(alertLabel("No urgent reminders."));
        }
    }

    private Label alertLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("alert-line");
        return label;
    }

    private boolean isCurrentMonth(FinanceTransaction transaction) {
        YearMonth month = parseMonth(transaction.getTransactionDate());
        return YearMonth.now().equals(month);
    }

    private YearMonth parseMonth(String date) {
        try {
            return date == null || date.isBlank() ? null : YearMonth.from(LocalDate.parse(date));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String labelOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void showReport(String reportType, String title) {
        NavigationBus.requestReportType(reportType);
        loadView("Reports.fxml", title);
    }

    private String reportTitle(String reportType) {
        if ("Account Balance Report".equals(reportType)) {
            return "Account Report";
        }
        return reportType == null || reportType.isBlank() ? "Reports" : reportType;
    }

    private void showLoanTransaction(String title, String transactionType, String purpose) {
        NavigationBus.requestTransaction(transactionType, purpose, null);
        loadView("Expenses.fxml", title);
    }

    private void loadView(String fileName, String title) {
        try {
            rememberCurrentView();
            sectionTitleLabel.setText(title);
            setDashboardSummaryVisible(false);
            Parent view = FXMLLoader.load(getClass().getResource("/com/wk/pfmis/views/" + fileName));
            Node displayView = unwrapScrollPane(view);
            makeDynamic(displayView);
            contentPane.getChildren().setAll(displayView);
            currentViewFileName = fileName;
            currentViewTitle = title;
            currentViewIsDashboard = false;
            refreshDashboard();
        } catch (IOException exception) {
            UiAlerts.error("Failed to open " + title, exception);
        }
    }

    private void rememberCurrentView() {
        if (navigatingBack) {
            return;
        }
        previousViewFileName = currentViewFileName;
        previousViewTitle = currentViewTitle;
        previousViewIsDashboard = currentViewIsDashboard;
    }

    private void goBack() {
        navigatingBack = true;
        try {
            if (previousViewIsDashboard || previousViewFileName == null) {
                showHome();
            } else {
                loadView(previousViewFileName, previousViewTitle);
            }
        } finally {
            navigatingBack = false;
        }
    }

    private Node unwrapScrollPane(Parent view) {
        if (view instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            Node content = scrollPane.getContent();
            scrollPane.setContent(null);
            return content;
        }
        return view;
    }

    private void loadPlaceholder(String title, String message) {
        sectionTitleLabel.setText(title);
        setDashboardSummaryVisible(false);
        VBox placeholder = new VBox(12);
        placeholder.getStyleClass().add("panel");
        Label heading = new Label(title);
        heading.getStyleClass().add("section-heading");
        Label body = new Label(message);
        body.setWrapText(true);
        body.getStyleClass().add("form-note");
        placeholder.getChildren().setAll(heading, body);
        contentPane.getChildren().setAll(placeholder);
    }

    private void setDashboardSummaryVisible(boolean visible) {
        dashboardSummaryPane.setVisible(visible);
        dashboardSummaryPane.setManaged(visible);
    }

    private void makeDynamic(Node node) {
        if (node instanceof Region region && !isMetricCard(node) && !isLeafControl(node)) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
        }
        if (!isMetricCard(node) && !isLeafControl(node)) {
            if (VBox.getVgrow(node) == null && !(node instanceof HBox)) {
                VBox.setVgrow(node, Priority.ALWAYS);
            }
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        if (node instanceof TableView<?> tableView) {
            TableActions.configureScrollableTable(tableView);
        }

        if (node instanceof GridPane gridPane) {
            configureGrid(gridPane);
        }

        if (node instanceof TitledPane titledPane) {
            makeDynamic(titledPane.getContent());
        }

        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                makeDynamic(child);
            }
        }
    }

    private void configureGrid(GridPane gridPane) {
        int columns = gridPane.getChildren().stream()
                .map(GridPane::getColumnIndex)
                .map(index -> index == null ? 0 : index)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
        if (gridPane.getColumnConstraints().isEmpty() && columns > 0) {
            for (int index = 0; index < columns; index++) {
                ColumnConstraints constraints = new ColumnConstraints();
                constraints.setPercentWidth(100.0 / columns);
                constraints.setHgrow(Priority.ALWAYS);
                constraints.setFillWidth(true);
                gridPane.getColumnConstraints().add(constraints);
            }
        }
        for (Node child : gridPane.getChildren()) {
            if (!isMetricCard(child) && !isLeafControl(child)) {
                GridPane.setHgrow(child, Priority.ALWAYS);
            }
            if (child instanceof Region region && !isMetricCard(child) && !isLeafControl(child)) {
                region.setMaxWidth(Double.MAX_VALUE);
            }
        }
    }

    private boolean isMetricCard(Node node) {
        return node.getStyleClass().contains("metric-card")
                || node.getStyleClass().contains("compact-metric-card")
                || node.getStyleClass().contains("wide-compact-metric-card");
    }

    private boolean isLeafControl(Node node) {
        return node instanceof Button
                || node instanceof CheckBox
                || node instanceof Label
                || node instanceof TextInputControl
                || node instanceof ComboBoxBase<?>;
    }
}
