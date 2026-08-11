package com.wk.pfmis.controllers;

import javafx.event.ActionEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxmlControllerAuditTest {
    private static final Pattern CONTROLLER_PATTERN = Pattern.compile("fx:controller=\"([^\"]+)\"");
    private static final Pattern ACTION_PATTERN = Pattern.compile("onAction=\"#([A-Za-z0-9_]+)\"");
    private static final Pattern FX_ID_PATTERN = Pattern.compile("fx:id=\"([A-Za-z0-9_]+)\"");

    @Test
    void everyFxmlFileIsWellFormedXml() throws Exception {
        List<String> failures = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        for (Path fxml : fxmlFiles()) {
            try {
                factory.newDocumentBuilder().parse(fxml.toFile());
            } catch (Exception exception) {
                failures.add(fxml.getFileName() + " XML parse failed: " + exception.getMessage());
            }
        }
        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }

    @Test
    void everyFxmlActionAndInjectedFieldResolvesAgainstController() throws Exception {
        List<String> failures = new ArrayList<>();
        for (Path fxml : fxmlFiles()) {
            String content = Files.readString(fxml);
            Matcher controllerMatcher = CONTROLLER_PATTERN.matcher(content);
            if (!controllerMatcher.find()) {
                continue;
            }
            Class<?> controllerClass = Class.forName(controllerMatcher.group(1));
            verifyActions(fxml, content, controllerClass, failures);
            verifyFxIds(fxml, content, controllerClass, failures);
        }
        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }

    @Test
    void fxmlButtonsDoNotUseManualEllipsisLabels() throws Exception {
        Pattern buttonWithText = Pattern.compile("<Button\\b[^>]*\\btext=\"([^\"]*)\"", Pattern.MULTILINE);
        List<String> failures = new ArrayList<>();
        for (Path fxml : fxmlFiles()) {
            String content = Files.readString(fxml);
            Matcher matcher = buttonWithText.matcher(content);
            while (matcher.find()) {
                String text = matcher.group(1);
                if (text.contains("...") || text.contains("…")) {
                    failures.add(fxml.getFileName() + " button text is manually truncated: " + text);
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }

    @Test
    void savingsGroupsNavigationIsTopLevelAndModeSpecific() throws Exception {
        Path dashboard = fxmlFiles().stream()
                .filter(path -> path.getFileName().toString().equals("Dashboard.fxml"))
                .findFirst()
                .orElseThrow();
        String content = Files.readString(dashboard);
        String accountsPane = content.substring(content.indexOf("<TitledPane text=\"Accounts\""),
                content.indexOf("<TitledPane text=\"Savings Groups\""));

        assertFalse(accountsPane.contains("Savings Groups"), "Savings Groups must not be nested under Accounts");
        assertTrue(content.contains("<TitledPane text=\"Savings Groups\""), "Savings Groups needs a top-level navigation pane");
        assertTrue(content.contains("onAction=\"#showCommunitySavingsOverview\""));
        assertTrue(content.contains("onAction=\"#showAddSavingsGroup\""));
        assertTrue(content.contains("onAction=\"#showBankNkhonde\""));
        assertTrue(content.contains("onAction=\"#showChipeleganyu\""));
        assertTrue(content.contains("onAction=\"#showCommunityContributions\""));
        assertTrue(content.contains("onAction=\"#showCommunityPayouts\""));
        assertTrue(content.contains("onAction=\"#showCommunitySavingsReports\""));
    }

    @Test
    void savingsGroupDashboardActionsPreserveCommunitySavingsMode() throws Exception {
        Path controller = Path.of("src/main/java/com/wk/pfmis/controllers/DashboardController.java");
        String source = Files.readString(controller);

        assertTrue(source.contains("openCommunitySavings(CommunitySavingsMode.OVERVIEW"));
        assertTrue(source.contains("openCommunitySavings(CommunitySavingsMode.ADD_GROUP"));
        assertTrue(source.contains("openCommunitySavings(CommunitySavingsMode.BANK_NKHONDE"));
        assertTrue(source.contains("openCommunitySavings(CommunitySavingsMode.CHIPELEGANYU"));
        assertTrue(source.contains("openCommunitySavings(CommunitySavingsMode.CONTRIBUTIONS"));
        assertTrue(source.contains("openCommunitySavings(CommunitySavingsMode.PAYOUTS_SHARE_OUTS"));
        assertTrue(source.contains("openCommunitySavings(CommunitySavingsMode.HISTORY"));
    }

    @Test
    void accountsPassiveTypeRefreshDoesNotRequireAccountType() throws Exception {
        Path controller = Path.of("src/main/java/com/wk/pfmis/controllers/AccountsController.java");
        String source = Files.readString(controller);
        String methodBody = source.substring(
                source.indexOf("private void updateTypeSpecificFields()"),
                source.indexOf("private void validateNewAccount")
        );

        assertTrue(methodBody.contains("currentAccountTypeText()"));
        assertTrue(methodBody.contains("type.isBlank()"));
        assertFalse(methodBody.contains("accountType()"), "Passive UI refresh must not call required Account Type validation");
    }

    @Test
    void communitySavingsInitializationDoesNotRequireCurrency() throws Exception {
        Path controller = Path.of("src/main/java/com/wk/pfmis/controllers/CommunitySavingsController.java");
        String source = Files.readString(controller);
        String initializeBody = source.substring(
                source.indexOf("public void initialize()"),
                source.indexOf("private void applyRequestedMode")
        );
        String clearBody = source.substring(
                source.indexOf("private void clearSavingsAccountForm()"),
                source.indexOf("private void configureOptions")
        );
        String summaryBody = source.substring(
                source.indexOf("private void updateChipeleganyuSummary"),
                source.indexOf("private void updateChipeleganyuButtons")
        );

        assertFalse(initializeBody.contains("requireCurrencyCode()"));
        assertFalse(clearBody.contains("database.getDefaultCurrency()"));
        assertTrue(clearBody.contains("currencyBox.setValue(null)"));
        assertTrue(summaryBody.contains("baseCurrencyCode()"));
        assertTrue(source.contains("private String currentCurrencyCode()"));
        assertTrue(source.contains("private String requireCurrencyCode()"));
    }

    @Test
    void communitySavingsEveryNavigationModeIsHandled() throws Exception {
        Path controller = Path.of("src/main/java/com/wk/pfmis/controllers/CommunitySavingsController.java");
        String source = Files.readString(controller);
        String methodBody = source.substring(
                source.indexOf("private void applyRequestedMode"),
                source.indexOf("private void focusSavingsGroupType")
        );

        assertTrue(methodBody.contains("case ADD_GROUP"));
        assertTrue(methodBody.contains("case BANK_NKHONDE"));
        assertTrue(methodBody.contains("case CHIPELEGANYU"));
        assertTrue(methodBody.contains("case CONTRIBUTIONS"));
        assertTrue(methodBody.contains("case PAYOUTS_SHARE_OUTS"));
        assertTrue(methodBody.contains("case HISTORY"));
        assertTrue(methodBody.contains("case OVERVIEW"));
    }

    @Test
    void dashboardViewLoadFailuresHideTechnicalDetailsByDefault() throws Exception {
        Path controller = Path.of("src/main/java/com/wk/pfmis/controllers/DashboardController.java");
        String source = Files.readString(controller);
        String methodBody = source.substring(
                source.indexOf("private void showViewLoadFailure"),
                source.indexOf("private String failureDetails")
        );

        assertTrue(methodBody.contains("could not be opened"));
        assertTrue(methodBody.contains("A problem occurred while preparing this screen."));
        assertTrue(methodBody.contains("new TitledPane(\"Technical details\", details)"));
        assertTrue(methodBody.contains("technicalDetails.setExpanded(false)"));
        assertTrue(methodBody.contains("database.recordSystemLog"));
    }

    @Test
    void dailyFinanceNavigationUsesTransactionAndExpenseRecordsSemantics() throws Exception {
        Path dashboard = fxmlFiles().stream()
                .filter(path -> path.getFileName().toString().equals("Dashboard.fxml"))
                .findFirst()
                .orElseThrow();
        String content = Files.readString(dashboard);
        String controller = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/DashboardController.java"));

        assertTrue(content.contains("<TitledPane text=\"Transactions\""));
        assertTrue(content.contains("text=\"Transaction Ledger\""));
        assertTrue(content.contains("text=\"Expense Records\""));
        assertFalse(content.contains("<TitledPane text=\"Transaction Ledger\""));
        assertFalse(content.contains("text=\"Expense Report\""));
        assertTrue(controller.contains("private void showExpenseRecords()"));
        assertTrue(controller.contains("NavigationBus.requestTransactionLedgerFilter(\"Expense\")"));
    }

    @Test
    void budgetsNavigationIsModeSpecificAndNotSingleManageScreen() throws Exception {
        Path dashboard = fxmlFiles().stream()
                .filter(path -> path.getFileName().toString().equals("Dashboard.fxml"))
                .findFirst()
                .orElseThrow();
        String content = Files.readString(dashboard);
        String controller = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/DashboardController.java"));
        String budgetFxml = Files.readString(fxmlFiles().stream()
                .filter(path -> path.getFileName().toString().equals("Budgets.fxml"))
                .findFirst()
                .orElseThrow());

        assertTrue(content.contains("<TitledPane text=\"Budgets\""));
        assertTrue(content.contains("onAction=\"#showBudgetOverview\""));
        assertTrue(content.contains("onAction=\"#showCreateBudget\""));
        assertTrue(content.contains("onAction=\"#showBudgetAllocations\""));
        assertTrue(content.contains("onAction=\"#showBudgetPerformance\""));
        assertTrue(content.contains("onAction=\"#showHouseholdBudget\""));
        assertTrue(content.contains("onAction=\"#showBudgetHistory\""));
        assertFalse(content.contains("Manage Budgets"));
        assertTrue(controller.contains("openBudgetMode(BudgetMode.OVERVIEW"));
        assertTrue(controller.contains("openBudgetMode(BudgetMode.CREATE"));
        assertTrue(controller.contains("openBudgetMode(BudgetMode.ALLOCATIONS"));
        assertTrue(controller.contains("openBudgetMode(BudgetMode.PERFORMANCE"));
        assertTrue(controller.contains("openBudgetMode(BudgetMode.HOUSEHOLD"));
        assertTrue(controller.contains("openBudgetMode(BudgetMode.HISTORY"));
        assertTrue(budgetFxml.contains("fx:id=\"overviewPane\""));
        assertTrue(budgetFxml.contains("fx:id=\"createPane\""));
        assertTrue(budgetFxml.contains("fx:id=\"allocationsPane\""));
        assertTrue(budgetFxml.contains("fx:id=\"performancePane\""));
        assertTrue(budgetFxml.contains("fx:id=\"householdPane\""));
        assertTrue(budgetFxml.contains("fx:id=\"historyPane\""));
    }

    @Test
    void sidebarNavigationHasSingleCentralActiveStateAndGlobalCssHooks() throws Exception {
        Path dashboard = fxmlFiles().stream()
                .filter(path -> path.getFileName().toString().equals("Dashboard.fxml"))
                .findFirst()
                .orElseThrow();
        String dashboardFxml = Files.readString(dashboard);
        String controller = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/DashboardController.java"));
        String css = Files.readString(Path.of("src/main/resources/com/wk/pfmis/css/Theme.css"));

        assertTrue(dashboardFxml.contains("fx:id=\"sidebarNavigation\""));
        assertTrue(dashboardFxml.contains("fx:id=\"dashboardButton\""));
        assertTrue(controller.contains("configureSidebarNavigationState()"));
        assertTrue(controller.contains("addEventFilter(ActionEvent.ACTION"));
        assertTrue(controller.contains("private void markNavigationButton(Button selectedButton)"));
        assertTrue(controller.contains("private void clearNavigationSelection()"));
        assertTrue(controller.contains("active-parent"));
        assertTrue(css.contains(".nav-button.active"));
        assertTrue(css.contains(".nav-pane.active-parent > .title"));
        assertTrue(css.contains(".workspace-error-panel"));
    }

    @Test
    void workspaceMigrationFailureStopsBeforeDashboardControllers() throws Exception {
        String mainApp = Files.readString(Path.of("src/main/java/com/wk/pfmis/MainApp.java"));

        assertTrue(mainApp.contains("database.initializeDatabase();"));
        assertTrue(mainApp.contains("showWorkspaceMigrationFailure"));
        assertTrue(mainApp.contains("Retry Migration"));
        assertTrue(mainApp.contains("No screen was opened because financial pages must not run against an incomplete schema."));
    }

    @Test
    void highRiskActionsUseInlineSecurityVerificationInsteadOfPasswordDialogs() throws Exception {
        String dataMaintenance = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/DataMaintenanceWorkflowController.java"));
        String syncRecovery = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/SyncRecoveryTaskController.java"));
        String recordDisposal = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/RecordDisposalController.java"));
        String accounts = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/AccountsController.java"));
        String login = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/LoginController.java"));
        String css = Files.readString(Path.of("src/main/resources/com/wk/pfmis/css/Theme.css"));

        assertFalse(dataMaintenance.contains("passwordDialog("));
        assertFalse(dataMaintenance.contains("passwordAndPhraseDialog("));
        assertFalse(syncRecovery.contains("restorePasswordDialog("));
        assertFalse(dataMaintenance.contains("new PasswordField()"));
        assertFalse(syncRecovery.contains("new PasswordField()"));
        assertFalse(recordDisposal.contains("TextInputDialog"));
        assertTrue(dataMaintenance.contains("SecurityVerificationPane"));
        assertTrue(syncRecovery.contains("SecurityVerificationPane"));
        assertTrue(recordDisposal.contains("SecurityVerificationPane"));
        assertTrue(accounts.contains("lifecycleInlinePane"));
        assertTrue(login.contains("canonicalLoginIdentifier"));
        assertTrue(css.contains(".security-verification-pane"));
        assertTrue(css.contains(".auth-saved-login-panel"));
    }

    @Test
    void dataRecordsPolicyIsWorkbenchNotVisibleWireframe() throws Exception {
        Path policy = fxmlFiles().stream()
                .filter(path -> path.getFileName().toString().equals("DataRecordsPolicy.fxml"))
                .findFirst()
                .orElseThrow();
        String fxml = Files.readString(policy);
        String controller = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/DataRecordsPolicyController.java"));

        assertFalse(fxml.toLowerCase().contains("wireframe"));
        assertFalse(controller.toLowerCase().contains("wireframe"));
        assertTrue(fxml.contains("Workflow Workbench"));
        assertTrue(controller.contains("Workflow Workbench"));
    }

    @Test
    void budgetDoesNotExposeAssetRegistrationButProjectsStillUseHandoff() throws Exception {
        String budgets = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/BudgetsController.java"));
        String projects = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/ProjectListController.java"));
        String budgetFxml = Files.readString(fxmlFiles().stream()
                .filter(path -> path.getFileName().toString().equals("Budgets.fxml"))
                .findFirst()
                .orElseThrow());
        String projectFxml = Files.readString(fxmlFiles().stream()
                .filter(path -> path.getFileName().toString().equals("ProjectList.fxml"))
                .findFirst()
                .orElseThrow());

        assertFalse(budgets.contains("NavigationBus.requestAssetRegistration"));
        assertTrue(projects.contains("NavigationBus.requestAssetRegistration"));
        assertFalse(budgets.contains("database.registerBudgetPlanAsAsset"));
        assertFalse(projects.contains("database.registerProjectAsAsset"));
        assertFalse(budgetFxml.contains("Assess for Asset"));
        assertTrue(projectFxml.contains("Assess for Asset"));
    }

    @Test
    void v9CoreModuleNavigationStartsWithOverviewAndUsesSharedSidebarStyle() throws Exception {
        Path dashboard = fxmlFiles().stream()
                .filter(path -> path.getFileName().toString().equals("Dashboard.fxml"))
                .findFirst()
                .orElseThrow();
        String content = Files.readString(dashboard);

        assertSectionOrder(content, "Income", "incomeOverviewButton", "addIncomeButton", "incomeRecordsButton", "expectedIncomeButton", "recurringIncomeButton");
        assertSectionOrder(content, "Expenses", "expenseOverviewButton", "recordExpenseButton", "expenseRecordsButton", "plannedExpensesButton");
        assertSectionOrder(content, "Transactions", "transactionsOverviewButton", "transactionLedgerButton", "transferMoneyButton", "scheduledTransfersButton", "correctionsReversalsButton");
        assertSectionOrder(content, "Loans", "loanOverviewButton", "newLoanButton", "loanRecordsButton", "recordRepaymentButton", "repaymentScheduleButton", "loanContactsButton");
        assertSectionOrder(content, "Goals", "goalsOverviewButton", "addGoalButton", "goalContributionsButton", "goalStepsButton", "goalHistoryButton");
        assertSectionOrder(content, "Projects", "projectOverviewButton", "addProjectButton", "projectActivitiesButton", "projectFinancesButton", "projectMilestonesStatusButton", "projectHistoryButton");
        assertSectionOrder(content, "Assets", "assetOverviewButton", "assetRegisterButton", "assetRecognitionButton", "registerAssetButton", "assetMaintenanceButton", "assetValuationButton", "assetTransferCustodyButton", "assetSaleDisposalButton", "assetHistoryButton");

        String css = Files.readString(Path.of("src/main/resources/com/wk/pfmis/css/Theme.css"));
        assertTrue(css.contains(".nav-sub-button.active"));
        assertFalse(content.contains("overview-button"), "Core module sidebar items should use the shared nav-sub-button style.");
    }

    @Test
    void v9CoreWorkspaceDestinationsExistAndRouteFromDashboard() throws Exception {
        String dashboard = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/DashboardController.java"));
        String[] viewFiles = {
                "IncomeOverview.fxml",
                "RecurringIncome.fxml",
                "ExpenseOverview.fxml",
                "PlannedRecurringExpenses.fxml",
                "TransactionsOverview.fxml",
                "CorrectionsReversals.fxml",
                "LoanOverview.fxml",
                "GoalsOverview.fxml",
                "GoalHistoryLifecycle.fxml",
                "ProjectOverview.fxml",
                "ProjectFinances.fxml",
                "ProjectMilestonesStatus.fxml",
                "ProjectHistoryLifecycle.fxml",
                "AssetOverview.fxml",
                "AssetRecognition.fxml",
                "AssetMaintenance.fxml",
                "AssetValuation.fxml",
                "AssetTransferCustody.fxml",
                "AssetSaleDisposal.fxml",
                "AssetHistory.fxml"
        };
        for (String viewFile : viewFiles) {
            Path view = fxmlFiles().stream()
                    .filter(path -> path.getFileName().toString().equals(viewFile))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(viewFile + " is missing"));
            String fxml = Files.readString(view);
            assertTrue(fxml.contains("fx:controller=\"com.wk.pfmis.controllers."), viewFile + " must have a controller");
            assertTrue(dashboard.contains("\"" + viewFile + "\""), viewFile + " must be loaded by DashboardController");
        }

        String[] routes = {
                "INCOME_OVERVIEW", "RECURRING_INCOME",
                "EXPENSE_OVERVIEW", "PLANNED_RECURRING_EXPENSES",
                "TRANSACTIONS_OVERVIEW", "CORRECTIONS_REVERSALS",
                "LOAN_OVERVIEW", "LOAN_RECORDS",
                "GOALS_OVERVIEW", "GOAL_HISTORY",
                "PROJECT_OVERVIEW", "PROJECT_FINANCES", "PROJECT_MILESTONES_STATUS", "PROJECT_HISTORY",
                "ASSET_OVERVIEW", "ASSET_RECOGNITION", "ASSET_MAINTENANCE", "ASSET_VALUATION",
                "ASSET_TRANSFER_CUSTODY", "ASSET_SALE_DISPOSAL", "ASSET_HISTORY"
        };
        for (String route : routes) {
            assertTrue(dashboard.contains("case " + route + " ->"), route + " must be handled by core workspace routing");
        }
    }

    @Test
    void assetRegistrationContextIsSingleUse() {
        NavigationBus.reset();
        NavigationBus.requestAssetRegistration("Project", 42, "Borehole", "Use purchase evidence.");

        NavigationBus.AssetRegistrationContext context = NavigationBus.consumeRequestedAssetRegistrationContext();

        assertEquals("Project", context.sourceType());
        assertEquals(42, context.sourceId());
        assertEquals("Borehole", context.sourceName());
        assertEquals("Use purchase evidence.", context.guidance());
        assertNull(NavigationBus.consumeRequestedAssetRegistrationContext());
        NavigationBus.reset();
    }

    @Test
    void reportsNavigationToleratesMissingRequestedReportType() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/ReportsController.java"));

        assertTrue(controller.contains("requestedReportType != null && group.reportTypes().contains(requestedReportType)"));
    }

    private static List<Path> fxmlFiles() throws URISyntaxException, IOException {
        Path views = Path.of(FxmlControllerAuditTest.class
                .getResource("/com/wk/pfmis/views")
                .toURI());
        try (var stream = Files.list(views)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".fxml"))
                    .sorted()
                    .toList();
        }
    }

    private static void verifyActions(Path fxml, String content, Class<?> controllerClass, List<String> failures) {
        Matcher matcher = ACTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String action = matcher.group(1);
            if (!hasActionMethod(controllerClass, action)) {
                failures.add(fxml.getFileName() + " -> " + controllerClass.getName() + "#" + action + " does not resolve");
            }
        }
    }

    private static boolean hasActionMethod(Class<?> controllerClass, String action) {
        for (Method method : controllerClass.getDeclaredMethods()) {
            if (!method.getName().equals(action)) {
                continue;
            }
            int parameterCount = method.getParameterCount();
            if (parameterCount == 0 || (parameterCount == 1 && ActionEvent.class.isAssignableFrom(method.getParameterTypes()[0]))) {
                return true;
            }
        }
        return false;
    }

    private static void verifyFxIds(Path fxml, String content, Class<?> controllerClass, List<String> failures) {
        Matcher matcher = FX_ID_PATTERN.matcher(content);
        while (matcher.find()) {
            String fxId = matcher.group(1);
            Field field = field(controllerClass, fxId);
            if (field == null) {
                failures.add(fxml.getFileName() + " -> " + controllerClass.getName() + " is missing field " + fxId);
            } else if (!field.isAnnotationPresent(javafx.fxml.FXML.class) && !Modifier.isPublic(field.getModifiers())) {
                failures.add(fxml.getFileName() + " -> " + controllerClass.getName() + "#" + fxId + " is not injectable");
            }
        }
    }

    private static Field field(Class<?> controllerClass, String name) {
        Class<?> current = controllerClass;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void assertSectionOrder(String content, String sectionTitle, String... fxIds) {
        int sectionStart = content.indexOf("<TitledPane text=\"" + sectionTitle + "\"");
        assertTrue(sectionStart >= 0, sectionTitle + " navigation section is missing");
        int sectionEnd = content.indexOf("<TitledPane text=\"", sectionStart + 1);
        String section = sectionEnd < 0 ? content.substring(sectionStart) : content.substring(sectionStart, sectionEnd);
        int cursor = 0;
        for (String fxId : fxIds) {
            String token = "fx:id=\"" + fxId + "\"";
            int index = section.indexOf(token, cursor);
            assertTrue(index >= 0, sectionTitle + " missing or out of order: " + fxId);
            String buttonStart = section.substring(0, index);
            int openTag = buttonStart.lastIndexOf("<Button");
            int closeTag = section.indexOf("/>", index);
            String button = section.substring(openTag, closeTag);
            assertTrue(button.contains("styleClass=\"nav-sub-button\""), fxId + " must use shared nav-sub-button style");
            cursor = index + token.length();
        }
    }
}
