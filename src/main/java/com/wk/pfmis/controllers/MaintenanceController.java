package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.BackupRecord;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.nio.file.Files;
import java.nio.file.Path;

public class MaintenanceController {
    @FXML private Label databaseStatusLabel;
    @FXML private Label backupStatusLabel;
    @FXML private Label storageStatusLabel;
    @FXML private TextArea maintenanceResultArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        refresh();
    }

    @FXML
    private void refresh() {
        BackupRecord backup = database.latestDailyBackupRecord();
        Path databasePath = DatabaseHandler.databasePath();
        databaseStatusLabel.setText(Files.isRegularFile(databasePath) ? "Available" : "Missing");
        backupStatusLabel.setText(backup == null ? "No latest daily backup" : backup.getStatus() + " | " + backup.getCreatedAt());
        storageStatusLabel.setText(DatabaseHandler.applicationDataDirectory().toString());
        maintenanceResultArea.setText("Run Maintenance Check to review database integrity, record counts, logs, and latest backup status.");
    }

    @FXML
    private void runMaintenanceCheck() {
        try {
            String summary = database.maintenanceSummary();
            database.recordSystemLog("Administration", "Maintenance check", "INFO", "Maintenance check completed.");
            maintenanceResultArea.setText(summary);
            refreshStatusOnly();
        } catch (RuntimeException exception) {
            database.recordSystemLog("Administration", "Maintenance check failed", "ERROR", rootMessage(exception));
            UiAlerts.error("Maintenance check failed", exception);
            maintenanceResultArea.setText(rootMessage(exception));
        }
    }

    private void refreshStatusOnly() {
        BackupRecord backup = database.latestDailyBackupRecord();
        databaseStatusLabel.setText(Files.isRegularFile(DatabaseHandler.databasePath()) ? "Available" : "Missing");
        backupStatusLabel.setText(backup == null ? "No latest daily backup" : backup.getStatus() + " | " + backup.getCreatedAt());
        storageStatusLabel.setText(DatabaseHandler.applicationDataDirectory().toString());
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
