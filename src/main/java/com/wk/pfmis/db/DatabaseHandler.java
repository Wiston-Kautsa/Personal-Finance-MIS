package com.wk.pfmis.db;

import com.wk.pfmis.ai.AiCredentialStore;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.AiInteractionRecord;
import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.Budget;
import com.wk.pfmis.models.BudgetProgress;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.CurrencyRecord;
import com.wk.pfmis.models.DashboardStats;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.GoalStep;
import com.wk.pfmis.models.HouseholdMonthMember;
import com.wk.pfmis.models.PaymentMethodRecord;
import com.wk.pfmis.models.Person;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectActivity;
import com.wk.pfmis.models.ReportRow;
import com.wk.pfmis.models.SystemLogRecord;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DatabaseHandler {
    private static final String DB_FILE_NAME = "pfmis.db";
    private static final String LOCK_FILE_NAME = "pfmis.lock";
    private static final Path APPLICATION_DATA_DIRECTORY = resolveApplicationDataDirectory();
    private static final DatabaseHandler INSTANCE = new DatabaseHandler();
    private static final String DEFAULT_CURRENCY_CODE = "MWK";
    private static final String DEFAULT_CURRENCY_DISPLAY = "MWK - Malawian Kwacha";
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ENGLISH);
    private static final String LATEST_DAILY_BACKUP_FILE_NAME = "pfmis-latest-daily-backup.db";
    private static final String PURPOSE_MONEY_LENT = "MONEY_LENT";
    private static final String PURPOSE_MONEY_BORROWED = "MONEY_BORROWED";
    private static final String PURPOSE_LENT_REPAID = "LENT_REPAID";
    private static final String PURPOSE_BORROWED_REPAID = "BORROWED_REPAID";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_PARTIALLY_CLEARED = "PARTIALLY_CLEARED";
    private static final String STATUS_CLEARED = "CLEARED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final double LOAN_CLEARANCE_EPSILON = 0.005;

    private record LoanSide(Integer personId, String principalPurpose, String repaymentPurpose) {
    }

    private record LoanPrincipal(int id, double amount, String status) {
    }

    private DatabaseHandler() {
    }

    private static Path resolveApplicationDataDirectory() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        String userHome = System.getProperty("user.home", ".");
        if (osName.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "PFMIS").toAbsolutePath().normalize();
            }
            return Path.of(userHome, "AppData", "Local", "PFMIS").toAbsolutePath().normalize();
        }
        if (osName.contains("mac")) {
            return Path.of(userHome, "Library", "Application Support", "PFMIS").toAbsolutePath().normalize();
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            return Path.of(xdgDataHome, "PFMIS").toAbsolutePath().normalize();
        }
        return Path.of(userHome, ".local", "share", "PFMIS").toAbsolutePath().normalize();
    }

    private static void prepareDatabaseDirectory() {
        try {
            Files.createDirectories(APPLICATION_DATA_DIRECTORY);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create PFMIS data directory: " + APPLICATION_DATA_DIRECTORY, exception);
        }
    }

    private static Path workspaceDirectory(int userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("A valid workspace user is required.");
        }
        prepareDatabaseDirectory();
        Path directory = APPLICATION_DATA_DIRECTORY.resolve("users").resolve(String.valueOf(userId)).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            try {
                Files.setPosixFilePermissions(directory, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                ));
            } catch (UnsupportedOperationException ignored) {
                // Windows and some file systems do not expose POSIX permissions.
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create the user workspace directory: " + directory, exception);
        }
        return directory;
    }

    public static Path userDatabasePath(int userId) {
        return workspaceDirectory(userId).resolve(DB_FILE_NAME).toAbsolutePath().normalize();
    }

    public static Path authDatabasePath() {
        prepareDatabaseDirectory();
        return APPLICATION_DATA_DIRECTORY.resolve("pfmis-auth.db").toAbsolutePath().normalize();
    }

    private static void prepareDatabaseFile() {
        Path database = databasePath();
        try {
            Files.createDirectories(database.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare the PFMIS workspace: " + database.getParent(), exception);
        }
    }

    public static void migrateLegacyDatabaseToUser(int userId) {
        Path target = userDatabasePath(userId);
        if (Files.exists(target)) {
            return;
        }
        List<Path> candidates = List.of(
                APPLICATION_DATA_DIRECTORY.resolve(DB_FILE_NAME).toAbsolutePath().normalize(),
                Path.of(DB_FILE_NAME).toAbsolutePath().normalize()
        );
        for (Path legacy : candidates) {
            if (!Files.isRegularFile(legacy) || legacy.equals(target)) {
                continue;
            }
            try {
                Files.copy(legacy, target, StandardCopyOption.COPY_ATTRIBUTES);
                return;
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to move the existing PFMIS data into the first user's workspace.", exception);
            }
        }
    }

    public static DatabaseHandler getInstance() {
        return INSTANCE;
    }

    public static Path applicationDataDirectory() {
        prepareDatabaseDirectory();
        return APPLICATION_DATA_DIRECTORY;
    }

    public static Path databasePath() {
        return userDatabasePath(UserSession.getWorkspaceUserId());
    }

    public static Path lockFilePath() {
        prepareDatabaseDirectory();
        return APPLICATION_DATA_DIRECTORY.resolve(LOCK_FILE_NAME).toAbsolutePath().normalize();
    }

    public static Path defaultBackupDirectory() {
        Path directory = workspaceDirectory(UserSession.getWorkspaceUserId()).resolve("backups").toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create the workspace backup directory.", exception);
        }
        return directory;
    }

    public static Path latestDailyBackupFile() {
        return defaultBackupDirectory().resolve(LATEST_DAILY_BACKUP_FILE_NAME).toAbsolutePath().normalize();
    }

    private Connection connect() throws SQLException {
        prepareDatabaseFile();
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath());
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
                    CREATE TABLE IF NOT EXISTS goal_steps (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        goal_id INTEGER NOT NULL,
                        step_name TEXT NOT NULL,
                        description TEXT,
                        estimated_cost REAL DEFAULT 0,
                        amount_reached REAL DEFAULT 0,
                        target_date TEXT,
                        status TEXT DEFAULT 'NEEDED',
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT,
                        FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS budgets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        budget_name TEXT NOT NULL,
                        category_id INTEGER,
                        budget_month TEXT NOT NULL,
                        amount_limit REAL NOT NULL,
                        rollover INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        notes TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT,
                        FOREIGN KEY (category_id) REFERENCES categories(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS household_budget_members (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        budget_month TEXT NOT NULL,
                        person_name TEXT NOT NULL,
                        relationship TEXT,
                        presence_status TEXT NOT NULL DEFAULT 'IN_HOUSE',
                        joined_date TEXT,
                        left_date TEXT,
                        share_weight REAL NOT NULL DEFAULT 1,
                        member_type TEXT NOT NULL DEFAULT 'MEMBER',
                        duration_scope TEXT NOT NULL DEFAULT 'MONTH_ONLY',
                        notes TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        account_id INTEGER NOT NULL,
                        category_id INTEGER,
                        project_id INTEGER,
                        project_activity_id INTEGER,
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
                        FOREIGN KEY (project_activity_id) REFERENCES project_activities(id),
                        FOREIGN KEY (person_id) REFERENCES people(id),
                        FOREIGN KEY (related_transaction_id) REFERENCES transactions(id)
                    )
                    """);
            migrateTransactionsTable(connection);
            createValidTransactionsView(connection);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS app_settings (
                        setting_key TEXT PRIMARY KEY,
                        setting_value TEXT
                    )
                    """);
            initializeSchemaMetadata(connection);
            initializeCurrencies(connection);
            initializePaymentMethods(connection);
            initializeBackupHistory(connection);
            initializeSystemEventLog(connection);
            initializeAiInteractionLog(connection);
            migrateHouseholdBudgetMembersTable(connection);
            createIndexes(connection);
            initializeAiSettings(connection);
            seedCategories(connection);
            refreshAllLoanStatuses(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize PFMIS database", exception);
        }
    }


    private void initializeAiSettings(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ai_settings (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        provider_type TEXT NOT NULL DEFAULT 'LOCAL_LLAMA',
                        display_name TEXT NOT NULL DEFAULT 'PFMIS Local AI',
                        base_url TEXT NOT NULL DEFAULT 'http://127.0.0.1:8080',
                        model_name TEXT NOT NULL DEFAULT 'pfmis-model',
                        api_key TEXT,
                        auto_start_local INTEGER NOT NULL DEFAULT 1,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        agents TEXT NOT NULL DEFAULT 'PFMIS Copilot,Transaction Coach,Data Quality Guardian,Goal Coach,Budget Analyst,Project Spending Review,Loan Review,Backup Guardian',
                        extensions TEXT NOT NULL DEFAULT 'Bundled Local Runtime,Local Provider Connector,CSV Insight Pack,Backup Guide',
                        key_status TEXT NOT NULL DEFAULT 'ACTIVE',
                        updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
        addColumnIfMissing(connection, "ai_settings", "display_name", "TEXT NOT NULL DEFAULT 'PFMIS Local AI'");
        addColumnIfMissing(connection, "ai_settings", "agents", "TEXT NOT NULL DEFAULT 'PFMIS Copilot,Transaction Coach,Data Quality Guardian,Goal Coach,Budget Analyst,Project Spending Review,Loan Review,Backup Guardian'");
        addColumnIfMissing(connection, "ai_settings", "extensions", "TEXT NOT NULL DEFAULT 'Bundled Local Runtime,Local Provider Connector,CSV Insight Pack,Backup Guide'");
        addColumnIfMissing(connection, "ai_settings", "key_status", "TEXT NOT NULL DEFAULT 'ACTIVE'");
        addColumnIfMissing(connection, "ai_settings", "updated_at", "TEXT");
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ai_settings SET agents = ? WHERE id = 1 AND agents = 'Goal Coach,Budget Analyst,Loan Advisor'")) {
            statement.setString(1, AiSettings.DEFAULT_AGENTS);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ai_settings SET agents = ? WHERE id = 1 AND agents = 'PFMIS Copilot,Transaction Coach,Data Quality Guardian,Goal Coach,Budget Analyst,Project Spending Advisor,Loan Advisor,Backup Guardian'")) {
            statement.setString(1, AiSettings.DEFAULT_AGENTS);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ai_settings SET extensions = ? WHERE id = 1 AND extensions = 'Bundled Local AI Runtime,Local AI Connector,CSV Insight Pack,Backup Guide'")) {
            statement.setString(1, AiSettings.DEFAULT_EXTENSIONS);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ai_settings SET display_name = ? WHERE id = 1 AND display_name = 'PFMIS Local Provider'")) {
            statement.setString(1, AiSettings.DEFAULT_DISPLAY_NAME);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ai_settings SET display_name = ? WHERE id = 1 AND display_name = 'PFMIS Local Assistant'")) {
            statement.setString(1, AiSettings.DEFAULT_DISPLAY_NAME);
            statement.executeUpdate();
        }
        if (!hasAiSettingsRow(connection)) {
            saveAiSettings(connection, defaultLocalAiSettings());
        }
    }

    private void initializeCurrencies(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS currencies (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        currency_name TEXT NOT NULL,
                        currency_code TEXT NOT NULL COLLATE NOCASE UNIQUE,
                        symbol TEXT,
                        rate_to_base REAL NOT NULL DEFAULT 1,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        base_currency INTEGER NOT NULL DEFAULT 0,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CHECK (status IN ('ACTIVE','INACTIVE'))
                    )
                    """);
            statement.execute("""
                    INSERT OR IGNORE INTO schema_version (version, description)
                    VALUES (4, 'Workspace currency registry')
                    """);
        }
        migrateCurrenciesTable(connection);
        if (!currencyExists(connection, DEFAULT_CURRENCY_CODE)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO currencies (
                        currency_name, currency_code, symbol, rate_to_base, status, base_currency, updated_at
                    ) VALUES (?, ?, ?, 1, 'ACTIVE', 1, CURRENT_TIMESTAMP)
                    """)) {
                statement.setString(1, "Malawian Kwacha");
                statement.setString(2, DEFAULT_CURRENCY_CODE);
                statement.setString(3, DEFAULT_CURRENCY_CODE);
                statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE currencies
                SET base_currency = 1,
                    status = 'ACTIVE',
                    rate_to_base = 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE upper(currency_code) = ?
                  AND NOT EXISTS (SELECT 1 FROM currencies WHERE base_currency = 1)
                """)) {
            statement.setString(1, DEFAULT_CURRENCY_CODE);
            statement.executeUpdate();
        }
    }

    private void initializePaymentMethods(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS payment_methods (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        method_name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                        method_type TEXT NOT NULL DEFAULT 'Other',
                        provider TEXT,
                        default_account TEXT,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT,
                        CHECK (status IN ('ACTIVE','INACTIVE'))
                    )
                    """);
            statement.execute("""
                    INSERT OR IGNORE INTO schema_version (version, description)
                    VALUES (5, 'Workspace payment method registry')
                    """);
        }
        migratePaymentMethodsTable(connection);
        String[][] defaults = {
                {"Cash", "Cash"},
                {"Bank Transfer", "Bank"},
                {"Mobile Money", "Mobile Money"},
                {"Card", "Card"},
                {"Cheque", "Cheque"},
                {"Other", "Other"}
        };
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment_methods (
                    method_name, method_type, provider, default_account, status, updated_at
                ) VALUES (?, ?, '', '', 'ACTIVE', CURRENT_TIMESTAMP)
                """)) {
            for (String[] method : defaults) {
                if (paymentMethodExists(connection, method[0])) {
                    continue;
                }
                statement.setString(1, method[0]);
                statement.setString(2, method[1]);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void migrateCurrenciesTable(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "currencies", "currency_name", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "currencies", "currency_code", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "currencies", "symbol", "TEXT");
        addColumnIfMissing(connection, "currencies", "rate_to_base", "REAL NOT NULL DEFAULT 1");
        addColumnIfMissing(connection, "currencies", "status", "TEXT NOT NULL DEFAULT 'ACTIVE'");
        addColumnIfMissing(connection, "currencies", "base_currency", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "currencies", "updated_at", "TEXT");
    }

    private void migratePaymentMethodsTable(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "payment_methods", "method_name", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "payment_methods", "method_type", "TEXT NOT NULL DEFAULT 'Other'");
        addColumnIfMissing(connection, "payment_methods", "provider", "TEXT");
        addColumnIfMissing(connection, "payment_methods", "default_account", "TEXT");
        addColumnIfMissing(connection, "payment_methods", "status", "TEXT NOT NULL DEFAULT 'ACTIVE'");
        addColumnIfMissing(connection, "payment_methods", "updated_at", "TEXT");
    }

    private boolean hasAiSettingsRow(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM ai_settings WHERE id = 1");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        }
    }

    private boolean hasLegacyAiSettings(Connection connection) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM app_settings
                WHERE setting_key IN ('ai.enabled', 'ai.provider', 'ai.endpoint', 'ai.model', 'ai.api_key')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        }
    }

    private AiSettings legacyAiSettings(Connection connection) throws SQLException {
        String provider = getSetting(connection, "ai.provider", AiSettings.PROVIDER_OPENAI);
        String endpoint = getSetting(connection, "ai.endpoint", "https://api.openai.com/v1");
        String apiKey = getSetting(connection, "ai.api_key", "");
        if (!apiKey.isBlank()) {
            AiCredentialStore.saveApiKey(apiKey);
        }
        return new AiSettings(
                Boolean.parseBoolean(getSetting(connection, "ai.enabled", "false")),
                getSetting(connection, "ai.display_name", "Primary AI"),
                provider,
                endpoint,
                getSetting(connection, "ai.model", "gpt-5-mini"),
                apiKey,
                getSetting(connection, "ai.agents", AiSettings.DEFAULT_AGENTS),
                getSetting(connection, "ai.extensions", AiSettings.DEFAULT_EXTENSIONS),
                getSetting(connection, "ai.key_status", ""),
                AiSettings.PROVIDER_LOCAL_LLAMA.equals(provider) || AiSettings.PROVIDER_BUNDLED_LOCAL.equals(provider)
        );
    }

    private AiSettings defaultLocalAiSettings() {
        return new AiSettings(
                true,
                AiSettings.DEFAULT_DISPLAY_NAME,
                AiSettings.PROVIDER_LOCAL_LLAMA,
                AiSettings.DEFAULT_ENDPOINT,
                AiSettings.DEFAULT_MODEL,
                "",
                AiSettings.DEFAULT_AGENTS,
                AiSettings.DEFAULT_EXTENSIONS,
                AiSettings.KEY_STATUS_ACTIVE,
                true
        );
    }

    private boolean isBundledLocalProviderName(String provider) {
        return provider != null
                && (AiSettings.PROVIDER_LOCAL_LLAMA.equals(provider)
                || AiSettings.PROVIDER_BUNDLED_LOCAL.equals(provider)
                || "PFMIS Local AI".equalsIgnoreCase(provider)
                || "PFMIS Local Assistant".equalsIgnoreCase(provider)
                || "PFMIS Local Provider".equalsIgnoreCase(provider));
    }

    private void migrateAccountsTable(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "accounts", "currency", "TEXT NOT NULL DEFAULT 'MWK'");
        addColumnIfMissing(connection, "accounts", "bank_provider_name", "TEXT");
        addColumnIfMissing(connection, "accounts", "account_number", "TEXT");
        addColumnIfMissing(connection, "accounts", "notes", "TEXT");
        addColumnIfMissing(connection, "accounts", "updated_at", "TEXT");
    }

    private void migrateHouseholdBudgetMembersTable(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "household_budget_members", "member_type", "TEXT NOT NULL DEFAULT 'MEMBER'");
        addColumnIfMissing(connection, "household_budget_members", "duration_scope", "TEXT NOT NULL DEFAULT 'MONTH_ONLY'");
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
        addColumnIfMissing(connection, "transactions", "related_transaction_id", "INTEGER");
        addColumnIfMissing(connection, "transactions", "project_activity_id", "INTEGER");
        addColumnIfMissing(connection, "transactions", "transaction_purpose", "TEXT DEFAULT 'NORMAL'");
        addColumnIfMissing(connection, "transactions", "transaction_status", "TEXT DEFAULT 'COMPLETED'");
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

    private void createValidTransactionsView(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP VIEW IF EXISTS valid_transactions");
            statement.execute("""
                    CREATE VIEW valid_transactions AS
                    SELECT *
                    FROM transactions
                    WHERE COALESCE(transaction_status, 'COMPLETED') <> 'CANCELLED'
                    """);
        }
    }

    private void initializeSchemaMetadata(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER PRIMARY KEY,
                        description TEXT NOT NULL,
                        applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    INSERT OR IGNORE INTO schema_version (version, description)
                    VALUES (1, 'Initial additive PFMIS schema management')
                    """);
        }
    }

    private void initializeBackupHistory(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS backup_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        backup_file TEXT NOT NULL,
                        checksum TEXT NOT NULL,
                        file_size INTEGER NOT NULL,
                        contents TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private void initializeSystemEventLog(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS system_event_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        module_name TEXT NOT NULL,
                        action_name TEXT NOT NULL,
                        severity TEXT NOT NULL,
                        details TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    INSERT OR IGNORE INTO schema_version (version, description)
                    VALUES (3, 'System event log')
                    """);
        }
    }

    private void initializeAiInteractionLog(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ai_interaction_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        module_name TEXT NOT NULL,
                        action_name TEXT NOT NULL,
                        provider_name TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    INSERT OR IGNORE INTO schema_version (version, description)
                VALUES (2, 'Smart Assist provider and interaction audit')
                    """);
        }
    }

    private void createIndexes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transaction_date)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_account ON transactions(account_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_project ON transactions(project_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_person ON transactions(person_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_type_status ON transactions(transaction_type, transaction_status)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_category_month ON transactions(category_id, transaction_date)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_project_activity ON transactions(project_activity_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_project_activities_project ON project_activities(project_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_budgets_month ON budgets(budget_month)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_budgets_category_month ON budgets(category_id, budget_month)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_household_budget_members_month ON household_budget_members(budget_month)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_household_budget_members_month_name ON household_budget_members(budget_month, person_name)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_household_budget_members_duration ON household_budget_members(duration_scope, budget_month)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_ai_interaction_log_created ON ai_interaction_log(created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_system_event_log_created ON system_event_log(created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_currencies_status ON currencies(status)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_payment_methods_status ON payment_methods(status)");
        }
    }

    private void seedCategories(Connection connection) throws SQLException {
        String[][] categories = {
                {"Salary", "INCOME"},
                {"Business", "BOTH"},
                {"Groceries", "EXPENSE"},
                {"Transport", "EXPENSE"},
                {"Public Transport", "EXPENSE"},
                {"Food", "EXPENSE"},
                {"Fuel", "EXPENSE"},
                {"School Fees", "EXPENSE"},
                {"Education", "EXPENSE"},
                {"Rent", "EXPENSE"},
                {"Electricity", "EXPENSE"},
                {"Water", "EXPENSE"},
                {"Utilities", "EXPENSE"},
                {"Medical", "EXPENSE"},
                {"Family Support", "EXPENSE"},
                {"Airtime/Data", "EXPENSE"},
                {"Internet", "EXPENSE"},
                {"Household Supplies", "EXPENSE"},
                {"Clothing", "EXPENSE"},
                {"Personal Care", "EXPENSE"},
                {"Entertainment", "EXPENSE"},
                {"Subscriptions", "EXPENSE"},
                {"Insurance", "EXPENSE"},
                {"Maintenance", "EXPENSE"},
                {"Repairs", "EXPENSE"},
                {"Bank Charges", "EXPENSE"},
                {"Donations", "EXPENSE"},
                {"Travel", "EXPENSE"},
                {"Office Supplies", "EXPENSE"},
                {"Taxes", "EXPENSE"},
                {"Project Expense", "EXPENSE"},
                {"Savings", "BOTH"},
                {"Loan Repayment", "BOTH"},
                {"Emergency", "EXPENSE"},
                {"Other", "BOTH"}
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
                              WHEN t.transaction_type = 'TRANSFER' AND t.transaction_purpose = 'TRANSFER_IN' THEN t.amount
                              WHEN t.transaction_type = 'TRANSFER' AND t.transaction_purpose = 'TRANSFER_OUT' THEN -t.amount
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
        List<String> suggestions = new ArrayList<>();
        for (CurrencyRecord currency : listCurrencies()) {
            if (!"INACTIVE".equals(currency.getStatus())) {
                addSuggestion(suggestions, currencyDisplayName(currency.getCurrencyCode(), currency.getCurrencyName()));
            }
        }
        addSuggestion(suggestions, DEFAULT_CURRENCY_DISPLAY);
        Currency.getAvailableCurrencies().stream()
                .sorted(Comparator.comparing(Currency::getCurrencyCode))
                .map(currency -> currencyDisplayName(currency.getCurrencyCode()))
                .forEach(currency -> addSuggestion(suggestions, currency));

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

    public List<CurrencyRecord> listCurrencies() {
        List<CurrencyRecord> currencies = new ArrayList<>();
        String sql = """
                SELECT id, currency_name, currency_code, symbol, rate_to_base, base_currency, status, updated_at
                FROM currencies
                ORDER BY base_currency DESC, currency_code COLLATE NOCASE
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                currencies.add(new CurrencyRecord(
                        resultSet.getInt("id"),
                        resultSet.getString("currency_name"),
                        resultSet.getString("currency_code"),
                        resultSet.getString("symbol"),
                        resultSet.getDouble("rate_to_base"),
                        resultSet.getInt("base_currency") == 1,
                        resultSet.getString("status"),
                        resultSet.getString("updated_at")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list currencies", exception);
        }
        return currencies;
    }

    public void saveCurrency(String currencyName, String currencyCode, String symbol, double rateToBase, String status) {
        String code = normalizedCurrencyCode(currencyCode);
        String cleanName = currencyName == null || currencyName.isBlank()
                ? currencyDisplayName(code)
                : currencyName.trim();
        String normalizedStatus = normalizedCurrencyStatus(status);
        boolean baseCurrency = "BASE".equals(normalizedStatus);
        double storedRate = baseCurrency ? 1 : rateToBase;
        if (storedRate <= 0) {
            throw new IllegalArgumentException("Rate to base must be greater than zero.");
        }
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                if (!baseCurrency && isBaseCurrency(connection, code)) {
                    throw new IllegalArgumentException("Set another base currency before marking this base currency inactive.");
                }
                if (baseCurrency) {
                    try (PreparedStatement clearBase = connection.prepareStatement("UPDATE currencies SET base_currency = 0")) {
                        clearBase.executeUpdate();
                    }
                }
                if (currencyExists(connection, code)) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE currencies
                            SET currency_name = ?,
                                symbol = ?,
                                rate_to_base = ?,
                                status = ?,
                                base_currency = ?,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE upper(currency_code) = ?
                            """)) {
                        statement.setString(1, cleanName);
                        statement.setString(2, cleanNullable(symbol));
                        statement.setDouble(3, storedRate);
                        statement.setString(4, baseCurrency ? "ACTIVE" : normalizedStatus);
                        statement.setInt(5, baseCurrency ? 1 : 0);
                        statement.setString(6, code);
                        statement.executeUpdate();
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO currencies (
                                currency_name, currency_code, symbol, rate_to_base, status, base_currency, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                            """)) {
                        statement.setString(1, cleanName);
                        statement.setString(2, code);
                        statement.setString(3, cleanNullable(symbol));
                        statement.setDouble(4, storedRate);
                        statement.setString(5, baseCurrency ? "ACTIVE" : normalizedStatus);
                        statement.setInt(6, baseCurrency ? 1 : 0);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save currency", exception);
        }
    }

    public String getDefaultCurrency() {
        String sql = """
                SELECT currency_code, currency_name
                FROM currencies
                WHERE base_currency = 1
                ORDER BY id
                LIMIT 1
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return currencyDisplayName(resultSet.getString("currency_code"), resultSet.getString("currency_name"));
            }
        } catch (SQLException exception) {
            return DEFAULT_CURRENCY_DISPLAY;
        }
        return DEFAULT_CURRENCY_DISPLAY;
    }

    private boolean currencyExists(Connection connection, String currencyCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM currencies
                WHERE upper(currency_code) = ?
                LIMIT 1
                """)) {
            statement.setString(1, currencyCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private String currencyDisplayName(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY_DISPLAY;
        }

        String value = currency.trim();
        int separator = value.indexOf(" - ");
        String code = (separator > 0 ? value.substring(0, separator) : value)
                .trim()
                .toUpperCase(Locale.ENGLISH);
        if (code.isEmpty()) {
            return DEFAULT_CURRENCY_DISPLAY;
        }

        if (DEFAULT_CURRENCY_CODE.equals(code)) {
            return DEFAULT_CURRENCY_DISPLAY;
        }

        try {
            Currency isoCurrency = Currency.getInstance(code);
            return isoCurrency.getCurrencyCode() + " - " + isoCurrency.getDisplayName(Locale.ENGLISH);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private String currencyDisplayName(String currencyCode, String currencyName) {
        String code = normalizedCurrencyCodeOrDefault(currencyCode);
        String name = currencyName == null ? "" : currencyName.trim();
        return name.isBlank() ? currencyDisplayName(code) : code + " - " + name;
    }

    private boolean isBaseCurrency(Connection connection, String currencyCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT base_currency
                FROM currencies
                WHERE upper(currency_code) = ?
                LIMIT 1
                """)) {
            statement.setString(1, currencyCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt("base_currency") == 1;
            }
        }
    }

    private String normalizedCurrencyCode(String currencyCode) {
        String code = normalizedCurrencyCodeOrDefault(currencyCode);
        if (code.isBlank()) {
            throw new IllegalArgumentException("Currency code is required.");
        }
        if (!code.matches("[A-Z0-9]{2,10}")) {
            throw new IllegalArgumentException("Currency code must use 2-10 letters or numbers.");
        }
        return code;
    }

    private String normalizedCurrencyCodeOrDefault(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return "";
        }
        String value = currencyCode.trim();
        int separator = value.indexOf(" - ");
        return (separator > 0 ? value.substring(0, separator) : value)
                .trim()
                .toUpperCase(Locale.ENGLISH);
    }

    private String normalizedCurrencyStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalized = status.trim().toUpperCase(Locale.ENGLISH).replace(' ', '_');
        if ("BASE_CURRENCY".equals(normalized)) {
            return "BASE";
        }
        if ("BASE".equals(normalized) || "INACTIVE".equals(normalized)) {
            return normalized;
        }
        return "ACTIVE";
    }

    private void addSuggestion(List<String> suggestions, String value) {
        if (value != null && !value.isBlank() && !suggestions.contains(value)) {
            suggestions.add(value);
        }
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

    public void updateCategory(int categoryId, String name, String type) {
        String categoryName = name == null ? "" : name.trim();
        if (categoryName.isEmpty()) {
            throw new IllegalArgumentException("Enter a category name.");
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE categories
                     SET category_name = ?,
                         category_type = ?
                     WHERE id = ?
                     """)) {
            statement.setString(1, categoryName);
            statement.setString(2, normalizedCategoryType(type));
            statement.setInt(3, categoryId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("Select a valid category to edit.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update category", exception);
        }
    }

    public Category findOrCreateCategory(String name, String type) {
        String categoryName = name == null ? "" : name.trim();
        if (categoryName.isEmpty()) {
            throw new IllegalArgumentException("Enter a category name.");
        }
        String requestedType = normalizedCategoryType(type);
        try (Connection connection = connect()) {
            try (PreparedStatement find = connection.prepareStatement(
                    "SELECT id, category_name, category_type FROM categories WHERE lower(category_name) = lower(?)")) {
                find.setString(1, categoryName);
                try (ResultSet resultSet = find.executeQuery()) {
                    if (resultSet.next()) {
                        int id = resultSet.getInt("id");
                        String existingName = resultSet.getString("category_name");
                        String mergedType = mergedCategoryType(resultSet.getString("category_type"), requestedType);
                        if (!mergedType.equals(resultSet.getString("category_type"))) {
                            try (PreparedStatement update = connection.prepareStatement(
                                    "UPDATE categories SET category_type = ? WHERE id = ?")) {
                                update.setString(1, mergedType);
                                update.setInt(2, id);
                                update.executeUpdate();
                            }
                        }
                        return new Category(id, existingName, mergedType);
                    }
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO categories (category_name, category_type) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, categoryName);
                insert.setString(2, requestedType);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new Category(keys.getInt(1), categoryName, requestedType);
                    }
                }
            }
            throw new IllegalStateException("Failed to create category.");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save category", exception);
        }
    }

    private String normalizedCategoryType(String type) {
        if ("INCOME".equals(type) || "EXPENSE".equals(type)) {
            return type;
        }
        return "BOTH";
    }

    private String mergedCategoryType(String existingType, String requestedType) {
        String existing = normalizedCategoryType(existingType);
        String requested = normalizedCategoryType(requestedType);
        if (existing.equals(requested)) {
            return existing;
        }
        return "BOTH";
    }

    public List<Budget> listBudgets() {
        List<Budget> budgets = new ArrayList<>();
        String sql = """
                SELECT b.id, b.budget_name, b.category_id, c.category_name, b.budget_month,
                       b.amount_limit, b.rollover, b.status, b.notes
                FROM budgets b
                LEFT JOIN categories c ON c.id = b.category_id
                ORDER BY b.budget_month DESC, b.budget_name
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                budgets.add(budgetFromResult(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list budgets", exception);
        }
        return budgets;
    }

    public List<BudgetProgress> listBudgetProgress(String month) {
        List<BudgetProgress> budgets = new ArrayList<>();
        String sql = """
                SELECT b.id, b.budget_name, b.category_id, c.category_name, b.budget_month,
                       b.amount_limit, b.rollover, b.status, b.notes,
                       COALESCE(SUM(t.amount), 0) AS spent
                FROM budgets b
                LEFT JOIN categories c ON c.id = b.category_id
                LEFT JOIN valid_transactions t ON t.transaction_type = 'EXPENSE'
                    AND substr(t.transaction_date, 1, 7) = b.budget_month
                    AND (b.category_id IS NULL OR t.category_id = b.category_id)
                WHERE (? IS NULL OR b.budget_month = ?)
                GROUP BY b.id
                ORDER BY b.budget_month DESC, b.budget_name
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String normalizedMonth = month == null || month.isBlank() ? null : month.trim();
            if (normalizedMonth != null) {
                ensureBudgetOwnerForMonth(connection, normalizedMonth);
            }
            statement.setString(1, normalizedMonth);
            statement.setString(2, normalizedMonth);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    double householdUnits = householdUnitsForMonth(connection, resultSet.getString("budget_month"));
                    budgets.add(new BudgetProgress(budgetFromResult(resultSet), resultSet.getDouble("spent"), householdUnits));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list budget progress", exception);
        }
        return budgets;
    }

    public void addBudget(
            String budgetName,
            Integer categoryId,
            String budgetMonth,
            double amountLimit,
            boolean rollover,
            String status,
            String notes
    ) {
        validateBudget(budgetName, budgetMonth, amountLimit);
        String sql = """
                INSERT INTO budgets (budget_name, category_id, budget_month, amount_limit, rollover, status, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, budgetName.trim());
            setNullableInt(statement, 2, categoryId);
            statement.setString(3, budgetMonth.trim());
            statement.setDouble(4, amountLimit);
            statement.setInt(5, rollover ? 1 : 0);
            statement.setString(6, normalizedBudgetStatus(status));
            statement.setString(7, notes);
            statement.executeUpdate();
            ensureBudgetOwnerForMonth(connection, budgetMonth);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add budget", exception);
        }
    }

    public void updateBudget(
            int budgetId,
            String budgetName,
            Integer categoryId,
            String budgetMonth,
            double amountLimit,
            boolean rollover,
            String status,
            String notes
    ) {
        validateBudget(budgetName, budgetMonth, amountLimit);
        String sql = """
                UPDATE budgets
                SET budget_name = ?,
                    category_id = ?,
                    budget_month = ?,
                    amount_limit = ?,
                    rollover = ?,
                    status = ?,
                    notes = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, budgetName.trim());
            setNullableInt(statement, 2, categoryId);
            statement.setString(3, budgetMonth.trim());
            statement.setDouble(4, amountLimit);
            statement.setInt(5, rollover ? 1 : 0);
            statement.setString(6, normalizedBudgetStatus(status));
            statement.setString(7, notes);
            statement.setInt(8, budgetId);
            statement.executeUpdate();
            ensureBudgetOwnerForMonth(connection, budgetMonth);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update budget", exception);
        }
    }

    public void deleteBudget(int budgetId) {
        String sql = "DELETE FROM budgets WHERE id = ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, budgetId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete budget", exception);
        }
    }

    public List<HouseholdMonthMember> listHouseholdMonthMembers(String budgetMonth) {
        List<HouseholdMonthMember> members = new ArrayList<>();
        String sql = """
                SELECT id, budget_month, person_name, relationship, presence_status,
                       joined_date, left_date, share_weight, member_type, duration_scope, notes
                FROM household_budget_members
                WHERE budget_month = ?
                   OR (
                        duration_scope = 'FOREVER'
                        AND budget_month <= ?
                        AND (COALESCE(left_date, '') = '' OR substr(left_date, 1, 7) >= ?)
                   )
                ORDER BY CASE COALESCE(member_type, 'MEMBER') WHEN 'OWNER' THEN 0 ELSE 1 END,
                         CASE COALESCE(duration_scope, 'MONTH_ONLY') WHEN 'FOREVER' THEN 0 ELSE 1 END,
                         person_name COLLATE NOCASE
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String normalizedMonth = normalizeBudgetMonth(budgetMonth);
            ensureBudgetOwnerForMonth(connection, normalizedMonth);
            statement.setString(1, normalizedMonth);
            statement.setString(2, normalizedMonth);
            statement.setString(3, normalizedMonth);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(householdMonthMemberFromResult(resultSet));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list household budget members", exception);
        }
        return members;
    }

    public void addHouseholdMonthMember(
            String budgetMonth,
            String personName,
            String relationship,
            String presenceStatus,
            LocalDate joinedDate,
            LocalDate leftDate,
            double shareWeight,
            String durationScope,
            String notes
    ) {
        validateHouseholdMonthMember(budgetMonth, personName, shareWeight);
        String sql = """
                INSERT INTO household_budget_members
                    (budget_month, person_name, relationship, presence_status, joined_date, left_date,
                     share_weight, notes, member_type, duration_scope)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'MEMBER', ?)
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindHouseholdMonthMember(
                    statement,
                    budgetMonth,
                    personName,
                    relationship,
                    presenceStatus,
                    joinedDate,
                    leftDate,
                    shareWeight,
                    durationScope,
                    notes
            );
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add household budget member", exception);
        }
    }

    public void updateHouseholdMonthMember(
            int memberId,
            String budgetMonth,
            String personName,
            String relationship,
            String presenceStatus,
            LocalDate joinedDate,
            LocalDate leftDate,
            double shareWeight,
            String durationScope,
            String notes
    ) {
        validateHouseholdMonthMember(budgetMonth, personName, shareWeight);
        String sql = """
                UPDATE household_budget_members
                SET budget_month = ?,
                    person_name = ?,
                    relationship = ?,
                    presence_status = ?,
                    joined_date = ?,
                    left_date = ?,
                    share_weight = ?,
                    notes = ?,
                    duration_scope = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND COALESCE(member_type, 'MEMBER') <> 'OWNER'
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindHouseholdMonthMember(
                    statement,
                    budgetMonth,
                    personName,
                    relationship,
                    presenceStatus,
                    joinedDate,
                    leftDate,
                    shareWeight,
                    durationScope,
                    notes
            );
            statement.setInt(10, memberId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("The budget owner is included automatically and cannot be edited here.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update household budget member", exception);
        }
    }

    public void deleteHouseholdMonthMember(int memberId) {
        String sql = """
                DELETE FROM household_budget_members
                WHERE id = ?
                  AND COALESCE(member_type, 'MEMBER') <> 'OWNER'
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, memberId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("The budget owner is included automatically and cannot be removed.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to remove household budget member", exception);
        }
    }

    public double householdUnitsForMonth(String budgetMonth) {
        try (Connection connection = connect()) {
            String normalizedMonth = normalizeBudgetMonth(budgetMonth);
            ensureBudgetOwnerForMonth(connection, normalizedMonth);
            return householdUnitsForMonth(connection, normalizedMonth);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to calculate household units", exception);
        }
    }

    private double householdUnitsForMonth(Connection connection, String budgetMonth) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(
                    CASE
                        WHEN presence_status = 'AWAY' THEN 0
                        ELSE share_weight
                    END
                ), 0)
                FROM household_budget_members
                WHERE budget_month = ?
                   OR (
                        duration_scope = 'FOREVER'
                        AND budget_month <= ?
                        AND (COALESCE(left_date, '') = '' OR substr(left_date, 1, 7) >= ?)
                   )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String normalizedMonth = normalizeBudgetMonth(budgetMonth);
            statement.setString(1, normalizedMonth);
            statement.setString(2, normalizedMonth);
            statement.setString(3, normalizedMonth);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Math.max(0, resultSet.getDouble(1)) : 0;
            }
        }
    }

    private void ensureBudgetOwnerForMonth(Connection connection, String budgetMonth) throws SQLException {
        if (!UserSession.isAuthenticated()) {
            return;
        }
        String normalizedMonth = normalizeBudgetMonth(budgetMonth);
        if (!normalizedMonth.matches("\\d{4}-\\d{2}")) {
            return;
        }
        SystemUser workspaceOwner = UserSession.getWorkspaceUser();
        String ownerName = workspaceOwner.getDisplayName();
        String ownerStartDate = monthStart(normalizedMonth).toString();
        Integer ownerId = null;
        String existingMonth = null;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, budget_month
                FROM household_budget_members
                WHERE COALESCE(member_type, 'MEMBER') = 'OWNER'
                ORDER BY budget_month
                LIMIT 1
                """);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                ownerId = resultSet.getInt("id");
                existingMonth = resultSet.getString("budget_month");
            }
        }
        if (ownerId == null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO household_budget_members (
                        budget_month, person_name, relationship, presence_status, joined_date,
                        left_date, share_weight, notes, member_type, duration_scope
                    ) VALUES (?, ?, 'Budget owner', 'IN_HOUSE', ?, '', 1, ?, 'OWNER', 'FOREVER')
                    """)) {
                statement.setString(1, normalizedMonth);
                statement.setString(2, ownerName);
                statement.setString(3, ownerStartDate);
                statement.setString(4, "Automatically included owner of this workspace.");
                statement.executeUpdate();
            }
            return;
        }

        String effectiveStartMonth = existingMonth == null || existingMonth.isBlank() || existingMonth.compareTo(normalizedMonth) > 0
                ? normalizedMonth
                : existingMonth;
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE household_budget_members
                SET budget_month = ?,
                    person_name = ?,
                    relationship = 'Budget owner',
                    presence_status = 'IN_HOUSE',
                    joined_date = ?,
                    left_date = '',
                    share_weight = 1,
                    member_type = 'OWNER',
                    duration_scope = 'FOREVER',
                    notes = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)) {
            statement.setString(1, effectiveStartMonth);
            statement.setString(2, ownerName);
            statement.setString(3, monthStart(effectiveStartMonth).toString());
            statement.setString(4, "Automatically included owner of this workspace.");
            statement.setInt(5, ownerId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM household_budget_members
                WHERE COALESCE(member_type, 'MEMBER') = 'OWNER'
                  AND id <> ?
                """)) {
            statement.setInt(1, ownerId);
            statement.executeUpdate();
        }
    }

    private void bindHouseholdMonthMember(
            PreparedStatement statement,
            String budgetMonth,
            String personName,
            String relationship,
            String presenceStatus,
            LocalDate joinedDate,
            LocalDate leftDate,
            double shareWeight,
            String durationScope,
            String notes
    ) throws SQLException {
        statement.setString(1, normalizeBudgetMonth(budgetMonth));
        statement.setString(2, personName.trim());
        statement.setString(3, relationship == null ? "" : relationship.trim());
        statement.setString(4, normalizedHouseholdStatus(presenceStatus));
        statement.setString(5, joinedDate == null ? "" : joinedDate.toString());
        statement.setString(6, leftDate == null ? "" : leftDate.toString());
        statement.setDouble(7, Math.max(0, shareWeight));
        statement.setString(8, notes == null ? "" : notes.trim());
        statement.setString(9, normalizedDurationScope(durationScope));
    }

    private HouseholdMonthMember householdMonthMemberFromResult(ResultSet resultSet) throws SQLException {
        return new HouseholdMonthMember(
                resultSet.getInt("id"),
                resultSet.getString("budget_month"),
                resultSet.getString("person_name"),
                resultSet.getString("relationship"),
                resultSet.getString("presence_status"),
                resultSet.getString("joined_date"),
                resultSet.getString("left_date"),
                resultSet.getDouble("share_weight"),
                resultSet.getString("member_type"),
                resultSet.getString("duration_scope"),
                resultSet.getString("notes")
        );
    }

    private void validateHouseholdMonthMember(String budgetMonth, String personName, double shareWeight) {
        String normalizedMonth = normalizeBudgetMonth(budgetMonth);
        if (!normalizedMonth.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Household month must use YYYY-MM format");
        }
        if (personName == null || personName.isBlank()) {
            throw new IllegalArgumentException("Person name is required");
        }
        if (shareWeight < 0) {
            throw new IllegalArgumentException("Share weight cannot be negative");
        }
    }

    private String normalizeBudgetMonth(String budgetMonth) {
        return budgetMonth == null ? YearMonth.now().toString() : budgetMonth.trim();
    }

    private LocalDate monthStart(String budgetMonth) {
        try {
            return YearMonth.parse(normalizeBudgetMonth(budgetMonth)).atDay(1);
        } catch (DateTimeParseException exception) {
            return LocalDate.now().withDayOfMonth(1);
        }
    }

    private String normalizedHouseholdStatus(String status) {
        if (status == null || status.isBlank()) {
            return "IN_HOUSE";
        }
        String normalized = status.trim().toUpperCase(Locale.ENGLISH).replace(' ', '_');
        if (List.of("IN_HOUSE", "JOINED", "LEFT", "AWAY").contains(normalized)) {
            return normalized;
        }
        return "IN_HOUSE";
    }

    private String normalizedDurationScope(String durationScope) {
        if (durationScope == null || durationScope.isBlank()) {
            return "MONTH_ONLY";
        }
        String normalized = durationScope.trim().toUpperCase(Locale.ENGLISH).replace(' ', '_');
        if (List.of("FOREVER", "ONGOING", "ALWAYS", "PER_FOREVER").contains(normalized)) {
            return "FOREVER";
        }
        return "MONTH_ONLY";
    }

    private Budget budgetFromResult(ResultSet resultSet) throws SQLException {
        return new Budget(
                resultSet.getInt("id"),
                resultSet.getString("budget_name"),
                nullableInt(resultSet, "category_id"),
                resultSet.getString("category_name"),
                resultSet.getString("budget_month"),
                resultSet.getDouble("amount_limit"),
                resultSet.getInt("rollover") == 1,
                resultSet.getString("status"),
                resultSet.getString("notes")
        );
    }

    private void validateBudget(String budgetName, String budgetMonth, double amountLimit) {
        if (budgetName == null || budgetName.isBlank()) {
            throw new IllegalArgumentException("Budget name is required");
        }
        if (budgetMonth == null || !budgetMonth.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Budget month must use YYYY-MM format");
        }
        if (amountLimit <= 0) {
            throw new IllegalArgumentException("Budget limit must be greater than zero");
        }
    }

    private String normalizedBudgetStatus(String status) {
        if (status == null || status.isBlank()) {
            return "PLANNED";
        }
        String normalized = status.trim().toUpperCase(Locale.ENGLISH).replace(' ', '_');
        if ("ACTIVE".equals(normalized)) {
            return "ON_BUDGET";
        }
        if (List.of("PLANNED", "ON_BUDGET", "FULFILLED", "NOT_MET", "PAUSED", "CLOSED").contains(normalized)) {
            return normalized;
        }
        return "PLANNED";
    }

    public List<Project> listProjects() {
        List<Project> projects = new ArrayList<>();
        String sql = """
                SELECT p.id, p.project_name, p.description, p.planned_budget, p.start_date, p.end_date, p.status,
                       COALESCE(SUM(CASE WHEN t.transaction_type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS amount_spent
                FROM projects p
                LEFT JOIN valid_transactions t ON t.project_id = p.id
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

    public boolean projectExistsByName(String projectName) {
        String sql = "SELECT 1 FROM projects WHERE lower(trim(project_name)) = lower(trim(?)) LIMIT 1";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectName == null ? "" : projectName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to check project name", exception);
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
            String activityName,
            String description,
            LocalDate activityDate,
            String reason,
            String status
    ) {
        if (activityName == null || activityName.isBlank()) {
            throw new IllegalArgumentException("Activity name is required");
        }
        String sql = """
                INSERT INTO project_activities (
                    project_id, activity_name, activity_date, description, reason, status
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, projectId);
            statement.setString(2, activityName.trim());
            statement.setString(3, (activityDate == null ? LocalDate.now() : activityDate).toString());
            statement.setString(4, description);
            statement.setString(5, reason);
            statement.setString(6, status == null || status.isBlank() ? "Pending" : status);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add project activity", exception);
        }
    }

    public List<ProjectActivity> listProjectActivities() {
        List<ProjectActivity> activities = new ArrayList<>();
        String sql = """
                SELECT pa.id, pa.project_id, p.project_name, pa.activity_name, pa.activity_date, pa.description,
                       CASE
                           WHEN COALESCE(SUM(CASE WHEN t.transaction_type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) > 0
                               THEN COALESCE(SUM(CASE WHEN t.transaction_type = 'EXPENSE' THEN t.amount ELSE 0 END), 0)
                           ELSE COALESCE(pa.amount_used, 0)
                       END AS amount_used,
                       c.category_name, a.account_name, pa.payment_method, pa.reason,
                       pa.start_date, pa.end_date, pa.status
                FROM project_activities pa
                JOIN projects p ON p.id = pa.project_id
                LEFT JOIN categories c ON c.id = pa.category_id
                LEFT JOIN accounts a ON a.id = pa.account_id
                LEFT JOIN valid_transactions t ON t.project_activity_id = pa.id
                GROUP BY pa.id
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

    public int addGoal(String name, double targetAmount, double currentAmount, double monthlyContribution, String targetDate) {
        return addGoal(name, targetAmount, currentAmount, monthlyContribution, targetDate, "ACTIVE");
    }

    public int addGoal(String name, double targetAmount, double currentAmount, double monthlyContribution, String targetDate, String status) {
        String sql = "INSERT INTO goals (goal_name, target_amount, current_amount, monthly_contribution, target_date, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setDouble(2, targetAmount);
            statement.setDouble(3, currentAmount);
            statement.setDouble(4, monthlyContribution);
            statement.setString(5, targetDate);
            statement.setString(6, status == null || status.isBlank() ? "ACTIVE" : status);
            statement.executeUpdate();
            return generatedId(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add goal", exception);
        }
    }

    public List<GoalStep> listGoalSteps(int goalId) {
        List<GoalStep> steps = new ArrayList<>();
        String sql = """
                SELECT gs.id, gs.goal_id, g.goal_name, gs.step_name, gs.description,
                       gs.estimated_cost, gs.amount_reached, gs.target_date, gs.status
                FROM goal_steps gs
                JOIN goals g ON g.id = gs.goal_id
                WHERE gs.goal_id = ?
                ORDER BY gs.id
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, goalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    steps.add(new GoalStep(
                            resultSet.getInt("id"),
                            resultSet.getInt("goal_id"),
                            resultSet.getString("goal_name"),
                            resultSet.getString("step_name"),
                            resultSet.getString("description"),
                            resultSet.getDouble("estimated_cost"),
                            resultSet.getDouble("amount_reached"),
                            resultSet.getString("target_date"),
                            resultSet.getString("status")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list goal steps", exception);
        }
        return steps;
    }

    public int addGoalStep(
            int goalId,
            String stepName,
            String description,
            double estimatedCost,
            double amountReached,
            String targetDate,
            String status
    ) {
        String sql = """
                INSERT INTO goal_steps (
                    goal_id, step_name, description, estimated_cost, amount_reached, target_date, status, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, goalId);
            statement.setString(2, stepName);
            statement.setString(3, description);
            statement.setDouble(4, estimatedCost);
            statement.setDouble(5, amountReached);
            statement.setString(6, targetDate);
            statement.setString(7, status == null || status.isBlank() ? "NEEDED" : status);
            statement.executeUpdate();
            return generatedId(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add goal step", exception);
        }
    }

    public void updateGoalStep(
            int stepId,
            String stepName,
            String description,
            double estimatedCost,
            double amountReached,
            String targetDate,
            String status
    ) {
        String sql = """
                UPDATE goal_steps
                SET step_name = ?,
                    description = ?,
                    estimated_cost = ?,
                    amount_reached = ?,
                    target_date = ?,
                    status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, stepName);
            statement.setString(2, description);
            statement.setDouble(3, estimatedCost);
            statement.setDouble(4, amountReached);
            statement.setString(5, targetDate);
            statement.setString(6, status == null || status.isBlank() ? "NEEDED" : status);
            statement.setInt(7, stepId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("Select a valid goal step to update.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update goal step", exception);
        }
    }

    public void updateGoal(
            int goalId,
            String name,
            double targetAmount,
            double currentAmount,
            double monthlyContribution,
            String targetDate,
            String status
    ) {
        String sql = """
                UPDATE goals
                SET goal_name = ?,
                    target_amount = ?,
                    current_amount = ?,
                    monthly_contribution = ?,
                    target_date = ?,
                    status = ?
                WHERE id = ?
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setDouble(2, targetAmount);
            statement.setDouble(3, currentAmount);
            statement.setDouble(4, monthlyContribution);
            statement.setString(5, targetDate);
            statement.setString(6, status == null || status.isBlank() ? "ACTIVE" : status);
            statement.setInt(7, goalId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("Select a valid goal to edit.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update goal", exception);
        }
    }

    public void deleteGoal(int goalId) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM goals WHERE id = ?")) {
            statement.setInt(1, goalId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("Select a valid goal to delete.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete goal", exception);
        }
    }

    public AiSettings getAiSettings() {
        String sql = """
                SELECT provider_type, display_name, base_url, model_name, api_key,
                       auto_start_local, enabled, agents, extensions, key_status
                FROM ai_settings
                WHERE id = 1
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                String provider = resultSet.getString("provider_type");
                String databaseApiKey = resultSet.getString("api_key");
                String apiKey = isBundledLocalProviderName(provider) ? "" : AiCredentialStore.loadApiKey();
                if (apiKey.isBlank() && databaseApiKey != null && !databaseApiKey.isBlank()) {
                    apiKey = databaseApiKey;
                    AiCredentialStore.saveApiKey(apiKey);
                    clearPlainTextAiKey(connection);
                }
                return new AiSettings(
                        resultSet.getInt("enabled") == 1,
                        resultSet.getString("display_name"),
                        provider,
                        resultSet.getString("base_url"),
                        resultSet.getString("model_name"),
                        apiKey,
                        resultSet.getString("agents"),
                        resultSet.getString("extensions"),
                        resultSet.getString("key_status"),
                        resultSet.getInt("auto_start_local") == 1
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read AI settings", exception);
        }
        return defaultLocalAiSettings();
    }

    public void saveAiSettings(AiSettings settings) {
        try (Connection connection = connect()) {
            saveAiSettings(connection, settings);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save AI settings", exception);
        }
    }

    private void saveAiSettings(Connection connection, AiSettings settings) throws SQLException {
        if (settings.isLocalProvider()) {
            AiCredentialStore.clearApiKey();
        } else if (!settings.getApiKey().isBlank()) {
            AiCredentialStore.saveApiKey(settings.getApiKey());
        } else {
            AiCredentialStore.clearApiKey();
        }
        String sql = """
                INSERT INTO ai_settings (
                    id, provider_type, display_name, base_url, model_name, api_key,
                    auto_start_local, enabled, agents, extensions, key_status, updated_at
                ) VALUES (
                    1, ?, ?, ?, ?, '', ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
                )
                ON CONFLICT(id) DO UPDATE SET
                    provider_type = excluded.provider_type,
                    display_name = excluded.display_name,
                    base_url = excluded.base_url,
                    model_name = excluded.model_name,
                    api_key = '',
                    auto_start_local = excluded.auto_start_local,
                    enabled = excluded.enabled,
                    agents = excluded.agents,
                    extensions = excluded.extensions,
                    key_status = excluded.key_status,
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, settings.getProvider());
            statement.setString(2, settings.getDisplayName());
            statement.setString(3, settings.getEndpoint());
            statement.setString(4, settings.getModel());
            statement.setInt(5, settings.isAutoStartLocal() ? 1 : 0);
            statement.setInt(6, settings.isEnabled() ? 1 : 0);
            statement.setString(7, settings.getAgents());
            statement.setString(8, settings.getExtensions());
            statement.setString(9, settings.getKeyStatus());
            statement.executeUpdate();
        }
    }

    public void saveAiKeyStatus(String keyStatus) {
        try (Connection connection = connect()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE ai_settings
                    SET key_status = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = 1
                    """)) {
                statement.setString(1, keyStatus);
                statement.executeUpdate();
            }
            saveSetting(connection, "ai.key_status", keyStatus);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save AI key status", exception);
        }
    }

    private String getSetting(String key, String fallback) {
        String sql = "SELECT setting_value FROM app_settings WHERE setting_key = ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String value = resultSet.getString("setting_value");
                    return value == null ? fallback : value;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read setting " + key, exception);
        }
        return fallback;
    }

    private String getSetting(Connection connection, String key, String fallback) throws SQLException {
        String sql = "SELECT setting_value FROM app_settings WHERE setting_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String value = resultSet.getString("setting_value");
                    return value == null ? fallback : value;
                }
            }
        }
        return fallback;
    }

    private void clearPlainTextAiKey(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE ai_settings SET api_key = '' WHERE id = 1")) {
            statement.executeUpdate();
        }
        saveSetting(connection, "ai.api_key", "");
    }

    private void saveSetting(Connection connection, String key, String value) throws SQLException {
        String sql = """
                INSERT INTO app_settings (setting_key, setting_value)
                VALUES (?, ?)
                ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value == null ? "" : value);
            statement.executeUpdate();
        }
    }

    public void recordAiInteraction(
            String moduleName,
            String actionName,
            String providerName,
            String status
    ) {
        String sql = """
                INSERT INTO ai_interaction_log (module_name, action_name, provider_name, status)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safeText(moduleName, "PFMIS"));
            statement.setString(2, safeText(actionName, "General assistance"));
            statement.setString(3, safeText(providerName, "Unknown provider"));
            statement.setString(4, safeText(status, "UNKNOWN"));
            statement.executeUpdate();
        } catch (SQLException exception) {
            System.err.println("Failed to record AI interaction: " + exception.getMessage());
        }
    }

    public List<AiInteractionRecord> listAiInteractionHistory(int limit) {
        List<AiInteractionRecord> history = new ArrayList<>();
        String sql = """
                SELECT id, module_name, action_name, provider_name, status, created_at
                FROM ai_interaction_log
                ORDER BY id DESC
                LIMIT ?
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, Math.min(limit, 500)));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    history.add(new AiInteractionRecord(
                            resultSet.getInt("id"),
                            resultSet.getString("module_name"),
                            resultSet.getString("action_name"),
                            resultSet.getString("provider_name"),
                            resultSet.getString("status"),
                            resultSet.getString("created_at")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list AI interaction history", exception);
        }
        return history;
    }

    public void recordSystemLog(
            String moduleName,
            String actionName,
            String severity,
            String details
    ) {
        String sql = """
                INSERT INTO system_event_log (module_name, action_name, severity, details)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safeText(moduleName, "System"));
            statement.setString(2, safeText(actionName, "Event"));
            statement.setString(3, safeText(severity, "INFO").toUpperCase(Locale.ENGLISH));
            statement.setString(4, safeText(details, ""));
            statement.executeUpdate();
        } catch (SQLException exception) {
            System.err.println("Failed to record system event: " + exception.getMessage());
        }
    }

    public List<SystemLogRecord> listSystemLogHistory(int limit) {
        List<SystemLogRecord> history = new ArrayList<>();
        String sql = """
                SELECT id, module_name, action_name, severity, details, created_at
                FROM system_event_log
                ORDER BY id DESC
                LIMIT ?
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, Math.min(limit, 500)));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    history.add(new SystemLogRecord(
                            resultSet.getInt("id"),
                            resultSet.getString("module_name"),
                            resultSet.getString("action_name"),
                            resultSet.getString("severity"),
                            resultSet.getString("details"),
                            resultSet.getString("created_at")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list system event history", exception);
        }
        return history;
    }

    public String maintenanceSummary() {
        StringBuilder summary = new StringBuilder();
        try (Connection connection = connect()) {
            summary.append("Database integrity: ").append(sqliteQuickCheck(connection)).append('\n');
            summary.append("Accounts: ").append(countRows(connection, "accounts")).append('\n');
            summary.append("Transactions: ").append(countRows(connection, "transactions")).append('\n');
            summary.append("Projects: ").append(countRows(connection, "projects")).append('\n');
            summary.append("Goals: ").append(countRows(connection, "goals")).append('\n');
            summary.append("Budgets: ").append(countRows(connection, "budgets")).append('\n');
            summary.append("System log records: ").append(countRows(connection, "system_event_log")).append('\n');
            summary.append("Smart Analysis log records: ").append(countRows(connection, "ai_interaction_log")).append('\n');
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to run maintenance check", exception);
        }
        BackupRecord backup = latestDailyBackupRecord();
        summary.append("Latest daily backup: ");
        if (backup == null) {
            summary.append("not available");
        } else {
            summary.append(backup.getCreatedAt()).append(" | ").append(backup.getStatus());
        }
        return summary.toString();
    }

    private String sqliteQuickCheck(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA quick_check")) {
            return resultSet.next() ? resultSet.getString(1) : "not returned";
        }
    }

    private int countRows(Connection connection, String tableName) throws SQLException {
        if (!tableExists(connection, tableName)) {
            return 0;
        }
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public BackupRecord createBackup(Path backupDirectory, String requestedName) {
        Path directory = backupDirectory == null ? defaultBackupDirectory() : backupDirectory.toAbsolutePath().normalize();
        return createBackupFile(
                directory,
                backupFileName(requestedName),
                "SQLite database, settings, accounts, transactions, goals, projects, reports data",
                "CREATED",
                false
        );
    }

    public BackupRecord ensureDailyBackup() {
        BackupRecord current = latestDailyBackupRecord();
        if (current != null && isToday(current.getCreatedAt())) {
            return current;
        }
        return createLatestDailyBackup();
    }

    public BackupRecord createLatestDailyBackup() {
        return createBackupFile(
                defaultBackupDirectory(),
                LATEST_DAILY_BACKUP_FILE_NAME,
                "Automatic daily backup: SQLite database, settings, accounts, transactions, goals, projects, reports data",
                "AUTO_DAILY",
                true
        );
    }

    public BackupRecord latestDailyBackupRecord() {
        Path backupFile = latestDailyBackupFile();
        if (!Files.isRegularFile(backupFile)) {
            return null;
        }
        try {
            String checksum = sha256(backupFile);
            return new BackupRecord(
                    backupFile.toString(),
                    Files.getLastModifiedTime(backupFile).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString(),
                    Files.size(backupFile),
                    checksum,
                    "Latest automatic daily backup",
                    isToday(Files.getLastModifiedTime(backupFile).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString())
                            ? "CURRENT"
                            : "AVAILABLE"
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read latest daily backup", exception);
        }
    }

    private BackupRecord createBackupFile(
            Path directory,
            String fileName,
            String contents,
            String status,
            boolean replaceExisting
    ) {
        Path backupFile = directory.resolve(fileName).toAbsolutePath().normalize();
        Path vacuumTarget = backupFile;
        try {
            Files.createDirectories(directory);
            if (replaceExisting) {
                vacuumTarget = Files.createTempFile(directory, "pfmis-backup-", ".tmp");
            }
            try (Connection connection = connect();
                 PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
                statement.setString(1, vacuumTarget.toString());
                statement.execute();
            }
            String checksum = sha256(vacuumTarget);
            if (replaceExisting) {
                Files.move(vacuumTarget, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(checksumFile(backupFile), checksum);
            BackupRecord record = new BackupRecord(
                    backupFile.toString(),
                    LocalDateTime.now().toString(),
                    Files.size(backupFile),
                    checksum,
                    contents,
                    status
            );
            recordBackup(record);
            recordSystemLog("Administration", "Backup created", "INFO", status + ": " + backupFile);
            return record;
        } catch (IOException | SQLException exception) {
            if (replaceExisting) {
                try {
                    Files.deleteIfExists(vacuumTarget);
                } catch (IOException ignored) {
                    // Best-effort cleanup only.
                }
            }
            throw new IllegalStateException("Failed to create backup", exception);
        }
    }

    private boolean isToday(String timestamp) {
        return timestamp != null
                && timestamp.length() >= 10
                && LocalDate.now().toString().equals(timestamp.substring(0, 10));
    }

    public String validateBackup(Path backupFile) {
        Path normalized = requireBackupFile(backupFile);
        try {
            boolean checksumVerified = false;
            Path checksumFile = checksumFile(normalized);
            if (Files.isRegularFile(checksumFile)) {
                String expectedChecksum = Files.readString(checksumFile).trim();
                String actualChecksum = sha256(normalized);
                if (!expectedChecksum.equalsIgnoreCase(actualChecksum)) {
                    throw new IllegalStateException("Backup checksum does not match. The file may be damaged or changed.");
                }
                checksumVerified = true;
            }
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + normalized)) {
                String quickCheck;
                try (Statement statement = connection.createStatement();
                     ResultSet resultSet = statement.executeQuery("PRAGMA quick_check")) {
                    quickCheck = resultSet.next() ? resultSet.getString(1) : "";
                }
                if (!"ok".equalsIgnoreCase(quickCheck)) {
                    throw new IllegalStateException("SQLite integrity check failed: " + quickCheck);
                }
                for (String tableName : List.of("accounts", "categories", "transactions", "projects", "goals")) {
                    if (!tableExists(connection, tableName)) {
                        throw new IllegalStateException("Backup is missing required table: " + tableName);
                    }
                }
            }
            return checksumVerified ? "Backup is valid. Checksum verified." : "Backup is valid. No checksum file was found.";
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Failed to validate backup", exception);
        }
    }

    public void restoreBackup(Path backupFile) {
        Path normalized = requireBackupFile(backupFile);
        validateBackup(normalized);
        createBackup(defaultBackupDirectory(), "pre-restore");
        try {
            Files.copy(normalized, databasePath(), StandardCopyOption.REPLACE_EXISTING);
            recordBackup(new BackupRecord(
                    normalized.toString(),
                    LocalDateTime.now().toString(),
                    Files.size(normalized),
                    Files.isRegularFile(checksumFile(normalized)) ? Files.readString(checksumFile(normalized)).trim() : sha256(normalized),
                    "Database restored from selected backup",
                    "RESTORED"
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to restore backup", exception);
        }
    }

    public List<BackupRecord> listBackupHistory() {
        List<BackupRecord> records = new ArrayList<>();
        List<String> knownFiles = new ArrayList<>();
        String sql = """
                SELECT backup_file, created_at, file_size, checksum, contents, status
                FROM backup_history
                ORDER BY created_at DESC
                """;
        try (Connection connection = connect()) {
            initializeBackupHistory(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String backupFile = resultSet.getString("backup_file");
                knownFiles.add(backupFile);
                records.add(new BackupRecord(
                        backupFile,
                        resultSet.getString("created_at"),
                        resultSet.getLong("file_size"),
                        resultSet.getString("checksum"),
                        resultSet.getString("contents"),
                        resultSet.getString("status")
                ));
            }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load backup history", exception);
        }

        Path directory = defaultBackupDirectory();
        if (!Files.isDirectory(directory)) {
            return records;
        }
        try (var stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".db"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> addDiscoveredBackup(records, knownFiles, path));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan backup folder", exception);
        }
        return records;
    }

    private void addDiscoveredBackup(List<BackupRecord> records, List<String> knownFiles, Path backupFile) {
        Path normalized = backupFile.toAbsolutePath().normalize();
        if (knownFiles.contains(normalized.toString())) {
            return;
        }
        try {
            String checksum = Files.isRegularFile(checksumFile(normalized))
                    ? Files.readString(checksumFile(normalized)).trim()
                    : sha256(normalized);
            records.add(new BackupRecord(
                    normalized.toString(),
                    Files.getLastModifiedTime(normalized).toString(),
                    Files.size(normalized),
                    checksum,
                    "Discovered SQLite backup file",
                    "FOUND"
            ));
        } catch (IOException exception) {
            records.add(new BackupRecord(
                    normalized.toString(),
                    "",
                    0,
                    "",
                    "Discovered file could not be read",
                    "UNREADABLE"
            ));
        }
    }

    private void recordBackup(BackupRecord record) {
        String sql = """
                INSERT INTO backup_history (backup_file, checksum, file_size, contents, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect()) {
            initializeBackupHistory(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getBackupFile());
            statement.setString(2, record.getChecksum());
            statement.setLong(3, record.getFileSize());
            statement.setString(4, record.getContents());
            statement.setString(5, record.getStatus());
            statement.setString(6, record.getCreatedAt());
            statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record backup history", exception);
        }
    }

    private String backupFileName(String requestedName) {
        String base = requestedName == null || requestedName.isBlank() ? "pfmis-backup" : requestedName.trim();
        base = base.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-+", "-");
        if (base.isBlank() || ".".equals(base) || "..".equals(base)) {
            base = "pfmis-backup";
        }
        if (base.toLowerCase(Locale.ENGLISH).endsWith(".db")) {
            base = base.substring(0, base.length() - 3);
        }
        return base + "-" + LocalDateTime.now().format(FILE_TIMESTAMP_FORMAT) + ".db";
    }

    private Path checksumFile(Path backupFile) {
        return backupFile.resolveSibling(backupFile.getFileName() + ".sha256");
    }

    private Path requireBackupFile(Path backupFile) {
        if (backupFile == null) {
            throw new IllegalArgumentException("Select a backup file first.");
        }
        Path normalized = backupFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Backup file does not exist: " + normalized);
        }
        return normalized;
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM sqlite_master
                WHERE type = 'table'
                  AND name = ?
                LIMIT 1
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to calculate backup checksum", exception);
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
        recordTransaction(
                accountId,
                categoryId,
                projectId,
                null,
                personId,
                transactionType,
                purpose,
                status,
                amount,
                date,
                description,
                paymentMethod,
                referenceNumber
        );
    }

    public void recordTransaction(
            int accountId,
            Integer categoryId,
            Integer projectId,
            Integer projectActivityId,
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
                    account_id, category_id, project_id, project_activity_id, person_id, transaction_type,
                    transaction_purpose, transaction_status, amount, transaction_date, description,
                    payment_method, reference_number
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setInt(1, accountId);
                setNullableInt(insert, 2, categoryId);
                setNullableInt(insert, 3, projectId);
                setNullableInt(insert, 4, projectActivityId);
                setNullableInt(insert, 5, personId);
                insert.setString(6, transactionType);
                insert.setString(7, purpose);
                insert.setString(8, status);
                insert.setDouble(9, amount);
                insert.setString(10, date.toString());
                insert.setString(11, description);
                insert.setString(12, paymentMethod);
                insert.setString(13, referenceNumber);
                insert.executeUpdate();
                refreshRelatedLoanStatuses(connection, personId, purpose);
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

    public void recordTransfer(
            int fromAccountId,
            int toAccountId,
            double amountSent,
            double amountReceived,
            LocalDate date,
            String description,
            String paymentMethod,
            String referenceNumber
    ) {
        if (fromAccountId == toAccountId) {
            throw new IllegalArgumentException("Choose two different accounts for a transfer");
        }
        if (amountSent <= 0 || amountReceived <= 0) {
            throw new IllegalArgumentException("Transfer amounts must be greater than zero");
        }
        if (date == null) {
            throw new IllegalArgumentException("Transfer date is required");
        }

        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                String fromAccountName = accountNameById(connection, fromAccountId);
                String toAccountName = accountNameById(connection, toAccountId);
                if (fromAccountName == null || toAccountName == null) {
                    throw new IllegalArgumentException("Select valid source and destination accounts");
                }

                int outgoingId = insertTransferRow(
                        connection,
                        fromAccountId,
                        null,
                        "TRANSFER_OUT",
                        amountSent,
                        date,
                        transferDescription("Transfer to " + toAccountName, description),
                        paymentMethod,
                        referenceNumber
                );
                int incomingId = insertTransferRow(
                        connection,
                        toAccountId,
                        outgoingId,
                        "TRANSFER_IN",
                        amountReceived,
                        date,
                        transferDescription("Transfer from " + fromAccountName, description),
                        paymentMethod,
                        referenceNumber
                );
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE transactions SET related_transaction_id = ? WHERE id = ?")) {
                    update.setInt(1, incomingId);
                    update.setInt(2, outgoingId);
                    update.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record transfer", exception);
        }
    }

    private int insertTransferRow(
            Connection connection,
            int accountId,
            Integer relatedTransactionId,
            String purpose,
            double amount,
            LocalDate date,
            String description,
            String paymentMethod,
            String referenceNumber
    ) throws SQLException {
        String sql = """
                INSERT INTO transactions (
                    account_id, related_transaction_id, transaction_type, transaction_purpose,
                    transaction_status, amount, transaction_date, description, payment_method, reference_number
                ) VALUES (?, ?, 'TRANSFER', ?, 'COMPLETED', ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement insert = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            insert.setInt(1, accountId);
            setNullableInt(insert, 2, relatedTransactionId);
            insert.setString(3, purpose);
            insert.setDouble(4, amount);
            insert.setString(5, date.toString());
            insert.setString(6, description);
            insert.setString(7, cleanNullable(paymentMethod));
            insert.setString(8, cleanNullable(referenceNumber));
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Transfer row was saved without a generated id");
    }

    private String accountNameById(Connection connection, int accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT account_name FROM accounts WHERE id = ?")) {
            statement.setInt(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("account_name") : null;
            }
        }
    }

    private String transferDescription(String base, String note) {
        String cleanNote = cleanNullable(note);
        return cleanNote == null ? base : base + " - " + cleanNote;
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public void updateTransaction(
            int transactionId,
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
        updateTransactionInternal(
                transactionId,
                accountId,
                categoryId,
                projectId,
                null,
                personId,
                transactionType,
                purpose,
                status,
                amount,
                date,
                description,
                paymentMethod,
                referenceNumber,
                false
        );
    }

    public void updateTransaction(
            int transactionId,
            int accountId,
            Integer categoryId,
            Integer projectId,
            Integer projectActivityId,
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
        updateTransactionInternal(
                transactionId,
                accountId,
                categoryId,
                projectId,
                projectActivityId,
                personId,
                transactionType,
                purpose,
                status,
                amount,
                date,
                description,
                paymentMethod,
                referenceNumber,
                true
        );
    }

    private void updateTransactionInternal(
            int transactionId,
            int accountId,
            Integer categoryId,
            Integer projectId,
            Integer projectActivityId,
            Integer personId,
            String transactionType,
            String purpose,
            String status,
            double amount,
            LocalDate date,
            String description,
            String paymentMethod,
            String referenceNumber,
            boolean updateProjectActivity
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        String sql = updateProjectActivity
                ? """
                    UPDATE transactions
                    SET account_id = ?,
                        category_id = ?,
                        project_id = ?,
                        project_activity_id = ?,
                        person_id = ?,
                        transaction_type = ?,
                        transaction_purpose = ?,
                        transaction_status = ?,
                        amount = ?,
                        transaction_date = ?,
                        description = ?,
                        payment_method = ?,
                        reference_number = ?
                    WHERE id = ?
                    """
                : """
                    UPDATE transactions
                    SET account_id = ?,
                        category_id = ?,
                        project_id = ?,
                        person_id = ?,
                        transaction_type = ?,
                        transaction_purpose = ?,
                        transaction_status = ?,
                        amount = ?,
                        transaction_date = ?,
                        description = ?,
                        payment_method = ?,
                        reference_number = ?
                    WHERE id = ?
                    """;
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                LoanSide previousLoanSide = loanSideForTransaction(connection, transactionId);
                int index = 1;
                statement.setInt(index++, accountId);
                setNullableInt(statement, index++, categoryId);
                setNullableInt(statement, index++, projectId);
                if (updateProjectActivity) {
                    setNullableInt(statement, index++, projectActivityId);
                }
                setNullableInt(statement, index++, personId);
                statement.setString(index++, transactionType);
                statement.setString(index++, purpose);
                statement.setString(index++, status);
                statement.setDouble(index++, amount);
                statement.setString(index++, date.toString());
                statement.setString(index++, description);
                statement.setString(index++, paymentMethod);
                statement.setString(index++, referenceNumber);
                statement.setInt(index, transactionId);
                statement.executeUpdate();
                refreshLoanStatuses(connection, previousLoanSide);
                refreshRelatedLoanStatuses(connection, personId, purpose);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update transaction", exception);
        }
    }

    public void deleteTransaction(int transactionId) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                LoanSide affectedLoanSide = loanSideForTransaction(connection, transactionId);
                Integer relatedTransactionId = null;
                try (PreparedStatement relation = connection.prepareStatement(
                        "SELECT related_transaction_id FROM transactions WHERE id = ?")) {
                    relation.setInt(1, transactionId);
                    try (ResultSet resultSet = relation.executeQuery()) {
                        if (resultSet.next()) {
                            relatedTransactionId = nullableInt(resultSet, "related_transaction_id");
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM transactions
                        WHERE id = ?
                           OR related_transaction_id = ?
                           OR (? IS NOT NULL AND id = ?)
                        """)) {
                    statement.setInt(1, transactionId);
                    statement.setInt(2, transactionId);
                    setNullableInt(statement, 3, relatedTransactionId);
                    setNullableInt(statement, 4, relatedTransactionId);
                    statement.executeUpdate();
                }
                refreshLoanStatuses(connection, affectedLoanSide);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete transaction", exception);
        }
    }

    private void refreshRelatedLoanStatuses(Connection connection, Integer personId, String purpose) throws SQLException {
        refreshLoanStatuses(connection, loanSideForPurpose(personId, purpose));
    }

    private void refreshAllLoanStatuses(Connection connection) throws SQLException {
        List<Integer> personIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT DISTINCT person_id
                FROM transactions
                WHERE person_id IS NOT NULL
                  AND transaction_purpose IN (?, ?, ?, ?)
                """)) {
            statement.setString(1, PURPOSE_MONEY_LENT);
            statement.setString(2, PURPOSE_LENT_REPAID);
            statement.setString(3, PURPOSE_MONEY_BORROWED);
            statement.setString(4, PURPOSE_BORROWED_REPAID);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    personIds.add(resultSet.getInt("person_id"));
                }
            }
        }
        for (Integer personId : personIds) {
            refreshLoanStatuses(connection, new LoanSide(personId, PURPOSE_MONEY_LENT, PURPOSE_LENT_REPAID));
            refreshLoanStatuses(connection, new LoanSide(personId, PURPOSE_MONEY_BORROWED, PURPOSE_BORROWED_REPAID));
        }
    }

    private LoanSide loanSideForTransaction(Connection connection, int transactionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT person_id, transaction_purpose FROM transactions WHERE id = ?")) {
            statement.setInt(1, transactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return loanSideForPurpose(
                            nullableInt(resultSet, "person_id"),
                            resultSet.getString("transaction_purpose")
                    );
                }
            }
        }
        return null;
    }

    private LoanSide loanSideForPurpose(Integer personId, String purpose) {
        if (personId == null || purpose == null || purpose.isBlank()) {
            return null;
        }
        return switch (purpose) {
            case PURPOSE_MONEY_LENT, PURPOSE_LENT_REPAID -> new LoanSide(personId, PURPOSE_MONEY_LENT, PURPOSE_LENT_REPAID);
            case PURPOSE_MONEY_BORROWED, PURPOSE_BORROWED_REPAID -> new LoanSide(personId, PURPOSE_MONEY_BORROWED, PURPOSE_BORROWED_REPAID);
            default -> null;
        };
    }

    private void refreshLoanStatuses(Connection connection, LoanSide loanSide) throws SQLException {
        if (loanSide == null) {
            return;
        }

        double remainingRepayments = loanRepaymentTotal(connection, loanSide);
        List<LoanPrincipal> principals = loanPrincipals(connection, loanSide);
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE transactions SET transaction_status = ? WHERE id = ?")) {
            for (LoanPrincipal principal : principals) {
                String recalculatedStatus = loanPrincipalStatus(principal.amount(), remainingRepayments);
                remainingRepayments = Math.max(0, remainingRepayments - principal.amount());
                if (!recalculatedStatus.equals(principal.status())) {
                    update.setString(1, recalculatedStatus);
                    update.setInt(2, principal.id());
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    private double loanRepaymentTotal(Connection connection, LoanSide loanSide) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(SUM(amount), 0)
                FROM transactions
                WHERE person_id = ?
                  AND transaction_purpose = ?
                  AND COALESCE(transaction_status, 'COMPLETED') <> ?
                """)) {
            statement.setInt(1, loanSide.personId());
            statement.setString(2, loanSide.repaymentPurpose());
            statement.setString(3, STATUS_CANCELLED);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble(1) : 0;
            }
        }
    }

    private List<LoanPrincipal> loanPrincipals(Connection connection, LoanSide loanSide) throws SQLException {
        List<LoanPrincipal> principals = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, amount, transaction_status
                FROM transactions
                WHERE person_id = ?
                  AND transaction_purpose = ?
                  AND COALESCE(transaction_status, 'COMPLETED') <> ?
                ORDER BY transaction_date, id
                """)) {
            statement.setInt(1, loanSide.personId());
            statement.setString(2, loanSide.principalPurpose());
            statement.setString(3, STATUS_CANCELLED);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    principals.add(new LoanPrincipal(
                            resultSet.getInt("id"),
                            resultSet.getDouble("amount"),
                            resultSet.getString("transaction_status")
                    ));
                }
            }
        }
        return principals;
    }

    private String loanPrincipalStatus(double principalAmount, double remainingRepayments) {
        if (remainingRepayments + LOAN_CLEARANCE_EPSILON >= principalAmount) {
            return STATUS_CLEARED;
        }
        if (remainingRepayments > LOAN_CLEARANCE_EPSILON) {
            return STATUS_PARTIALLY_CLEARED;
        }
        return STATUS_OPEN;
    }

    private Integer nullableInt(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private int generatedId(Connection connection, PreparedStatement statement) throws SQLException {
        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        try (Statement idStatement = connection.createStatement();
             ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid()")) {
            return resultSet.next() ? resultSet.getInt(1) : -1;
        }
    }

    public List<FinanceTransaction> listRecentTransactions(int limit) {
        List<FinanceTransaction> transactions = new ArrayList<>();
        String sql = """
                SELECT t.id, a.account_name, t.transaction_type, t.transaction_purpose, t.transaction_status,
                       c.category_name, p.project_name, t.project_activity_id, pa.activity_name, pe.full_name,
                       t.amount, t.transaction_date, t.description, t.payment_method, t.reference_number
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                LEFT JOIN categories c ON c.id = t.category_id
                LEFT JOIN projects p ON p.id = t.project_id
                LEFT JOIN project_activities pa ON pa.id = t.project_activity_id
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
                            resultSet.getString("reference_number"),
                            nullableInt(resultSet, "project_activity_id"),
                            resultSet.getString("activity_name")
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
                       c.category_name, p.project_name, t.project_activity_id, pa.activity_name, pe.full_name,
                       t.amount, t.transaction_date, t.description, t.payment_method, t.reference_number
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                LEFT JOIN categories c ON c.id = t.category_id
                LEFT JOIN projects p ON p.id = t.project_id
                LEFT JOIN project_activities pa ON pa.id = t.project_activity_id
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
                            resultSet.getString("reference_number"),
                            nullableInt(resultSet, "project_activity_id"),
                            resultSet.getString("activity_name")
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
        for (PaymentMethodRecord method : listPaymentMethods()) {
            if (!"INACTIVE".equals(method.getStatus())) {
                addSuggestion(suggestions, method.getMethodName());
            }
        }
        String sql = "SELECT DISTINCT payment_method FROM transactions WHERE payment_method IS NOT NULL AND trim(payment_method) <> '' ORDER BY payment_method";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String paymentMethod = resultSet.getString("payment_method");
                addSuggestion(suggestions, paymentMethod);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list payment method suggestions", exception);
        }
        return suggestions;
    }

    public List<PaymentMethodRecord> listPaymentMethods() {
        List<PaymentMethodRecord> methods = new ArrayList<>();
        String sql = """
                SELECT pm.id, pm.method_name, pm.method_type, pm.provider, pm.default_account,
                       pm.status, MAX(t.transaction_date) AS last_used
                FROM payment_methods pm
                LEFT JOIN valid_transactions t ON lower(t.payment_method) = lower(pm.method_name)
                GROUP BY pm.id
                ORDER BY CASE pm.status WHEN 'ACTIVE' THEN 0 ELSE 1 END,
                         pm.method_name COLLATE NOCASE
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                methods.add(new PaymentMethodRecord(
                        resultSet.getInt("id"),
                        resultSet.getString("method_name"),
                        resultSet.getString("method_type"),
                        resultSet.getString("provider"),
                        resultSet.getString("default_account"),
                        resultSet.getString("status"),
                        resultSet.getString("last_used")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list payment methods", exception);
        }
        return methods;
    }

    public void savePaymentMethod(String methodName, String methodType, String provider, String defaultAccount, String status) {
        String cleanName = requireText(methodName, "Payment method name");
        String cleanType = methodType == null || methodType.isBlank() ? "Other" : methodType.trim();
        String cleanStatus = normalizedActiveStatus(status);
        try (Connection connection = connect()) {
            if (paymentMethodExists(connection, cleanName)) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE payment_methods
                        SET method_type = ?,
                            provider = ?,
                            default_account = ?,
                            status = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE lower(method_name) = lower(?)
                        """)) {
                    statement.setString(1, cleanType);
                    statement.setString(2, provider == null ? "" : provider.trim());
                    statement.setString(3, defaultAccount == null ? "" : defaultAccount.trim());
                    statement.setString(4, cleanStatus);
                    statement.setString(5, cleanName);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO payment_methods (
                            method_name, method_type, provider, default_account, status, updated_at
                        ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """)) {
                    statement.setString(1, cleanName);
                    statement.setString(2, cleanType);
                    statement.setString(3, provider == null ? "" : provider.trim());
                    statement.setString(4, defaultAccount == null ? "" : defaultAccount.trim());
                    statement.setString(5, cleanStatus);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save payment method", exception);
        }
    }

    private boolean paymentMethodExists(Connection connection, String methodName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM payment_methods
                WHERE lower(method_name) = lower(?)
                LIMIT 1
                """)) {
            statement.setString(1, methodName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private String normalizedActiveStatus(String status) {
        return status != null && "INACTIVE".equals(status.trim().toUpperCase(Locale.ENGLISH))
                ? "INACTIVE"
                : "ACTIVE";
    }

    private String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
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
                                               WHEN t.transaction_type = 'TRANSFER' AND t.transaction_purpose = 'TRANSFER_IN' THEN t.amount
                                               WHEN t.transaction_type = 'TRANSFER' AND t.transaction_purpose = 'TRANSFER_OUT' THEN -t.amount
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
                    queryDouble(connection, """
                            SELECT COALESCE(SUM(
                                CASE
                                    WHEN transaction_purpose IN ('MONEY_LENT', 'SUPPORT_GIVEN') THEN amount
                                    WHEN transaction_purpose = 'LENT_REPAID' THEN -amount
                                    WHEN transaction_purpose = 'MONEY_BORROWED' THEN -amount
                                    WHEN transaction_purpose = 'BORROWED_REPAID' THEN amount
                                    ELSE 0
                                END
                            ), 0)
                            FROM transactions
                            WHERE transaction_purpose IN ('MONEY_LENT', 'SUPPORT_GIVEN', 'LENT_REPAID', 'MONEY_BORROWED', 'BORROWED_REPAID')
                              AND COALESCE(transaction_status, 'COMPLETED') <> 'CANCELLED'
                            """)
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load dashboard stats", exception);
        }
    }

    private double queryMonthlyTotal(Connection connection, String month, String type) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(amount), 0)
                FROM transactions
                WHERE transaction_type = ?
                  AND substr(transaction_date, 1, 7) = ?
                  AND COALESCE(transaction_status, 'COMPLETED') <> 'CANCELLED'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            statement.setString(2, month);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble(1) : 0;
            }
        }
    }

    public double transactionTotalByTypeForMonth(String type, String month) {
        try (Connection connection = connect()) {
            return queryMonthlyTotal(connection, month, type);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load monthly transaction total", exception);
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
                  AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY label
                ORDER BY amount DESC
                """);
    }

    public List<ReportRow> categorySpendingReport(String month) {
        return report("""
                SELECT COALESCE(c.category_name, 'Uncategorized') AS label, COALESCE(SUM(t.amount), 0) AS amount
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.transaction_type = 'EXPENSE'
                  AND substr(t.transaction_date, 1, 7) = ?
                  AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY label
                ORDER BY amount DESC
                """, month);
    }

    public List<ReportRow> incomeSourceReport() {
        return report("""
                SELECT COALESCE(c.category_name, 'Uncategorized') AS label, COALESCE(SUM(t.amount), 0) AS amount
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.transaction_type = 'INCOME'
                  AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY label
                ORDER BY amount DESC
                """);
    }

    public List<ReportRow> incomeSourceByAccountReport() {
        return reportWithAccount("""
                SELECT COALESCE(c.category_name, 'Uncategorized') AS label,
                       a.account_name AS account,
                       COALESCE(SUM(t.amount), 0) AS amount
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.transaction_type = 'INCOME'
                  AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY label, account
                ORDER BY label, account
                """);
    }

    public List<ReportRow> incomeSourceByAccountReport(String month) {
        return reportWithAccount("""
                SELECT COALESCE(c.category_name, 'Uncategorized') AS label,
                       a.account_name AS account,
                       COALESCE(SUM(t.amount), 0) AS amount
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.transaction_type = 'INCOME'
                  AND substr(t.transaction_date, 1, 7) = ?
                  AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY label, account
                ORDER BY label, account
                """, month);
    }

    public List<ReportRow> categorySpendingByAccountReport() {
        return reportWithAccount("""
                SELECT COALESCE(c.category_name, 'Uncategorized') AS label,
                       a.account_name AS account,
                       COALESCE(SUM(t.amount), 0) AS amount
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.transaction_type = 'EXPENSE'
                  AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY label, account
                ORDER BY label, account
                """);
    }

    public List<ReportRow> categorySpendingByAccountReport(String month) {
        return reportWithAccount("""
                SELECT COALESCE(c.category_name, 'Uncategorized') AS label,
                       a.account_name AS account,
                       COALESCE(SUM(t.amount), 0) AS amount
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.transaction_type = 'EXPENSE'
                  AND substr(t.transaction_date, 1, 7) = ?
                  AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY label, account
                ORDER BY label, account
                """, month);
    }

    public List<ReportRow> projectSpendingReport() {
        return report("""
                SELECT p.project_name AS label, COALESCE(SUM(t.amount), 0) AS amount
                FROM projects p
                LEFT JOIN transactions t ON t.project_id = p.id
                    AND t.transaction_type = 'EXPENSE'
                    AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY p.id
                ORDER BY amount DESC
                """);
    }

    public List<ReportRow> projectSpendingReport(String month) {
        return report("""
                SELECT p.project_name AS label, COALESCE(SUM(t.amount), 0) AS amount
                FROM projects p
                LEFT JOIN transactions t ON t.project_id = p.id
                    AND t.transaction_type = 'EXPENSE'
                    AND substr(t.transaction_date, 1, 7) = ?
                    AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY p.id
                ORDER BY amount DESC
                """, month);
    }

    public List<ReportRow> accountBalanceReport() {
        return report("""
                SELECT a.account_name AS label,
                       a.opening_balance + COALESCE(SUM(
                           CASE
                              WHEN t.transaction_type = 'INCOME' THEN t.amount
                              WHEN t.transaction_type = 'EXPENSE' THEN -t.amount
                              WHEN t.transaction_type = 'TRANSFER' AND t.transaction_purpose = 'TRANSFER_IN' THEN t.amount
                              WHEN t.transaction_type = 'TRANSFER' AND t.transaction_purpose = 'TRANSFER_OUT' THEN -t.amount
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

    public List<ReportRow> accountBalanceReportThroughMonth(String month) {
        return report("""
                SELECT a.account_name AS label,
                       a.opening_balance + COALESCE(SUM(
                           CASE
                              WHEN t.transaction_type = 'INCOME' THEN t.amount
                              WHEN t.transaction_type = 'EXPENSE' THEN -t.amount
                              WHEN t.transaction_type = 'TRANSFER' AND t.transaction_purpose = 'TRANSFER_IN' THEN t.amount
                              WHEN t.transaction_type = 'TRANSFER' AND t.transaction_purpose = 'TRANSFER_OUT' THEN -t.amount
                              ELSE 0
                          END
                       ), 0) AS amount
                FROM accounts a
                LEFT JOIN transactions t ON t.account_id = a.id
                    AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                    AND substr(t.transaction_date, 1, 7) <= ?
                GROUP BY a.id
                ORDER BY a.account_name
                """, month);
    }

    public List<ReportRow> lendingByPersonReport() {
        return report("""
                SELECT COALESCE(pe.full_name, 'Unassigned') AS label,
                       COALESCE(SUM(
                           CASE
                               WHEN t.transaction_purpose IN ('MONEY_LENT', 'SUPPORT_GIVEN') THEN t.amount
                               WHEN t.transaction_purpose = 'LENT_REPAID' THEN -t.amount
                               WHEN t.transaction_purpose = 'MONEY_BORROWED' THEN -t.amount
                               WHEN t.transaction_purpose = 'BORROWED_REPAID' THEN t.amount
                               ELSE 0
                           END
                       ), 0) AS amount
                FROM transactions t
                LEFT JOIN people pe ON pe.id = t.person_id
                WHERE t.transaction_purpose IN ('MONEY_LENT', 'SUPPORT_GIVEN', 'LENT_REPAID', 'MONEY_BORROWED', 'BORROWED_REPAID')
                  AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY label
                ORDER BY amount DESC
                """);
    }

    public List<ReportRow> lendingByPersonReport(String month) {
        return report("""
                SELECT COALESCE(pe.full_name, 'Unassigned') AS label,
                       COALESCE(SUM(
                           CASE
                               WHEN t.transaction_purpose IN ('MONEY_LENT', 'SUPPORT_GIVEN') THEN t.amount
                               WHEN t.transaction_purpose = 'LENT_REPAID' THEN -t.amount
                               WHEN t.transaction_purpose = 'MONEY_BORROWED' THEN -t.amount
                               WHEN t.transaction_purpose = 'BORROWED_REPAID' THEN t.amount
                               ELSE 0
                           END
                       ), 0) AS amount
                FROM transactions t
                LEFT JOIN people pe ON pe.id = t.person_id
                WHERE t.transaction_purpose IN ('MONEY_LENT', 'SUPPORT_GIVEN', 'LENT_REPAID', 'MONEY_BORROWED', 'BORROWED_REPAID')
                  AND substr(t.transaction_date, 1, 7) = ?
                  AND COALESCE(t.transaction_status, 'COMPLETED') <> 'CANCELLED'
                GROUP BY label
                ORDER BY amount DESC
                """, month);
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

    private List<ReportRow> report(String sql, String value) {
        List<ReportRow> rows = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new ReportRow(resultSet.getString("label"), resultSet.getDouble("amount")));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load report", exception);
        }
        return rows;
    }

    private List<ReportRow> reportWithAccount(String sql) {
        List<ReportRow> rows = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new ReportRow(
                        resultSet.getString("label"),
                        resultSet.getString("account"),
                        resultSet.getDouble("amount")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load report", exception);
        }
        return rows;
    }

    private List<ReportRow> reportWithAccount(String sql, String value) {
        List<ReportRow> rows = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new ReportRow(
                            resultSet.getString("label"),
                            resultSet.getString("account"),
                            resultSet.getDouble("amount")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load report", exception);
        }
        return rows;
    }
}
