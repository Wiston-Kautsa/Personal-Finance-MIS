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

    @Test
    void windowsInstallerScriptUsesSelfContainedGuiJpackageLauncher() throws Exception {
        String script = readRepositoryFile("scripts", "build-windows-installer.ps1");

        assertTrue(script.contains("jpackage.exe"), "Installer build must require jpackage.");
        assertTrue(script.contains("\"--type\", \"app-image\""), "App-image must be built before the installer.");
        assertTrue(script.contains("\"--type\", \"exe\""), "Windows EXE installer packaging must be configured.");
        assertTrue(script.contains("\"--app-image\", $appImagePath"), "EXE must be created from the validated app-image.");
        assertTrue(script.contains("\"--main-class\", \"com.wk.pfmis.Launcher\""), "Native launcher must target the non-JavaFX wrapper.");
        assertTrue(script.contains("\"--win-shortcut\""), "Desktop shortcut metadata must be configured.");
        assertTrue(script.contains("\"--win-menu\""), "Start Menu metadata must be configured.");
        assertTrue(script.contains("\"--win-per-user-install\""), "Installer should avoid unnecessary admin rights.");
        assertTrue(script.contains("dependency:copy-dependencies"), "Runtime dependencies must be collected.");
        assertTrue(script.contains("-DincludeScope=runtime"), "Only runtime dependencies should be packaged.");
        assertTrue(script.contains("javafx-controls"), "JavaFX controls must be validated.");
        assertTrue(script.contains("sqlite-jdbc"), "SQLite JDBC must be validated.");
        assertTrue(script.contains("jna-platform"), "JNA platform must be validated.");
        assertFalse(script.contains("--win-console"), "PFMIS is a GUI app and must not request a console launcher.");
    }

    @Test
    void windowsPackageValidatorChecksRuntimeLibrariesAndResources() throws Exception {
        String script = readRepositoryFile("scripts", "validate-windows-package.ps1");

        assertTrue(script.contains("--pfmis-runtime-check"), "Runtime diagnostics must run through the native launcher.");
        assertTrue(script.contains("packaged runtime dependency check"), "Packaged app-image must run runtime dependency diagnostics.");
        assertTrue(script.contains("bin\\jli.dll"), "Validator must verify the bundled runtime image.");
        assertTrue(script.contains("personal-finance-mis-*.jar"), "Validator must locate the application JAR.");
        assertTrue(script.contains("com/wk/pfmis/views/Login.fxml"), "Login FXML must be checked.");
        assertTrue(script.contains("com/wk/pfmis/views/Dashboard.fxml"), "Dashboard FXML must be checked.");
        assertTrue(script.contains("com/wk/pfmis/css/Theme.css"), "Theme CSS must be checked.");
        assertTrue(script.contains("sqlite-jdbc"), "SQLite JDBC must be required in the app image.");
        assertTrue(script.contains("jna-platform"), "JNA platform must be required in the app image.");
        assertTrue(script.contains("slf4j-simple"), "SLF4J runtime binding must be required in the app image.");
        assertTrue(script.contains("junit"), "Validator must reject test dependencies.");
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

    private String readRepositoryFile(String first, String... more) throws IOException {
        Path path = Path.of(System.getProperty("user.dir"), first);
        for (String segment : more) {
            path = path.resolve(segment);
        }
        assertTrue(Files.isRegularFile(path), "Missing repository file: " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
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
