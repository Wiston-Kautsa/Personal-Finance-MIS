package com.wk.pfmis;

import com.wk.pfmis.ai.BundledLocalAiManager;
import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.config.AppConfig;
import com.wk.pfmis.config.FxConfig;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.controllers.ControllerSessionState;
import com.wk.pfmis.diagnostics.PackagingRuntimeCheck;
import com.wk.pfmis.fx.ExchangeRateService;
import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.PrivilegedActionService;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.utils.ReadableTextSupport;
import com.wk.pfmis.utils.RequiredFieldSupport;
import com.wk.pfmis.utils.StartupDiagnostics;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
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
    public void start(Stage stage) {
        StartupDiagnostics.logStage("JavaFX start entered");
        try {
            instance = this;
            primaryStage = stage;
            StartupDiagnostics.logStage("Loading application configuration");
            AppConfig.ensureLocalEnvFileExists();
            System.setProperty(
                    "org.slf4j.simpleLogger.defaultLogLevel",
                    AppConfig.loggingConfig().level().name().toLowerCase(Locale.ENGLISH)
            );
            StartupDiagnostics.cleanupOldLogs(AppConfig.loggingConfig().retentionDays());
            StartupDiagnostics.logStage("Acquiring single-instance lock");
            if (!acquireSingleInstanceLock()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("PFMIS");
                alert.setHeaderText("PFMIS is already running");
                alert.setContentText("Only one system session can be opened at a time.");
                alert.showAndWait();
                Platform.exit();
                return;
            }

            StartupDiagnostics.logStage("Initializing authentication database");
            AuthDatabase.getInstance().initialize();
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            StartupDiagnostics.logStage("Loading sign-in screen");
            showLogin();
            stage.show();
            StartupDiagnostics.logStage("JavaFX sign-in screen displayed");
        } catch (Throwable throwable) {
            StartupDiagnostics.logFailure("PFMIS startup failed", throwable);
            showStartupFailureDialog();
            Platform.exit();
        }
    }

    public static void showLogin() {
        requireInstance().stopWorkspaceServices();
        ControllerSessionState.reset();
        PrivilegedActionService.getInstance().invalidate();
        UserSession.clear();
        requireInstance().loadScene("Login.fxml", "PFMIS - Sign In", 980, 640);
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
        PrivilegedActionService.getInstance().invalidate();
        UserSession.switchWorkspace(targetUser);
        app.openWorkspace("Workspace switch");
    }

    public static void returnToOwnWorkspace() {
        MainApp app = requireInstance();
        app.stopWorkspaceServices();
        PrivilegedActionService.getInstance().invalidate();
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
        try {
            StartupDiagnostics.logStage("Initializing workspace database");
            database.initializeDatabase();
        } catch (RuntimeException exception) {
            StartupDiagnostics.logFailure("Workspace database initialization failed", exception);
            stopWorkspaceServices();
            showWorkspaceMigrationFailure(reason, exception);
            return;
        }
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
        startExchangeRateRefresh(database);
        loadScene(
                "Dashboard.fxml",
                "PFMIS - " + workspace.getDisplayName() + " Workspace",
                1180,
                740
        );
    }

    private void startExchangeRateRefresh(DatabaseHandler database) {
        FxConfig config = AppConfig.fxConfig();
        if (!config.enabled()) {
            database.recordSystemLog("Foreign Exchange", "Startup Refresh Skipped", "INFO", "Automatic exchange rates are disabled.");
            return;
        }
        if (!config.refreshOnStartup()) {
            database.recordSystemLog("Foreign Exchange", "Startup Refresh Skipped", "INFO", "Automatic exchange-rate startup refresh is disabled.");
            return;
        }
        ExchangeRateService.getInstance().refreshRatesAsync().whenComplete((quotes, throwable) -> {
            if (throwable == null) {
                database.recordSystemLog("Foreign Exchange", "Startup Refresh Completed", "INFO", quotes.size() + " exchange rate(s) refreshed in background.");
            } else {
                database.recordSystemLog("Foreign Exchange", "Startup Refresh Failed", "WARNING", rootMessage(throwable));
            }
        });
    }

    private void showWorkspaceMigrationFailure(String retryReason, RuntimeException exception) {
        Throwable root = rootCause(exception);
        Label title = new Label("Workspace Could Not Be Opened");
        title.getStyleClass().add("page-title");

        Label message = new Label("PFMIS could not finish the required database migration for this workspace. "
                + "No screen was opened because financial pages must not run against an incomplete schema.");
        message.setWrapText(true);
        message.getStyleClass().add("form-note");

        Label reason = new Label("Reason: " + rootMessage(root));
        reason.setWrapText(true);
        reason.getStyleClass().add("workspace-error-reason");

        Label databasePath = new Label("Workspace database: " + DatabaseHandler.databasePath());
        databasePath.setWrapText(true);
        databasePath.getStyleClass().add("workspace-error-path");

        TextArea details = new TextArea(failureDetails(exception));
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(7);
        details.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(details, Priority.ALWAYS);

        TitledPane technicalDetails = new TitledPane("Technical details", details);
        technicalDetails.setExpanded(false);
        technicalDetails.setMaxWidth(Double.MAX_VALUE);

        Button retryButton = new Button("Retry Migration");
        retryButton.getStyleClass().add("primary-button");
        retryButton.setOnAction(event -> openWorkspace(retryReason));

        Button signInButton = new Button("Back to Sign In");
        signInButton.getStyleClass().add("secondary-button");
        signInButton.setOnAction(event -> showLogin());

        VBox actions = new VBox(8, retryButton, signInButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(14, title, message, reason, databasePath, technicalDetails, actions);
        panel.getStyleClass().addAll("panel", "workspace-error-panel");
        panel.setMaxWidth(760);
        panel.setPadding(new Insets(24));

        BorderPane rootPane = new BorderPane(panel);
        rootPane.getStyleClass().addAll("app-root", "workspace-migration-error");
        BorderPane.setAlignment(panel, Pos.CENTER);
        rootPane.setPadding(new Insets(28));

        Scene scene = new Scene(rootPane, 980, 640);
        scene.getStylesheets().add(MainApp.class.getResource("/com/wk/pfmis/css/Theme.css").toExternalForm());
        primaryStage.setFullScreen(false);
        primaryStage.setIconified(false);
        primaryStage.setMaximized(false);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setTitle("PFMIS - Workspace Migration Required");
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
    }

    private static String failureDetails(Throwable exception) {
        Throwable root = rootCause(exception);
        String summary = exception.getClass().getName() + ": " + rootMessage(exception);
        String rootSummary = root == exception
                ? ""
                : System.lineSeparator() + System.lineSeparator()
                + root.getClass().getName() + ": " + rootMessage(root);
        return summary + rootSummary;
    }

    private void loadScene(String fxml, String title, double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/wk/pfmis/views/" + fxml));
            Parent root = loader.load();
            RequiredFieldSupport.apply(root);
            ReadableTextSupport.apply(root);
            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(MainApp.class.getResource("/com/wk/pfmis/css/Theme.css").toExternalForm());
            primaryStage.setFullScreen(false);
            primaryStage.setIconified(false);
            primaryStage.setMaximized(false);
            if ("Login.fxml".equals(fxml)) {
                primaryStage.setMinWidth(900);
                primaryStage.setMinHeight(600);
            } else {
                primaryStage.setMinWidth(920);
                primaryStage.setMinHeight(620);
            }
            primaryStage.setTitle(title);
            primaryStage.setScene(scene);
            primaryStage.setWidth(width);
            primaryStage.setHeight(height);
            primaryStage.centerOnScreen();
            if ("Login.fxml".equals(fxml)) {
                Platform.runLater(() -> {
                    primaryStage.setMaximized(false);
                    primaryStage.setWidth(width);
                    primaryStage.setHeight(height);
                    primaryStage.centerOnScreen();
                });
            }
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
                StartupDiagnostics.logFailure("PFMIS Local AI startup failed", exception);
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
                    PrivilegedActionService.getInstance().invalidate();
                    UserSession.clear();
                    super.stop();
                }
            }
        }
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                StartupDiagnostics.logFailure("Uncaught exception on " + thread.getName(), throwable));
        StartupDiagnostics.logStage("Launcher main entered");
        if (args != null && Arrays.asList(args).contains("--pfmis-runtime-check")) {
            try {
                PackagingRuntimeCheck.runChecks();
                System.out.println("PFMIS packaged runtime check passed.");
            } catch (Throwable throwable) {
                StartupDiagnostics.logFailure("Packaged runtime check failed", throwable);
                System.err.println("PFMIS packaged runtime check failed: " + throwable.getMessage());
                System.exit(1);
            }
            return;
        }
        launch(args);
    }

    private void showStartupFailureDialog() {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("PFMIS");
            alert.setHeaderText("PFMIS could not start correctly.");
            alert.setContentText("Diagnostic information has been saved to:\n" + StartupDiagnostics.startupLogPath());
            alert.showAndWait();
        } catch (RuntimeException ignored) {
            // The startup log remains available even if JavaFX cannot display the dialog.
        }
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

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
