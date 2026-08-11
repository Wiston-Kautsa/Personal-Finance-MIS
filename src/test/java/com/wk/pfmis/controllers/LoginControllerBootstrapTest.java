package com.wk.pfmis.controllers;

import com.wk.pfmis.auth.AuthDatabase;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginControllerBootstrapTest {
    @TempDir
    Path dataRoot;

    private AuthDatabase authDatabase;

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
    void initializeAuthDatabase() {
        System.setProperty("pfmis.auth.db.path", dataRoot.resolve("pfmis-auth.db").toString());
        System.setProperty("pfmis.data.dir", dataRoot.toString());
        authDatabase = AuthDatabase.getInstance();
        authDatabase.initialize();
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty("pfmis.auth.db.path");
        System.clearProperty("pfmis.data.dir");
    }

    @Test
    void noAdministratorDisplaysBootstrapForm() throws Exception {
        LoadedLogin loaded = loadLogin();

        VBox bootstrapForm = field(loaded.controller(), "bootstrapForm", VBox.class);
        VBox loginForm = field(loaded.controller(), "loginForm", VBox.class);

        assertTrue(bootstrapForm.isVisible());
        assertTrue(bootstrapForm.isManaged());
        assertFalse(loginForm.isVisible());
        assertFalse(loginForm.isManaged());
    }

    @Test
    void bootstrapUsernameFieldIsEditable() throws Exception {
        LoadedLogin loaded = loadLogin();

        TextField username = field(loaded.controller(), "bootstrapUsernameField", TextField.class);

        assertTrue(username.isVisible());
        assertTrue(username.isManaged());
        assertFalse(username.isDisabled());
        assertTrue(username.isEditable());
        assertFalse(username.isMouseTransparent());
        assertTrue(username.isFocusTraversable());
    }

    @Test
    void bootstrapPasswordFieldIsEditable() throws Exception {
        LoadedLogin loaded = loadLogin();

        PasswordField password = field(loaded.controller(), "bootstrapPasswordField", PasswordField.class);

        assertTrue(password.isVisible());
        assertTrue(password.isManaged());
        assertFalse(password.isDisabled());
        assertTrue(password.isEditable());
        assertFalse(password.isMouseTransparent());
        assertTrue(password.isFocusTraversable());
    }

    @Test
    void bootstrapPasswordVisibilityToggleDoesNotLeaveHiddenFieldsPickable() throws Exception {
        LoadedLogin loaded = loadLogin();

        runOnFxAndWait(() -> invoke(loaded.controller(), "toggleBootstrapPasswordVisibility"));

        PasswordField password = field(loaded.controller(), "bootstrapPasswordField", PasswordField.class);
        TextField visiblePassword = field(loaded.controller(), "bootstrapVisiblePasswordField", TextField.class);
        PasswordField confirm = field(loaded.controller(), "bootstrapConfirmPasswordField", PasswordField.class);
        TextField visibleConfirm = field(loaded.controller(), "bootstrapVisibleConfirmPasswordField", TextField.class);

        assertFalse(password.isVisible());
        assertFalse(password.isManaged());
        assertTrue(password.isMouseTransparent());
        assertFalse(password.isFocusTraversable());
        assertTrue(visiblePassword.isVisible());
        assertTrue(visiblePassword.isManaged());
        assertFalse(visiblePassword.isMouseTransparent());

        assertFalse(confirm.isVisible());
        assertFalse(confirm.isManaged());
        assertTrue(confirm.isMouseTransparent());
        assertFalse(confirm.isFocusTraversable());
        assertTrue(visibleConfirm.isVisible());
        assertTrue(visibleConfirm.isManaged());
        assertFalse(visibleConfirm.isMouseTransparent());
    }

    @Test
    void administratorCreationSwitchesToSignInWithoutRestart() throws Exception {
        LoadedLogin loaded = loadLogin();

        runOnFxAndWait(() -> {
            field(loaded.controller(), "bootstrapFullNameField", TextField.class).setText("PFMIS Administrator");
            field(loaded.controller(), "bootstrapUsernameField", TextField.class).setText("pfmisadmin");
            field(loaded.controller(), "bootstrapEmailField", TextField.class).setText("admin@example.invalid");
            field(loaded.controller(), "bootstrapPasswordField", PasswordField.class).setText("ValidPass123");
            field(loaded.controller(), "bootstrapConfirmPasswordField", PasswordField.class).setText("ValidPass123");
            invoke(loaded.controller(), "createAdministrator");
        });

        VBox bootstrapForm = field(loaded.controller(), "bootstrapForm", VBox.class);
        VBox loginForm = field(loaded.controller(), "loginForm", VBox.class);
        TextField username = field(loaded.controller(), "usernameField", TextField.class);

        assertTrue(authDatabase.hasActiveSuperAdministrator());
        assertFalse(bootstrapForm.isVisible());
        assertTrue(loginForm.isVisible());
        assertEquals("pfmisadmin", username.getText());
    }

    @Test
    void existingAdministratorDisplaysNormalSignInForm() throws Exception {
        authDatabase.registerUser(
                "PFMIS Administrator",
                "pfmisadmin",
                "admin@example.invalid",
                "ValidPass123"
        );

        LoadedLogin loaded = loadLogin();

        VBox bootstrapForm = field(loaded.controller(), "bootstrapForm", VBox.class);
        VBox loginForm = field(loaded.controller(), "loginForm", VBox.class);
        CheckBox rememberMe = field(loaded.controller(), "rememberMeCheckBox", CheckBox.class);
        Button forgotPassword = field(loaded.controller(), "forgotPasswordButton", Button.class);

        assertFalse(bootstrapForm.isVisible());
        assertTrue(loginForm.isVisible());
        assertTrue(rememberMe.isVisible());
        assertFalse(rememberMe.isDisabled());
        assertTrue(forgotPassword.isVisible());
        assertFalse(forgotPassword.isDisabled());
    }

    @Test
    void loginUiDoesNotExposeSuperAdministratorEscalationButton() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/com/wk/pfmis/views/Login.fxml"));
        String controller = Files.readString(Path.of("src/main/java/com/wk/pfmis/controllers/LoginController.java"));

        assertFalse(fxml.contains("Sign in as Super Administrator"));
        assertFalse(fxml.contains("signInAsSuperAdmin"));
        assertFalse(controller.contains("signInAsSuperAdmin"));
    }

    private LoadedLogin loadLogin() throws Exception {
        URL resource = LoginControllerBootstrapTest.class.getResource("/com/wk/pfmis/views/Login.fxml");
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
