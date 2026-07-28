package itt.tcl.ui.controller;

import itt.tcl.config.TCLPaths;
import itt.tcl.ui.App;
import itt.tcl.ui.LanguageManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.text.Text;

import java.awt.Desktop;
import java.io.IOException;

public final class SettingsController {
    @FXML private ComboBox<String> mirrorCombo;
    @FXML private Text mirrorDescriptionText;
    @FXML private Text dataPathText;
    @FXML private Text settingsStatusText;
    @FXML private Button openFolderBtn;
    @FXML private Button homeBtn;
    @FXML private Button downloadsBtn;

    @FXML
    public void initialize() {
        mirrorCombo.getItems().setAll(
                LanguageManager.text("settings.source.auto"),
                LanguageManager.text("settings.source.official")
        );
        mirrorCombo.getSelectionModel().selectFirst();
        updateMirrorDescription(0);

        dataPathText.setText(TCLPaths.MINECRAFT_DIR.toAbsolutePath().normalize().toString());
        mirrorCombo.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldIndex, newIndex) -> updateMirrorDescription(newIndex.intValue())
        );
        openFolderBtn.setOnAction(e -> openGameFolder());
        homeBtn.setOnAction(e -> App.getSceneManager().showMain());
        downloadsBtn.setOnAction(e -> App.getSceneManager().showDownloads());
    }

    private void updateMirrorDescription(int selectedIndex) {
        if (selectedIndex == 1) {
            mirrorDescriptionText.setText(LanguageManager.text(
                    "settings.source.officialDescription"
            ));
        } else {
            mirrorDescriptionText.setText(LanguageManager.text(
                    "settings.source.autoDescription"
            ));
        }
    }

    private void openGameFolder() {
        if (!Desktop.isDesktopSupported()) {
            showError(LanguageManager.text("settings.folderUnsupported"));
            return;
        }
        try {
            Desktop.getDesktop().open(TCLPaths.MINECRAFT_DIR.toFile());
            settingsStatusText.setText("");
        } catch (IOException | UnsupportedOperationException e) {
            showError(LanguageManager.text("settings.folderFailed", friendlyMessage(e)));
        }
    }

    private void showError(String message) {
        settingsStatusText.setText(message);
        settingsStatusText.getStyleClass().removeAll("status-muted", "status-error");
        settingsStatusText.getStyleClass().add("status-error");
    }

    private String friendlyMessage(Throwable error) {
        if (error.getMessage() == null || error.getMessage().isBlank()) {
            return LanguageManager.text("common.unknownError");
        }
        return error.getMessage();
    }
}
