package com.wk.pfmis.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

public final class StartupDiagnostics {
    private static final Object LOCK = new Object();
    private static final String APP_VERSION = "1.0.0";

    private StartupDiagnostics() {
    }

    public static Path logDirectory() {
        Path directory = applicationDataDirectory().resolve("logs").toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
        } catch (Exception ignored) {
            // Logging must not prevent application startup.
        }
        return directory;
    }

    public static Path startupLogPath() {
        return logDirectory().resolve("startup.log").toAbsolutePath().normalize();
    }

    public static Path localAiLogPath() {
        return logDirectory().resolve("local-ai.log").toAbsolutePath().normalize();
    }

    public static void cleanupOldLogs(int retentionDays) {
        int days = Math.max(1, retentionDays);
        Path directory = logDirectory();
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        try (Stream<Path> paths = Files.list(directory)) {
            paths
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .filter(path -> path.startsWith(directory))
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".log"))
                    .filter(path -> isOlderThan(path, cutoff))
                    .forEach(StartupDiagnostics::deleteLogQuietly);
        } catch (Exception ignored) {
            // Diagnostic log cleanup must not prevent startup.
        }
    }

    public static void logStage(String stage) {
        append("STAGE", safe(stage), null);
    }

    public static void logFailure(String stage, Throwable throwable) {
        append("ERROR", safe(stage), throwable);
    }

    private static void append(String level, String stage, Throwable throwable) {
        synchronized (LOCK) {
            try {
                StringBuilder builder = new StringBuilder();
                builder.append("timestamp=").append(LocalDateTime.now()).append(System.lineSeparator());
                builder.append("level=").append(level).append(System.lineSeparator());
                builder.append("applicationVersion=").append(applicationVersion()).append(System.lineSeparator());
                builder.append("os=").append(System.getProperty("os.name", "unknown")).append(System.lineSeparator());
                builder.append("architecture=").append(System.getProperty("os.arch", "unknown")).append(System.lineSeparator());
                builder.append("javaRuntime=").append(System.getProperty("java.runtime.version", "unknown")).append(System.lineSeparator());
                builder.append("stage=").append(stage).append(System.lineSeparator());
                if (throwable != null) {
                    builder.append("exceptionType=").append(throwable.getClass().getName()).append(System.lineSeparator());
                    builder.append("stackTrace=").append(stackTrace(throwable)).append(System.lineSeparator());
                }
                builder.append(System.lineSeparator());
                Files.writeString(
                        startupLogPath(),
                        builder.toString(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } catch (Exception ignored) {
                // Startup diagnostics are best-effort and must not introduce a second failure.
            }
        }
    }

    private static Path applicationDataDirectory() {
        String explicitDataDirectory = System.getProperty("pfmis.data.dir", "").trim();
        if (!explicitDataDirectory.isBlank()) {
            return Path.of(explicitDataDirectory).toAbsolutePath().normalize();
        }
        String userHome = System.getProperty("user.home", ".");
        String osName = System.getProperty("os.name", "").toLowerCase();
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
        return Path.of(userHome, ".local", "share", "PFMIS").toAbsolutePath().normalize();
    }

    private static String applicationVersion() {
        Package appPackage = StartupDiagnostics.class.getPackage();
        String implementationVersion = appPackage == null ? null : appPackage.getImplementationVersion();
        return safe(implementationVersion).isBlank() ? APP_VERSION : implementationVersion;
    }

    private static boolean isOlderThan(Path path, Instant cutoff) {
        try {
            return Files.isRegularFile(path) && Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void deleteLogQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
