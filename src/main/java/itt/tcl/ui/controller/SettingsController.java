package itt.tcl.ui.controller;

import itt.tcl.config.TCLPaths;
import itt.tcl.ui.App;
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
    @FXML private Button backBtn;

    @FXML
    public void initialize() {
        mirrorCombo.getItems().setAll(
                "Automatic — BMCLAPI with official fallback",
                "Official Mojang services"
        );
        mirrorCombo.getSelectionModel().selectFirst();
        updateMirrorDescription(0);

        dataPathText.setText(TCLPaths.MINECRAFT_DIR.toAbsolutePath().normalize().toString());
        mirrorCombo.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldIndex, newIndex) -> updateMirrorDescription(newIndex.intValue())
        );
        openFolderBtn.setOnAction(e -> openGameFolder());
        backBtn.setOnAction(e -> App.getSceneManager().showMain());
    }

    private void updateMirrorDescription(int selectedIndex) {
        if (selectedIndex == 1) {
            mirrorDescriptionText.setText(
                    "The current launcher automatically falls back to official Mojang services when a mirror is unavailable."
            );
        } else {
            mirrorDescriptionText.setText(
                    "Uses BMCLAPI first for faster downloads in China, then retries with official Mojang services."
            );
        }
    }

    private void openGameFolder() {
        if (!Desktop.isDesktopSupported()) {
            showError("Opening folders is not supported on this system.");
            return;
        }
        try {
            Desktop.getDesktop().open(TCLPaths.MINECRAFT_DIR.toFile());
            settingsStatusText.setText("");
        } catch (IOException | UnsupportedOperationException e) {
            showError("Could not open the game folder: " + friendlyMessage(e));
        }
    }

    private void showError(String message) {
        settingsStatusText.setText(message);
        settingsStatusText.getStyleClass().removeAll("status-muted", "status-error");
        settingsStatusText.getStyleClass().add("status-error");
    }

    private String friendlyMessage(Throwable error) {
        if (error.getMessage() == null || error.getMessage().isBlank()) {
            return "Unknown error";
        }
        return error.getMessage();
    }
}
