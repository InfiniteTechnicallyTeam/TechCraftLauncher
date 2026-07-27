package itt.tcl.ui.controller;

import itt.tcl.ui.App;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;

public class SettingsController {
    @FXML private ComboBox<String> mirrorCombo;
    @FXML private Button backBtn;

    @FXML
    public void initialize() {
        mirrorCombo.getItems().addAll("BMCLAPI (国内加速)", "Official (官方)");
        mirrorCombo.getSelectionModel().selectFirst();

        backBtn.setOnAction(e -> App.getSceneManager().showMain());
    }
}
