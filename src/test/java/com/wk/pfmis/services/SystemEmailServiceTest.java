package com.wk.pfmis.services;

import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.auth.SuperAdminProvisioningService;
import com.wk.pfmis.config.AppConfig;
import com.wk.pfmis.mail.EmailService;
import com.wk.pfmis.models.EmailSettings;
import com.wk.pfmis.models.SystemUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemEmailServiceTest {
    @TempDir
    Path dataRoot;

    private Path envFile;
    private AuthDatabase authDatabase;
    private SystemEmailService systemEmailService;

    @BeforeEach
    void initialize() throws Exception {
        envFile = dataRoot.resolve(".env");
        Files.writeString(envFile, """
                PFMIS_SUPER_ADMIN_EMAIL=admin@example.invalid
                PFMIS_SUPER_ADMIN_PASSWORD=BootstrapPass123
                PFMIS_SYSTEM_EMAIL=system@example.invalid
                PFMIS_MAIL_ENABLED=true
                PFMIS_EMAIL_FROM_NAME=PFMIS
                PFMIS_EMAIL_REPLY_TO=reply@example.invalid
                PFMIS_SMTP_HOST=smtp.example.invalid
                PFMIS_SMTP_PORT=587
                PFMIS_SMTP_USERNAME=system@example.invalid
                PFMIS_SMTP_PASSWORD=SmtpPass123
                PFMIS_SMTP_AUTH=true
                PFMIS_SMTP_STARTTLS=true
                """);
        System.setProperty("pfmis.auth.db.path", dataRoot.resolve("pfmis-auth.db").toString());
        System.setProperty("pfmis.data.dir", dataRoot.toString());
        System.setProperty("PFMIS_ENV_FILE", envFile.toString());
        AppConfig.reload();
        authDatabase = AuthDatabase.getInstance();
        authDatabase.initialize();
        systemEmailService = SystemEmailService.getInstance();
        systemEmailService.initializeDefaultsFromEnvironment();
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty("pfmis.auth.db.path");
        System.clearProperty("pfmis.data.dir");
        System.clearProperty("PFMIS_ENV_FILE");
        AppConfig.reload();
    }

    @Test
    void defaultSystemEmailComesFromEnvironmentBackedSettings() {
        EmailSettings settings = systemEmailService.currentSettings();

        assertEquals("system@example.invalid", settings.systemEmail());
        assertEquals("reply@example.invalid", settings.effectiveReplyTo());
        assertEquals("system@example.invalid", settings.effectiveSmtpUsername());
    }

    @Test
    void changingSystemEmailDoesNotChangeSuperAdministratorLoginEmail() {
        SystemUser superAdmin = SuperAdminProvisioningService.getInstance()
                .provisionConfiguredSuperAdministrator()
                .orElseThrow();
        EmailSettings current = systemEmailService.currentSettings();
        EmailSettings changed = new EmailSettings(
                current.enabled(),
                "pfmis-system@example.invalid",
                current.fromName(),
                "pfmis-reply@example.invalid",
                current.smtpHost(),
                current.smtpPort(),
                current.effectiveSmtpUsername(),
                current.smtpPassword(),
                current.smtpAuth(),
                current.smtpStartTls(),
                current.smtpSsl(),
                current.imapHost(),
                current.imapPort(),
                current.imapSsl(),
                current.connectTimeout(),
                current.readTimeout(),
                current.writeTimeout()
        );

        systemEmailService.saveSettings(changed, true, superAdmin.getId());

        assertEquals("pfmis-system@example.invalid", systemEmailService.currentSettings().systemEmail());
        assertEquals("admin@example.invalid", authDatabase.authenticate("admin@example.invalid", "BootstrapPass123").getEmail());
    }

    @Test
    void passwordResetMessageUsesConfiguredSystemEmailAsSender() throws Exception {
        EmailSettings settings = systemEmailService.currentSettings();
        Method method = EmailService.class.getDeclaredMethod(
                "buildMessage",
                EmailSettings.class,
                String.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        String message = (String) method.invoke(
                EmailService.getInstance(),
                settings,
                "user@example.invalid",
                "PFMIS password reset",
                "Body"
        );

        assertTrue(message.contains("From: PFMIS <system@example.invalid>"));
        assertTrue(message.contains("Reply-To: reply@example.invalid"));
        assertTrue(message.contains("To: user@example.invalid"));
    }
}
