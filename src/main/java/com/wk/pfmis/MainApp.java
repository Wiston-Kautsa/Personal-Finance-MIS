package com.wk.pfmis;

import com.wk.pfmis.db.DatabaseHandler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MainApp extends Application {
    private FileChannel lockChannel;
    private FileLock appLock;

    @Override
    public void start(Stage stage) throws IOException {
        if (!acquireSingleInstanceLock()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("PFMIS");
            alert.setHeaderText("PFMIS is already running");
            alert.setContentText("Only one system session can be opened at a time.");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        DatabaseHandler.getInstance().initializeDatabase();

        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/wk/pfmis/views/Dashboard.fxml"));
        Scene scene = new Scene(loader.load(), 1180, 740);
        scene.getStylesheets().add(MainApp.class.getResource("/com/wk/pfmis/css/Theme.css").toExternalForm());

        stage.setTitle("PFMIS - Personal Finance Management Information System");
        stage.setMinWidth(980);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    private boolean acquireSingleInstanceLock() throws IOException {
        lockChannel = FileChannel.open(
                Path.of("pfmis.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        );
        appLock = lockChannel.tryLock();
        return appLock != null;
    }

    @Override
    public void stop() throws Exception {
        if (appLock != null && appLock.isValid()) {
            appLock.release();
        }
        if (lockChannel != null && lockChannel.isOpen()) {
            lockChannel.close();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
