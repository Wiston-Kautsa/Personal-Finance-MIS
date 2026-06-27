package com.wk.pfmis.db;

import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.DashboardStats;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.Person;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectActivity;
import com.wk.pfmis.models.ReportRow;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHandler {
    private static final DatabaseHandler INSTANCE = new DatabaseHandler();
    private static final String DB_URL = "jdbc:sqlite:pfmis.db";

    private DatabaseHandler() {
    }

    public static DatabaseHandler getInstance() {
        return INSTANCE;
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(DB_URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public void initializeDatabase() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        account_name TEXT NOT NULL,
                        account_type TEXT NOT NULL,
                        currency TEXT NOT NULL DEFAULT 'MWK',
                        bank_provider_name TEXT,
                        account_number TEXT,
                        opening_balance REAL NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        notes TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT
                    )
                    """);
            migrateAccountsTable(connection);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        category_name TEXT NOT NULL UNIQUE,
                        category_type TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS projects (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        project_name TEXT NOT NULL,
                        description TEXT,
                        planned_budget REAL DEFAULT 0,
                        start_date TEXT,
                        end_date TEXT,
                        status TEXT DEFAULT 'ACTIVE',
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS project_activities (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        project_id INTEGER NOT NULL,
                        account_id INTEGER,
                        category_id INTEGER,
                        activity_name TEXT NOT NULL,
                        activity_date TEXT,
                        description TEXT,
                        planned_cost REAL DEFAULT 0,
                        amount_used REAL DEFAULT 0,
                        payment_method TEXT,
                        reason TEXT,
                        start_date TEXT,
                        end_date TEXT,
                        status TEXT DEFAULT 'ACTIVE',
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (project_id) REFERENCES projects(id),
                        FOREIGN KEY (account_id) REFERENCES accounts(id),
                        FOREIGN KEY (category_id) REFERENCES categories(id)
                    )
                    """);
            migrateProjectActivitiesTable(connection);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS people (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        full_name TEXT NOT NULL,
                        phone_number TEXT,
                        relationship TEXT,
                        notes TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS goals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        goal_name TEXT NOT NULL,
                        target_amount REAL NOT NULL,
                        current_amount REAL DEFAULT 0,
                        monthly_contribution REAL DEFAULT 0,
                        target_date TEXT,
                        status TEXT DEFAULT 'ACTIVE',
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        account_id INTEGER NOT NULL,
                        category_id INTEGER,
                        project_id INTEGER,
                        person_id INTEGER,
                        related_transaction_id INTEGER,
                        transaction_type TEXT NOT NULL,
                        transaction_purpose TEXT DEFAULT 'NORMAL',
                        transaction_status TEXT DEFAULT 'COMPLETED',
                        amount REAL NOT NULL,
                        transaction_date TEXT NOT NULL,
                        description TEXT,
                        source TEXT DEFAULT 'MANUAL',
                        payment_method TEXT,
                        reference_number TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (account_id) REFERENCES accounts(id),
                        FOREIGN KEY (category_id) REFERENCES categories(id),
                        FOREIGN KEY (project_id) REFERENCES projects(id),
                        FOREIGN KEY (person_id) REFERENCES people(id),
                        FOREIGN KEY (related_transaction_id) REFERENCES transactions(id)
                    )
                    """);
            migrateTransactionsTable(connection);
            seedCategories(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize PFMIS database", exception);
        }
    }

    private void migrateAccountsTable(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "accounts", "currency", "TEXT NOT NULL DEFAULT 'MWK'");
        addColumnIfMissing(connection, "accounts", "bank_provider_name", "TEXT");
        addColumnIfMissing(connection, "accounts", "account_number", "TEXT");
        addColumnIfMissing(connection, "accounts", "notes", "TEXT");
        addColumnIfMissing(connection, "accounts", "updated_at", "TEXT");
    }

    private void addColumnIfMissing(Connection connection, String tableName, String columnName, String columnDefinition) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if (columnName.equals(resultSet.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private void migrateTransactionsTable(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "transactions", "payment_method", "TEXT");
        addColumnIfMissing(connection, "transactions", "reference_number", "TEXT");
    }

    private void migrateProjectActivitiesTable(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "project_activities", "account_id", "INTEGER");
        addColumnIfMissing(connection, "project_activities", "category_id", "INTEGER");
        addColumnIfMissing(connection, "project_activities", "activity_date", "TEXT");
        addColumnIfMissing(connection, "project_activities", "amount_used", "REAL DEFAULT 0");
        addColumnIfMissing(connection, "project_activities", "payment_method", "TEXT");
        addColumnIfMissing(connection, "project_activities", "reason", "TEXT");
    }

    private void seedCategories(Connection connection) throws SQLException {
        String[][] categories = {
                {"Salary", "INCOME"},
                {"Business", "BOTH"},
                {"Transport", "EXPENSE"},
                {"Food", "EXPENSE"},
                {"Fuel", "EXPENSE"},
                {"School Fees", "EXPENSE"},
                {"Rent", "EXPENSE"},
                {"Utilities", "EXPENSE"},
                {"Medical", "EXPENSE"},
                {"Family Support", "EXPENSE"},
                {"Airtime/Data", "EXPENSE"},
                {"Project Expense", "EXPENSE"},
                {"Savings", "BOTH"},
                {"Loan Repayment", "BOTH"},
                {"Emergency", "EXPENSE"}
        };
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO categories (category_name, category_type) VALUES (?, ?)")) {
            for (String[] category : categories) {
                statement.setString(1, category[0]);
                statement.setString(2, category[1]);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public List<Account> listAccounts() {
        List<Account> accounts = new ArrayList<>();
        String sql = """
                SELECT a.id, a.account_name, a.account_type, a.currency, a.bank_provider_name,
                       a.account_number, a.opening_balance, a.status, a.notes, a.created_at,
                       a.opening_balance + COALESCE(SUM(
                           CASE
                               WHEN t.transaction_type = 'INCOME' THEN t.amount
                               WHEN t.transaction_type = 'EXPENSE' THEN -t.amount
                               ELSE 0
                           END
                       ), 0) AS current_balance
                FROM accounts a
                LEFT JOIN transactions t ON t.account_id = a.id
                    AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY a.id
                ORDER BY a.account_name
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                accounts.add(new Account(
                        resultSet.getInt("id"),
                        resultSet.getString("account_name"),
                        resultSet.getString("account_type"),
                        resultSet.getString("currency"),
                        resultSet.getString("bank_provider_name"),
                        resultSet.getString("account_number"),
                        resultSet.getDouble("opening_balance"),
                        resultSet.getDouble("current_balance"),
                        resultSet.getString("status"),
                        resultSet.getString("notes"),
                        resultSet.getString("created_at")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list accounts", exception);
        }
        return accounts;
    }

    public List<String> listAccountTypeSuggestions() {
        List<String> suggestions = new ArrayList<>(List.of(
                "Cash",
                "Bank Account",
                "Mobile Money",
                "Savings Account",
                "Credit Account / Loan",
                "Project Account",
                "Other"
        ));
        String sql = "SELECT DISTINCT account_type FROM accounts WHERE account_type IS NOT NULL AND trim(account_type) <> '' ORDER BY account_type";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String accountType = resultSet.getString("account_type");
                if (!suggestions.contains(accountType)) {
                    suggestions.add(accountType);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list account type suggestions", exception);
        }
        return suggestions;
    }

    public List<String> listCurrencySuggestions() {
        List<String> suggestions = new ArrayList<>(List.of("MWK - Malawian Kwacha", "USD - US Dollar", "ZAR - South African Rand"));
        String sql = "SELECT DISTINCT currency FROM accounts WHERE currency IS NOT NULL AND trim(currency) <> '' ORDER BY currency";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String currency = currencyDisplayName(resultSet.getString("currency"));
                if (!suggestions.contains(currency)) {
                    suggestions.add(currency);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list currency suggestions", exception);
        }
        return suggestions;
    }

    public String getDefaultCurrency() {
        String sql = """
                SELECT currency
                FROM accounts
                WHERE currency IS NOT NULL AND trim(currency) <> ''
                GROUP BY currency
                ORDER BY COUNT(*) DESC, currency
                LIMIT 1
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? currencyDisplayName(resultSet.getString("currency")) : "MWK - Malawian Kwacha";
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load default currency", exception);
        }
    }

    private String currencyDisplayName(String currency) {
        if (currency == null || currency.isBlank()) {
            return "MWK - Malawian Kwacha";
        }
        return switch (currency.trim()) {
            case "MWK" -> "MWK - Malawian Kwacha";
            case "USD" -> "USD - US Dollar";
            case "ZAR" -> "ZAR - South African Rand";
            default -> currency.trim();
        };
    }

    public void addAccount(
            String name,
            String type,
            String currency,
            String bankProviderName,
            String accountNumber,
            double openingBalance,
            String status,
            String notes
    ) {
        String sql = """
                INSERT INTO accounts (
                    account_name, account_type, currency, bank_provider_name, account_number,
                    opening_balance, status, notes, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, type);
            statement.setString(3, currency);
            statement.setString(4, bankProviderName);
            statement.setString(5, accountNumber);
            statement.setDouble(6, openingBalance);
            statement.setString(7, status);
            statement.setString(8, notes);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add account", exception);
        }
    }

    public void updateAccount(
            int accountId,
            String name,
            String type,
            String currency,
            String bankProviderName,
            String accountNumber,
            double openingBalance,
            String status,
            String notes
    ) {
        String sql = """
                UPDATE accounts
                SET account_name = ?,
                    account_type = ?,
                    currency = ?,
                    bank_provider_name = ?,
                    account_number = ?,
                    opening_balance = ?,
                    status = ?,
                    notes = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, type);
            statement.setString(3, currency);
            statement.setString(4, bankProviderName);
            statement.setString(5, accountNumber);
            statement.setDouble(6, openingBalance);
            statement.setString(7, status);
            statement.setString(8, notes);
            statement.setInt(9, accountId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update account", exception);
        }
    }

    public void activateAccount(int accountId) {
        updateAccountStatus(accountId, "ACTIVE");
    }

    public void deactivateAccount(int accountId) {
        updateAccountStatus(accountId, "INACTIVE");
    }

    public void deleteAccount(int accountId) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE id = ?")) {
            statement.setInt(1, accountId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete account. Accounts with transactions should be deactivated instead.", exception);
        }
    }

    private void updateAccountStatus(int accountId, String status) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("UPDATE accounts SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            statement.setString(1, status);
            statement.setInt(2, accountId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update account status", exception);
        }
    }

    public List<Category> listCategories() {
        List<Category> categories = new ArrayList<>();
        String sql = "SELECT id, category_name, category_type FROM categories ORDER BY category_name";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                categories.add(new Category(
                        resultSet.getInt("id"),
                        resultSet.getString("category_name"),
                        resultSet.getString("category_type")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list categories", exception);
        }
        return categories;
    }

    public void addCategory(String name, String type) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO categories (category_name, category_type) VALUES (?, ?)")) {
            statement.setString(1, name);
            statement.setString(2, type);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add category", exception);
        }
    }

    public List<Project> listProjects() {
        List<Project> projects = new ArrayList<>();
        String sql = """
                SELECT p.id, p.project_name, p.description, p.planned_budget, p.start_date, p.end_date, p.status,
                       COALESCE(SUM(CASE WHEN t.transaction_type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS amount_spent
                FROM projects p
                LEFT JOIN transactions t ON t.project_id = p.id
                GROUP BY p.id
                ORDER BY p.project_name
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                projects.add(new Project(
                        resultSet.getInt("id"),
                        resultSet.getString("project_name"),
                        resultSet.getString("description"),
                        resultSet.getDouble("planned_budget"),
                        resultSet.getDouble("amount_spent"),
                        resultSet.getString("start_date"),
                        resultSet.getString("end_date"),
                        resultSet.getString("status")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list projects", exception);
        }
        return projects;
    }

    public void addProject(String name, String description, double plannedBudget, String startDate, String endDate) {
        addProject(name, description, plannedBudget, startDate, endDate, "ACTIVE");
    }

    public void addProject(String name, String description, double plannedBudget, String startDate, String endDate, String status) {
        String sql = "INSERT INTO projects (project_name, description, planned_budget, start_date, end_date, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, description);
            statement.setDouble(3, plannedBudget);
            statement.setString(4, startDate);
            statement.setString(5, endDate);
            statement.setString(6, status == null || status.isBlank() ? "ACTIVE" : status);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add project", exception);
        }
    }

    public void updateProjectStatus(int projectId, String status) {
        String sql = "UPDATE projects SET status = ? WHERE id = ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status == null || status.isBlank() ? "ACTIVE" : status);
            statement.setInt(2, projectId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update project status", exception);
        }
    }

    public void addProjectActivity(
            int projectId,
            int accountId,
            Integer categoryId,
            String activityName,
            String description,
            double amountUsed,
            LocalDate activityDate,
            String paymentMethod,
            String reason,
            String status
    ) {
        if (amountUsed <= 0) {
            throw new IllegalArgumentException("Amount used must be greater than zero");
        }
        String activitySql = """
                INSERT INTO project_activities (
                    project_id, account_id, category_id, activity_name, activity_date, description,
                    planned_cost, amount_used, payment_method, reason, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String transactionSql = """
                INSERT INTO transactions (
                    account_id, category_id, project_id, transaction_type, transaction_purpose,
                    transaction_status, amount, transaction_date, description, payment_method
                ) VALUES (?, ?, ?, 'EXPENSE', 'PROJECT', ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement activity = connection.prepareStatement(activitySql);
                 PreparedStatement transaction = connection.prepareStatement(transactionSql)) {
                activity.setInt(1, projectId);
                activity.setInt(2, accountId);
                setNullableInt(activity, 3, categoryId);
                activity.setString(4, activityName);
                activity.setString(5, activityDate.toString());
                activity.setString(6, description);
                activity.setDouble(7, amountUsed);
                activity.setDouble(8, amountUsed);
                activity.setString(9, paymentMethod);
                activity.setString(10, reason);
                activity.setString(11, status);
                activity.executeUpdate();

                transaction.setInt(1, accountId);
                setNullableInt(transaction, 2, categoryId);
                transaction.setInt(3, projectId);
                transaction.setString(4, transactionStatusFromActivity(status));
                transaction.setDouble(5, amountUsed);
                transaction.setString(6, activityDate.toString());
                transaction.setString(7, activityName + (description == null || description.isBlank() ? "" : " - " + description));
                transaction.setString(8, paymentMethod);
                transaction.executeUpdate();
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add project activity", exception);
        }
    }

    private String transactionStatusFromActivity(String status) {
        return switch (status) {
            case "Pending" -> "OPEN";
            case "Cancelled" -> "CANCELLED";
            default -> "COMPLETED";
        };
    }

    public List<ProjectActivity> listProjectActivities() {
        List<ProjectActivity> activities = new ArrayList<>();
        String sql = """
                SELECT pa.id, pa.project_id, p.project_name, pa.activity_name, pa.activity_date, pa.description,
                       COALESCE(NULLIF(pa.amount_used, 0), pa.planned_cost) AS amount_used,
                       c.category_name, a.account_name, pa.payment_method, pa.reason,
                       pa.start_date, pa.end_date, pa.status
                FROM project_activities pa
                JOIN projects p ON p.id = pa.project_id
                LEFT JOIN categories c ON c.id = pa.category_id
                LEFT JOIN accounts a ON a.id = pa.account_id
                ORDER BY p.project_name, pa.activity_name
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                activities.add(new ProjectActivity(
                        resultSet.getInt("id"),
                        resultSet.getInt("project_id"),
                        resultSet.getString("project_name"),
                        resultSet.getString("activity_name"),
                        resultSet.getString("activity_date"),
                        resultSet.getString("description"),
                        resultSet.getDouble("amount_used"),
                        resultSet.getString("category_name"),
                        resultSet.getString("account_name"),
                        resultSet.getString("payment_method"),
                        resultSet.getString("reason"),
                        resultSet.getString("start_date"),
                        resultSet.getString("end_date"),
                        resultSet.getString("status")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list project activities", exception);
        }
        return activities;
    }

    public List<Person> listPeople() {
        List<Person> people = new ArrayList<>();
        String sql = "SELECT id, full_name, phone_number, relationship, notes FROM people ORDER BY full_name";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                people.add(new Person(
                        resultSet.getInt("id"),
                        resultSet.getString("full_name"),
                        resultSet.getString("phone_number"),
                        resultSet.getString("relationship"),
                        resultSet.getString("notes")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list people", exception);
        }
        return people;
    }

    public void addPerson(String fullName, String phoneNumber, String relationship, String notes) {
        String sql = "INSERT INTO people (full_name, phone_number, relationship, notes) VALUES (?, ?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fullName);
            statement.setString(2, phoneNumber);
            statement.setString(3, relationship);
            statement.setString(4, notes);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add person", exception);
        }
    }

    public List<Goal> listGoals() {
        List<Goal> goals = new ArrayList<>();
        String sql = "SELECT id, goal_name, target_amount, current_amount, monthly_contribution, target_date, status FROM goals ORDER BY goal_name";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                goals.add(new Goal(
                        resultSet.getInt("id"),
                        resultSet.getString("goal_name"),
                        resultSet.getDouble("target_amount"),
                        resultSet.getDouble("current_amount"),
                        resultSet.getDouble("monthly_contribution"),
                        resultSet.getString("target_date"),
                        resultSet.getString("status")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list goals", exception);
        }
        return goals;
    }

    public void addGoal(String name, double targetAmount, double currentAmount, double monthlyContribution, String targetDate) {
        String sql = "INSERT INTO goals (goal_name, target_amount, current_amount, monthly_contribution, target_date, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setDouble(2, targetAmount);
            statement.setDouble(3, currentAmount);
            statement.setDouble(4, monthlyContribution);
            statement.setString(5, targetDate);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add goal", exception);
        }
    }

    public void recordTransaction(
            int accountId,
            Integer categoryId,
            Integer projectId,
            Integer personId,
            String transactionType,
            String purpose,
            String status,
            double amount,
            LocalDate date,
            String description
    ) {
        recordTransaction(accountId, categoryId, projectId, personId, transactionType, purpose, status, amount, date, description, null, null);
    }

    public void recordTransaction(
            int accountId,
            Integer categoryId,
            Integer projectId,
            Integer personId,
            String transactionType,
            String purpose,
            String status,
            double amount,
            LocalDate date,
            String description,
            String paymentMethod,
            String referenceNumber
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        String insertSql = """
                INSERT INTO transactions (
                    account_id, category_id, project_id, person_id, transaction_type,
                    transaction_purpose, transaction_status, amount, transaction_date, description,
                    payment_method, reference_number
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setInt(1, accountId);
                setNullableInt(insert, 2, categoryId);
                setNullableInt(insert, 3, projectId);
                setNullableInt(insert, 4, personId);
                insert.setString(5, transactionType);
                insert.setString(6, purpose);
                insert.setString(7, status);
                insert.setDouble(8, amount);
                insert.setString(9, date.toString());
                insert.setString(10, description);
                insert.setString(11, paymentMethod);
                insert.setString(12, referenceNumber);
                insert.executeUpdate();
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record transaction", exception);
        }
    }

    private void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    public List<FinanceTransaction> listRecentTransactions(int limit) {
        List<FinanceTransaction> transactions = new ArrayList<>();
        String sql = """
                SELECT t.id, a.account_name, t.transaction_type, t.transaction_purpose, t.transaction_status,
                       c.category_name, p.project_name, pe.full_name, t.amount, t.transaction_date, t.description,
                       t.payment_method, t.reference_number
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                LEFT JOIN categories c ON c.id = t.category_id
                LEFT JOIN projects p ON p.id = t.project_id
                LEFT JOIN people pe ON pe.id = t.person_id
                ORDER BY t.transaction_date DESC, t.id DESC
                LIMIT ?
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    transactions.add(new FinanceTransaction(
                            resultSet.getInt("id"),
                            resultSet.getString("account_name"),
                            resultSet.getString("transaction_type"),
                            resultSet.getString("transaction_purpose"),
                            resultSet.getString("transaction_status"),
                            resultSet.getString("category_name"),
                            resultSet.getString("project_name"),
                            resultSet.getString("full_name"),
                            resultSet.getDouble("amount"),
                            resultSet.getString("transaction_date"),
                            resultSet.getString("description"),
                            resultSet.getString("payment_method"),
                            resultSet.getString("reference_number")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list transactions", exception);
        }
        return transactions;
    }

    public List<FinanceTransaction> listTransactionsForAccount(int accountId) {
        List<FinanceTransaction> transactions = new ArrayList<>();
        String sql = """
                SELECT t.id, a.account_name, t.transaction_type, t.transaction_purpose, t.transaction_status,
                       c.category_name, p.project_name, pe.full_name, t.amount, t.transaction_date, t.description,
                       t.payment_method, t.reference_number
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                LEFT JOIN categories c ON c.id = t.category_id
                LEFT JOIN projects p ON p.id = t.project_id
                LEFT JOIN people pe ON pe.id = t.person_id
                WHERE t.account_id = ?
                ORDER BY t.transaction_date DESC, t.id DESC
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    transactions.add(new FinanceTransaction(
                            resultSet.getInt("id"),
                            resultSet.getString("account_name"),
                            resultSet.getString("transaction_type"),
                            resultSet.getString("transaction_purpose"),
                            resultSet.getString("transaction_status"),
                            resultSet.getString("category_name"),
                            resultSet.getString("project_name"),
                            resultSet.getString("full_name"),
                            resultSet.getDouble("amount"),
                            resultSet.getString("transaction_date"),
                            resultSet.getString("description"),
                            resultSet.getString("payment_method"),
                            resultSet.getString("reference_number")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list account transactions", exception);
        }
        return transactions;
    }

    public List<String> listTransactionDates() {
        List<String> dates = new ArrayList<>();
        String sql = """
                SELECT DISTINCT transaction_date
                FROM transactions
                WHERE transaction_date IS NOT NULL AND trim(transaction_date) <> ''
                ORDER BY transaction_date DESC
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                dates.add(resultSet.getString("transaction_date"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list transaction dates", exception);
        }
        return dates;
    }

    public List<String> listPaymentMethodSuggestions() {
        List<String> suggestions = new ArrayList<>(List.of("Cash", "Bank Transfer", "Mobile Money", "Cheque", "Card", "Other"));
        String sql = "SELECT DISTINCT payment_method FROM transactions WHERE payment_method IS NOT NULL AND trim(payment_method) <> '' ORDER BY payment_method";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String paymentMethod = resultSet.getString("payment_method");
                if (!suggestions.contains(paymentMethod)) {
                    suggestions.add(paymentMethod);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list payment method suggestions", exception);
        }
        return suggestions;
    }

    public DashboardStats getDashboardStats() {
        String month = YearMonth.now().toString();
        try (Connection connection = connect()) {
            return new DashboardStats(
                    queryDouble(connection, """
                            SELECT COALESCE(SUM(account_balance), 0)
                            FROM (
                                SELECT a.id,
                                       a.opening_balance + COALESCE(SUM(
                                           CASE
                                               WHEN t.transaction_type = 'INCOME' THEN t.amount
                                               WHEN t.transaction_type = 'EXPENSE' THEN -t.amount
                                               ELSE 0
                                           END
                                       ), 0) AS account_balance
                                FROM accounts a
                                LEFT JOIN transactions t ON t.account_id = a.id
                                    AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                                WHERE a.status = 'ACTIVE'
                                GROUP BY a.id
                            )
                            """),
                    queryMonthlyTotal(connection, month, "INCOME"),
                    queryMonthlyTotal(connection, month, "EXPENSE"),
                    queryInt(connection, "SELECT COUNT(*) FROM accounts WHERE status = 'ACTIVE'"),
                    queryInt(connection, "SELECT COUNT(*) FROM projects WHERE status = 'ACTIVE'"),
                    queryInt(connection, "SELECT COUNT(*) FROM goals WHERE status = 'ACTIVE'"),
                    queryDouble(connection, "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE transaction_purpose IN ('SUPPORT_GIVEN', 'MONEY_LENT')")
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load dashboard stats", exception);
        }
    }

    private double queryMonthlyTotal(Connection connection, String month, String type) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE transaction_type = ? AND substr(transaction_date, 1, 7) = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            statement.setString(2, month);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble(1) : 0;
            }
        }
    }

    private double queryDouble(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getDouble(1) : 0;
        }
    }

    private int queryInt(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    public List<ReportRow> categorySpendingReport() {
        return report("""
                SELECT COALESCE(c.category_name, 'Uncategorized') AS label, COALESCE(SUM(t.amount), 0) AS amount
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.transaction_type = 'EXPENSE'
                GROUP BY label
                ORDER BY amount DESC
                """);
    }

    public List<ReportRow> projectSpendingReport() {
        return report("""
                SELECT p.project_name AS label, COALESCE(SUM(t.amount), 0) AS amount
                FROM projects p
                LEFT JOIN transactions t ON t.project_id = p.id AND t.transaction_type = 'EXPENSE'
                GROUP BY p.id
                ORDER BY amount DESC
                """);
    }

    public List<ReportRow> accountBalanceReport() {
        return report("""
                SELECT a.account_name AS label,
                       a.opening_balance + COALESCE(SUM(
                           CASE
                               WHEN t.transaction_type = 'INCOME' THEN t.amount
                               WHEN t.transaction_type = 'EXPENSE' THEN -t.amount
                               ELSE 0
                           END
                       ), 0) AS amount
                FROM accounts a
                LEFT JOIN transactions t ON t.account_id = a.id
                    AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY a.id
                ORDER BY a.account_name
                """);
    }

    private List<ReportRow> report(String sql) {
        List<ReportRow> rows = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new ReportRow(resultSet.getString("label"), resultSet.getDouble("amount")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load report", exception);
        }
        return rows;
    }
}
