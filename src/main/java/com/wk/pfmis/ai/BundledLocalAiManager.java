package com.wk.pfmis.ai;

import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.utils.StartupDiagnostics;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class BundledLocalAiManager {
    public static final String HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8080;
    public static final int PORT = DEFAULT_PORT;
    public static final String MODEL_ALIAS = AiSettings.BUNDLED_LOCAL_MODEL;
    public static final String ENDPOINT = AiSettings.BUNDLED_LOCAL_ENDPOINT;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration START_TIMEOUT = Duration.ofMinutes(3);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    private static volatile Process serverProcess;
    private static volatile String currentEndpoint;
    private static volatile String currentApiKey;

    private BundledLocalAiManager() {
    }

    public static synchronized String ensureReady() {
        ensureAgentConfig();
        if (isReady()) {
            return "PFMIS Local AI ready.";
        }
        resetManagedEndpointIfProcessStopped();
        Path serverExecutable = serverExecutable();
        Path modelFile = modelFile();
        if (!Files.isRegularFile(serverExecutable)) {
            throw new IllegalStateException("PFMIS Local AI runtime is missing: " + serverExecutable);
        }
        if (!Files.isRegularFile(modelFile)) {
            throw new IllegalStateException("PFMIS Local AI model is missing: " + modelFile);
        }
        if (serverProcess == null || !serverProcess.isAlive()) {
            startServer(serverExecutable, modelFile);
        }
        waitUntilReady();
        return "PFMIS Local AI ready.";
    }

    public static boolean isReady() {
        return serverProcess != null
                && serverProcess.isAlive()
                && "ok".equalsIgnoreCase(healthStatus());
    }

    public static String healthStatus() {
        if (serverProcess == null || !serverProcess.isAlive()) {
            resetManagedEndpoint();
            return "not available";
        }
        if (currentEndpoint == null || currentApiKey == null || currentApiKey.isBlank()) {
            return "not available";
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(healthUri())
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + currentApiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "not available";
            }
            String status = firstJsonStringValue(response.body(), "status");
            return status.isBlank() ? "not available" : status;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "not available";
        }
    }

    public static synchronized String restart() {
        shutdown();
        return ensureReady();
    }

    public static synchronized void shutdown() {
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            try {
                if (!serverProcess.waitFor(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)) {
                    serverProcess.destroyForcibly();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping PFMIS Local AI.", exception);
            }
        }
        serverProcess = null;
        resetManagedEndpoint();
    }

    public static List<String> modelAliases() {
        return List.of(MODEL_ALIAS);
    }

    public static Path localAiDirectory() {
        return applicationDirectory().resolve("local-ai").toAbsolutePath().normalize();
    }

    public static Path applicationDirectory() {
        try {
            Path codeSource = Path.of(BundledLocalAiManager.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(codeSource)) {
                return codeSource.getParent();
            }
            if (Files.isDirectory(codeSource)
                    && "classes".equalsIgnoreCase(codeSource.getFileName().toString())
                    && codeSource.getParent() != null
                    && "target".equalsIgnoreCase(codeSource.getParent().getFileName().toString())
                    && codeSource.getParent().getParent() != null) {
                return codeSource.getParent().getParent();
            }
            if (Files.isDirectory(codeSource)) {
                return codeSource;
            }
        } catch (Exception ignored) {
            // Fall back to the launcher working directory for development and simple installers.
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    public static Path serverExecutable() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        String executableName = osName.contains("win") ? "llama-server.exe" : "llama-server";
        return localAiDirectory().resolve("runtime").resolve(executableName);
    }

    public static Path modelFile() {
        return localAiDirectory().resolve("models").resolve("pfmis-model.gguf");
    }

    public static Path agentConfigFile() {
        return localAiDirectory().resolve("agents").resolve("pfmis-finance-agent.json");
    }

    public static Path logDirectory() {
        return StartupDiagnostics.logDirectory();
    }

    public static Path logFile() {
        return StartupDiagnostics.localAiLogPath();
    }

    public static URI healthUri() {
        return URI.create(endpoint() + "/health");
    }

    public static URI chatCompletionsUri() {
        return URI.create(endpoint() + "/v1/chat/completions");
    }

    public static String endpoint() {
        return currentEndpoint == null || currentEndpoint.isBlank() ? ENDPOINT : currentEndpoint;
    }

    public static String apiKey() {
        return currentApiKey == null ? "" : currentApiKey;
    }

    private static void startServer(Path serverExecutable, Path modelFile) {
        try {
            Files.createDirectories(logDirectory());
            int port = availableLoopbackPort();
            String apiKey = randomApiKey();
            String endpoint = "http://" + HOST + ":" + port;
            ProcessBuilder processBuilder = new ProcessBuilder(
                    serverExecutable.toString(),
                    "-m",
                    modelFile.toString(),
                    "-c",
                    "2048",
                    "-np",
                    "1",
                    "--host",
                    HOST,
                    "--port",
                    String.valueOf(port),
                    "--api-key",
                    apiKey,
                    "--sleep-idle-seconds",
                    "300"
            );
            processBuilder.directory(serverExecutable.getParent().toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile().toFile()));
            Process process = processBuilder.start();
            currentEndpoint = endpoint;
            currentApiKey = apiKey;
            serverProcess = process;
        } catch (IOException exception) {
            resetManagedEndpoint();
            throw new IllegalStateException("Failed to start PFMIS Local AI.", exception);
        }
    }

    private static void waitUntilReady() {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (isReady()) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while starting PFMIS Local AI.", exception);
            }
        }
        throw new IllegalStateException("PFMIS Local AI did not become ready within " + START_TIMEOUT.toSeconds() + " seconds.");
    }

    private static int availableLoopbackPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        }
    }

    private static String randomApiKey() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private static void resetManagedEndpointIfProcessStopped() {
        if (serverProcess == null || !serverProcess.isAlive()) {
            resetManagedEndpoint();
        }
    }

    private static void resetManagedEndpoint() {
        currentEndpoint = null;
        currentApiKey = null;
    }

    private static void ensureAgentConfig() {
        Path agentConfig = agentConfigFile();
        if (Files.exists(agentConfig)) {
            return;
        }
        try {
            Files.createDirectories(agentConfig.getParent());
            Files.writeString(agentConfig, defaultAgentConfig(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create PFMIS Local AI configuration.", exception);
        }
    }

    private static String firstJsonStringValue(String json, String key) {
        if (json == null || json.isBlank()) {
            return "";
        }
        String compact = json.replaceAll("\\s+", "").toLowerCase(Locale.ENGLISH);
        String needle = "\"" + key.toLowerCase(Locale.ENGLISH) + "\":\"";
        int start = compact.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int valueEnd = compact.indexOf('"', valueStart);
        return valueEnd < 0 ? "" : compact.substring(valueStart, valueEnd);
    }

    private static String defaultAgentConfig() {
        return """
                {
                  "name": "PFMIS Smart Analysis",
                  "currency": "MWK",
                  "instructions": [
                    "Provide practical personal-finance recommendations.",
                    "Use only financial figures supplied by PFMIS.",
                    "Do not invent income, expenses or balances.",
                    "Clearly state when available data is insufficient.",
                    "Keep recommendations concise and actionable."
                  ],
                  "allowedFunctions": [
                    "getMonthlySummary",
                    "getGoalProgress",
                    "getCategorySpending",
                    "getHistoricalAverage",
                    "calculateRequiredMonthlySaving"
                  ]
                }
                """;
    }
}
