package com.wk.pfmis.controllers;

import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.auth.SuperAdminProvisioningService;
import com.wk.pfmis.config.AppConfig;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginControllerTest {
    @TempDir
    Path dataRoot;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start.");
    }

    @BeforeEach
    void initializeAuthDatabase() throws Exception {
        Path envFile = dataRoot.resolve(".env");
        Files.writeString(envFile, """
                PFMIS_SUPER_ADMIN_EMAIL=admin@example.invalid
                PFMIS_SUPER_ADMIN_PASSWORD=BootstrapPass123
                PFMIS_SYSTEM_EMAIL=system@example.invalid
                PFMIS_MAIL_ENABLED=false
                """);
        System.setProperty("pfmis.auth.db.path", dataRoot.resolve("pfmis-auth.db").toString());
        System.setProperty("pfmis.data.dir", dataRoot.toString());
        System.setProperty("PFMIS_ENV_FILE", envFile.toString());
        System.setProperty("pfmis.login.credentials.disabled", "true");
        AppConfig.reload();
        AuthDatabase.getInstance().initialize();
        SuperAdminProvisioningService.getInstance().provisionConfiguredSuperAdministrator();
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty("pfmis.auth.db.path");
        System.clearProperty("pfmis.data.dir");
        System.clearProperty("PFMIS_ENV_FILE");
        System.clearProperty("pfmis.login.credentials.disabled");
        AppConfig.reload();
    }

    @Test
    void loginDisplaysOnlyStandardSignInForm() throws Exception {
        LoadedLogin loaded = loadLogin();

        VBox signInPanel = field(loaded.controller(), "signInPanel", VBox.class);
        VBox resetPanel = field(loaded.controller(), "resetPanel", VBox.class);
        CheckBox rememberMe = field(loaded.controller(), "rememberMeCheckBox", CheckBox.class);
        Button forgotPassword = field(loaded.controller(), "forgotPasswordButton", Button.class);

        assertTrue(signInPanel.isVisible());
        assertTrue(signInPanel.isManaged());
        assertFalse(resetPanel.isVisible());
        assertFalse(resetPanel.isManaged());
        assertTrue(rememberMe.isVisible());
        assertFalse(rememberMe.isDisabled());
        assertTrue(forgotPassword.isVisible());
        assertFalse(forgotPassword.isDisabled());
    }

    @Test
    void usernameAndPasswordFieldsAreEditableAndPickable() throws Exception {
        LoadedLogin loaded = loadLogin();

        TextField username = field(loaded.controller(), "usernameField", TextField.class);
        PasswordField password = field(loaded.controller(), "passwordField", PasswordField.class);

        assertTrue(username.isVisible());
        assertTrue(username.isManaged());
        assertFalse(username.isDisabled());
        assertTrue(username.isEditable());
        assertFalse(username.isMouseTransparent());
        assertTrue(username.isFocusTraversable());

        assertTrue(password.isVisible());
        assertTrue(password.isManaged());
        assertFalse(password.isDisabled());
        assertTrue(password.isEditable());
        assertFalse(password.isMouseTransparent());
        assertTrue(password.isFocusTraversable());
    }

    @Test
    void passwordVisibilityToggleDoesNotLeaveHiddenFieldPickable() throws Exception {
        LoadedLogin loaded = loadLogin();

        runOnFxAndWait(() -> invoke(loaded.controller(), "togglePasswordVisibility"));

        PasswordField password = field(loaded.controller(), "passwordField", PasswordField.class);
        TextField visiblePassword = field(loaded.controller(), "visiblePasswordField", TextField.class);

        assertFalse(password.isVisible());
        assertFalse(password.isManaged());
        assertTrue(password.isMouseTransparent());
        assertFalse(password.isFocusTraversable());
        assertTrue(visiblePassword.isVisible());
        assertTrue(visiblePassword.isManaged());
        assertFalse(visiblePassword.isMouseTransparent());
        assertTrue(visiblePassword.isFocusTraversable());
    }

    @Test
    void blankLoginShowsInlineValidation() throws Exception {
        LoadedLogin loaded = loadLogin();

        runOnFxAndWait(() -> invoke(loaded.controller(), "signIn"));

        Label usernameError = field(loaded.controller(), "loginUsernameErrorLabel", Label.class);
        Label passwordError = field(loaded.controller(), "loginPasswordErrorLabel", Label.class);

        assertTrue(usernameError.isVisible());
        assertTrue(usernameError.isManaged());
        assertTrue(usernameError.getText().contains("required"));
        assertTrue(passwordError.isVisible());
        assertTrue(passwordError.isManaged());
        assertTrue(passwordError.getText().contains("required"));
    }

    @Test
    void forgotPasswordShowsSimpleResetPanel() throws Exception {
        LoadedLogin loaded = loadLogin();

        runOnFxAndWait(() -> invoke(loaded.controller(), "focusPasswordReset"));

        VBox signInPanel = field(loaded.controller(), "signInPanel", VBox.class);
        VBox resetPanel = field(loaded.controller(), "resetPanel", VBox.class);
        TextField resetEmail = field(loaded.controller(), "resetEmailField", TextField.class);

        assertFalse(signInPanel.isVisible());
        assertTrue(resetPanel.isVisible());
        assertFalse(resetEmail.isDisabled());
        assertTrue(resetEmail.isEditable());
        assertFalse(resetEmail.isMouseTransparent());
    }

    @Test
    void loginUiDoesNotExposeBootstrapOrPrivilegeEscalationControls() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/com/wk/pfmis/views/Login.fxml"));
        String controller = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/LoginController.java"));

        assertFalse(fxml.contains("Create " + "First Administrator"));
        assertFalse(fxml.contains("Create the " + "First Super Administrator"));
        assertFalse(fxml.contains("Create User " + "and Open Workspace"));
        assertFalse(fxml.contains("Sign in as " + "Super Administrator"));
        assertFalse(fxml.contains(" OR "));
        assertFalse(fxml.contains("bootstrap"));
        assertFalse(controller.contains("create" + "Administrator"));
        assertFalse(controller.contains("AuthenticationMode"));
        assertFalse(controller.contains("BOOTSTRAP"));
        assertFalse(controller.contains("signInAs" + "SuperAdmin"));
    }

    private LoadedLogin loadLogin() throws Exception {
        URL resource = LoginControllerTest.class.getResource("/com/wk/pfmis/views/Login.fxml");
        assertNotNull(resource);
        FutureTask<LoadedLogin> task = new FutureTask<>(() -> {
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            return new LoadedLogin(root, loader.getController());
        });
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void runOnFxAndWait(Runnable runnable) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            runnable.run();
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    private static <T> T field(Object target, String fieldName, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing field: " + fieldName, exception);
        }
    }

    private static void invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing method: " + methodName, exception);
        }
    }

    private record LoadedLogin(Parent root, LoginController controller) {
    }
}
