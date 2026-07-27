package itt.tcl.ui;

import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.concurrent.atomic.AtomicBoolean;

public final class App {
    public static final String APP_NAME = "TechCraft Launcher";

    private static final AtomicBoolean FX_RUNTIME_STARTED = new AtomicBoolean(false);

    private static volatile Stage stage;
    private static volatile SceneManager sceneManager;

    private App() {}

    public static void launchGUI(boolean keepRuntimeAlive) {
        if (FX_RUNTIME_STARTED.compareAndSet(false, true)) {
            try {
                Platform.startup(() -> showWindow(keepRuntimeAlive));
            } catch (RuntimeException e) {
                FX_RUNTIME_STARTED.set(false);
                throw e;
            }
            return;
        }

        Platform.runLater(() -> showWindow(keepRuntimeAlive));
    }

    public static void shutdownGUI() {
        if (!FX_RUNTIME_STARTED.get()) {
            return;
        }

        try {
            Platform.runLater(() -> {
                if (stage != null) {
                    stage.close();
                }
                Platform.exit();
            });
        } catch (IllegalStateException ignored) {
            // The JavaFX runtime has already stopped.
        }
    }

    private static void showWindow(boolean keepRuntimeAlive) {
        Platform.setImplicitExit(!keepRuntimeAlive);

        if (stage == null) {
            stage = new Stage();
            configureStage(stage);
            sceneManager = new SceneManager(stage);
            sceneManager.showLogin();
        }

        boolean alreadyShowing = stage.isShowing();
        if (!alreadyShowing) {
            stage.show();
            Platform.runLater(stage::centerOnScreen);
        }
        stage.toFront();
        stage.requestFocus();
    }

    private static void configureStage(Stage stage) {
        stage.setTitle(APP_NAME);
        stage.setMinWidth(720);
        stage.setMinHeight(500);
        stage.setResizable(true);
    }

    public static SceneManager getSceneManager() {
        if (sceneManager == null) {
            throw new IllegalStateException("The JavaFX application has not started yet.");
        }
        return sceneManager;
    }
}
