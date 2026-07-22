package com.wk.pfmis.ai;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.AiInteractionRecord;
import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.BudgetProgress;
import com.wk.pfmis.models.DashboardStats;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.HouseholdMonthMember;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ReportRow;
import com.wk.pfmis.models.SystemLogRecord;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class PfmisIntelligenceService {
    private static final int MAX_PROMPT_CHARACTERS = 11_000;
    private static final int MAX_SCREEN_ITEMS = 45;
    private static final int MAX_LIST_ITEMS = 8;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final AiRecommendationService aiService = new AiRecommendationService();

    public String askPrepared(String moduleName, String actionName, String preparedPrompt) {
        AiSettings settings = database.getAiSettings();
        String provider = settings == null ? "Not configured" : settings.getDisplayName();
        try {
            String response = aiService.generateGoalRecommendation(settings, preparedPrompt);
            database.recordAiInteraction(clean(moduleName, "PFMIS"), clean(actionName, "General assistance"), provider, "SUCCESS");
            return response;
        } catch (RuntimeException exception) {
            database.recordAiInteraction(clean(moduleName, "PFMIS"), clean(actionName, "General assistance"), provider, "FAILED");
            throw exception;
        }
    }

    public String buildPrompt(
            AiSettings settings,
            String moduleName,
            String actionName,
            String userQuestion,
            Node screenRoot
    ) {
        boolean includeEnteredValues = settings != null && settings.isLocalProvider();
        String module = clean(moduleName, "PFMIS");
        String action = clean(actionName, "General assistance");
        String question = clean(userQuestion, "Review this PFMIS step and recommend the safest next action.");
        String prompt = """
                PFMIS CONTEXTUAL ASSISTANCE REQUEST
                Current module: %s
                Requested assistance: %s
                User question: %s

                VERIFIED FINANCIAL FACTS FROM PFMIS
                %s

                CURRENT SCREEN / STEP
                %s

                RESPONSE RULES
                1. Use only the verified facts and visible screen information supplied above.
                2. Never claim that you saved, edited, approved, deleted, transferred, or submitted anything.
                3. Point out missing information, contradictions, budget impact, affordability, and data-quality risks.
                4. Give a clear recommendation and the next practical action in PFMIS.
                5. Distinguish facts from advice. Do not invent exchange rates, income, balances, dates, or policies.
                6. Amounts are primarily in MWK. Warn when different currencies are mixed without conversion.
                7. Keep the response structured under: SUMMARY, CHECKS, RISKS, NEXT ACTIONS.
                """.formatted(
                module,
                action,
                question,
                financialContext(module),
                extractScreenContext(screenRoot, includeEnteredValues)
        );
        return limit(prompt, MAX_PROMPT_CHARACTERS);
    }

    public String deterministicOverview() {
        DashboardStats stats = database.getDashboardStats();
        double savingsRate = stats.getMonthlyIncome() <= 0
                ? 0
                : (stats.getMonthlySavings() / stats.getMonthlyIncome()) * 100;
        return "Balance: " + MoneyUtil.mwk(stats.getTotalBalance())
                + " | Income: " + MoneyUtil.mwk(stats.getMonthlyIncome())
                + " | Expenses: " + MoneyUtil.mwk(stats.getMonthlyExpenses())
                + " | Savings: " + MoneyUtil.mwk(stats.getMonthlySavings())
                + " | Savings rate: " + String.format(Locale.ENGLISH, "%.1f%%", savingsRate)
                + " | Active goals: " + stats.getActiveGoals()
                + " | Active projects: " + stats.getActiveProjects() + ".";
    }

    public List<String> smartNudges() {
        List<String> nudges = new ArrayList<>();
        DashboardStats stats = database.getDashboardStats();

        if (stats.getMonthlyIncome() <= 0 && stats.getMonthlyExpenses() > 0) {
            nudges.add("No income is recorded this month while expenses exist. Confirm income entries are complete.");
        } else if (stats.getMonthlyExpenses() > stats.getMonthlyIncome() && stats.getMonthlyExpenses() > 0) {
            nudges.add("This month's expenses exceed income by "
                    + MoneyUtil.mwk(stats.getMonthlyExpenses() - stats.getMonthlyIncome()) + ".");
        } else if (stats.getMonthlyIncome() > 0) {
            double savingsRate = (stats.getMonthlySavings() / stats.getMonthlyIncome()) * 100;
            if (savingsRate < 10) {
                nudges.add("The current savings rate is "
                        + String.format(Locale.ENGLISH, "%.1f%%", savingsRate)
                        + ". Review discretionary expenses and active budgets.");
            }
        }

        String currentMonth = YearMonth.now().toString();
        for (BudgetProgress progress : database.listBudgetProgress(currentMonth)) {
            if (progress.getPercentUsed() >= 100) {
                nudges.add(progress.getBudgetName() + " is over its monthly limit by "
                        + MoneyUtil.mwk(Math.abs(progress.getRemaining())) + ".");
            } else if (progress.getPercentUsed() >= 80) {
                nudges.add(progress.getBudgetName() + " has used "
                        + String.format(Locale.ENGLISH, "%.0f%%", progress.getPercentUsed())
                        + " of its limit.");
            }
        }
        double householdUnits = database.householdUnitsForMonth(currentMonth);
        if (householdUnits <= 0 && !database.listBudgetProgress(currentMonth).isEmpty()) {
            nudges.add("No household count is registered for this month, so per-person budget analysis is incomplete.");
        }

        for (Project project : database.listProjects()) {
            if (project.getPlannedBudget() > 0 && project.getAmountSpent() > project.getPlannedBudget()) {
                nudges.add(project.getProjectName() + " is over project budget by "
                        + MoneyUtil.mwk(project.getAmountSpent() - project.getPlannedBudget()) + ".");
            }
        }

        for (Goal goal : database.listGoals()) {
            LocalDate targetDate = parseDate(goal.getTargetDate());
            if (targetDate != null
                    && targetDate.isBefore(LocalDate.now())
                    && goal.getRemainingAmount() > 0
                    && !"COMPLETED".equalsIgnoreCase(goal.getStatus())) {
                nudges.add(goal.getGoalName() + " is past its target date with "
                        + MoneyUtil.mwk(goal.getRemainingAmount()) + " still needed.");
            }
        }

        Set<String> currencies = database.listAccounts().stream()
                .map(Account::getCurrency)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ENGLISH))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (currencies.size() > 1) {
            nudges.add("Multiple account currencies are present (" + String.join(", ", currencies)
                    + "). Dashboard totals need exchange-rate conversion before they are comparable.");
        }

        List<BackupRecord> backups = database.listBackupHistory();
        if (backups.isEmpty()) {
            nudges.add("No verified database backup is recorded. Create a backup before making major changes.");
        } else if (backupIsOlderThanDays(backups.getFirst().getCreatedAt(), 7)) {
            nudges.add("The latest recorded backup is more than seven days old.");
        }

        if (nudges.isEmpty()) {
            nudges.add("No urgent issues found.");
        }
        return nudges.stream().distinct().limit(10).toList();
    }

    public String primaryNudge() {
        return smartNudges().getFirst();
    }

    private String financialContext(String moduleName) {
        String normalized = clean(moduleName, "PFMIS").toLowerCase(Locale.ENGLISH);
        StringBuilder context = new StringBuilder();
        context.append("Current month: ").append(YearMonth.now()).append('\n');
        context.append(deterministicOverview()).append('\n');

        if (containsAny(normalized, "dashboard", "report", "ai", "assist", "analysis", "pfmis")) {
            appendSystemWorkflowCoverage(context);
            appendFullReportPackage(context);
        }
        if (containsAny(normalized, "account", "transfer", "income", "expense", "transaction", "ledger", "currency", "payment", "analysis")) {
            appendAccounts(context);
        }
        if (containsAny(normalized, "budget", "expense", "dashboard", "report", "ai", "assist", "analysis")) {
            appendBudgets(context);
        }
        if (containsAny(normalized, "goal", "dashboard", "ai", "assist", "analysis")) {
            appendGoals(context);
        }
        if (containsAny(normalized, "project", "dashboard", "report", "ai", "assist", "analysis")) {
            appendProjects(context);
        }
        if (containsAny(normalized, "income", "expense", "transaction", "ledger", "transfer", "dashboard", "report", "ai", "assist", "analysis")) {
            appendRecentTransactions(context);
        }
        if (containsAny(normalized, "analysis", "log", "audit", "system")) {
            appendLogs(context);
        }
        return limit(context.toString(), 8_500);
    }

    private void appendSystemWorkflowCoverage(StringBuilder context) {
        context.append("System workflow coverage requested:\n");
        context.append("- Setup and administration: accounts, categories, payment methods, currencies, backup/restore, Smart Analysis settings.\n");
        context.append("- Transaction recording: income, expenses, transfers, money lent, money borrowed, repayments, cancellations.\n");
        context.append("- Planning: monthly budgets, household members, goals, goal steps, goal-to-project conversion.\n");
        context.append("- Project management: projects, project activities, project status, planned cost, actual spending.\n");
        context.append("- Reporting: monthly summary, income, expense, project, account, loan reports.\n");
        context.append("- Controls: data quality, cancelled transaction filters, mixed currency warnings, backup age, system logs, analysis logs.\n");
    }

    private void appendFullReportPackage(StringBuilder context) {
        String month = YearMonth.now().toString();
        appendReportRows(context, "Monthly expense report by category", database.categorySpendingReport(month));
        appendReportRows(context, "All-time expense report by category", database.categorySpendingReport());
        appendReportRows(context, "Income report by source", database.incomeSourceReport());
        appendReportRows(context, "Income report by source and account", database.incomeSourceByAccountReport(month));
        appendReportRows(context, "Expense report by category and account", database.categorySpendingByAccountReport(month));
        appendReportRows(context, "Project report", database.projectSpendingReport(month));
        appendReportRows(context, "Account report", database.accountBalanceReport());
        appendReportRows(context, "Loan report by person", database.lendingByPersonReport(month));
    }

    private void appendAccounts(StringBuilder context) {
        List<Account> accounts = database.listAccounts();
        context.append("Accounts:\n");
        accounts.stream().limit(MAX_LIST_ITEMS).forEach(account -> context.append("- ")
                .append(account.getAccountName()).append(" | type=").append(account.getAccountType())
                .append(" | currency=").append(account.getCurrency())
                .append(" | balance=").append(account.getCurrentBalance())
                .append(" | status=").append(account.getStatus()).append('\n'));
        if (accounts.isEmpty()) {
            context.append("- None registered.\n");
        }
    }

    private void appendBudgets(StringBuilder context) {
        String month = YearMonth.now().toString();
        double householdUnits = database.householdUnitsForMonth(month);
        List<HouseholdMonthMember> household = database.listHouseholdMonthMembers(month);
        context.append("Current-month household units: ").append(householdUnits).append('\n');
        context.append("Current-month household members:\n");
        household.stream().limit(MAX_LIST_ITEMS).forEach(member -> context.append("- ")
                .append(member.getPersonName())
                .append(" | type=").append(member.isBudgetOwner() ? "OWNER" : "MEMBER")
                .append(" | relationship=").append(blank(member.getRelationship(), "-"))
                .append(" | status=").append(member.getPresenceStatus())
                .append(" | duration=").append("FOREVER".equalsIgnoreCase(member.getDurationScope()) ? "ONGOING" : "THIS_MONTH_ONLY")
                .append(" | share=").append(member.getShareWeight()).append('\n'));
        if (household.isEmpty()) {
            context.append("- No household roster registered for this month.\n");
        }
        context.append("Current-month budgets:\n");
        List<BudgetProgress> budgets = database.listBudgetProgress(month);
        budgets.stream().limit(MAX_LIST_ITEMS).forEach(progress -> context.append("- ")
                .append(progress.getBudgetName()).append(" | category=").append(blank(progress.getCategoryName(), "All expenses"))
                .append(" | limit=").append(progress.getAmountLimit())
                .append(" | spent=").append(progress.getSpent())
                .append(" | spent per person=").append(progress.getSpentPerPerson())
                .append(" | result=").append(progress.getMonthResult()).append('\n'));
        if (budgets.isEmpty()) {
            context.append("- No budget registered for this month.\n");
        }
    }

    private void appendGoals(StringBuilder context) {
        context.append("Goals:\n");
        List<Goal> goals = database.listGoals();
        goals.stream().limit(MAX_LIST_ITEMS).forEach(goal -> context.append("- ")
                .append(goal.getGoalName()).append(" | target=").append(goal.getTargetAmount())
                .append(" | saved=").append(goal.getCurrentAmount())
                .append(" | remaining=").append(goal.getRemainingAmount())
                .append(" | target date=").append(blank(goal.getTargetDate(), "not set"))
                .append(" | status=").append(goal.getStatus()).append('\n'));
        if (goals.isEmpty()) {
            context.append("- No goals registered.\n");
        }
    }

    private void appendProjects(StringBuilder context) {
        context.append("Projects:\n");
        List<Project> projects = database.listProjects();
        projects.stream().limit(MAX_LIST_ITEMS).forEach(project -> context.append("- ")
                .append(project.getProjectName()).append(" | budget=").append(project.getPlannedBudget())
                .append(" | spent=").append(project.getAmountSpent())
                .append(" | remaining=").append(project.getRemainingBudget())
                .append(" | status=").append(project.getStatus()).append('\n'));
        if (projects.isEmpty()) {
            context.append("- No projects registered.\n");
        }
    }

    private void appendRecentTransactions(StringBuilder context) {
        context.append("Recent transactions:\n");
        List<FinanceTransaction> transactions = database.listRecentTransactions(MAX_LIST_ITEMS);
        transactions.forEach(transaction -> context.append("- ")
                .append(transaction.getTransactionDate()).append(" | ")
                .append(transaction.getTransactionType()).append(" | ")
                .append(blank(transaction.getCategoryName(), "Uncategorised")).append(" | amount=")
                .append(transaction.getAmount()).append(" | account=")
                .append(transaction.getAccountName()).append('\n'));
        if (transactions.isEmpty()) {
            context.append("- No transactions registered.\n");
        }
    }

    private void appendLogs(StringBuilder context) {
        context.append("Recent system events:\n");
        List<SystemLogRecord> systemLogs = database.listSystemLogHistory(MAX_LIST_ITEMS);
        systemLogs.forEach(log -> context.append("- ")
                .append(log.getCreatedAt()).append(" | ")
                .append(log.getSeverity()).append(" | ")
                .append(log.getModuleName()).append(" | ")
                .append(log.getActionName())
                .append(log.getDetails() == null || log.getDetails().isBlank() ? "" : " | " + log.getDetails())
                .append('\n'));
        if (systemLogs.isEmpty()) {
            context.append("- No system log events recorded.\n");
        }

        context.append("Recent analysis requests:\n");
        List<AiInteractionRecord> aiLogs = database.listAiInteractionHistory(MAX_LIST_ITEMS);
        aiLogs.forEach(log -> context.append("- ")
                .append(log.getCreatedAt()).append(" | ")
                .append(log.getStatus()).append(" | ")
                .append(log.getModuleName()).append(" | ")
                .append(log.getActionName()).append(" | provider=")
                .append(log.getProviderName())
                .append('\n'));
        if (aiLogs.isEmpty()) {
            context.append("- No analysis requests recorded.\n");
        }
    }

    private void appendReportRows(StringBuilder context, String heading, List<ReportRow> rows) {
        context.append(heading).append(":\n");
        rows.stream().limit(MAX_LIST_ITEMS).forEach(row -> context.append("- ")
                .append(row.getLabel())
                .append(row.getAccount() == null || row.getAccount().isBlank() ? "" : " | account=" + row.getAccount())
                .append(" | amount=").append(row.getAmount()).append('\n'));
        if (rows.isEmpty()) {
            context.append("- No data.\n");
        }
    }

    private String extractScreenContext(Node root, boolean includeEnteredValues) {
        if (root == null) {
            return "No current screen controls were available.";
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        collectScreenItems(root, includeEnteredValues, items);
        if (items.isEmpty()) {
            return "No entered form values or visible step labels were detected.";
        }
        String privacyNote = includeEnteredValues
                ? "Local mode: non-sensitive entered values are included."
                : "External mode: entered values are withheld; only screen labels are included.";
        return privacyNote + "\n" + items.stream()
                .limit(MAX_SCREEN_ITEMS)
                .map(item -> "- " + item)
                .collect(Collectors.joining("\n"));
    }

    private void collectScreenItems(Node node, boolean includeEnteredValues, Set<String> items) {
        if (node == null || items.size() >= MAX_SCREEN_ITEMS || !node.isVisible()) {
            return;
        }
        if (node instanceof PasswordField) {
            items.add("Sensitive password/API-key field is present; its value was not shared.");
        } else if (node instanceof TextInputControl input && input.isEditable()) {
            String prompt = clean(input.getPromptText(), "Input field");
            String value = input.getText() == null ? "" : input.getText().trim();
            if (isSensitiveLabel(prompt)) {
                items.add(prompt + ": [redacted]");
            } else if (includeEnteredValues && !value.isBlank()) {
                items.add(prompt + ": " + limit(value, 180));
            } else if (!prompt.equals("Input field")) {
                items.add(prompt + (value.isBlank() ? "" : ": [entered value withheld]"));
            }
        } else if (node instanceof DatePicker datePicker) {
            items.add("Date: " + (datePicker.getValue() == null ? "not selected" : datePicker.getValue()));
        } else if (node instanceof ComboBoxBase<?> comboBox && comboBox.getValue() != null) {
            items.add("Selected option: " + limit(String.valueOf(comboBox.getValue()), 160));
        } else if (node instanceof CheckBox checkBox) {
            items.add(clean(checkBox.getText(), "Option") + ": " + (checkBox.isSelected() ? "selected" : "not selected"));
        } else if (node instanceof RadioButton radioButton) {
            items.add(clean(radioButton.getText(), "Option") + ": " + (radioButton.isSelected() ? "selected" : "not selected"));
        } else if (node instanceof TableView<?> tableView) {
            Object selected = tableView.getSelectionModel().getSelectedItem();
            items.add(selected == null ? "Table present; no row selected." : "Selected table row: " + limit(String.valueOf(selected), 180));
        } else if (node instanceof Label label) {
            String value = label.getText() == null ? "" : label.getText().trim();
            if (!value.isBlank() && value.length() <= 120 && !looksLikePureAmount(value)) {
                items.add(value);
            }
        } else if (node instanceof Labeled labeled) {
            String value = labeled.getText() == null ? "" : labeled.getText().trim();
            if (!value.isBlank() && value.length() <= 120) {
                items.add(value);
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectScreenItems(child, includeEnteredValues, items);
                if (items.size() >= MAX_SCREEN_ITEMS) {
                    return;
                }
            }
        }
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSensitiveLabel(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ENGLISH);
        return containsAny(normalized, "api key", "password", "account number", "reference number", "phone", "token", "secret");
    }

    private boolean looksLikePureAmount(String value) {
        return value.matches("(?i)^(MWK|USD|EUR|GBP|ZAR)?\\s*[+-]?[0-9,]+(?:\\.[0-9]{1,2})?$");
    }

    private boolean backupIsOlderThanDays(String createdAt, int days) {
        if (createdAt == null || createdAt.isBlank()) {
            return true;
        }
        try {
            String normalized = createdAt.replace(' ', 'T');
            LocalDateTime backupDate = LocalDateTime.parse(normalized.length() > 19 ? normalized.substring(0, 19) : normalized);
            return backupDate.isBefore(LocalDateTime.now().minusDays(days));
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String limit(String value, int maximum) {
        if (value == null) {
            return "";
        }
        return value.length() <= maximum ? value : value.substring(0, Math.max(0, maximum - 3)) + "...";
    }
}
