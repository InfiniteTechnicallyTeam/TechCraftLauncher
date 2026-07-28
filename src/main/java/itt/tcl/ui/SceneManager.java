package itt.tcl.ui;

import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public final class SceneManager {
    private static final String STYLESHEET = "/itt/tcl/ui/css/style.css";
    private static final Duration TRANSITION_DURATION = Duration.millis(180);

    private final Stage stage;

    public SceneManager(Stage stage) {
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    public void showLogin() {
        show(new View("/itt/tcl/ui/fxml/login.fxml", "window.login", 900, 580));
    }

    public void showMain() {
        show(new View("/itt/tcl/ui/fxml/main.fxml", "window.main", 980, 650));
    }

    public void showSettings() {
        show(new View("/itt/tcl/ui/fxml/settings.fxml", "window.settings", 820, 600));
    }

    public void showDownloads() {
        show(new View("/itt/tcl/ui/fxml/download.fxml", "window.downloads", 1080, 720));
    }

    private void show(View view) {
        try {
            URL fxml = requireResource(view.fxmlPath());
            URL stylesheet = requireResource(STYLESHEET);

            FXMLLoader loader = new FXMLLoader(fxml, LanguageManager.bundle());
            Parent content = loader.load();
            FontManager.applyLanguageFont(content);
            String title = App.APP_NAME + " — " + LanguageManager.text(view.titleKey());
            Parent root = WindowChrome.wrap(stage, content, title);
            if (root != content) {
                FontManager.applyLanguageFont(root);
            }
            Scene scene = stage.getScene();
            double windowHeight = view.height() + WindowChrome.additionalHeight();

            if (scene == null) {
                scene = new Scene(root, view.width(), windowHeight);
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
                if (!stage.isMaximized()) {
                    stage.setWidth(view.width());
                    stage.setHeight(windowHeight);
                }
            }

            scene.getStylesheets().setAll(stylesheet.toExternalForm());
            stage.setTitle(title);

            if (stage.isShowing()) {
                stage.centerOnScreen();
            }
            playEntrance(root);
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
        root.setOpacity(0);
        FadeTransition transition = new FadeTransition(TRANSITION_DURATION, root);
        transition.setFromValue(0);
        transition.setToValue(1);
        transition.play();
    }

    private record View(String fxmlPath, String titleKey, double width, double height) {}
}
