package com.wk.pfmis.auth;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.AuthenticationEventRecord;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.PasswordSecurity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class AuthDatabase {
    private static final AuthDatabase INSTANCE = new AuthDatabase();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;
    private static final DateTimeFormatter DB_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
    private static final String LATEST_SECURITY_BACKUP = "pfmis-auth-latest-daily-backup.db";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    public record PasswordResetDelivery(
            int userId,
            String username,
            String displayName,
            String email,
            String temporaryPassword
    ) {
    }

    private record AuthenticationRow(
            int id,
            String username,
            String passwordHash,
            String passwordSalt,
            int passwordIterations,
            String status,
            int failedLoginCount,
            String lockedUntil
    ) {
    }

    private AuthDatabase() {
    }

    public static AuthDatabase getInstance() {
        return INSTANCE;
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.authDatabasePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    public void initialize() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL COLLATE NOCASE UNIQUE,
                        full_name TEXT NOT NULL,
                        email TEXT COLLATE NOCASE UNIQUE,
                        password_hash TEXT NOT NULL,
                        password_salt TEXT NOT NULL,
                        password_iterations INTEGER NOT NULL,
                        role TEXT NOT NULL DEFAULT 'USER',
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        failed_login_count INTEGER NOT NULL DEFAULT 0,
                        locked_until TEXT,
                        must_change_password INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT,
                        last_login_at TEXT,
                        CHECK (role IN ('SUPER_ADMIN','ADMIN','USER')),
                        CHECK (status IN ('ACTIVE','INACTIVE'))
                    )
                    """);
            migrateUsersTable(connection);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_users_status ON users(status)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_users_role ON users(role)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS authentication_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER,
                        username_attempt TEXT,
                        event_type TEXT NOT NULL,
                        success INTEGER NOT NULL DEFAULT 0,
                        details TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(id)
                    )
                    """);
            statement.execute("DROP TABLE IF EXISTS remembered_login_tokens");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize PFMIS user security database.", exception);
        }
    }

    public Path ensureDailySecurityBackup() {
        Path directory = DatabaseHandler.applicationDataDirectory().resolve("security-backups").toAbsolutePath().normalize();
        Path backup = directory.resolve(LATEST_SECURITY_BACKUP);
        try {
            Files.createDirectories(directory);
            if (Files.isRegularFile(backup)) {
                LocalDateTime modified = Files.getLastModifiedTime(backup).toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                if (modified.toLocalDate().equals(java.time.LocalDate.now())) {
                    return backup;
                }
            }
            Path temporary = Files.createTempFile(directory, "pfmis-auth-backup-", ".tmp");
            try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
                statement.setString(1, temporary.toString());
                statement.execute();
            }
            Files.move(temporary, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(Path.of(backup + ".sha256"), sha256(backup));
            return backup;
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Failed to back up the PFMIS user registry.", exception);
        }
    }

    private void migrateUsersTable(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "users", "must_change_password", "INTEGER NOT NULL DEFAULT 0");
        ensureAdminRoleAllowed(connection);
    }

    private void ensureAdminRoleAllowed(Connection connection) throws SQLException {
        String createSql = null;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sql
                FROM sqlite_master
                WHERE type = 'table' AND name = 'users'
                """);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                createSql = resultSet.getString("sql");
            }
        }
        if (createSql == null || createSql.contains("'ADMIN'")) {
            return;
        }
        boolean originalAutoCommit = connection.getAutoCommit();
        int originalCount = countRows(connection, "users");
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = OFF");
            connection.setAutoCommit(false);
            statement.execute("DROP TABLE IF EXISTS users_new");
            statement.execute("""
                    CREATE TABLE users_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL COLLATE NOCASE UNIQUE,
                        full_name TEXT NOT NULL,
                        email TEXT COLLATE NOCASE UNIQUE,
                        password_hash TEXT NOT NULL,
                        password_salt TEXT NOT NULL,
                        password_iterations INTEGER NOT NULL,
                        role TEXT NOT NULL DEFAULT 'USER',
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        failed_login_count INTEGER NOT NULL DEFAULT 0,
                        locked_until TEXT,
                        must_change_password INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT,
                        last_login_at TEXT,
                        CHECK (role IN ('SUPER_ADMIN','ADMIN','USER')),
                        CHECK (status IN ('ACTIVE','INACTIVE'))
                    )
                    """);
            statement.execute("""
                    INSERT INTO users_new (
                        id, username, full_name, email, password_hash, password_salt,
                        password_iterations, role, status, failed_login_count, locked_until,
                        must_change_password, created_at, updated_at, last_login_at
                    )
                    SELECT id, username, full_name, email, password_hash, password_salt,
                           password_iterations, role, status, failed_login_count, locked_until,
                           must_change_password, created_at, updated_at, last_login_at
                    FROM users
                    """);
            int migratedCount = countRows(connection, "users_new");
            if (migratedCount != originalCount) {
                throw new SQLException("User migration copied " + migratedCount + " of " + originalCount + " users.");
            }
            statement.execute("DROP TABLE users");
            statement.execute("ALTER TABLE users_new RENAME TO users");
            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly(connection);
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
        }
        assertNoForeignKeyViolations(connection);
    }

    private void addColumnIfMissing(
            Connection connection,
            String tableName,
            String columnName,
            String columnDefinition
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private int countRows(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private void assertNoForeignKeyViolations(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (resultSet.next()) {
                throw new SQLException("Foreign key violation after users migration: "
                        + resultSet.getString(1) + "#" + resultSet.getString(2));
            }
        }
    }

    public Path securityBackupDirectory() {
        Path directory = DatabaseHandler.applicationDataDirectory().resolve("security-backups").toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create the security backup directory.", exception);
        }
    }

    public boolean hasUsers() {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM users)");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to check registered users.", exception);
        }
    }

    public SystemUser registerUser(String fullName, String username, String email, String password) {
        return registerUserInternal(fullName, username, email, password, null, null);
    }

    public SystemUser registerUserByAdmin(String fullName, String username, String email, String password,
                                          String requestedRole, int actingUserId) {
        return registerUserInternal(fullName, username, email, password, requestedRole, actingUserId);
    }

    private SystemUser registerUserInternal(String fullName, String username, String email, String password,
                                            String requestedRole, Integer actingUserId) {
        String cleanName = requireText(fullName, "Full name");
        String cleanUsername = normalizeUsername(username);
        String cleanEmail = email == null ? "" : email.trim().toLowerCase(Locale.ENGLISH);
        if (!cleanEmail.isBlank() && (!cleanEmail.contains("@") || cleanEmail.startsWith("@") || cleanEmail.endsWith("@"))) {
            throw new IllegalArgumentException("Enter a valid email address or leave it blank.");
        }
        PasswordSecurity.PasswordRecord passwordRecord = PasswordSecurity.hash(password);
        int createdUserId;
        boolean createdFirstUser;

        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                createdFirstUser = userCount(connection) == 0;
                if (!createdFirstUser && actingUserId == null) {
                    throw new SecurityException("New users must be created by a super administrator.");
                }
                if (!createdFirstUser && !isActiveSuperAdmin(connection, actingUserId)) {
                    throw new SecurityException("Only an active super administrator can create users.");
                }
                String role = createdFirstUser
                        ? SystemUser.ROLE_SUPER_ADMIN
                        : normalizeRequestedRole(requestedRole);
                String sql = """
                        INSERT INTO users (
                            username, full_name, email, password_hash, password_salt,
                            password_iterations, role, status, must_change_password
                        ) VALUES (?, ?, NULLIF(?, ''), ?, ?, ?, ?, 'ACTIVE', ?)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, cleanUsername);
                    statement.setString(2, cleanName);
                    statement.setString(3, cleanEmail);
                    statement.setString(4, passwordRecord.hash());
                    statement.setString(5, passwordRecord.salt());
                    statement.setInt(6, passwordRecord.iterations());
                    statement.setString(7, role);
                    statement.setInt(8, !createdFirstUser && actingUserId != null ? 1 : 0);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("No user ID was generated.");
                        }
                        createdUserId = keys.getInt(1);
                    }
                }
                String registrationDetail = "User account created as " + role + "."
                        + (actingUserId == null ? "" : " Created by user " + actingUserId + ".");
                recordAuthEvent(connection, createdUserId, cleanUsername, "REGISTER", true, registrationDetail);
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                if (isConstraintFailure(exception)) {
                    throw new IllegalArgumentException("That username or email address is already registered.");
                }
                throw exception;
            } catch (RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to register the user.", exception);
        }

        if (createdFirstUser) {
            DatabaseHandler.migrateLegacyDatabaseToUser(createdUserId);
        }
        return findUserById(createdUserId);
    }

    public SystemUser authenticate(String usernameOrEmail, String password) {
        String login = requireText(usernameOrEmail, "Username or email").toLowerCase(Locale.ENGLISH);
        String sql = """
                SELECT id, username, password_hash, password_salt, password_iterations,
                       status, failed_login_count, locked_until
                FROM users
                WHERE lower(username) = ? OR lower(COALESCE(email, '')) = ?
                LIMIT 1
                """;
        try (Connection connection = connect()) {
            AuthenticationRow authenticationRow;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, login);
                statement.setString(2, login);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        recordAuthEvent(connection, null, login, "LOGIN", false, "Unknown username or email.");
                        throw new IllegalArgumentException("Invalid username/email or password.");
                    }
                    authenticationRow = new AuthenticationRow(
                            resultSet.getInt("id"),
                            resultSet.getString("username"),
                            resultSet.getString("password_hash"),
                            resultSet.getString("password_salt"),
                            resultSet.getInt("password_iterations"),
                            resultSet.getString("status"),
                            resultSet.getInt("failed_login_count"),
                            resultSet.getString("locked_until")
                    );
                }
            }

            if (!SystemUser.STATUS_ACTIVE.equals(authenticationRow.status())) {
                recordAuthEvent(connection, authenticationRow.id(), authenticationRow.username(), "LOGIN", false, "Inactive account.");
                throw new IllegalArgumentException("This account is inactive. Contact the super administrator.");
            }
            if (isStillLocked(authenticationRow.lockedUntil())) {
                recordAuthEvent(connection, authenticationRow.id(), authenticationRow.username(), "LOGIN", false, "Account temporarily locked.");
                throw new IllegalArgumentException("Too many failed attempts. Try again after " + authenticationRow.lockedUntil() + ".");
            }

            boolean valid = PasswordSecurity.verify(
                    password,
                    authenticationRow.passwordHash(),
                    authenticationRow.passwordSalt(),
                    authenticationRow.passwordIterations()
            );
            if (!valid) {
                int attempts = authenticationRow.failedLoginCount() + 1;
                String newLock = attempts >= MAX_FAILED_ATTEMPTS
                        ? LocalDateTime.now().plusMinutes(LOCK_MINUTES).format(DB_TIME)
                        : null;
                updateFailedLogin(
                        connection,
                        authenticationRow.id(),
                        attempts >= MAX_FAILED_ATTEMPTS ? 0 : attempts,
                        newLock
                );
                recordAuthEvent(
                        connection,
                        authenticationRow.id(),
                        authenticationRow.username(),
                        "LOGIN",
                        false,
                        newLock == null ? "Invalid password." : "Account locked for " + LOCK_MINUTES + " minutes."
                );
                throw new IllegalArgumentException(newLock == null
                        ? "Invalid username/email or password."
                        : "Too many failed attempts. The account is locked for " + LOCK_MINUTES + " minutes.");
            }

            if (PasswordSecurity.needsRehash(authenticationRow.passwordIterations())) {
                PasswordSecurity.PasswordRecord upgraded = PasswordSecurity.hash(password);
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE users
                        SET password_hash = ?, password_salt = ?, password_iterations = ?,
                            failed_login_count = 0, locked_until = NULL,
                            last_login_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """)) {
                    update.setString(1, upgraded.hash());
                    update.setString(2, upgraded.salt());
                    update.setInt(3, upgraded.iterations());
                    update.setInt(4, authenticationRow.id());
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE users
                        SET failed_login_count = 0, locked_until = NULL,
                            last_login_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """)) {
                    update.setInt(1, authenticationRow.id());
                    update.executeUpdate();
                }
            }
            recordAuthEvent(connection, authenticationRow.id(), authenticationRow.username(), "LOGIN", true, "Successful login.");
            return findUserById(connection, authenticationRow.id());
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to sign in.", exception);
        }
    }

    public void recordWorkspaceAccess(int actingUserId, int targetUserId) {
        try (Connection connection = connect()) {
            if (!isActiveSuperAdmin(connection, actingUserId)) {
                throw new SecurityException("Only a super administrator can access another user's workspace.");
            }
            recordAuthEvent(
                    connection,
                    targetUserId,
                    null,
                    "WORKSPACE_ACCESS",
                    true,
                    "Workspace opened by super administrator user " + actingUserId + "."
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record workspace access.", exception);
        }
    }

    public void recordLogout(int userId) {
        try (Connection connection = connect()) {
            recordAuthEvent(connection, userId, null, "LOGOUT", true, "User signed out.");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record logout.", exception);
        }
    }

    public List<SystemUser> listUsers(int actingUserId) {
        String sql = """
                SELECT id, username, full_name, email, role, status, created_at, last_login_at, must_change_password
                FROM users
                ORDER BY CASE role WHEN 'SUPER_ADMIN' THEN 0 WHEN 'ADMIN' THEN 1 ELSE 2 END, full_name COLLATE NOCASE
                """;
        List<SystemUser> users = new ArrayList<>();
        try (Connection connection = connect()) {
            if (!isActiveSuperAdmin(connection, actingUserId)) {
                throw new SecurityException("Only a super administrator can list system users.");
            }
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    users.add(mapUser(resultSet));
                }
            }
            return users;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load users.", exception);
        }
    }

    public List<AuthenticationEventRecord> listAuthenticationEvents(int actingUserId, int limit) {
        String sql = """
                SELECT log.id, log.user_id,
                       COALESCE(user.username, log.username_attempt, '-') AS username,
                       log.event_type, log.success, log.details, log.created_at
                FROM authentication_log log
                LEFT JOIN users user ON user.id = log.user_id
                ORDER BY log.id DESC
                LIMIT ?
                """;
        List<AuthenticationEventRecord> events = new ArrayList<>();
        try (Connection connection = connect()) {
            if (!isActiveSuperAdmin(connection, actingUserId)) {
                throw new SecurityException("Only a super administrator can view authentication events.");
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, Math.max(1, Math.min(limit, 1000)));
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        events.add(mapAuthenticationEvent(resultSet));
                    }
                }
            }
            return events;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load authentication events.", exception);
        }
    }

    public List<AuthenticationEventRecord> listOwnAuthenticationEvents(int userId, int limit) {
        String sql = """
                SELECT log.id, log.user_id,
                       COALESCE(user.username, log.username_attempt, '-') AS username,
                       log.event_type, log.success, log.details, log.created_at
                FROM authentication_log log
                LEFT JOIN users user ON user.id = log.user_id
                JOIN users signed_user ON signed_user.id = ?
                WHERE log.user_id = signed_user.id
                   OR lower(COALESCE(log.username_attempt, '')) = lower(signed_user.username)
                   OR (
                        signed_user.email IS NOT NULL
                        AND signed_user.email <> ''
                        AND lower(COALESCE(log.username_attempt, '')) = lower(signed_user.email)
                   )
                ORDER BY log.id DESC
                LIMIT ?
                """;
        List<AuthenticationEventRecord> events = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, Math.max(1, Math.min(limit, 1000)));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(mapAuthenticationEvent(resultSet));
                }
            }
            return events;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load your authentication events.", exception);
        }
    }

    public SystemUser findUserById(int userId) {
        try (Connection connection = connect()) {
            return findUserById(connection, userId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load the user.", exception);
        }
    }

    private SystemUser findUserById(Connection connection, int userId) throws SQLException {
        String sql = """
                SELECT id, username, full_name, email, role, status, created_at, last_login_at, must_change_password
                FROM users WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapUser(resultSet);
                }
            }
        }
        throw new IllegalArgumentException("The selected user no longer exists.");
    }

    public void updateUserStatus(int userId, String status, int actingUserId) {
        if (!SystemUser.STATUS_ACTIVE.equals(status) && !SystemUser.STATUS_INACTIVE.equals(status)) {
            throw new IllegalArgumentException("Invalid user status.");
        }
        if (userId == actingUserId && SystemUser.STATUS_INACTIVE.equals(status)) {
            throw new IllegalArgumentException("You cannot deactivate your own signed-in account.");
        }
        try (Connection connection = connect()) {
            if (!isActiveSuperAdmin(connection, actingUserId)) {
                throw new SecurityException("Only a super administrator can change a user's status.");
            }
            if (SystemUser.STATUS_INACTIVE.equals(status) && isLastActiveSuperAdmin(connection, userId)) {
                throw new IllegalArgumentException("At least one active super administrator must remain.");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE users SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """)) {
                statement.setString(1, status);
                statement.setInt(2, userId);
                if (statement.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Select a valid user.");
                }
            }
            recordAuthEvent(connection, userId, null, "STATUS_CHANGE", true, "Status changed to " + status + " by user " + actingUserId + ".");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update the user status.", exception);
        }
    }

    public void changeOwnPassword(int userId, String currentPassword, String newPassword) {
        PasswordSecurity.PasswordRecord newRecord = PasswordSecurity.hash(newPassword);
        String sql = """
                SELECT password_hash, password_salt, password_iterations
                FROM users WHERE id = ? AND status = 'ACTIVE'
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || !PasswordSecurity.verify(
                        currentPassword,
                        resultSet.getString("password_hash"),
                        resultSet.getString("password_salt"),
                        resultSet.getInt("password_iterations")
                )) {
                    recordAuthEvent(connection, userId, null, "PASSWORD_CHANGE", false, "Current password did not match.");
                    throw new IllegalArgumentException("The current password is incorrect.");
                }
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE users
                    SET password_hash = ?, password_salt = ?, password_iterations = ?,
                        failed_login_count = 0, locked_until = NULL,
                        must_change_password = 0, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """)) {
                update.setString(1, newRecord.hash());
                update.setString(2, newRecord.salt());
                update.setInt(3, newRecord.iterations());
                update.setInt(4, userId);
                update.executeUpdate();
            }
            recordAuthEvent(connection, userId, null, "PASSWORD_CHANGE", true, "Password changed by the user.");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to change the password.", exception);
        }
    }

    public void resetPassword(int userId, String newPassword, int actingUserId) {
        PasswordSecurity.PasswordRecord record = PasswordSecurity.hash(newPassword);
        try (Connection connection = connect()) {
            if (!isActiveSuperAdmin(connection, actingUserId)) {
                throw new SecurityException("Only a super administrator can reset another user's password.");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE users
                SET password_hash = ?, password_salt = ?, password_iterations = ?,
                    failed_login_count = 0, locked_until = NULL,
                    must_change_password = 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)) {
                statement.setString(1, record.hash());
                statement.setString(2, record.salt());
                statement.setInt(3, record.iterations());
                statement.setInt(4, userId);
                if (statement.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Select a valid user.");
                }
                recordAuthEvent(connection, userId, null, "PASSWORD_RESET", true, "Password reset by user " + actingUserId + ".");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to reset the password.", exception);
        }
    }

    public PasswordResetDelivery createEmailPasswordReset(String emailOrUsername, Integer actingUserId) {
        PasswordResetDelivery reset = prepareEmailPasswordReset(emailOrUsername, actingUserId);
        completeEmailPasswordReset(reset, actingUserId);
        return reset;
    }

    public PasswordResetDelivery prepareEmailPasswordReset(String emailOrUsername, Integer actingUserId) {
        String login = requireText(emailOrUsername, "Email or username").toLowerCase(Locale.ENGLISH);
        String temporaryPassword = generateTemporaryPassword();
        try (Connection connection = connect()) {
            if (actingUserId != null && !isActiveSuperAdmin(connection, actingUserId)) {
                throw new SecurityException("Only a super administrator can reset another user's password.");
            }
            SystemUser user = findActiveUserByEmailOrUsername(connection, login);
            if (!isEmailLike(user.getEmail())) {
                throw new IllegalArgumentException("This account does not have a valid email address.");
            }
            return new PasswordResetDelivery(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getEmail(),
                    temporaryPassword
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to prepare the email password reset.", exception);
        }
    }

    public void completeEmailPasswordReset(PasswordResetDelivery reset, Integer actingUserId) {
        if (reset == null) {
            throw new IllegalArgumentException("Password reset details are required.");
        }
        PasswordSecurity.PasswordRecord record = PasswordSecurity.hash(reset.temporaryPassword());
        try (Connection connection = connect()) {
            if (actingUserId != null && !isActiveSuperAdmin(connection, actingUserId)) {
                throw new SecurityException("Only a super administrator can reset another user's password.");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE users
                    SET password_hash = ?, password_salt = ?, password_iterations = ?,
                        failed_login_count = 0, locked_until = NULL,
                        must_change_password = 1, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'ACTIVE'
                    """)) {
                statement.setString(1, record.hash());
                statement.setString(2, record.salt());
                statement.setInt(3, record.iterations());
                statement.setInt(4, reset.userId());
                if (statement.executeUpdate() == 0) {
                    throw new IllegalArgumentException("The selected account is no longer active.");
                }
            }
            recordAuthEvent(
                    connection,
                    reset.userId(),
                    reset.username(),
                    "PASSWORD_RESET_EMAIL",
                    true,
                    actingUserId == null
                            ? "Password reset requested from the sign-in screen."
                            : "Password reset email sent by user " + actingUserId + "."
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to complete the email password reset.", exception);
        }
    }

    private int userCount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private String normalizeRequestedRole(String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            return SystemUser.ROLE_USER;
        }
        return switch (requestedRole.trim().toUpperCase(Locale.ENGLISH)) {
            case SystemUser.ROLE_SUPER_ADMIN -> SystemUser.ROLE_SUPER_ADMIN;
            case SystemUser.ROLE_ADMIN -> SystemUser.ROLE_ADMIN;
            case SystemUser.ROLE_USER -> SystemUser.ROLE_USER;
            default -> throw new IllegalArgumentException("Invalid user role.");
        };
    }

    private boolean isActiveSuperAdmin(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS(
                    SELECT 1 FROM users
                    WHERE id = ? AND role = 'SUPER_ADMIN' AND status = 'ACTIVE'
                )
                """)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private boolean isLastActiveSuperAdmin(Connection connection, int userId) throws SQLException {
        try (PreparedStatement selected = connection.prepareStatement("SELECT role FROM users WHERE id = ?")) {
            selected.setInt(1, userId);
            try (ResultSet resultSet = selected.executeQuery()) {
                if (!resultSet.next() || !SystemUser.ROLE_SUPER_ADMIN.equals(resultSet.getString(1))) {
                    return false;
                }
            }
        }
        try (PreparedStatement count = connection.prepareStatement("""
                SELECT COUNT(*) FROM users WHERE role = 'SUPER_ADMIN' AND status = 'ACTIVE'
                """ ); ResultSet resultSet = count.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) <= 1;
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original registration failure.
        }
    }

    private void updateFailedLogin(Connection connection, int userId, int attempts, String lockedUntil) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE users SET failed_login_count = ?, locked_until = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """)) {
            statement.setInt(1, attempts);
            statement.setString(2, lockedUntil);
            statement.setInt(3, userId);
            statement.executeUpdate();
        }
    }

    private void recordAuthEvent(Connection connection, Integer userId, String usernameAttempt,
                                 String eventType, boolean success, String details) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO authentication_log (user_id, username_attempt, event_type, success, details)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            if (userId == null) {
                statement.setNull(1, java.sql.Types.INTEGER);
            } else {
                statement.setInt(1, userId);
            }
            statement.setString(2, usernameAttempt);
            statement.setString(3, eventType);
            statement.setInt(4, success ? 1 : 0);
            statement.setString(5, details);
            statement.executeUpdate();
        }
    }

    private SystemUser mapUser(ResultSet resultSet) throws SQLException {
        return new SystemUser(
                resultSet.getInt("id"),
                resultSet.getString("username"),
                resultSet.getString("full_name"),
                resultSet.getString("email"),
                resultSet.getString("role"),
                resultSet.getString("status"),
                resultSet.getString("created_at"),
                resultSet.getString("last_login_at"),
                resultSet.getInt("must_change_password") == 1
        );
    }

    private AuthenticationEventRecord mapAuthenticationEvent(ResultSet resultSet) throws SQLException {
        int rawUserId = resultSet.getInt("user_id");
        Integer eventUserId = resultSet.wasNull() ? null : rawUserId;
        return new AuthenticationEventRecord(
                resultSet.getLong("id"),
                eventUserId,
                resultSet.getString("username"),
                resultSet.getString("event_type"),
                resultSet.getInt("success") == 1,
                resultSet.getString("details"),
                resultSet.getString("created_at")
        );
    }

    private SystemUser findActiveUserByEmailOrUsername(Connection connection, String emailOrUsername) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, username, full_name, email, role, status, created_at, last_login_at, must_change_password
                FROM users
                WHERE status = 'ACTIVE'
                  AND (lower(username) = ? OR lower(COALESCE(email, '')) = ?)
                LIMIT 1
                """)) {
            statement.setString(1, emailOrUsername);
            statement.setString(2, emailOrUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapUser(resultSet);
                }
            }
        }
        throw new IllegalArgumentException("No active account was found for that email or username.");
    }

    private String normalizeUsername(String username) {
        String clean = requireText(username, "Username").toLowerCase(Locale.ENGLISH);
        if (!clean.matches("[a-z0-9._-]{3,40}")) {
            throw new IllegalArgumentException("Username must be 3–40 characters and use only letters, numbers, dot, underscore, or hyphen.");
        }
        return clean;
    }

    private String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private String generateTemporaryPassword() {
        StringBuilder builder = new StringBuilder("Reset@");
        for (int index = 0; index < 12; index++) {
            builder.append(TEMP_PASSWORD_CHARS[RANDOM.nextInt(TEMP_PASSWORD_CHARS.length)]);
        }
        builder.append("7Aa");
        return builder.toString();
    }

    private boolean isEmailLike(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.contains("@") && !clean.startsWith("@") && !clean.endsWith("@");
    }

    private boolean isStillLocked(String lockedUntil) {
        if (lockedUntil == null || lockedUntil.isBlank()) {
            return false;
        }
        try {
            return LocalDateTime.parse(lockedUntil, DB_TIME).isAfter(LocalDateTime.now());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream inputStream = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private boolean isConstraintFailure(SQLException exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase(Locale.ENGLISH).contains("constraint");
    }

}
