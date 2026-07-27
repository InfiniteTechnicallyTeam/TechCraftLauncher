package itt.tcl.ui.controller;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import itt.tcl.auth.AuthManager;
import itt.tcl.config.TCLPaths;
import itt.tcl.ui.App;
import itt.tcl.ui.LanguageManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

public final class LoginController {
    private static final int MAX_USERNAME_LENGTH = 16;

    @FXML private Button msLoginBtn;
    @FXML private TextField offlineNameField;
    @FXML private Button offlineLoginBtn;
    @FXML private Text statusText;
    @FXML private ProgressIndicator progressIndicator;

    @FXML
    public void initialize() {
        progressIndicator.setManaged(false);
        progressIndicator.setVisible(false);

        msLoginBtn.setOnAction(e -> doMicrosoftLogin());
        offlineLoginBtn.setOnAction(e -> doOfflineLogin());
        offlineNameField.setOnAction(e -> doOfflineLogin());
        offlineNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > MAX_USERNAME_LENGTH) {
                offlineNameField.setText(oldValue);
            }
            if (!newValue.isBlank()) {
                clearStatus();
            }
        });
    }

    private void doMicrosoftLogin() {
        setBusy(true);
        setStatus(LanguageManager.text("login.status.microsoft"), "status-active");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AuthManager.login();
                return null;
            }
        };

        task.setOnSucceeded(e -> App.getSceneManager().showMain());

        task.setOnFailed(e -> {
            setBusy(false);
            setStatus(
                    LanguageManager.text("login.status.failed", friendlyMessage(task.getException())),
                    "status-error"
            );
        });

        Thread worker = new Thread(task, "tcl-microsoft-login");
        worker.setDaemon(true);
        worker.start();
    }

    private void doOfflineLogin() {
        String name = offlineNameField.getText().trim();
        if (name.isEmpty()) {
            setStatus(LanguageManager.text("login.status.enterName"), "status-warning");
            offlineNameField.requestFocus();
            return;
        }
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            setStatus(LanguageManager.text("login.status.invalidName"), "status-warning");
            offlineNameField.requestFocus();
            return;
        }

        setBusy(true);
        try {
            JsonObject auth = new JsonObject();
            auth.addProperty("access_token", "0");
            auth.addProperty("uuid", createOfflineUuid(name));
            auth.addProperty("username", name);
            auth.addProperty("client_id", "");
            Files.writeString(
                    TCLPaths.TCL_DIR.resolve("auth.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(auth)
            );
        } catch (Exception ex) {
            setBusy(false);
            setStatus(
                    LanguageManager.text("login.status.profileFailed", friendlyMessage(ex)),
                    "status-error"
            );
            return;
        }
        App.getSceneManager().showMain();
    }

    private String createOfflineUuid(String username) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

    private void setBusy(boolean busy) {
        msLoginBtn.setDisable(busy);
        offlineLoginBtn.setDisable(busy);
        offlineNameField.setDisable(busy);
        progressIndicator.setManaged(busy);
        progressIndicator.setVisible(busy);
    }

    private void clearStatus() {
        setStatus("", "status-muted");
    }

    private void setStatus(String message, String styleClass) {
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
