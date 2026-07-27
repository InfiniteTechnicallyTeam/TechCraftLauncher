package itt.tcl.ui.controller;

import itt.tcl.auth.AuthManager;
import itt.tcl.config.TCLPaths;
import itt.tcl.launch.GameLauncher;
import itt.tcl.ui.App;
import itt.tcl.ui.SceneManager;
import itt.tcl.version.VersionInstaller;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;

public class MainController {
    @FXML private Text accountText;
    @FXML private ComboBox<String> versionCombo;
    @FXML private Button launchBtn;
    @FXML private Button settingsBtn;
    @FXML private Button logoutBtn;
    @FXML private ProgressBar progressBar;
    @FXML private Text statusText;

    @FXML
    public void initialize() {
        // Load account
        try {
            AuthManager.AuthResult auth = AuthManager.loadAuth();
            if (auth != null) {
                accountText.setText(auth.username());
            } else {
                accountText.setText("Offline");
            }
        } catch (IOException e) {
            accountText.setText("Offline");
        }

        // Scan versions
        scanVersions();

        // Button actions
        launchBtn.setOnAction(e -> doLaunch());
        settingsBtn.setOnAction(e -> App.getSceneManager().showSettings());
        logoutBtn.setOnAction(e -> doLogout());
    }

    private void scanVersions() {
        versionCombo.getItems().clear();
        try (Stream<java.nio.file.Path> dirs = Files.list(TCLPaths.VERSIONS_DIR)) {
            dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .forEach(v -> versionCombo.getItems().add(v));
            if (!versionCombo.getItems().isEmpty()) {
                versionCombo.getSelectionModel().selectFirst();
            }
        } catch (IOException e) {
            statusText.setText("No versions found.");
        }
    }

    private void doLaunch() {
        String version = versionCombo.getValue();
        if (version == null || version.isEmpty()) {
            statusText.setText("Please select a version.");
            return;
        }

        launchBtn.setDisable(true);
        progressBar.setVisible(true);
        statusText.setText("Preparing " + version + "...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Downloading files...");
                VersionInstaller.ensureVersion(version);
                updateMessage("Launching...");
                GameLauncher.launch(version);
                return null;
            }
        };

        task.messageProperty().addListener((obs, old, msg) -> Platform.runLater(() -> statusText.setText(msg)));

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            launchBtn.setDisable(false);
            progressBar.setVisible(false);
            statusText.setText("Game exited.");
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            launchBtn.setDisable(false);
            progressBar.setVisible(false);
            statusText.setText("Error: " + task.getException().getMessage());
        }));

        new Thread(task).start();
    }

    private void doLogout() {
        try {
            AuthManager.logout();
        } catch (IOException e) {
            // ignore
        }
        App.getSceneManager().showLogin();
    }
}
