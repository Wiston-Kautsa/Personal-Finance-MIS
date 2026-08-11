package com.wk.pfmis.utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ExportPathService {
    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss", Locale.ENGLISH);
    private static final String DOWNLOADS_OVERRIDE_PROPERTY = "pfmis.downloads.dir";

    private ExportPathService() {
    }

    public static Path getPfmisExportDirectory() throws IOException {
        Path directory = downloadsDirectory().resolve("PFMIS").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        return directory;
    }

    public static Path resolveExportFile(String fileName) throws IOException {
        String safeName = safeFileName(fileName);
        return uniquePath(getPfmisExportDirectory(), safeName);
    }

    public static Path resolveExportDirectory(String directoryName) throws IOException {
        String safeName = safeNamePart(directoryName);
        if (safeName.isBlank()) {
            safeName = "PFMIS_Export_" + LocalDateTime.now().format(EXPORT_TIMESTAMP);
        }
        return uniqueDirectory(getPfmisExportDirectory(), safeName);
    }

    public static Path writeTextExport(String fileName, CharSequence content) throws IOException {
        return writeTextExport(fileName, content, StandardCharsets.UTF_8);
    }

    public static Path writeTextExport(String fileName, CharSequence content, Charset charset) throws IOException {
        Path directory = getPfmisExportDirectory();
        String safeName = safeFileName(fileName);
        for (int index = 0; index < 1_000; index++) {
            Path candidate = numberedPath(directory, safeName, index);
            try {
                Files.writeString(candidate, content == null ? "" : content, charset,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                return candidate;
            } catch (FileAlreadyExistsException ignored) {
                // Try the next numbered name.
            }
        }
        throw new IOException("Unable to create a unique export filename in " + directory);
    }

    public static String defaultFileName(String title, String extension) {
        String name = safeNamePart(title);
        if (name.isBlank()) {
            name = "Export";
        }
        String safeExtension = extension == null ? "" : extension.trim().replaceFirst("^\\.+", "");
        if (safeExtension.isBlank()) {
            safeExtension = "txt";
        }
        return "PFMIS_" + name + "_" + LocalDateTime.now().format(EXPORT_TIMESTAMP) + "." + safeExtension;
    }

    public static String defaultDirectoryName(String title) {
        String name = safeNamePart(title);
        if (name.isBlank()) {
            name = "Export";
        }
        return "PFMIS_" + name + "_" + LocalDateTime.now().format(EXPORT_TIMESTAMP);
    }

    public static String successMessage(Path file) {
        return "Export completed successfully." + System.lineSeparator()
                + System.lineSeparator()
                + "Saved to:" + System.lineSeparator()
                + file.toAbsolutePath().normalize();
    }

    public static String failureMessage(IOException exception) {
        String reason = exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "The file could not be written."
                : exception.getMessage();
        return "Unable to export the file." + System.lineSeparator()
                + System.lineSeparator()
                + "PFMIS could not create or write to:" + System.lineSeparator()
                + fallbackExportDirectoryText() + System.lineSeparator()
                + System.lineSeparator()
                + "Reason:" + System.lineSeparator()
                + reason;
    }

    private static Path downloadsDirectory() {
        String override = System.getProperty(DOWNLOADS_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }

        String userProfile = System.getenv("USERPROFILE");
        if (isWindows() && userProfile != null && !userProfile.isBlank()) {
            return Paths.get(userProfile, "Downloads");
        }

        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return Paths.get("Downloads");
        }
        return Paths.get(home, "Downloads");
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ENGLISH).contains("win");
    }

    private static Path uniquePath(Path directory, String safeName) {
        for (int index = 0; index < 1_000; index++) {
            Path candidate = numberedPath(directory, safeName, index);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return directory.resolve(stem(safeName) + "_" + System.nanoTime() + extension(safeName));
    }

    private static Path uniqueDirectory(Path parent, String safeName) throws IOException {
        for (int index = 0; index < 1_000; index++) {
            Path candidate = parent.resolve(index == 0 ? safeName : safeName + "_" + index);
            try {
                Files.createDirectory(candidate);
                return candidate;
            } catch (FileAlreadyExistsException ignored) {
                // Try the next numbered directory.
            }
        }
        return Files.createDirectory(parent.resolve(safeName + "_" + System.nanoTime()));
    }

    private static Path numberedPath(Path directory, String safeName, int index) {
        if (index == 0) {
            return directory.resolve(safeName);
        }
        return directory.resolve(stem(safeName) + "_" + index + extension(safeName));
    }

    private static String safeFileName(String fileName) {
        String safe = fileName == null ? "" : fileName.trim();
        if (safe.isBlank()) {
            safe = defaultFileName("Export", "txt");
        }
        String extension = extension(safe);
        String stem = safeNamePart(stem(safe));
        if (stem.isBlank()) {
            stem = "PFMIS_Export_" + LocalDateTime.now().format(EXPORT_TIMESTAMP);
        }
        if (extension.isBlank()) {
            extension = ".txt";
        }
        return stem + extension.toLowerCase(Locale.ENGLISH);
    }

    private static String safeNamePart(String value) {
        return value == null ? "" : value.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("[^A-Za-z0-9._ -]+", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._ -]+|[._ -]+$", "");
    }

    private static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot);
    }

    private static String fallbackExportDirectoryText() {
        try {
            return getPfmisExportDirectory().toString();
        } catch (IOException ignored) {
            return downloadsDirectory().resolve("PFMIS").toAbsolutePath().normalize().toString();
        }
    }
}
