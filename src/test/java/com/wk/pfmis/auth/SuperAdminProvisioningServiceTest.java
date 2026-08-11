package com.wk.pfmis.auth;

import com.wk.pfmis.config.AppConfig;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.PasswordSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuperAdminProvisioningServiceTest {
    private static final String ADMIN_EMAIL = "admin@example.invalid";
    private static final String ADMIN_PASSWORD = "BootstrapPass123";

    @TempDir
    Path dataRoot;

    private Path authDatabasePath;
    private Path envFile;
    private AuthDatabase authDatabase;

    @BeforeEach
    void initializeAuthDatabase() throws Exception {
        authDatabasePath = dataRoot.resolve("pfmis-auth.db");
        envFile = dataRoot.resolve(".env");
        writeEnv(ADMIN_EMAIL, ADMIN_PASSWORD, "system@example.invalid");
        System.setProperty("pfmis.auth.db.path", authDatabasePath.toString());
        System.setProperty("pfmis.data.dir", dataRoot.toString());
        System.setProperty("PFMIS_ENV_FILE", envFile.toString());
        AppConfig.reload();
        authDatabase = AuthDatabase.getInstance();
        authDatabase.initialize();
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty("pfmis.auth.db.path");
        System.clearProperty("pfmis.data.dir");
        System.clearProperty("PFMIS_ENV_FILE");
        AppConfig.reload();
    }

    @Test
    void freshInstallationAutoProvisionsDefaultSuperAdministrator() {
        SystemUser user = SuperAdminProvisioningService.getInstance()
                .provisionConfiguredSuperAdministrator()
                .orElseThrow();

        assertEquals("admin", user.getUsername());
        assertEquals(ADMIN_EMAIL, user.getEmail());
        assertEquals(SystemUser.ROLE_SUPER_ADMIN, user.getRole());
        assertEquals(SystemUser.STATUS_ACTIVE, user.getStatus());
        assertTrue(authDatabase.hasActiveSuperAdministrator());
        assertEquals(1, authDatabase.countActiveSuperAdministrators());

        SystemUser authenticated = authDatabase.authenticate(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertTrue(authenticated.isSuperAdmin());
    }

    @Test
    void restartDoesNotDuplicateOrResetExistingSuperAdministrator() throws Exception {
        SuperAdminProvisioningService.getInstance().provisionConfiguredSuperAdministrator();

        writeEnv(ADMIN_EMAIL, "ChangedPass123", "system@example.invalid");
        AppConfig.reload();
        SuperAdminProvisioningService.getInstance().provisionConfiguredSuperAdministrator();

        assertEquals(1, authDatabase.countActiveSuperAdministrators());
        assertTrue(authDatabase.authenticate(ADMIN_EMAIL, ADMIN_PASSWORD).isSuperAdmin());
        assertThrows(IllegalArgumentException.class, () -> authDatabase.authenticate(ADMIN_EMAIL, "ChangedPass123"));
    }

    @Test
    void wrongPasswordIsRejectedAndCorrectPasswordAuthenticates() {
        SuperAdminProvisioningService.getInstance().provisionConfiguredSuperAdministrator();

        assertThrows(IllegalArgumentException.class, () -> authDatabase.authenticate(ADMIN_EMAIL, "WrongPass123"));

        SystemUser user = authDatabase.authenticate(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertEquals(SystemUser.ROLE_SUPER_ADMIN, user.getRole());
        assertTrue(user.isSuperAdmin());
    }

    @Test
    void provisionedPasswordIsStoredAsSecureHash() throws Exception {
        SuperAdminProvisioningService.getInstance().provisionConfiguredSuperAdministrator();

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + authDatabasePath);
             var statement = connection.prepareStatement("""
                     SELECT password_hash, password_salt, password_iterations
                     FROM users
                     WHERE lower(email) = lower(?)
                     """)) {
            statement.setString(1, ADMIN_EMAIL);
            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                String hash = resultSet.getString("password_hash");
                String salt = resultSet.getString("password_salt");
                int iterations = resultSet.getInt("password_iterations");

                assertFalse(hash.isBlank());
                assertFalse(salt.isBlank());
                assertNotEquals(ADMIN_PASSWORD, hash);
                assertTrue(iterations >= PasswordSecurity.DEFAULT_ITERATIONS);
            }
        }
    }

    @Test
    void superAdministratorCanCreateAnotherSuperAdministrator() {
        SystemUser first = SuperAdminProvisioningService.getInstance()
                .provisionConfiguredSuperAdministrator()
                .orElseThrow();

        SystemUser second = authDatabase.registerUserByAdmin(
                "Second Super Administrator",
                "secondsuper",
                "second@example.invalid",
                "SecondPass123",
                SystemUser.ROLE_SUPER_ADMIN,
                first.getId()
        );

        assertEquals(SystemUser.ROLE_SUPER_ADMIN, second.getRole());
        assertEquals(2, authDatabase.countActiveSuperAdministrators());
        assertTrue(authDatabase.authenticate("second@example.invalid", "SecondPass123").isSuperAdmin());
    }

    @Test
    void oneSuperAdministratorCanBeDisabledWhenAnotherActiveSuperAdministratorExists() {
        SystemUser first = SuperAdminProvisioningService.getInstance()
                .provisionConfiguredSuperAdministrator()
                .orElseThrow();
        SystemUser second = authDatabase.registerUserByAdmin(
                "Second Super Administrator",
                "secondsuper",
                "second@example.invalid",
                "SecondPass123",
                SystemUser.ROLE_SUPER_ADMIN,
                first.getId()
        );

        authDatabase.updateUserStatus(second.getId(), SystemUser.STATUS_INACTIVE, first.getId());

        assertEquals(1, authDatabase.countActiveSuperAdministrators());
    }

    @Test
    void signedInSuperAdministratorCannotDeactivateOwnAccount() {
        SystemUser first = SuperAdminProvisioningService.getInstance()
                .provisionConfiguredSuperAdministrator()
                .orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> authDatabase.updateUserStatus(first.getId(), SystemUser.STATUS_INACTIVE, first.getId()));
    }

    @Test
    void missingBootstrapPasswordBlocksFreshProvisioning() throws Exception {
        writeEnv(ADMIN_EMAIL, "", "system@example.invalid");
        AppConfig.reload();

        assertThrows(IllegalStateException.class,
                () -> SuperAdminProvisioningService.getInstance().provisionConfiguredSuperAdministrator());
        assertFalse(authDatabase.hasActiveSuperAdministrator());
    }

    private void writeEnv(String email, String password, String systemEmail) throws Exception {
        Files.writeString(envFile, """
                PFMIS_SUPER_ADMIN_EMAIL=%s
                PFMIS_SUPER_ADMIN_PASSWORD=%s
                PFMIS_SYSTEM_EMAIL=%s
                PFMIS_MAIL_ENABLED=false
                """.formatted(email, password, systemEmail));
    }
}
