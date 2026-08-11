package com.wk.pfmis.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

public record LocalAiConfig(
        boolean enabled,
        String host,
        int port,
        int contextSize,
        Duration startupTimeout,
        Duration healthPollInterval,
        Duration requestTimeout,
        Path localAiDirectory
) {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8080;
    public static final int DEFAULT_CONTEXT_SIZE = 2048;
    public static final int DEFAULT_STARTUP_TIMEOUT_SECONDS = 120;
    public static final int DEFAULT_HEALTH_POLL_MILLISECONDS = 1000;
    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 10;
    public static final String DEFAULT_ENDPOINT = "http://" + DEFAULT_HOST + ":" + DEFAULT_PORT;

    public LocalAiConfig {
        host = host == null || host.isBlank() ? DEFAULT_HOST : host.trim();
        port = port < 1 || port > 65_535 ? DEFAULT_PORT : port;
        contextSize = contextSize < 512 || contextSize > 131_072 ? DEFAULT_CONTEXT_SIZE : contextSize;
        startupTimeout = startupTimeout == null || startupTimeout.isNegative() || startupTimeout.isZero()
                ? Duration.ofSeconds(DEFAULT_STARTUP_TIMEOUT_SECONDS)
                : startupTimeout;
        healthPollInterval = healthPollInterval == null || healthPollInterval.isNegative() || healthPollInterval.isZero()
                ? Duration.ofMillis(DEFAULT_HEALTH_POLL_MILLISECONDS)
                : healthPollInterval;
        requestTimeout = requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                ? Duration.ofSeconds(DEFAULT_REQUEST_TIMEOUT_SECONDS)
                : requestTimeout;
        localAiDirectory = localAiDirectory == null ? null : localAiDirectory.toAbsolutePath().normalize();
    }

    public static LocalAiConfig fromConfig() {
        String directory = AppConfig.get("PFMIS_LOCAL_AI_DIR", "");
        return new LocalAiConfig(
                AppConfig.getBoolean("PFMIS_LOCAL_AI_ENABLED", true),
                AppConfig.get("PFMIS_LOCAL_AI_HOST", DEFAULT_HOST),
                AppConfig.getInt("PFMIS_LOCAL_AI_PORT", DEFAULT_PORT, 1, 65_535),
                AppConfig.getInt("PFMIS_LOCAL_AI_CONTEXT_SIZE", DEFAULT_CONTEXT_SIZE, 512, 131_072),
                AppConfig.getDurationSeconds("PFMIS_LOCAL_AI_STARTUP_TIMEOUT_SECONDS", DEFAULT_STARTUP_TIMEOUT_SECONDS, 5, 600),
                AppConfig.getDurationMillis("PFMIS_LOCAL_AI_HEALTH_POLL_MILLISECONDS", DEFAULT_HEALTH_POLL_MILLISECONDS, 100, 10_000),
                AppConfig.getDurationSeconds("PFMIS_LOCAL_AI_REQUEST_TIMEOUT_SECONDS", DEFAULT_REQUEST_TIMEOUT_SECONDS, 1, 120),
                directory.isBlank() ? null : Path.of(directory)
        );
    }

    public URI endpoint() {
        return URI.create("http://" + host + ":" + port);
    }
}
