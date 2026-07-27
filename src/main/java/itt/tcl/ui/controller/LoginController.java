package itt.tcl.ui.controller;

import itt.tcl.auth.AuthManager;
import itt.tcl.ui.App;
import itt.tcl.ui.SceneManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;

public class LoginController {
    @FXML private Button msLoginBtn;
    @FXML private TextField offlineNameField;
    @FXML private Button offlineLoginBtn;
    @FXML private Text statusText;
    @FXML private ProgressIndicator progressIndicator;

    @FXML
    public void initialize() {
        progressIndicator.setVisible(false);

        msLoginBtn.setOnAction(e -> doMicrosoftLogin());
        offlineLoginBtn.setOnAction(e -> doOfflineLogin());
    }

    private void doMicrosoftLogin() {
        msLoginBtn.setDisable(true);
        offlineLoginBtn.setDisable(true);
        progressIndicator.setVisible(true);
        statusText.setText("Starting Microsoft login...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AuthManager.login();
                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            SceneManager scene = App.getSceneManager();
            scene.showMain();
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            msLoginBtn.setDisable(false);
            offlineLoginBtn.setDisable(false);
            statusText.setText("Login failed: " + task.getException().getMessage());
        }));

        new Thread(task).start();
    }

    private void doOfflineLogin() {
        String name = offlineNameField.getText().trim();
        if (name.isEmpty()) {
            statusText.setText("Please enter a username.");
            return;
        }
        // Save offline auth
        try {
            com.google.gson.JsonObject auth = new com.google.gson.JsonObject();
            auth.addProperty("access_token", "0");
            auth.addProperty("uuid", "00000000-0000-0000-0000-000000000000");
            auth.addProperty("username", name);
            auth.addProperty("client_id", "");
            java.nio.file.Files.writeString(
                    itt.tcl.config.TCLPaths.TCL_DIR.resolve("auth.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(auth)
            );
        } catch (Exception ex) {
            statusText.setText("Error: " + ex.getMessage());
            return;
        }
        App.getSceneManager().showMain();
    }
}
