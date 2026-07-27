package itt.tcl.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SceneManager {
    private final Stage stage;

    public SceneManager(Stage stage) {
        this.stage = stage;
    }

    public void showLogin() {
        loadScene("/itt/tcl/ui/fxml/login.fxml", 500, 400);
    }

    public void showMain() {
        loadScene("/itt/tcl/ui/fxml/main.fxml", 700, 500);
    }

    public void showSettings() {
        loadScene("/itt/tcl/ui/fxml/settings.fxml", 500, 400);
    }

    private void loadScene(String fxmlPath, double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(getClass().getResource("/itt/tcl/ui/css/style.css").toExternalForm());
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
