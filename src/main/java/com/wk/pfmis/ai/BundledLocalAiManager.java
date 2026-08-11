package com.wk.pfmis.ai;

import com.wk.pfmis.config.AppConfig;
import com.wk.pfmis.config.LocalAiConfig;
import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.utils.StartupDiagnostics;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class BundledLocalAiManager {
    public static final String MODEL_ALIAS = AiSettings.BUNDLED_LOCAL_MODEL;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RECENT_LOG_BYTES = 24_000;
    private static final List<String> WINDOWS_REQUIRED_DLLS = List.of(
            "llama-server-impl.dll",
            "llama-common.dll",
            "llama.dll",
            "ggml.dll",
            "ggml-base.dll",
            "libomp140.x86_64.dll"
    );

    private static volatile Process serverProcess;
    private static volatile String currentApiKey;
    private static volatile LocalAiStatusSnapshot lastStatus = stoppedSnapshot(config());
    private static volatile LocalAiConfig configOverride;
    private static volatile ProcessLauncher processLauncher = ProcessBuilder::start;
    private static volatile boolean skipPortPreflightForTests;
    private static volatile long currentStartupLogOffset;

    private BundledLocalAiManager() {
    }

    public static synchronized String ensureReady() {
        ensureAgentConfig();
        LocalAiStatusSnapshot readyStatus = ensureReadyStatus();
        return readyStatus.summary();
    }

    public static synchronized LocalAiStatusSnapshot ensureReadyStatus() {
        LocalAiConfig config = config();
        if (!config.enabled()) {
            return setStatus(snapshot(LocalAiStatus.DISABLED, "PFMIS Local AI is disabled.", "", false, false, false, false, false, false, null));
        }
        LocalAiStatusSnapshot install = validateInstallation(config);
        if (!install.runtimeInstalled() || !install.modelInstalled()) {
            setStatus(install);
            throw new LocalAiException(install.status(), install.summary());
        }
        if (isReady()) {
            return setStatus(readySnapshot(config, true));
        }
        resetManagedEndpointIfProcessStopped();
        if (serverProcess == null || !serverProcess.isAlive()) {
            if (!skipPortPreflightForTests && isPortListening(config.host(), config.port())) {
                LocalAiStatusSnapshot conflict = snapshot(
                        LocalAiStatus.PORT_CONFLICT,
                        "Local AI could not start because its network port is already in use.",
                        "Port " + config.port() + " on " + config.host() + " is already listening.",
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        null
                );
                setStatus(conflict);
                log("PORT_CONFLICT", conflict.detail());
                throw new LocalAiException(LocalAiStatus.PORT_CONFLICT, conflict.summary());
            }
            startServer(config, serverExecutable(config), modelFile(config));
        }
        return waitUntilReady(config);
    }

    public static synchronized String restart() {
        shutdown();
        LocalAiStatusSnapshot status = ensureReadyStatus();
        return status.summary();
    }

    public static synchronized String testInference() {
        LocalAiStatusSnapshot status = ensureReadyStatus();
        try {
            HttpRequest request = authorized(HttpRequest.newBuilder(chatCompletionsUri())
                            .timeout(config().requestTimeout())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(testInferenceBody())),
                    currentApiKey
            ).build();
            HttpResponse<String> response = client(config()).send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LocalAiException(LocalAiStatus.ERROR, "Local AI inference failed. Open AI Logs for details.");
            }
            String content = firstJsonStringValue(response.body(), "content");
            if (content.isBlank()) {
                throw new LocalAiException(LocalAiStatus.ERROR, "Local AI responded, but the inference response was not readable.");
            }
            LocalAiStatusSnapshot ready = setStatus(new LocalAiStatusSnapshot(
                    LocalAiStatus.READY,
                    "PFMIS Local AI test successful.",
                    "Runtime: Available\nModel: Available\nService: Running\nInference: Successful",
                    true,
                    true,
                    true,
                    serverProcess != null && serverProcess.isAlive(),
                    true,
                    true,
                    config().host(),
                    config().port(),
                    endpoint(),
                    processId(),
                    serverExecutable(config()),
                    modelFile(config()),
                    logFile()
            ));
            log("INFERENCE_READY", ready.summary());
            return ready.displayText();
        } catch (IOException exception) {
            LocalAiException local = localAiFailure(exception);
            setStatus(snapshot(local.status(), local.userMessage(), "", true, true, true, processAlive(), false, false, processId()));
            throw local;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LocalAiException(LocalAiStatus.ERROR, "Local AI test was interrupted.", exception);
        }
    }

    public static synchronized void shutdown() {
        Process process = serverProcess;
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(Duration.ofSeconds(3).toMillis(), TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new LocalAiException(LocalAiStatus.ERROR, "Interrupted while stopping PFMIS Local AI.", exception);
            }
        }
        serverProcess = null;
        resetManagedEndpoint();
        setStatus(stoppedSnapshot(config()));
        log("STOPPED", "PFMIS-managed Local AI process stopped.");
    }

    public static boolean isReady() {
        if (!processAlive() || currentApiKey == null || currentApiKey.isBlank()) {
            return false;
        }
        HealthCheck health = checkHealth(config());
        return health.ready();
    }

    public static boolean isServerHealthy() {
        return isReady();
    }

    public static String healthStatus() {
        LocalAiStatusSnapshot status = status();
        return status.ready() ? "ok" : status.status().displayName();
    }

    public static LocalAiStatusSnapshot status() {
        return status(null);
    }

    public static LocalAiStatusSnapshot status(AiSettings settings) {
        LocalAiConfig config = config();
        if (settings != null && (!settings.isEnabled() || !settings.isBundledLocalProvider())) {
            return snapshot(LocalAiStatus.DISABLED, "PFMIS Local AI is disabled.", "", false, false, false, false, false, false, null);
        }
        if (!config.enabled()) {
            return snapshot(LocalAiStatus.DISABLED, "PFMIS Local AI is disabled.", "", false, false, false, false, false, false, null);
        }
        LocalAiStatusSnapshot install = validateInstallation(config);
        if (!install.runtimeInstalled() || !install.modelInstalled()) {
            return setStatus(install);
        }
        if (processAlive()) {
            HealthCheck health = checkHealth(config);
            if (health.ready()) {
                return setStatus(readySnapshot(config, false));
            }
            LocalAiStatus state = health.loading() ? LocalAiStatus.LOADING_MODEL : LocalAiStatus.WAITING_FOR_SERVER;
            return setStatus(snapshot(state, health.summary(), health.detail(), true, true, true, true, false, false, processId()));
        }
        resetManagedEndpointIfProcessStopped();
        return setStatus(stoppedSnapshot(config));
    }

    public static String userMessage(Throwable throwable) {
        if (throwable instanceof LocalAiException localAiException) {
            return localAiException.userMessage();
        }
        Throwable root = rootCause(throwable);
        String message = root.getMessage() == null ? "" : root.getMessage().toLowerCase(Locale.ENGLISH);
        if (root instanceof ConnectException || message.contains("connection refused") || message.contains("getsockopt")) {
            return "Unable to connect to PFMIS Local AI. The Local AI service is not running or has not finished starting.";
        }
        if (root instanceof HttpTimeoutException || message.contains("timed out") || message.contains("timeout")) {
            return "Local AI is still loading or did not respond in time. Please wait, then try again.";
        }
        if (message.contains("model")) {
            return "Local AI model could not be loaded. Open AI Logs for details.";
        }
        return root.getMessage() == null || root.getMessage().isBlank()
                ? "Local AI could not start. Open AI Logs for details."
                : root.getMessage();
    }

    public static List<String> modelAliases() {
        return List.of(MODEL_ALIAS);
    }

    public static Path localAiDirectory() {
        LocalAiConfig config = config();
        if (config.localAiDirectory() != null) {
            return config.localAiDirectory();
        }
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
        return serverExecutable(config());
    }

    public static Path modelFile() {
        return modelFile(config());
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
        return endpoint().resolve("/health");
    }

    public static URI chatCompletionsUri() {
        return endpoint().resolve("/v1/chat/completions");
    }

    public static URI endpoint() {
        return config().endpoint();
    }

    public static String apiKey() {
        return currentApiKey == null ? "" : currentApiKey;
    }

    private static void startServer(LocalAiConfig config, Path serverExecutable, Path modelFile) {
        try {
            Files.createDirectories(logDirectory());
            String apiKey = randomApiKey();
            List<String> command = List.of(
                    serverExecutable.toString(),
                    "-m",
                    modelFile.toString(),
                    "-c",
                    String.valueOf(config.contextSize()),
                    "-np",
                    "1",
                    "--host",
                    config.host(),
                    "--port",
                    String.valueOf(config.port()),
                    "--api-key",
                    apiKey,
                    "--sleep-idle-seconds",
                    "300"
            );
            currentStartupLogOffset = logSize();
            logStartup(config, serverExecutable, modelFile, command);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(serverExecutable.getParent().toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile().toFile()));
            setStatus(snapshot(LocalAiStatus.STARTING, "Local AI is starting.", "Launching llama-server.", true, true, true, false, false, false, null));
            Process process = processLauncher.start(processBuilder);
            currentApiKey = apiKey;
            serverProcess = process;
            setStatus(snapshot(LocalAiStatus.LOADING_MODEL, "Loading the local AI model. This may take a moment.", "", true, true, true, true, false, false, process.pid()));
            log("PROCESS_STARTED", "pid=" + process.pid());
        } catch (IOException exception) {
            resetManagedEndpoint();
            LocalAiException local = localAiFailure(exception);
            setStatus(snapshot(local.status(), local.userMessage(), "", true, true, true, false, false, false, null));
            throw local;
        }
    }

    private static LocalAiStatusSnapshot waitUntilReady(LocalAiConfig config) {
        long deadline = System.nanoTime() + config.startupTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            Process process = serverProcess;
            if (process == null || !process.isAlive()) {
                LocalAiException failure = processExitFailure(process);
                setStatus(snapshot(failure.status(), failure.userMessage(), recentLogText(), true, true, true, false, false, false, processId(process)));
                throw failure;
            }
            HealthCheck health = checkHealth(config);
            if (health.ready()) {
                LocalAiStatusSnapshot ready = readySnapshot(config, false);
                setStatus(ready);
                log("HTTP_READY", "PFMIS Local AI health check passed at " + healthUri() + ".");
                return ready;
            }
            LocalAiStatus state = health.loading() ? LocalAiStatus.LOADING_MODEL : LocalAiStatus.WAITING_FOR_SERVER;
            setStatus(snapshot(state, health.summary(), health.detail(), true, true, true, true, false, false, process.pid()));
            sleep(config.healthPollInterval());
        }
        LocalAiStatusSnapshot timeout = snapshot(
                LocalAiStatus.STARTUP_TIMEOUT,
                "Local AI did not become ready in time.",
                "Health check did not pass within " + config.startupTimeout().toSeconds() + " seconds.",
                true,
                true,
                true,
                processAlive(),
                false,
                false,
                processId()
        );
        setStatus(timeout);
        log("STARTUP_TIMEOUT", timeout.detail());
        throw new LocalAiException(LocalAiStatus.STARTUP_TIMEOUT, timeout.summary());
    }

    private static HealthCheck checkHealth(LocalAiConfig config) {
        if (currentApiKey == null || currentApiKey.isBlank()) {
            return new HealthCheck(false, false, "Local AI service is stopped.", "");
        }
        try {
            HttpRequest request = authorized(HttpRequest.newBuilder(healthUri())
                            .timeout(config.requestTimeout())
                            .GET(),
                    currentApiKey
            ).build();
            HttpResponse<String> response = client(config).send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body() == null ? "" : response.body();
            if (response.statusCode() >= 200 && response.statusCode() < 300 && healthyBody(body)) {
                return new HealthCheck(true, false, "Local AI is available and ready to use.", body);
            }
            boolean loading = body.toLowerCase(Locale.ENGLISH).contains("loading");
            return new HealthCheck(false, loading, loading ? "Loading the local AI model." : "Waiting for the Local AI HTTP service.", body);
        } catch (IOException exception) {
            return new HealthCheck(false, false, "Waiting for the Local AI HTTP service.", userMessage(exception));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new HealthCheck(false, false, "Local AI health check was interrupted.", "");
        }
    }

    private static LocalAiStatusSnapshot validateInstallation(LocalAiConfig config) {
        Path serverExecutable = serverExecutable(config);
        Path modelFile = modelFile(config);
        boolean runtimeOk = Files.isRegularFile(serverExecutable) && Files.isReadable(serverExecutable) && requiredDllsPresent(serverExecutable.getParent());
        boolean modelOk = validModelFile(modelFile);
        if (!runtimeOk && !modelOk) {
            return snapshot(LocalAiStatus.NOT_INSTALLED, "The Local AI runtime or model is missing.", installDetail(serverExecutable, modelFile), true, false, false, false, false, false, null);
        }
        if (!runtimeOk) {
            return snapshot(LocalAiStatus.RUNTIME_MISSING, "Local AI runtime is incomplete.", installDetail(serverExecutable, modelFile), true, false, modelOk, false, false, false, null);
        }
        if (!modelOk) {
            return snapshot(LocalAiStatus.MODEL_MISSING, "Local AI model is not installed.", installDetail(serverExecutable, modelFile), true, true, false, false, false, false, null);
        }
        return snapshot(LocalAiStatus.STOPPED, "Local AI service is stopped.", "", true, true, true, false, false, false, null);
    }

    private static boolean requiredDllsPresent(Path runtimeDirectory) {
        if (runtimeDirectory == null) {
            return false;
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        if (!osName.contains("win")) {
            return true;
        }
        for (String dll : WINDOWS_REQUIRED_DLLS) {
            Path path = runtimeDirectory.resolve(dll);
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                return false;
            }
        }
        try (Stream<Path> paths = Files.list(runtimeDirectory)) {
            return paths.anyMatch(path -> path.getFileName().toString().toLowerCase(Locale.ENGLISH).startsWith("ggml-cpu")
                    && path.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".dll")
                    && Files.isRegularFile(path)
                    && Files.isReadable(path));
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean validModelFile(Path modelFile) {
        try {
            return Files.isRegularFile(modelFile)
                    && Files.isReadable(modelFile)
                    && Files.size(modelFile) > 0
                    && modelFile.getFileName() != null
                    && modelFile.getFileName().toString().equalsIgnoreCase("pfmis-model.gguf");
        } catch (IOException exception) {
            return false;
        }
    }

    private static String installDetail(Path serverExecutable, Path modelFile) {
        long modelSize = 0;
        try {
            modelSize = Files.isRegularFile(modelFile) ? Files.size(modelFile) : 0;
        } catch (IOException ignored) {
            // Report zero below.
        }
        return "Runtime: " + serverExecutable + "\nModel: " + modelFile + "\nModel size: " + modelSize + " bytes";
    }

    private static LocalAiException processExitFailure(Process process) {
        int exitCode = -1;
        if (process != null) {
            try {
                exitCode = process.exitValue();
            } catch (IllegalThreadStateException ignored) {
                // Process is still alive.
            }
        }
        String logText = recentLogText();
        LocalAiStatus classified = classifyStartupFailure(logText);
        String message = switch (classified) {
            case PORT_CONFLICT -> "Local AI could not start because its network port is already in use.";
            case MODEL_ERROR -> memoryFailure(logText)
                    ? "Local AI could not load the model because the computer may not have enough available memory."
                    : "Local AI model could not be loaded. Open AI Logs for details.";
            case RUNTIME_ERROR -> "Local AI runtime is incomplete or not compatible with this computer.";
            default -> "Local AI could not start. Open AI Logs for details.";
        };
        log("PROCESS_EXITED", "exitCode=" + exitCode + "\nclassification=" + classified + "\n" + logText);
        return new LocalAiException(classified, message);
    }

    private static LocalAiStatus classifyStartupFailure(String output) {
        String text = output == null ? "" : output.toLowerCase(Locale.ENGLISH);
        if (text.contains("address already in use")
                || text.contains("port already in use")
                || text.contains("bind failed")
                || text.contains("failed to bind")
                || text.contains("cannot bind")) {
            return LocalAiStatus.PORT_CONFLICT;
        }
        if (text.contains("missing") && text.contains(".dll") || text.contains("could not find") && text.contains(".dll")) {
            return LocalAiStatus.RUNTIME_ERROR;
        }
        if (text.contains("illegal instruction") || text.contains("unsupported cpu") || text.contains("instruction set")) {
            return LocalAiStatus.RUNTIME_ERROR;
        }
        if (text.contains("model not found")
                || text.contains("failed to load model")
                || text.contains("could not load model")
                || text.contains("error loading model")
                || text.contains("invalid gguf")
                || text.contains("bad gguf")
                || text.contains("llama_model_load")) {
            return LocalAiStatus.MODEL_ERROR;
        }
        if (memoryFailure(text)) {
            return LocalAiStatus.MODEL_ERROR;
        }
        if (text.contains("unknown argument") || text.contains("invalid argument") || text.contains("usage:")) {
            return LocalAiStatus.RUNTIME_ERROR;
        }
        return LocalAiStatus.ERROR;
    }

    private static boolean memoryFailure(String output) {
        String text = output == null ? "" : output.toLowerCase(Locale.ENGLISH);
        return text.contains("out of memory")
                || text.contains("bad allocation")
                || text.contains("failed to allocate")
                || text.contains("not enough memory")
                || text.contains("insufficient memory");
    }

    private static LocalAiException localAiFailure(IOException exception) {
        String message = userMessage(exception);
        LocalAiStatus status = message.toLowerCase(Locale.ENGLISH).contains("connect")
                ? LocalAiStatus.STOPPED
                : LocalAiStatus.ERROR;
        return new LocalAiException(status, message, exception);
    }

    private static boolean isPortListening(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 250);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void resetManagedEndpointIfProcessStopped() {
        if (serverProcess == null || !serverProcess.isAlive()) {
            resetManagedEndpoint();
        }
    }

    private static void resetManagedEndpoint() {
        currentApiKey = null;
        currentStartupLogOffset = 0;
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
            throw new LocalAiException(LocalAiStatus.ERROR, "Failed to create PFMIS Local AI configuration.", exception);
        }
    }

    private static Path serverExecutable(LocalAiConfig config) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        String executableName = osName.contains("win") ? "llama-server.exe" : "llama-server";
        return localAiDirectory(config).resolve("runtime").resolve(executableName);
    }

    private static Path modelFile(LocalAiConfig config) {
        return localAiDirectory(config).resolve("models").resolve("pfmis-model.gguf");
    }

    private static Path localAiDirectory(LocalAiConfig config) {
        if (config.localAiDirectory() != null) {
            return config.localAiDirectory();
        }
        return applicationDirectory().resolve("local-ai").toAbsolutePath().normalize();
    }

    private static LocalAiConfig config() {
        LocalAiConfig override = configOverride;
        return override == null ? AppConfig.localAiConfig() : override;
    }

    private static HttpClient client(LocalAiConfig config) {
        return HttpClient.newBuilder()
                .connectTimeout(config.requestTimeout())
                .build();
    }

    private static HttpRequest.Builder authorized(HttpRequest.Builder builder, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    private static String randomApiKey() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private static boolean healthyBody(String body) {
        String lower = body == null ? "" : body.toLowerCase(Locale.ENGLISH);
        return lower.contains("\"status\":\"ok\"")
                || lower.contains("\"status\":\"ready\"")
                || lower.contains("\"status\": \"ok\"")
                || lower.contains("\"status\": \"ready\"");
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

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LocalAiException(LocalAiStatus.ERROR, "Interrupted while starting PFMIS Local AI.", exception);
        }
    }

    private static String testInferenceBody() {
        return "{"
                + "\"model\":\"" + MODEL_ALIAS + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"Reply with OK.\"}],"
                + "\"temperature\":0"
                + "}";
    }

    private static boolean processAlive() {
        Process process = serverProcess;
        return process != null && process.isAlive();
    }

    private static Long processId() {
        return processId(serverProcess);
    }

    private static Long processId(Process process) {
        return process == null ? null : process.pid();
    }

    private static LocalAiStatusSnapshot readySnapshot(LocalAiConfig config, boolean inferenceReady) {
        return new LocalAiStatusSnapshot(
                LocalAiStatus.READY,
                "Local AI is available and ready to use.",
                "HTTP server is responding at " + endpoint() + ".",
                true,
                true,
                true,
                processAlive(),
                true,
                inferenceReady,
                config.host(),
                config.port(),
                endpoint(),
                processId(),
                serverExecutable(config),
                modelFile(config),
                logFile()
        );
    }

    private static LocalAiStatusSnapshot stoppedSnapshot(LocalAiConfig config) {
        return snapshot(LocalAiStatus.STOPPED, "Local AI service is stopped.", "", true, true, true, false, false, false, null);
    }

    private static LocalAiStatusSnapshot snapshot(
            LocalAiStatus status,
            String summary,
            String detail,
            boolean enabled,
            boolean runtimeInstalled,
            boolean modelInstalled,
            boolean processRunning,
            boolean httpServerReady,
            boolean inferenceReady,
            Long processId
    ) {
        LocalAiConfig config = config();
        return new LocalAiStatusSnapshot(
                status,
                summary,
                detail,
                enabled,
                runtimeInstalled,
                modelInstalled,
                processRunning,
                httpServerReady,
                inferenceReady,
                config.host(),
                config.port(),
                config.endpoint(),
                processId,
                serverExecutable(config),
                modelFile(config),
                logFile()
        );
    }

    private static LocalAiStatusSnapshot setStatus(LocalAiStatusSnapshot status) {
        lastStatus = status;
        return status;
    }

    private static void logStartup(LocalAiConfig config, Path serverExecutable, Path modelFile, List<String> command) {
        long modelSize = 0;
        try {
            modelSize = Files.isRegularFile(modelFile) ? Files.size(modelFile) : 0;
        } catch (IOException ignored) {
            // Report zero below.
        }
        log("STARTUP", "timestamp=" + Instant.now()
                + "\nruntimePath=" + serverExecutable
                + "\nmodelPath=" + modelFile
                + "\nmodelExists=" + Files.isRegularFile(modelFile)
                + "\nmodelSize=" + modelSize
                + "\nhost=" + config.host()
                + "\nport=" + config.port()
                + "\ncommand=" + safeCommand(command));
    }

    private static void log(String stage, String message) {
        try {
            Files.createDirectories(logDirectory());
            Files.writeString(
                    logFile(),
                    "\n[" + Instant.now() + "] " + stage + "\n" + (message == null ? "" : message) + "\n",
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // Local AI diagnostics are best-effort.
        }
    }

    private static String safeCommand(List<String> command) {
        List<String> safe = new ArrayList<>();
        boolean skipNext = false;
        for (String part : command) {
            if (skipNext) {
                safe.add("<redacted>");
                skipNext = false;
                continue;
            }
            if ("--api-key".equals(part)) {
                safe.add(part);
                skipNext = true;
            } else {
                safe.add(part);
            }
        }
        return String.join(" ", safe);
    }

    private static String recentLogText() {
        try {
            if (!Files.isRegularFile(logFile())) {
                return "";
            }
            byte[] bytes = Files.readAllBytes(logFile());
            int start = Math.max(0, bytes.length - RECENT_LOG_BYTES);
            if (currentStartupLogOffset > 0 && currentStartupLogOffset < bytes.length) {
                start = (int) Math.max(start, currentStartupLogOffset);
            }
            return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private static long logSize() {
        try {
            return Files.isRegularFile(logFile()) ? Files.size(logFile()) : 0;
        } catch (IOException exception) {
            return 0;
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
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

    static synchronized void configureForTests(LocalAiConfig config, ProcessLauncher launcher) {
        configureForTests(config, launcher, false);
    }

    static synchronized void configureForTests(LocalAiConfig config, ProcessLauncher launcher, boolean skipPortPreflight) {
        shutdownQuietlyForTests();
        configOverride = config;
        processLauncher = launcher == null ? ProcessBuilder::start : launcher;
        skipPortPreflightForTests = skipPortPreflight;
        currentApiKey = null;
        serverProcess = null;
        currentStartupLogOffset = 0;
        lastStatus = stoppedSnapshot(config());
    }

    static synchronized void resetForTests() {
        shutdownQuietlyForTests();
        configOverride = null;
        processLauncher = ProcessBuilder::start;
        skipPortPreflightForTests = false;
        currentApiKey = null;
        serverProcess = null;
        currentStartupLogOffset = 0;
        lastStatus = stoppedSnapshot(config());
    }

    private static void shutdownQuietlyForTests() {
        try {
            Process process = serverProcess;
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        } catch (RuntimeException ignored) {
            // Test reset only.
        }
    }

    @FunctionalInterface
    interface ProcessLauncher {
        Process start(ProcessBuilder processBuilder) throws IOException;
    }

    private record HealthCheck(boolean ready, boolean loading, String summary, String detail) {
    }
}
