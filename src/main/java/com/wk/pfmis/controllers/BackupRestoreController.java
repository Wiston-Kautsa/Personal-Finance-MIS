package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.BackupRecord;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;

public class BackupRestoreController {
    @FXML private Label latestBackupLabel;
    @FXML private Label scheduleLabel;
    @FXML private Label backupStatusLabel;
    @FXML private Label backupLocationLabel;
    @FXML private Label checksumLabel;
    @FXML private CheckBox restoreConfirmationBox;
    @FXML private Label statusLabel;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        scheduleLabel.setText("Enabled: checked on startup and once every hour while PFMIS is open.");
        refresh();
    }

    @FXML
    private void runBackupNow() {
        try {
            BackupRecord record = database.createLatestDailyBackup();
            restoreConfirmationBox.setSelected(false);
            statusLabel.setText("Latest daily backup updated: " + fileName(record.getBackupFile()));
            refresh();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to run daily backup", exception);
            statusLabel.setText(rootMessage(exception));
        }
    }

    @FXML
    private void validateLatestBackup() {
        try {
            Path backupFile = latestBackupPath();
            String result = database.validateBackup(backupFile);
            database.recordSystemLog("Administration", "Backup validated", "INFO", backupFile.toString());
            statusLabel.setText(result);
            refresh();
        } catch (RuntimeException exception) {
            database.recordSystemLog("Administration", "Backup validation failed", "ERROR", rootMessage(exception));
            UiAlerts.error("Backup validation failed", exception);
            statusLabel.setText(rootMessage(exception));
        }
    }

    @FXML
    private void restoreLatestBackup() {
        if (!restoreConfirmationBox.isSelected()) {
            UiAlerts.info("Tick the restore confirmation first.");
            return;
        }
        try {
            Path backupFile = latestBackupPath();
            database.restoreBackup(backupFile);
            database.recordSystemLog("Administration", "Backup restored", "WARN", backupFile.toString());
            restoreConfirmationBox.setSelected(false);
            statusLabel.setText("Restore completed from: " + backupFile);
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            database.recordSystemLog("Administration", "Backup restore failed", "ERROR", rootMessage(exception));
            UiAlerts.error("Failed to restore latest backup", exception);
            statusLabel.setText(rootMessage(exception));
        }
    }

    @FXML
    private void openBackupFolder() {
        try {
            Path directory = DatabaseHandler.defaultBackupDirectory();
            Files.createDirectories(directory);
            if (!Desktop.isDesktopSupported()) {
                statusLabel.setText("Backup folder: " + directory);
                return;
            }
            Desktop.getDesktop().open(directory.toFile());
        } catch (Exception exception) {
            UiAlerts.error("Failed to open backup folder", exception);
            statusLabel.setText(rootMessage(exception));
        }
    }

    @FXML
    private void refresh() {
        BackupRecord record = database.latestDailyBackupRecord();
        Path latestBackup = DatabaseHandler.latestDailyBackupFile();
        backupLocationLabel.setText("Location: " + latestBackup);
        if (record == null) {
            latestBackupLabel.setText("No latest daily backup exists yet.");
            backupStatusLabel.setText("Waiting for automatic backup.");
            checksumLabel.setText("Checksum: not available");
            statusLabel.setText("PFMIS will create the latest daily backup automatically, or you can run Create Backup Now.");
            return;
        }
        latestBackupLabel.setText(fileName(record.getBackupFile()) + " | " + record.getCreatedAt());
        backupStatusLabel.setText(statusText(record));
        checksumLabel.setText("Checksum: " + shortChecksum(record.getChecksum()));
        statusLabel.setText("Latest daily backup is available.");
    }

    private Path latestBackupPath() {
        Path backupFile = DatabaseHandler.latestDailyBackupFile();
        if (!Files.isRegularFile(backupFile)) {
            throw new IllegalStateException("No latest daily backup exists yet. Run Create Backup Now first.");
        }
        return backupFile;
    }

    private String statusText(BackupRecord record) {
        if ("CURRENT".equals(record.getStatus()) || "AUTO_DAILY".equals(record.getStatus())) {
            return "Current for today.";
        }
        return "Latest available backup. If this is yesterday's backup, PFMIS will replace it with today's automatic backup.";
    }

    private String fileName(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return Path.of(value).getFileName().toString();
    }

    private String shortChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return "not available";
        }
        return checksum.length() <= 16 ? checksum : checksum.substring(0, 16);
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
