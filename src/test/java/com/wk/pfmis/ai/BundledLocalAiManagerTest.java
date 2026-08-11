package com.wk.pfmis.ai;

import com.wk.pfmis.config.LocalAiConfig;
import com.wk.pfmis.models.AiSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledLocalAiManagerTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetManager() {
        BundledLocalAiManager.resetForTests();
    }

    @Test
    void enabledButUnreachableIsStoppedNotReadyOrActive() throws Exception {
        LocalAiConfig config = configWithInstalledRuntime(freePort(), Duration.ofMillis(300));
        BundledLocalAiManager.configureForTests(config, processBuilder -> {
            throw new AssertionError("Process should not be started by a status check.");
        });

        LocalAiStatusSnapshot status = BundledLocalAiManager.status(defaultLocalSettings());

        assertEquals(LocalAiStatus.STOPPED, status.status());
        assertFalse(status.ready());
        assertFalse(status.displayText().toLowerCase(Locale.ENGLISH).contains("active"));
        assertFalse(status.displayText().toLowerCase(Locale.ENGLISH).contains("status: ready"));
        assertTrue(BundledLocalAiManager.userMessage(new ConnectException("Connection refused: getsockopt"))
                .startsWith("Unable to connect to PFMIS Local AI."));
    }

    @Test
    void missingRuntimeAndModelDoNotAttemptHttpOrProcessStartup() {
        AtomicInteger launches = new AtomicInteger();
        BundledLocalAiManager.configureForTests(config(freePort(), Duration.ofMillis(300)), processBuilder -> {
            launches.incrementAndGet();
            return new FakeProcess(true);
        });

        LocalAiException exception = assertThrows(LocalAiException.class, BundledLocalAiManager::ensureReadyStatus);

        assertEquals(LocalAiStatus.NOT_INSTALLED, exception.status());
        assertEquals(0, launches.get());
    }

    @Test
    void modelMissingIsClassifiedBeforeStartup() throws Exception {
        LocalAiConfig config = config(freePort(), Duration.ofMillis(300));
        createRuntimeFiles(config.localAiDirectory());
        BundledLocalAiManager.configureForTests(config, processBuilder -> new FakeProcess(true));

        LocalAiException exception = assertThrows(LocalAiException.class, BundledLocalAiManager::ensureReadyStatus);

        assertEquals(LocalAiStatus.MODEL_MISSING, exception.status());
    }

    @Test
    void portConflictPreventsDuplicateProcess() throws Exception {
        int port = freePort();
        LocalAiConfig config = configWithInstalledRuntime(port, Duration.ofMillis(500));
        AtomicInteger launches = new AtomicInteger();
        try (FakeHttpServer ignored = FakeHttpServer.start(port, 0, true)) {
            BundledLocalAiManager.configureForTests(config, processBuilder -> {
                launches.incrementAndGet();
                return new FakeProcess(true);
            });

            LocalAiException exception = assertThrows(LocalAiException.class, BundledLocalAiManager::ensureReadyStatus);

            assertEquals(LocalAiStatus.PORT_CONFLICT, exception.status());
            assertEquals(0, launches.get());
        }
    }

    @Test
    void slowHealthEventuallyBecomesReady() throws Exception {
        int port = freePort();
        LocalAiConfig config = configWithInstalledRuntime(port, Duration.ofSeconds(2));
        AtomicInteger launches = new AtomicInteger();
        List<FakeHttpServer> servers = new ArrayList<>();
        BundledLocalAiManager.configureForTests(config, processBuilder -> {
            launches.incrementAndGet();
            FakeHttpServer server = FakeHttpServer.start(port, 2, true);
            servers.add(server);
            return new FakeProcess(true, server::close);
        });
        try {
            LocalAiStatusSnapshot status = BundledLocalAiManager.ensureReadyStatus();

            assertEquals(LocalAiStatus.READY, status.status());
            assertTrue(status.httpServerReady());
            assertEquals(1, launches.get());
        } finally {
            servers.forEach(FakeHttpServer::close);
        }
    }

    @Test
    void startupTimeoutDoesNotBecomeReady() throws Exception {
        LocalAiConfig config = configWithInstalledRuntime(freePort(), Duration.ofMillis(180));
        BundledLocalAiManager.configureForTests(config, processBuilder -> new FakeProcess(true));

        LocalAiException exception = assertThrows(LocalAiException.class, BundledLocalAiManager::ensureReadyStatus);

        assertEquals(LocalAiStatus.STARTUP_TIMEOUT, exception.status());
        assertFalse(BundledLocalAiManager.status().ready());
    }

    @Test
    void processExitDuringStartupIsDetected() throws Exception {
        LocalAiConfig config = configWithInstalledRuntime(freePort(), Duration.ofSeconds(1));
        BundledLocalAiManager.configureForTests(config, processBuilder -> new FakeProcess(false), true);

        LocalAiException exception = assertThrows(LocalAiException.class, BundledLocalAiManager::ensureReadyStatus);

        assertEquals(LocalAiStatus.ERROR, exception.status());
        assertFalse(BundledLocalAiManager.status().ready());
    }

    @Test
    void testInferenceRequiresHealthAndCompletion() throws Exception {
        int port = freePort();
        LocalAiConfig config = configWithInstalledRuntime(port, Duration.ofSeconds(2));
        List<FakeHttpServer> servers = new ArrayList<>();
        BundledLocalAiManager.configureForTests(config, processBuilder -> {
            FakeHttpServer server = FakeHttpServer.start(port, 0, true);
            servers.add(server);
            return new FakeProcess(true, server::close);
        });
        try {
            String result = BundledLocalAiManager.testInference();

            assertTrue(result.contains("PFMIS Local AI test successful."));
            assertTrue(result.contains("Inference: Successful"));
            assertEquals(LocalAiStatus.READY, BundledLocalAiManager.status().status());
        } finally {
            servers.forEach(FakeHttpServer::close);
        }
    }

    @Test
    void restartStopsManagedProcessBeforeStartingAnother() throws Exception {
        int port = freePort();
        LocalAiConfig config = configWithInstalledRuntime(port, Duration.ofSeconds(2));
        AtomicInteger launches = new AtomicInteger();
        List<FakeProcess> processes = new ArrayList<>();
        List<FakeHttpServer> servers = new ArrayList<>();
        BundledLocalAiManager.configureForTests(config, processBuilder -> {
            launches.incrementAndGet();
            FakeHttpServer server = FakeHttpServer.start(port, 0, true);
            servers.add(server);
            FakeProcess process = new FakeProcess(true, server::close);
            processes.add(process);
            return process;
        });
        try {
            BundledLocalAiManager.ensureReadyStatus();
            String restartResult = BundledLocalAiManager.restart();

            assertEquals(2, launches.get());
            assertTrue(processes.getFirst().destroyed());
            assertTrue(restartResult.contains("Local AI is available"));
            assertEquals(LocalAiStatus.READY, BundledLocalAiManager.status().status());
        } finally {
            servers.forEach(FakeHttpServer::close);
        }
    }

    private AiSettings defaultLocalSettings() {
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

    private LocalAiConfig configWithInstalledRuntime(int port, Duration startupTimeout) throws Exception {
        LocalAiConfig config = config(port, startupTimeout);
        createRuntimeFiles(config.localAiDirectory());
        createModelFile(config.localAiDirectory());
        return config;
    }

    private LocalAiConfig config(int port, Duration startupTimeout) {
        return new LocalAiConfig(
                true,
                LocalAiConfig.DEFAULT_HOST,
                port,
                LocalAiConfig.DEFAULT_CONTEXT_SIZE,
                startupTimeout,
                Duration.ofMillis(25),
                Duration.ofMillis(300),
                tempDir.resolve("local-ai")
        );
    }

    private void createRuntimeFiles(Path localAiDirectory) throws Exception {
        Path runtime = localAiDirectory.resolve("runtime");
        Files.createDirectories(runtime);
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        Files.writeString(runtime.resolve(osName.contains("win") ? "llama-server.exe" : "llama-server"), "fake");
        if (osName.contains("win")) {
            for (String dll : List.of(
                    "llama-server-impl.dll",
                    "llama-common.dll",
                    "llama.dll",
                    "ggml.dll",
                    "ggml-base.dll",
                    "libomp140.x86_64.dll",
                    "ggml-cpu-x64.dll"
            )) {
                Files.writeString(runtime.resolve(dll), "fake");
            }
        }
    }

    private void createModelFile(Path localAiDirectory) throws Exception {
        Path models = localAiDirectory.resolve("models");
        Files.createDirectories(models);
        Files.writeString(models.resolve("pfmis-model.gguf"), "fake-model");
    }

    private int freePort() {
        for (int attempt = 0; attempt < 50; attempt++) {
            int port;
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            if (!canConnect(port)) {
                return port;
            }
        }
        throw new IllegalStateException("Could not find a quiet loopback port for Local AI tests.");
    }

    private boolean canConnect(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(LocalAiConfig.DEFAULT_HOST, port), 100);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static final class FakeProcess extends Process {
        private volatile boolean alive;
        private volatile boolean destroyed;
        private final Runnable onDestroy;

        private FakeProcess(boolean alive) {
            this(alive, () -> {
            });
        }

        private FakeProcess(boolean alive, Runnable onDestroy) {
            this.alive = alive;
            this.onDestroy = onDestroy;
        }

        boolean destroyed() {
            return destroyed;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            alive = false;
            return 1;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            alive = false;
            return true;
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException("still running");
            }
            return 1;
        }

        @Override
        public void destroy() {
            destroyed = true;
            alive = false;
            onDestroy.run();
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public long pid() {
            return 42;
        }
    }

    private static final class FakeHttpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final int loadingResponses;
        private final boolean completionSuccess;
        private final AtomicInteger healthCalls = new AtomicInteger();
        private volatile boolean closed;
        private Thread thread;

        private FakeHttpServer(int port, int loadingResponses, boolean completionSuccess) throws IOException {
            this.serverSocket = new ServerSocket();
            this.serverSocket.setReuseAddress(true);
            this.serverSocket.bind(new InetSocketAddress(LocalAiConfig.DEFAULT_HOST, port));
            this.loadingResponses = loadingResponses;
            this.completionSuccess = completionSuccess;
        }

        static FakeHttpServer start(int port, int loadingResponses, boolean completionSuccess) throws IOException {
            FakeHttpServer server = new FakeHttpServer(port, loadingResponses, completionSuccess);
            server.thread = new Thread(server::serve, "fake-local-ai-server");
            server.thread.setDaemon(true);
            server.thread.start();
            return server;
        }

        private void serve() {
            while (!closed) {
                try (Socket socket = serverSocket.accept()) {
                    socket.setSoTimeout(1000);
                    InputStream inputStream = socket.getInputStream();
                    String requestLine = readLine(inputStream);
                    int contentLength = readHeaders(inputStream);
                    readBody(inputStream, contentLength);
                    Response response = response(requestLine);
                    byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
                    String headers = "HTTP/1.1 " + response.status() + " OK\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + body.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                    socket.getOutputStream().write(headers.getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().write(body);
                    socket.getOutputStream().flush();
                } catch (IOException ignored) {
                    if (!closed) {
                        // Continue accepting test requests.
                    }
                }
            }
        }

        private Response response(String requestLine) {
            String request = requestLine == null ? "" : requestLine;
            if (request.contains("/health")) {
                int call = healthCalls.incrementAndGet();
                if (call <= loadingResponses) {
                    return new Response(503, "{\"status\":\"loading model\"}");
                }
                return new Response(200, "{\"status\":\"ok\"}");
            }
            if (request.contains("/v1/chat/completions") && completionSuccess) {
                return new Response(200, "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}");
            }
            return new Response(500, "{\"error\":\"failed\"}");
        }

        private String readLine(InputStream inputStream) throws IOException {
            StringBuilder builder = new StringBuilder();
            int value;
            while ((value = inputStream.read()) != -1) {
                if (value == '\n') {
                    break;
                }
                if (value != '\r') {
                    builder.append((char) value);
                }
            }
            return builder.toString();
        }

        private int readHeaders(InputStream inputStream) throws IOException {
            int contentLength = 0;
            String line;
            do {
                line = readLine(inputStream);
                String lower = line.toLowerCase(Locale.ENGLISH);
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                }
            } while (!line.isBlank());
            return contentLength;
        }

        private void readBody(InputStream inputStream, int contentLength) throws IOException {
            for (int index = 0; index < contentLength; index++) {
                if (inputStream.read() == -1) {
                    return;
                }
            }
        }

        @Override
        public void close() {
            closed = true;
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Test cleanup only.
            }
            if (thread != null) {
                try {
                    thread.join(500);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private record Response(int status, String body) {
    }
}
