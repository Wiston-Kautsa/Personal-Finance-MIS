package com.wk.pfmis.config;

import com.wk.pfmis.fx.ExchangeRateProviderType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearProperties() {
        System.clearProperty("PFMIS_ENV_FILE");
        System.clearProperty("PFMIS_FX_ENABLED");
        System.clearProperty("PFMIS_FX_REFRESH_MINUTES");
        System.clearProperty("PFMIS_FX_PROVIDER");
        System.clearProperty("PFMIS_MAIL_ENABLED");
        System.clearProperty("PFMIS_LOG_LEVEL");
        AppConfig.reload();
    }

    @Test
    void readsExplicitEnvFileAndAppliesTypedDefaults() throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                PFMIS_FX_ENABLED=false
                PFMIS_FX_REFRESH_MINUTES=120
                PFMIS_FX_PROVIDER=FRANKFURTER
                PFMIS_MAIL_ENABLED=false
                PFMIS_LOG_LEVEL=WARN
                """);
        System.setProperty("PFMIS_ENV_FILE", envFile.toString());
        AppConfig.reload();

        FxConfig fxConfig = AppConfig.fxConfig();

        assertFalse(fxConfig.enabled());
        assertEquals(120, fxConfig.refreshMinutes());
        assertEquals(ExchangeRateProviderType.FRANKFURTER, fxConfig.provider());
        assertFalse(AppConfig.mailConfig().enabled());
        assertEquals(LoggingConfig.LogLevel.WARN, AppConfig.loggingConfig().level());
    }

    @Test
    void invalidConfigurationFallsBackSafely() {
        System.setProperty("PFMIS_FX_REFRESH_MINUTES", "-10");
        System.setProperty("PFMIS_FX_PROVIDER", "not-real");
        System.setProperty("PFMIS_FX_ENABLED", "not-a-boolean");
        System.setProperty("PFMIS_LOG_LEVEL", "NOPE");

        FxConfig fxConfig = AppConfig.fxConfig();

        assertTrue(fxConfig.enabled());
        assertEquals(360, fxConfig.refreshMinutes());
        assertEquals(ExchangeRateProviderType.FRANKFURTER, fxConfig.provider());
        assertFalse(fxConfig.hasFallbackProvider());
        assertEquals(LoggingConfig.LogLevel.INFO, AppConfig.loggingConfig().level());
    }

    @Test
    void systemPropertiesCanOverrideEnvFileForTestsAndLocalRuns() throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "PFMIS_FX_ENABLED=false\n");
        System.setProperty("PFMIS_ENV_FILE", envFile.toString());
        System.setProperty("PFMIS_FX_ENABLED", "true");
        AppConfig.reload();

        assertTrue(AppConfig.fxConfig().enabled());
    }

    @Test
    void parsesFxMailAndLoggingTypedValues() {
        System.setProperty("PFMIS_FX_REQUEST_TIMEOUT_SECONDS", "9");
        System.setProperty("PFMIS_FX_CONNECT_TIMEOUT_SECONDS", "4");
        System.setProperty("PFMIS_FX_MAX_RETRIES", "2");
        System.setProperty("PFMIS_FX_STALE_AFTER_HOURS", "12");
        System.setProperty("PFMIS_MAIL_ENABLED", "true");
        System.setProperty("PFMIS_MAIL_CONNECT_TIMEOUT_SECONDS", "7");
        System.setProperty("PFMIS_LOG_LEVEL", "DEBUG");

        FxConfig fxConfig = AppConfig.fxConfig();

        assertEquals(Duration.ofSeconds(9), fxConfig.requestTimeout());
        assertEquals(Duration.ofSeconds(4), fxConfig.connectTimeout());
        assertEquals(2, fxConfig.maxRetries());
        assertEquals(12, fxConfig.staleAfterHours());
        assertTrue(AppConfig.mailConfig().enabled());
        assertEquals(Duration.ofSeconds(7), AppConfig.mailConfig().connectTimeout());
        assertEquals(LoggingConfig.LogLevel.DEBUG, AppConfig.loggingConfig().level());
    }
}
