package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.BackupRecord;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;

public class SyncCenterController {
    @FXML private Label dataStoreLabel;
    @FXML private Label backupStateLabel;
    @FXML private Label syncModeLabel;
    @FXML private Label syncStatusLabel;
    @FXML private Label syncDetailLabel;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        refresh();
    }

    @FXML
    private void refresh() {
        Path databasePath = DatabaseHandler.databasePath();
        BackupRecord backup = database.latestDailyBackupRecord();
        dataStoreLabel.setText("Local data store: " + databasePath);
        backupStateLabel.setText(backup == null
                ? "Latest daily backup: not created yet"
                : "Latest daily backup: " + fileName(backup.getBackupFile()) + " | " + backup.getCreatedAt());
        syncModeLabel.setText("Sync mode: local device record center");
        syncStatusLabel.setText(backup == null ? "Action needed" : "Ready");
        syncDetailLabel.setText(backup == null
                ? "Create a daily backup before external transfer, restore, or device migration."
                : "Records are available locally. Remote sync can be added here when a PostgreSQL or cloud sync service is connected.");
    }

    @FXML
    private void runSyncCheck() {
        try {
            Path databasePath = DatabaseHandler.databasePath();
            BackupRecord backup = database.latestDailyBackupRecord();
            boolean databaseAvailable = Files.isRegularFile(databasePath);
            refresh();
            if (!databaseAvailable) {
                syncStatusLabel.setText("Action needed");
                syncDetailLabel.setText("Local data store is missing: " + databasePath);
            } else if (backup == null) {
                syncStatusLabel.setText("Action needed");
                syncDetailLabel.setText("Local data is available. Create a backup from Administration > Backup & Restore before migration.");
            } else {
                syncStatusLabel.setText("Ready");
                syncDetailLabel.setText("Local data and latest backup are ready for record review.");
            }
            database.recordSystemLog("Data And Records", "Sync check", "INFO", syncDetailLabel.getText());
        } catch (RuntimeException exception) {
            database.recordSystemLog("Data And Records", "Sync check failed", "ERROR", rootMessage(exception));
            UiAlerts.error("Sync check failed", exception);
            syncStatusLabel.setText("Failed");
            syncDetailLabel.setText(rootMessage(exception));
        }
    }

    @FXML
    private void openDataFolder() {
        openFolder(DatabaseHandler.applicationDataDirectory(), "Data folder");
    }

    private void openFolder(Path folder, String label) {
        try {
            Files.createDirectories(folder);
            if (!Desktop.isDesktopSupported()) {
                syncDetailLabel.setText(label + ": " + folder);
                return;
            }
            Desktop.getDesktop().open(folder.toFile());
        } catch (Exception exception) {
            UiAlerts.error("Failed to open " + label.toLowerCase(), exception);
            syncDetailLabel.setText(rootMessage(exception));
        }
    }

    private String fileName(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return Path.of(value).getFileName().toString();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
