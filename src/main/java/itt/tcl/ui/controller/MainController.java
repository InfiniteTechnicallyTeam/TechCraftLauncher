package itt.tcl.ui.controller;

import itt.tcl.auth.AuthManager;
import itt.tcl.config.TCLPaths;
import itt.tcl.launch.GameLauncher;
import itt.tcl.ui.App;
import itt.tcl.ui.LanguageManager;
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
    @FXML private Text accountText;
    @FXML private Text accountTypeText;
    @FXML private Label avatarLabel;
    @FXML private ComboBox<String> languageCombo;
    @FXML private ComboBox<String> versionCombo;
    @FXML private Text versionCountText;
    @FXML private Button launchBtn;
    @FXML private Button settingsBtn;
    @FXML private Button logoutBtn;
    @FXML private ProgressBar progressBar;
    @FXML private Text statusText;

    @FXML
    public void initialize() {
        configureLanguageSelector();
        loadAccount();
        scanVersions();

        launchBtn.setOnAction(e -> doLaunch());
        settingsBtn.setOnAction(e -> App.getSceneManager().showSettings());
        logoutBtn.setOnAction(e -> doLogout());

        progressBar.setManaged(false);
        progressBar.setVisible(false);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    }

    private void configureLanguageSelector() {
        languageCombo.getItems().setAll(
                LanguageManager.getAvailableLanguages().stream()
                        .map(LanguageManager.Language::displayName)
                        .toList()
        );
        languageCombo.getSelectionModel().select(
                LanguageManager.getLanguage().displayName()
        );
        languageCombo.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldLanguage, newLanguage) -> {
                    if (newLanguage == null) {
                        return;
                    }
                    boolean changed = LanguageManager.setLanguage(
                            LanguageManager.fromDisplayName(newLanguage)
                    );
                    if (changed) {
                        App.getSceneManager().showMain();
                    }
                }
        );
    }

    private void loadAccount() {
        try {
            AuthManager.AuthResult auth = AuthManager.loadAuth();
            if (auth == null) {
                showOfflineAccount(LanguageManager.text("account.offlinePlayer"));
                return;
            }

            String username = auth.username();
            accountText.setText(username);
            accountTypeText.setText(LanguageManager.text(
                    "0".equals(auth.accessToken()) ? "account.offline" : "account.microsoft"
            ));
            avatarLabel.setText(username.isBlank() ? "?" : username.substring(0, 1).toUpperCase());
        } catch (IOException | RuntimeException e) {
            showOfflineAccount(LanguageManager.text("account.offlinePlayer"));
        }
    }

    private void showOfflineAccount(String username) {
        accountText.setText(username);
        accountTypeText.setText(LanguageManager.text("account.offline"));
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
            versionCountText.setText(LanguageManager.text(
                    versions.size() == 1 ? "main.versions.one" : "main.versions.many",
                    versions.size()
            ));

            if (versions.isEmpty()) {
                launchBtn.setDisable(true);
                setStatus(LanguageManager.text("main.status.noVersions"), "status-warning");
            } else {
                versionCombo.getSelectionModel().selectFirst();
                launchBtn.setDisable(false);
                setStatus(LanguageManager.text("main.status.default"), "status-muted");
            }
        } catch (IOException e) {
            launchBtn.setDisable(true);
            versionCountText.setText(LanguageManager.text("main.versions.unavailable"));
            setStatus(
                    LanguageManager.text("main.status.scanFailed", friendlyMessage(e)),
                    "status-error"
            );
        }
    }

    private void doLaunch() {
        String version = versionCombo.getValue();
        if (version == null || version.isBlank()) {
            setStatus(LanguageManager.text("main.status.selectVersion"), "status-warning");
            return;
        }

        setLaunchingState(true);
        setStatus(LanguageManager.text("main.status.preparing", version), "status-active");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage(LanguageManager.text("main.status.checking"));
                GameLauncher.launch(version);
                return null;
            }
        };

        statusText.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            statusText.textProperty().unbind();
            setLaunchingState(false);
            setStatus(LanguageManager.text("main.status.closed"), "status-success");
        });

        task.setOnFailed(e -> {
            statusText.textProperty().unbind();
            setLaunchingState(false);
            setStatus(
                    LanguageManager.text("main.status.launchFailed", friendlyMessage(task.getException())),
                    "status-error"
            );
        });

        Thread worker = new Thread(task, "tcl-game-launcher");
        worker.setDaemon(true);
        worker.start();
    }

    private void setLaunchingState(boolean launching) {
        launchBtn.setDisable(launching);
        launchBtn.setText(LanguageManager.text(
                launching ? "main.launching" : "main.launch"
        ));
        languageCombo.setDisable(launching);
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
            setStatus(
                    LanguageManager.text("main.status.logoutFailed", friendlyMessage(e)),
                    "status-error"
            );
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
            return LanguageManager.text("common.unknownError");
        }
        return error.getMessage();
    }
}
