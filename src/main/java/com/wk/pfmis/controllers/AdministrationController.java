package com.wk.pfmis.controllers;

import com.wk.pfmis.ai.BundledLocalAiManager;
import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.AuthenticationEventRecord;
import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.SystemLogRecord;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public class AdministrationController {
    @FXML private Label administrationStatusLabel;
    @FXML private Label adminSettingsStatusLabel;
    @FXML private Label adminBackupStatusLabel;
    @FXML private Label adminMaintenanceStatusLabel;
    @FXML private Label adminRecordsStatusLabel;
    @FXML private Label adminVersionLabel;
    @FXML private Label adminSchemaLabel;
    @FXML private Label adminActiveUserLabel;
    @FXML private Label adminWorkspaceLabel;
    @FXML private Label adminDatabaseLocationLabel;
    @FXML private Label adminDatabaseSizeLabel;
    @FXML private Label adminIntegrityLabel;
    @FXML private Label adminDataQualityLabel;
    @FXML private Label adminSecurityWarningsLabel;
    @FXML private Label adminLocalAiLabel;
    @FXML private Label adminAiProviderLabel;
    @FXML private Label adminAutomaticBackupLabel;
    @FXML private Label adminLastLoginLabel;
    @FXML private Label adminFailedLoginLabel;
    @FXML private Label adminConclusionLabel;
    @FXML private TextArea adminActionResultArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final AuthDatabase authDatabase = AuthDatabase.getInstance();

    @FXML
    public void initialize() {
        refreshDashboard();
    }

    @FXML
    private void refreshDashboard() {
        AiSettings settings = database.getAiSettings();
        BackupRecord backup = database.latestDailyBackupRecord();
        Path databasePath = DatabaseHandler.databasePath();
        SystemUser signedIn = UserSession.getAuthenticatedUser();
        SystemUser workspace = UserSession.getWorkspaceUser();

        administrationStatusLabel.setText("Administration dashboard for workspace health, security, Smart Analysis, backup, data quality and maintenance.");
        adminSettingsStatusLabel.setText(settings.isEnabled() ? "Configured" : "Not configured");
        adminBackupStatusLabel.setText(backup == null ? "No latest backup" : backup.getStatus() + " | " + backup.getCreatedAt());
        adminMaintenanceStatusLabel.setText(Files.isRegularFile(databasePath) ? "Available" : "Missing");
        adminRecordsStatusLabel.setText(database.unresolvedDataQualityIssueCount() + " data quality signal(s)");
        adminVersionLabel.setText(System.getProperty("pfmis.version", "Development"));
        adminSchemaLabel.setText(database.schemaVersionSummary());
        adminActiveUserLabel.setText(signedIn.getDisplayName() + " | " + signedIn.getRoleDisplay());
        adminWorkspaceLabel.setText(workspace.getDisplayName() + " (" + workspace.getUsername() + ")");
        adminDatabaseLocationLabel.setText(databasePath.toString());
        adminDatabaseSizeLabel.setText(databaseSizeText(databasePath));
        adminIntegrityLabel.setText(database.databaseIntegrityStatus());
        adminDataQualityLabel.setText(database.unresolvedDataQualityIssueCount() + " issue signal(s)");
        SecuritySnapshot security = securitySnapshot();
        adminSecurityWarningsLabel.setText(security.warningText());
        adminLastLoginLabel.setText(signedIn.getLastLoginAt().isBlank() ? "Not recorded" : signedIn.getLastLoginAt());
        adminFailedLoginLabel.setText(security.lastFailedLogin());
        adminLocalAiLabel.setText(localAiStatus());
        adminAiProviderLabel.setText(settings.getProviderDisplayName() + " | " + settings.getModel());
        adminAutomaticBackupLabel.setText("Enabled while PFMIS is open");
        adminConclusionLabel.setText(systemConclusion(backup, database.unresolvedDataQualityIssueCount(), security.warningCount()));
        adminActionResultArea.setText("Run System Health Check to see detailed evidence.");
    }

    @FXML
    private void runSystemHealthCheck() {
        StringBuilder builder = new StringBuilder();
        builder.append("System Health Check\n\n");
        builder.append("Database integrity: ").append(database.databaseIntegrityStatus()).append('\n');
        builder.append("Schema: ").append(database.schemaVersionSummary()).append('\n');
        builder.append("Data quality issues: ").append(database.unresolvedDataQualityIssueCount()).append('\n');
        builder.append("Local AI: ").append(localAiStatus()).append('\n');
        builder.append("Backup: ").append(adminBackupStatusLabel.getText()).append('\n');
        builder.append("Security warnings: ").append(adminSecurityWarningsLabel.getText()).append('\n');
        adminActionResultArea.setText(builder.toString());
        database.recordSystemLog("Administration", "System health check", "INFO", "Administration dashboard health check completed.");
        refreshStatusOnly();
    }

    @FXML
    private void checkDatabaseIntegrity() {
        String integrity = database.databaseIntegrityStatus();
        adminIntegrityLabel.setText(integrity);
        adminActionResultArea.setText("Database integrity: " + integrity);
        database.recordSystemLog("Administration", "Database integrity check", "INFO", integrity);
    }

    @FXML
    private void backupNow() {
        try {
            BackupRecord backup = database.createBackup(DatabaseHandler.defaultBackupDirectory(), "administration-safety-backup");
            adminBackupStatusLabel.setText(backup.getStatus() + " | " + backup.getCreatedAt());
            adminActionResultArea.setText("Backup created:\n" + backup.getBackupFile() + "\nChecksum: " + backup.getChecksum());
            refreshStatusOnly();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to create backup", exception);
        }
    }

    @FXML
    private void openBackupHistory() {
        List<BackupRecord> backups = database.listBackupHistory();
        StringBuilder builder = new StringBuilder("Backup History\n\n");
        if (backups.isEmpty()) {
            builder.append("No backup records found.");
        } else {
            for (BackupRecord backup : backups) {
                builder.append(backup.getCreatedAt())
                        .append(" | ")
                        .append(backup.getStatus())
                        .append(" | ")
                        .append(backup.getBackupFile())
                        .append('\n');
            }
        }
        adminActionResultArea.setText(builder.toString());
    }

    @FXML
    private void reviewDataQuality() {
        adminActionResultArea.setText(database.dataQualitySummary());
        refreshStatusOnly();
    }

    @FXML
    private void viewAuditTrail() {
        List<SystemLogRecord> records = database.listSystemLogHistory(30);
        StringBuilder builder = new StringBuilder("Recent Audit Trail\n\n");
        for (SystemLogRecord record : records) {
            builder.append(record.getCreatedAt())
                    .append(" | ")
                    .append(record.getModuleName())
                    .append(" | ")
                    .append(record.getActionName())
                    .append(" | ")
                    .append(record.getSeverity())
                    .append(" | ")
                    .append(record.getDetails())
                    .append('\n');
        }
        adminActionResultArea.setText(builder.toString());
    }

    @FXML
    private void openAiStatus() {
        AiSettings settings = database.getAiSettings();
        adminActionResultArea.setText("AI Status\n\n"
                + "Provider: " + settings.getProviderDisplayName() + "\n"
                + "Model: " + settings.getModel() + "\n"
                + "Local AI: " + localAiStatus() + "\n"
                + "Endpoint: " + settings.getEndpoint() + "\n"
                + "Auto-start: " + (settings.isAutoStartLocal() ? "Enabled" : "Disabled"));
    }

    @FXML
    private void databaseMaintenance() {
        adminActionResultArea.setText(database.maintenanceSummary());
        database.recordSystemLog("Administration", "Database maintenance review", "INFO", "Maintenance summary opened from Administration.");
        refreshStatusOnly();
    }

    @FXML
    private void manageWorkspace() {
        Path databasePath = DatabaseHandler.databasePath();
        adminActionResultArea.setText("Workspace Management\n\n"
                + "Signed-in user: " + UserSession.getAuthenticatedUser().getDisplayName() + "\n"
                + "Active workspace: " + UserSession.getWorkspaceUser().getDisplayName() + "\n"
                + "Workspace database: " + databasePath + "\n"
                + "Backup folder: " + DatabaseHandler.defaultBackupDirectory() + "\n"
                + "Deletion policy: use Archive and Restore or Danger Zone setup controls for high-impact actions.");
    }

    private void refreshStatusOnly() {
        BackupRecord backup = database.latestDailyBackupRecord();
        adminBackupStatusLabel.setText(backup == null ? "No latest backup" : backup.getStatus() + " | " + backup.getCreatedAt());
        adminIntegrityLabel.setText(database.databaseIntegrityStatus());
        adminDataQualityLabel.setText(database.unresolvedDataQualityIssueCount() + " issue signal(s)");
        adminLocalAiLabel.setText(localAiStatus());
        SecuritySnapshot security = securitySnapshot();
        adminSecurityWarningsLabel.setText(security.warningText());
        adminFailedLoginLabel.setText(security.lastFailedLogin());
        adminConclusionLabel.setText(systemConclusion(backup, database.unresolvedDataQualityIssueCount(), security.warningCount()));
    }

    private String databaseSizeText(Path databasePath) {
        try {
            long bytes = Files.isRegularFile(databasePath) ? Files.size(databasePath) : 0;
            FileStore store = Files.getFileStore(databasePath.getParent());
            return String.format("%.2f MB | free %.2f GB", bytes / 1_048_576.0, store.getUsableSpace() / 1_073_741_824.0);
        } catch (Exception exception) {
            return "Not available";
        }
    }

    private String localAiStatus() {
        if (!Files.isRegularFile(BundledLocalAiManager.serverExecutable())
                || !Files.isRegularFile(BundledLocalAiManager.modelFile())) {
            return "Not Available";
        }
        String status = BundledLocalAiManager.healthStatus();
        return "ok".equalsIgnoreCase(status) ? "Running" : "Starting / Not Ready";
    }

    private SecuritySnapshot securitySnapshot() {
        if (!UserSession.isSuperAdmin()) {
            return new SecuritySnapshot(0, "Super admin only", "Authentication detail visible to Super Administrators.");
        }
        try {
            List<AuthenticationEventRecord> events = authDatabase.listAuthenticationEvents(UserSession.getAuthenticatedUser().getId(), 50);
            int failures = 0;
            String lastFailed = "None in recent events";
            for (AuthenticationEventRecord event : events) {
                if ("FAILED".equals(event.getResult())) {
                    failures++;
                    if ("None in recent events".equals(lastFailed)) {
                        lastFailed = event.getCreatedAt() + " | " + event.getUsername() + " | " + event.getEventType();
                    }
                }
            }
            return new SecuritySnapshot(failures, failures + " recent failed security event(s)", lastFailed);
        } catch (RuntimeException exception) {
            return new SecuritySnapshot(0, "Not available", rootMessage(exception));
        }
    }

    private String systemConclusion(BackupRecord backup, int dataIssues, int securityWarnings) {
        List<String> issues = new java.util.ArrayList<>();
        if (backup == null) {
            issues.add("no verified backup is recorded");
        } else if (backupLooksOld(backup)) {
            issues.add("the latest backup may be stale");
        }
        if (dataIssues > 0) {
            issues.add(dataIssues + " data-quality signal(s) need review");
        }
        if (securityWarnings > 0) {
            issues.add(securityWarnings + " recent security warning(s) were found");
        }
        if ("Not Available".equals(localAiStatus())) {
            issues.add("local AI is not available");
        }
        if (issues.isEmpty()) {
            return "System health is acceptable. Backup, data quality, security and local AI checks do not currently require attention.";
        }
        return "System health requires attention: " + String.join(", ", issues) + ".";
    }

    private boolean backupLooksOld(BackupRecord backup) {
        try {
            String createdAt = backup.getCreatedAt();
            if (createdAt == null || createdAt.isBlank()) {
                return true;
            }
            String normalized = createdAt.replace(' ', 'T');
            LocalDateTime backupTime = LocalDateTime.parse(normalized.length() > 19 ? normalized.substring(0, 19) : normalized);
            return backupTime.isBefore(LocalDateTime.now().minusDays(7));
        } catch (RuntimeException exception) {
            return false;
        }
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

    private record SecuritySnapshot(int warningCount, String warningText, String lastFailedLogin) {
    }
}
