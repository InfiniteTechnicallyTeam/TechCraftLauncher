package itt.tcl.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class App extends Application {
    private static SceneManager sceneManager;

    @Override
    public void start(Stage stage) {
        stage.setTitle("TechCraftLauncher");
        stage.setResizable(false);

        sceneManager = new SceneManager(stage);
        sceneManager.showLogin();

        stage.show();
    }

    public static SceneManager getSceneManager() {
        return sceneManager;
    }

    public static void launchGUI() {
        launch();
    }
}
