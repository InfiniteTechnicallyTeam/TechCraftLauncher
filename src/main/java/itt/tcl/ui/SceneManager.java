package itt.tcl.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public final class SceneManager {
    private static final String STYLESHEET = "/itt/tcl/ui/css/style.css";
    private static final Duration TRANSITION_DURATION = Duration.millis(210);

    private final Stage stage;
    private StackPane contentHost;
    private Parent windowRoot;
    private Object currentController;
    private ParallelTransition activeTransition;

    public SceneManager(Stage stage) {
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    public void showLogin() {
        show(new View("/itt/tcl/ui/fxml/login.fxml", "window.login", 900, 580));
    }

    public void showMain() {
        show(new View("/itt/tcl/ui/fxml/main.fxml", "window.main", 1080, 720));
    }

    public void showSettings() {
        show(new View("/itt/tcl/ui/fxml/settings.fxml", "window.settings", 1080, 720));
    }

    public void showDownloads() {
        show(new View("/itt/tcl/ui/fxml/download.fxml", "window.downloads", 1080, 720));
    }

    private void show(View view) {
        try {
            boolean preservePosition = stage.isShowing();
            double previousX = stage.getX();
            double previousY = stage.getY();
            URL fxml = requireResource(view.fxmlPath());
            URL stylesheet = requireResource(STYLESHEET);

            FXMLLoader loader = new FXMLLoader(fxml, LanguageManager.bundle());
            Parent content = loader.load();
            Object nextController = loader.getController();
            FontManager.applyLanguageFont(content);
            String title = App.APP_NAME + " — " + LanguageManager.text(view.titleKey());
            Scene scene = stage.getScene();
            double windowHeight = view.height() + WindowChrome.additionalHeight();
            boolean firstView = contentHost == null;

            if (currentController instanceof ViewLifecycle lifecycle) {
                lifecycle.onViewHidden();
            }

            if (firstView) {
                contentHost = new StackPane(content);
                windowRoot = WindowChrome.wrap(stage, contentHost, title);
                if (windowRoot != contentHost) {
                    FontManager.applyLanguageFont(windowRoot);
                }
                scene = new Scene(windowRoot, view.width(), windowHeight);
                stage.setScene(scene);
            } else {
                if (activeTransition != null) {
                    activeTransition.stop();
                }
                contentHost.getChildren().setAll(content);
                if (!stage.isMaximized()) {
                    stage.setWidth(view.width());
                    stage.setHeight(windowHeight);
                }
            }

            scene.getStylesheets().setAll(stylesheet.toExternalForm());
            WindowChrome.updateTitle(stage, title);

            if (preservePosition && !stage.isMaximized()) {
                stage.setX(previousX);
                stage.setY(previousY);
            }
            currentController = nextController;
            if (currentController instanceof ViewLifecycle lifecycle) {
                lifecycle.onViewShown();
            }
            if (!firstView) {
                playEntrance(content);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load view: " + view.fxmlPath(), e);
        }
    }

    private URL requireResource(String path) {
        return Objects.requireNonNull(
                getClass().getResource(path),
                () -> "Missing UI resource: " + path
        );
    }

    private void playEntrance(Parent root) {
        root.setOpacity(0.92);
        root.setTranslateX(12);
        FadeTransition transition = new FadeTransition(TRANSITION_DURATION, root);
        transition.setFromValue(0.92);
        transition.setToValue(1);
        transition.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition movement = new TranslateTransition(
                TRANSITION_DURATION,
                root
        );
        movement.setFromX(12);
        movement.setToX(0);
        movement.setInterpolator(Interpolator.EASE_OUT);

        activeTransition = new ParallelTransition(transition, movement);
        activeTransition.setOnFinished(event -> activeTransition = null);
        activeTransition.play();
    }

    private record View(String fxmlPath, String titleKey, double width, double height) {}
}
