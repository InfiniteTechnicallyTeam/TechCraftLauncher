package itt.tcl.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public final class App extends Application {
    public static final String APP_NAME = "TechCraft Launcher";

    private static SceneManager sceneManager;

    @Override
    public void start(Stage stage) {
        stage.setTitle(APP_NAME);
        stage.setMinWidth(720);
        stage.setMinHeight(500);
        stage.setResizable(true);

        sceneManager = new SceneManager(stage);
        sceneManager.showLogin();

        stage.show();
        Platform.runLater(stage::centerOnScreen);
    }

    public static SceneManager getSceneManager() {
        if (sceneManager == null) {
            throw new IllegalStateException("The JavaFX application has not started yet.");
        }
        return sceneManager;
    }

    public static void launchGUI(String... args) {
        launch(args);
    }
}
