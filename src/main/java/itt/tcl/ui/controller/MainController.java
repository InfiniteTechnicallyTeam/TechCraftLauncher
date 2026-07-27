package itt.tcl.ui.controller;

import itt.tcl.auth.AuthManager;
import itt.tcl.config.TCLPaths;
import itt.tcl.launch.GameLauncher;
import itt.tcl.ui.App;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class MainController {
    private static final String DEFAULT_STATUS = "Choose an installed version to start playing.";

    @FXML private Text accountText;
    @FXML private Text accountTypeText;
    @FXML private Label avatarLabel;
    @FXML private ComboBox<String> versionCombo;
    @FXML private Text versionCountText;
    @FXML private Button launchBtn;
    @FXML private Button settingsBtn;
    @FXML private Button logoutBtn;
    @FXML private ProgressBar progressBar;
    @FXML private Text statusText;

    @FXML
    public void initialize() {
        loadAccount();
        scanVersions();

        launchBtn.setOnAction(e -> doLaunch());
        settingsBtn.setOnAction(e -> App.getSceneManager().showSettings());
        logoutBtn.setOnAction(e -> doLogout());

        progressBar.setManaged(false);
        progressBar.setVisible(false);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    }

    private void loadAccount() {
        try {
            AuthManager.AuthResult auth = AuthManager.loadAuth();
            if (auth == null) {
                showOfflineAccount("Offline Player");
                return;
            }

            String username = auth.username();
            accountText.setText(username);
            accountTypeText.setText("0".equals(auth.accessToken()) ? "Offline account" : "Microsoft account");
            avatarLabel.setText(username.isBlank() ? "?" : username.substring(0, 1).toUpperCase());
        } catch (IOException | RuntimeException e) {
            showOfflineAccount("Offline Player");
        }
    }

    private void showOfflineAccount(String username) {
        accountText.setText(username);
        accountTypeText.setText("Offline account");
        avatarLabel.setText(username.substring(0, 1).toUpperCase());
    }

    private void scanVersions() {
        versionCombo.getItems().clear();
        try (Stream<Path> dirs = Files.list(TCLPaths.VERSIONS_DIR)) {
            List<String> versions = dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .toList();

            versionCombo.getItems().setAll(versions);
            versionCountText.setText(versions.size() + (versions.size() == 1 ? " version installed" : " versions installed"));

            if (versions.isEmpty()) {
                launchBtn.setDisable(true);
                setStatus("No installed versions were found in .minecraft/versions.", "status-warning");
            } else {
                versionCombo.getSelectionModel().selectFirst();
                launchBtn.setDisable(false);
                setStatus(DEFAULT_STATUS, "status-muted");
            }
        } catch (IOException e) {
            launchBtn.setDisable(true);
            versionCountText.setText("Versions unavailable");
            setStatus("Could not scan installed versions: " + friendlyMessage(e), "status-error");
        }
    }

    private void doLaunch() {
        String version = versionCombo.getValue();
        if (version == null || version.isBlank()) {
            setStatus("Please select a version before launching.", "status-warning");
            return;
        }

        setLaunchingState(true);
        setStatus("Preparing " + version + "…", "status-active");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Checking game files and downloading anything missing…");
                GameLauncher.launch(version);
                return null;
            }
        };

        statusText.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            statusText.textProperty().unbind();
            setLaunchingState(false);
            setStatus("Minecraft closed. Ready for another launch.", "status-success");
        });

        task.setOnFailed(e -> {
            statusText.textProperty().unbind();
            setLaunchingState(false);
            setStatus("Launch failed: " + friendlyMessage(task.getException()), "status-error");
        });

        Thread worker = new Thread(task, "tcl-game-launcher");
        worker.setDaemon(true);
        worker.start();
    }

    private void setLaunchingState(boolean launching) {
        launchBtn.setDisable(launching);
        launchBtn.setText(launching ? "LAUNCHING…" : "LAUNCH GAME");
        versionCombo.setDisable(launching);
        settingsBtn.setDisable(launching);
        logoutBtn.setDisable(launching);
        progressBar.setManaged(launching);
        progressBar.setVisible(launching);
    }

    private void doLogout() {
        try {
            AuthManager.logout();
            App.getSceneManager().showLogin();
        } catch (IOException e) {
            setStatus("Could not sign out: " + friendlyMessage(e), "status-error");
        }
    }

    private void setStatus(String message, String styleClass) {
        statusText.textProperty().unbind();
        statusText.setText(message);
        statusText.getStyleClass().removeAll(
                "status-muted", "status-active", "status-success", "status-warning", "status-error"
        );
        statusText.getStyleClass().add(styleClass);
    }

    private String friendlyMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "Unknown error";
        }
        return error.getMessage();
    }
}
