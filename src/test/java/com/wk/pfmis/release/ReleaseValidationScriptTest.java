package com.wk.pfmis.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseValidationScriptTest {
    private static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path temporaryDirectory;

    @Test
    void validatorAllowsSafeTemplate() throws Exception {
        Path releaseRoot = Files.createDirectory(temporaryDirectory.resolve("release-safe"));
        Files.writeString(
                releaseRoot.resolve(".env.example"),
                "PFMIS_MAIL_PASSWORD=replace-with-private-app-password%nPFMIS_MAIL_FROM=sender@example.invalid%n".formatted(),
                StandardCharsets.UTF_8
        );
        Files.writeString(releaseRoot.resolve("README.txt"), "PFMIS release fixture", StandardCharsets.UTF_8);

        ScriptResult result = runValidator(releaseRoot);

        assertEquals(0, result.exitCode(), result.combinedOutput());
        assertTrue(result.combinedOutput().contains("Release validation passed"));
    }

    @Test
    void validatorRejectsEnvFileWithoutPrintingSecretValue() throws Exception {
        Path releaseRoot = Files.createDirectory(temporaryDirectory.resolve("release-blocked"));
        String fakeSecret = "UnitTestSecretValue123456789";
        Files.writeString(
                releaseRoot.resolve(".env"),
                "PFMIS_MAIL_PASSWORD=" + fakeSecret + System.lineSeparator(),
                StandardCharsets.UTF_8
        );

        ScriptResult result = runValidator(releaseRoot);

        assertNotEquals(0, result.exitCode(), result.combinedOutput());
        assertTrue(result.combinedOutput().contains("Release validation failed"));
        assertFalse(result.combinedOutput().contains(fakeSecret));
    }

    private ScriptResult runValidator(Path releasePath) throws Exception {
        Path script = Path.of(System.getProperty("user.dir"), "scripts", "validate-release.ps1");
        assertTrue(Files.isRegularFile(script), "Missing release validation script.");

        ProcessBuilder processBuilder = new ProcessBuilder(
                powershellExecutable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                script.toString(),
                "-Path",
                releasePath.toString()
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        boolean completed = process.waitFor(SCRIPT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new AssertionError("Release validator timed out.");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ScriptResult(process.exitValue(), output);
    }

    private String powershellExecutable() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        if (os.contains("windows")) {
            return "powershell";
        }
        return "pwsh";
    }

    private record ScriptResult(int exitCode, String combinedOutput) {
    }
}
