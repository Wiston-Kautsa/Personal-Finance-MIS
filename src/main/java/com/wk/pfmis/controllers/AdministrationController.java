package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.models.BackupRecord;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.nio.file.Files;
import java.nio.file.Path;

public class AdministrationController {
    @FXML private Label administrationStatusLabel;
    @FXML private Label adminSettingsStatusLabel;
    @FXML private Label adminBackupStatusLabel;
    @FXML private Label adminMaintenanceStatusLabel;
    @FXML private Label adminRecordsStatusLabel;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        administrationStatusLabel.setText("One administration workspace for Smart Analysis settings, backup, restore, and maintenance checks.");
        AiSettings settings = database.getAiSettings();
        BackupRecord backup = database.latestDailyBackupRecord();
        Path databasePath = DatabaseHandler.databasePath();

        adminSettingsStatusLabel.setText(settings.isEnabled() ? "Configured" : "Not configured");
        adminBackupStatusLabel.setText(backup == null ? "No latest backup" : backup.getStatus() + " | " + backup.getCreatedAt());
        adminMaintenanceStatusLabel.setText(Files.isRegularFile(databasePath) ? "Available" : "Missing");
        adminRecordsStatusLabel.setText("Audit logs and sync tools");
    }
}
