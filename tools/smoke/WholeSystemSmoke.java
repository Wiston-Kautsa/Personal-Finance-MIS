package com.wk.pfmis.controllers;

import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class WholeSystemSmoke {
    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path dataRoot = Path.of("target", "smoke-workspace-" + System.currentTimeMillis()).toAbsolutePath();
        Files.createDirectories(dataRoot);
        System.setProperty("pfmis.data.dir", dataRoot.toString());

        AuthDatabase authDatabase = AuthDatabase.getInstance();
        authDatabase.initialize();
        SystemUser smokeUser = authDatabase.registerUser(
                "Smoke Administrator",
                "smoke-admin",
                "smoke@example.invalid",
                "SmokePass123!"
        );
        UserSession.login(smokeUser);
        DatabaseHandler.getInstance().initializeDatabase();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                List<String> fxmlFiles = Files.list(Path.of("src", "main", "resources", "com", "wk", "pfmis", "views"))
                        .filter(path -> path.getFileName().toString().endsWith(".fxml"))
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .toList();
                List<String> loaded = new ArrayList<>();
                for (String fxml : fxmlFiles) {
                    if ("Register.fxml".equals(fxml)) {
                        loaded.add(fxml + "#SkippedStandaloneAuthRedirect");
                    } else if ("CommunitySavings.fxml".equals(fxml)) {
                        for (CommunitySavingsMode mode : CommunitySavingsMode.values()) {
                            NavigationBus.requestCommunitySavingsMode(mode);
                            load(fxml);
                            loaded.add(fxml + "#" + mode);
                        }
                    } else if ("Budgets.fxml".equals(fxml)) {
                        for (BudgetMode mode : BudgetMode.values()) {
                            NavigationBus.requestBudgetMode(mode);
                            load(fxml);
                            loaded.add(fxml + "#" + mode);
                        }
                    } else if ("Transactions.fxml".equals(fxml)) {
                        NavigationBus.requestTransactionLedgerFilter("Expense");
                        load(fxml);
                        loaded.add(fxml + "#ExpenseFilter");
                        NavigationBus.requestTransactionLedgerFilter(null);
                        load(fxml);
                        loaded.add(fxml);
                    } else if ("RegisterAsset.fxml".equals(fxml)) {
                        NavigationBus.requestAssetRegistration("Project", 77, "Smoke Project", "Smoke handoff guidance.");
                        load(fxml);
                        loaded.add(fxml + "#Handoff");
                    } else {
                        load(fxml);
                        loaded.add(fxml);
                    }
                }
                System.out.println("Loaded FXML screens: " + loaded.size());
                for (String item : loaded) {
                    System.out.println("OK " + item);
                }
                exerciseDashboardSidebar();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (failure.get() != null) {
            failure.get().printStackTrace(System.err);
            System.exit(1);
        }
        System.exit(0);
    }

    private static Parent load(String fxml) throws Exception {
        return new FXMLLoader(WholeSystemSmoke.class.getResource("/com/wk/pfmis/views/" + fxml)).load();
    }

    private static void exerciseDashboardSidebar() throws Exception {
        LoadedView dashboard = loadWithNamespace("Dashboard.fxml");
        VBox sidebar = requireNode(dashboard.namespace(), "sidebarNavigation", VBox.class);
        StackPane contentPane = requireNode(dashboard.namespace(), "contentPane", StackPane.class);
        List<Button> buttons = new ArrayList<>();
        collectSidebarButtons(sidebar, buttons);
        for (Button button : buttons) {
            button.fire();
            int activeCount = countActiveButtons(sidebar);
            if (activeCount != 1) {
                throw new IllegalStateException("Expected one active sidebar button after "
                        + button.getText() + " but found " + activeCount + ".");
            }
            if (contentPane.getChildren().size() > 1) {
                throw new IllegalStateException(button.getText() + " stacked content panes instead of replacing the view.");
            }
            if (containsLoadFailure(contentPane)) {
                throw new IllegalStateException(button.getText() + " opened a view-load failure panel.");
            }
        }
        System.out.println("Dashboard sidebar buttons exercised: " + buttons.size());
    }

    private static void collectSidebarButtons(Node node, List<Button> buttons) {
        if (node instanceof Button button && isSidebarNavigationButton(button)) {
            buttons.add(button);
        }
        if (node instanceof javafx.scene.control.TitledPane titledPane && titledPane.getContent() != null) {
            collectSidebarButtons(titledPane.getContent(), buttons);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectSidebarButtons(child, buttons);
            }
        }
    }

    private static boolean isSidebarNavigationButton(Button button) {
        return button.getStyleClass().contains("nav-button")
                || button.getStyleClass().contains("nav-sub-button")
                || button.getStyleClass().contains("setup-section-button");
    }

    private static int countActiveButtons(Node node) {
        int count = node instanceof Button button && button.getStyleClass().contains("active") ? 1 : 0;
        if (node instanceof javafx.scene.control.TitledPane titledPane && titledPane.getContent() != null) {
            count += countActiveButtons(titledPane.getContent());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                count += countActiveButtons(child);
            }
        }
        return count;
    }

    private static boolean containsLoadFailure(Node node) {
        if (node instanceof Label label && label.getText() != null && label.getText().contains("could not be opened")) {
            return true;
        }
        if (node instanceof javafx.scene.control.TitledPane titledPane && titledPane.getContent() != null) {
            return containsLoadFailure(titledPane.getContent());
        }
        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                if (containsLoadFailure(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static LoadedView loadWithNamespace(String fxml) throws Exception {
        FXMLLoader loader = new FXMLLoader(WholeSystemSmoke.class.getResource("/com/wk/pfmis/views/" + fxml));
        Parent root = loader.load();
        return new LoadedView(root, loader.getNamespace());
    }

    private static <T extends Node> T requireNode(Map<String, Object> namespace, String id, Class<T> type) {
        Object match = namespace.get(id);
        if (match == null || !type.isInstance(match)) {
            throw new IllegalStateException("Missing Dashboard node: " + id);
        }
        return type.cast(match);
    }

    private record LoadedView(Parent root, Map<String, Object> namespace) {
    }
}
