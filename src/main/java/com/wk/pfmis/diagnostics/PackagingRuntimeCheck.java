package com.wk.pfmis.diagnostics;

import com.sun.jna.Native;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public final class PackagingRuntimeCheck {
    private PackagingRuntimeCheck() {
    }

    public static void main(String[] args) throws Exception {
        runChecks();
        System.out.println("PFMIS packaged runtime check passed.");
    }

    public static void runChecks() throws Exception {
        requireResource("/com/wk/pfmis/views/Login.fxml");
        requireResource("/com/wk/pfmis/views/Dashboard.fxml");
        requireResource("/com/wk/pfmis/css/Theme.css");
        Class.forName("org.sqlite.JDBC");
        Path database = Files.createTempFile("pfmis-sqlite-runtime-check-", ".db");
        try {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT sqlite_version()")) {
                if (!resultSet.next() || resultSet.getString(1).isBlank()) {
                    throw new IllegalStateException("SQLite did not report a runtime version.");
                }
            }
        } finally {
            Files.deleteIfExists(database);
        }
        if (Native.VERSION == null || Native.VERSION.isBlank()) {
            throw new IllegalStateException("JNA did not initialize.");
        }
    }

    private static void requireResource(String resourcePath) {
        if (PackagingRuntimeCheck.class.getResource(resourcePath) == null) {
            throw new IllegalStateException("Required packaged resource is missing: " + resourcePath);
        }
    }
}
