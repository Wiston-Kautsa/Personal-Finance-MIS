package com.wk.pfmis;

import com.wk.pfmis.ai.BundledLocalAiManager;
import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.controllers.ControllerSessionState;
import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainApp extends Application {
    private static MainApp instance;
    private Stage primaryStage;
    private FileChannel lockChannel;
    private FileLock appLock;
    private ScheduledExecutorService automaticBackupExecutor;

    @Override
    public void start(Stage stage) throws IOException {
        instance = this;
        primaryStage = stage;
        if (!acquireSingleInstanceLock()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("PFMIS");
            alert.setHeaderText("PFMIS is already running");
            alert.setContentText("Only one system session can be opened at a time.");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        AuthDatabase.getInstance().initialize();
        stage.setMinWidth(920);
        stage.setMinHeight(620);
        showLogin();
        stage.show();
    }

    public static void showLogin() {
        requireInstance().stopWorkspaceServices();
        ControllerSessionState.reset();
        UserSession.clear();
        requireInstance().loadScene("Login.fxml", "PFMIS - Sign In", 940, 650);
    }

    public static void showRegistration() {
        requireInstance().loadScene("Register.fxml", "PFMIS - Create User", 960, 700);
    }

    public static void completeLogin(SystemUser user) {
        UserSession.login(user);
        requireInstance().openWorkspace("Login");
    }

    public static void switchWorkspace(SystemUser targetUser) {
        MainApp app = requireInstance();
        SystemUser actingUser = UserSession.getAuthenticatedUser();
        AuthDatabase.getInstance().recordWorkspaceAccess(actingUser.getId(), targetUser.getId());
        app.stopWorkspaceServices();
        UserSession.switchWorkspace(targetUser);
        app.openWorkspace("Workspace switch");
    }

    public static void returnToOwnWorkspace() {
        MainApp app = requireInstance();
        app.stopWorkspaceServices();
        UserSession.returnToOwnWorkspace();
        app.openWorkspace("Return to own workspace");
    }

    public static void logout() {
        MainApp app = requireInstance();
        try {
            if (UserSession.isAuthenticated()) {
                int userId = UserSession.getAuthenticatedUser().getId();
                try {
                    DatabaseHandler.getInstance().recordSystemLog(
                            "Security",
                            "Logout",
                            "INFO",
                            UserSession.getAuthenticatedUser().getUsername() + " signed out."
                    );
                } finally {
                    AuthDatabase.getInstance().recordLogout(userId);
                }
            }
        } catch (RuntimeException ignored) {
            // Logout must continue even if an audit entry cannot be written.
        }
        showLogin();
    }

    private void openWorkspace(String reason) {
        ControllerSessionState.reset();
        DatabaseHandler database = DatabaseHandler.getInstance();
        database.initializeDatabase();
        SystemUser signedIn = UserSession.getAuthenticatedUser();
        SystemUser workspace = UserSession.getWorkspaceUser();
        database.recordSystemLog(
                "Security",
                reason,
                "INFO",
                "Signed in as " + signedIn.getUsername() + "; active workspace: " + workspace.getUsername() + "."
        );
        startAutomaticDailyBackup(database);
        startBundledLocalAiIfConfigured(database);
        loadScene(
                "Dashboard.fxml",
                "PFMIS - " + workspace.getDisplayName() + " Workspace",
                1180,
                740
        );
    }

    private void loadScene(String fxml, String title, double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/wk/pfmis/views/" + fxml));
            Scene scene = new Scene(loader.load(), width, height);
            scene.getStylesheets().add(MainApp.class.getResource("/com/wk/pfmis/css/Theme.css").toExternalForm());
            primaryStage.setTitle(title);
            primaryStage.setScene(scene);
            primaryStage.centerOnScreen();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to open " + fxml + ".", exception);
        }
    }

    private boolean acquireSingleInstanceLock() throws IOException {
        lockChannel = FileChannel.open(
                DatabaseHandler.lockFilePath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        );
        appLock = lockChannel.tryLock();
        return appLock != null;
    }

    private void startBundledLocalAiIfConfigured(DatabaseHandler database) {
        int workspaceUserId = UserSession.getWorkspaceUserId();
        AiSettings settings = database.getAiSettings();
        if (!settings.isEnabled() || !settings.isBundledLocalProvider() || !settings.isAutoStartLocal()) {
            database.recordSystemLog("Smart Analysis", "PFMIS Local AI skipped", "INFO", "PFMIS Local AI auto-start is disabled or not selected.");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                BundledLocalAiManager.ensureReady();
                if (UserSession.isAuthenticated() && UserSession.getWorkspaceUserId() == workspaceUserId) {
                    database.recordSystemLog("Smart Analysis", "PFMIS Local AI ready", "INFO", "Bundled llama.cpp runtime started successfully.");
                }
            } catch (RuntimeException exception) {
                System.err.println(exception.getMessage());
                try {
                    database.recordSystemLog("Smart Analysis", "PFMIS Local AI failed", "ERROR", rootMessage(exception));
                } catch (RuntimeException ignored) {
                    // The workspace may have changed while the provider was starting.
                }
            }
        });
    }

    private void startAutomaticDailyBackup(DatabaseHandler database) {
        stopAutomaticBackup();
        int workspaceUserId = UserSession.getWorkspaceUserId();
        automaticBackupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "pfmis-automatic-daily-backup");
            thread.setDaemon(true);
            return thread;
        });
        automaticBackupExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (!UserSession.isAuthenticated() || UserSession.getWorkspaceUserId() != workspaceUserId) {
                    return;
                }
                BackupRecord record = database.ensureDailyBackup();
                if (UserSession.isSuperAdmin() && UserSession.isViewingOwnWorkspace()) {
                    AuthDatabase.getInstance().ensureDailySecurityBackup();
                }
                if ("AUTO_DAILY".equals(record.getStatus())) {
                    database.recordSystemLog("Administration", "Automatic daily backup", "INFO", record.getBackupFile());
                }
            } catch (RuntimeException exception) {
                try {
                    database.recordSystemLog("Administration", "Automatic backup failed", "ERROR", rootMessage(exception));
                } catch (RuntimeException ignored) {
                    // The user may have signed out while the scheduled task was running.
                }
            }
        }, 10, 3600, TimeUnit.SECONDS);
    }

    private void stopWorkspaceServices() {
        stopAutomaticBackup();
        BundledLocalAiManager.shutdown();
    }

    private void stopAutomaticBackup() {
        if (automaticBackupExecutor != null) {
            automaticBackupExecutor.shutdownNow();
            automaticBackupExecutor = null;
        }
    }

    @Override
    public void stop() throws Exception {
        try {
            if (UserSession.isAuthenticated()) {
                try {
                    DatabaseHandler.getInstance().recordSystemLog("Application", "Shutdown", "INFO", "PFMIS application closed.");
                } catch (RuntimeException ignored) {
                    // Continue cleanup.
                }
            }
        } finally {
            try {
                stopWorkspaceServices();
            } finally {
                try {
                    if (appLock != null && appLock.isValid()) {
                        appLock.release();
                    }
                } finally {
                    if (lockChannel != null && lockChannel.isOpen()) {
                        lockChannel.close();
                    }
                    UserSession.clear();
                    super.stop();
                }
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static MainApp requireInstance() {
        if (instance == null) {
            throw new IllegalStateException("PFMIS application has not started.");
        }
        return instance;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
