package com.wk.pfmis.auth;

import com.wk.pfmis.models.SystemUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthDatabaseBootstrapTest {
    @TempDir
    Path dataRoot;

    private AuthDatabase authDatabase;

    @BeforeEach
    void initializeAuthDatabase() {
        System.setProperty("pfmis.auth.db.path", dataRoot.resolve("pfmis-auth.db").toString());
        System.setProperty("pfmis.data.dir", dataRoot.toString());
        authDatabase = AuthDatabase.getInstance();
        authDatabase.initialize();
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty("pfmis.auth.db.path");
        System.clearProperty("pfmis.data.dir");
    }

    @Test
    void noAdministratorExistsReportsBootstrapRequired() {
        assertFalse(authDatabase.hasActiveSuperAdministrator());
    }

    @Test
    void firstAdministratorCreationPersistsActiveSuperAdministrator() {
        SystemUser user = authDatabase.registerUser(
                "PFMIS Administrator",
                "pfmisadmin",
                "admin@example.invalid",
                "ValidPass123"
        );

        assertEquals(SystemUser.ROLE_SUPER_ADMIN, user.getRole());
        assertEquals(SystemUser.STATUS_ACTIVE, user.getStatus());
        assertTrue(authDatabase.hasActiveSuperAdministrator());
        assertTrue(authDatabase.usernameExists("pfmisadmin"));
        assertTrue(authDatabase.emailExists("admin@example.invalid"));
    }

    @Test
    void wrongPasswordIsRejectedAndCorrectPasswordAuthenticates() {
        authDatabase.registerUser(
                "PFMIS Administrator",
                "pfmisadmin",
                "admin@example.invalid",
                "ValidPass123"
        );

        assertThrows(IllegalArgumentException.class, () -> authDatabase.authenticate("pfmisadmin", "WrongPass123"));

        SystemUser user = authDatabase.authenticate("pfmisadmin", "ValidPass123");

        assertEquals(SystemUser.ROLE_SUPER_ADMIN, user.getRole());
        assertTrue(user.isSuperAdmin());
    }

    @Test
    void restartKeepsBootstrapClosedAfterAdministratorExists() {
        authDatabase.registerUser(
                "PFMIS Administrator",
                "pfmisadmin",
                "admin@example.invalid",
                "ValidPass123"
        );

        AuthDatabase restarted = AuthDatabase.getInstance();
        restarted.initialize();

        assertTrue(restarted.hasActiveSuperAdministrator());
        assertThrows(SecurityException.class, () -> restarted.registerUser(
                "Second Administrator",
                "secondadmin",
                "second@example.invalid",
                "ValidPass123"
        ));
    }
}
