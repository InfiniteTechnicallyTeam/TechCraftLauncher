package itt.tcl.ui.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import itt.tcl.config.TCLPaths;
import itt.tcl.download.DownloadCatalogService;
import itt.tcl.download.DownloadCatalogService.GameVersion;
import itt.tcl.download.DownloadCatalogService.LoaderVersion;
import itt.tcl.download.DownloadCatalogService.OptiFineVersion;
import itt.tcl.download.DownloadCatalogService.ProjectFile;
import itt.tcl.download.DownloadCatalogService.ProjectResult;
import itt.tcl.download.LoaderInstallService;
import itt.tcl.download.LoaderType;
import itt.tcl.download.ProjectSource;
import itt.tcl.ui.App;
import itt.tcl.ui.LanguageManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class DownloadController {
    @FXML private Button homeBtn;
    @FXML private Button settingsBtn;

    @FXML private ToggleButton gameTabBtn;
    @FXML private ToggleButton modsTabBtn;
    @FXML private ToggleButton packsTabBtn;
    @FXML private VBox gamePane;
    @FXML private VBox modsPane;
    @FXML private VBox packsPane;

    @FXML private ComboBox<GameVersion> gameVersionCombo;
    @FXML private ComboBox<LoaderType> loaderCombo;
    @FXML private ComboBox<LoaderVersion> loaderVersionCombo;
    @FXML private CheckBox optiFineCheck;
    @FXML private ComboBox<OptiFineVersion> optiFineVersionCombo;
    @FXML private Text compatibilityText;
    @FXML private Button installBtn;

    @FXML private TextField modQueryField;
    @FXML private ComboBox<ProjectSource> modSourceCombo;
    @FXML private ComboBox<GameVersion> modGameVersionCombo;
    @FXML private ComboBox<LoaderType> modLoaderCombo;
    @FXML private Button modSearchBtn;
    @FXML private ListView<ProjectResult> modResultsList;
    @FXML private ComboBox<String> modTargetCombo;
    @FXML private Button modDownloadBtn;

    @FXML private TextField packQueryField;
    @FXML private ComboBox<ProjectSource> packSourceCombo;
    @FXML private ComboBox<GameVersion> packGameVersionCombo;
    @FXML private ComboBox<LoaderType> packLoaderCombo;
    @FXML private Button packSearchBtn;
    @FXML private ListView<ProjectResult> packResultsList;
    @FXML private Button packDownloadBtn;

    @FXML private ProgressBar downloadProgress;
    @FXML private Text downloadStatusText;

    private final DownloadCatalogService catalog =
            new DownloadCatalogService();
    private final LoaderInstallService installer =
            new LoaderInstallService(catalog);
    private final AtomicInteger catalogRequest = new AtomicInteger();
    private volatile boolean busy;

    @FXML
    public void initialize() {
        configureTabs();
        configureGameInstaller();
        configureProjectBrowsers();

        homeBtn.setOnAction(event -> App.getSceneManager().showMain());
        settingsBtn.setOnAction(event -> App.getSceneManager().showSettings());
        installBtn.setOnAction(event -> installSelectedGame());
        modSearchBtn.setOnAction(event -> searchMods());
        modDownloadBtn.setOnAction(event -> downloadSelectedMod());
        packSearchBtn.setOnAction(event -> searchPacks());
        packDownloadBtn.setOnAction(event -> downloadSelectedPack());
        modQueryField.setOnAction(event -> searchMods());
        packQueryField.setOnAction(event -> searchPacks());

        downloadProgress.setManaged(false);
        downloadProgress.setVisible(false);
        downloadProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        showStatus(LanguageManager.text("download.status.loadingCatalog"), "status-active");
        reloadInstalledTargets();
        loadGameCatalog();
    }

    private void configureTabs() {
        gameTabBtn.setOnAction(event -> showTab(gamePane, gameTabBtn));
        modsTabBtn.setOnAction(event -> showTab(modsPane, modsTabBtn));
        packsTabBtn.setOnAction(event -> showTab(packsPane, packsTabBtn));
        showTab(gamePane, gameTabBtn);
    }

    private void configureGameInstaller() {
        loaderCombo.getItems().setAll(LoaderType.values());
        loaderCombo.getSelectionModel().select(LoaderType.VANILLA);
        loaderCombo.valueProperty().addListener(
                (observable, oldValue, newValue) -> refreshInstallerCatalog()
        );
        gameVersionCombo.valueProperty().addListener(
                (observable, oldValue, newValue) -> refreshInstallerCatalog()
        );
        loaderVersionCombo.valueProperty().addListener(
                (observable, oldValue, newValue) -> updateCompatibility()
        );
        optiFineCheck.selectedProperty().addListener(
                (observable, oldValue, newValue) -> {
                    optiFineVersionCombo.setDisable(!newValue);
                    if (newValue && optiFineVersionCombo.getValue() == null) {
                        optiFineVersionCombo.getSelectionModel().selectFirst();
                    }
                    updateCompatibility();
                }
        );
        optiFineVersionCombo.valueProperty().addListener(
                (observable, oldValue, newValue) -> updateCompatibility()
        );
        optiFineCheck.setDisable(true);
        optiFineVersionCombo.setDisable(true);
    }

    private void configureProjectBrowsers() {
        List<LoaderType> modLoaders = List.of(
                LoaderType.FORGE,
                LoaderType.NEOFORGE,
                LoaderType.FABRIC,
                LoaderType.QUILT
        );
        modSourceCombo.getItems().setAll(ProjectSource.values());
        packSourceCombo.getItems().setAll(ProjectSource.values());
        modSourceCombo.getSelectionModel().select(ProjectSource.MODRINTH);
        packSourceCombo.getSelectionModel().select(ProjectSource.MODRINTH);
        modSourceCombo.valueProperty().addListener(
                (observable, oldValue, newValue) -> updateProjectSource(
                        newValue,
                        modResultsList
                )
        );
        packSourceCombo.valueProperty().addListener(
                (observable, oldValue, newValue) -> updateProjectSource(
                        newValue,
                        packResultsList
                )
        );
        modLoaderCombo.getItems().setAll(modLoaders);
        modLoaderCombo.getSelectionModel().select(LoaderType.FORGE);
        modGameVersionCombo.valueProperty().addListener(
                (observable, oldValue, newValue) -> reloadInstalledTargets()
        );
        modLoaderCombo.valueProperty().addListener(
                (observable, oldValue, newValue) -> reloadInstalledTargets()
        );
        packLoaderCombo.getItems().setAll(modLoaders);
        packLoaderCombo.getSelectionModel().clearSelection();

        modResultsList.setCellFactory(list -> new ProjectCell());
        packResultsList.setCellFactory(list -> new ProjectCell());
        modResultsList.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) ->
                        modDownloadBtn.setDisable(newValue == null || busy)
        );
        packResultsList.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) ->
                        packDownloadBtn.setDisable(newValue == null || busy)
        );
        modTargetCombo.valueProperty().addListener(
                (observable, oldValue, newValue) -> modDownloadBtn.setDisable(
                        newValue == null
                                || modResultsList.getSelectionModel()
                                .getSelectedItem() == null
                                || busy
                )
        );
        modDownloadBtn.setDisable(true);
        packDownloadBtn.setDisable(true);
        updateProjectSource(
                modSourceCombo.getValue(),
                modResultsList
        );
        updateProjectSource(
                packSourceCombo.getValue(),
                packResultsList
        );
    }

    private void updateProjectSource(
            ProjectSource source,
            ListView<ProjectResult> results
    ) {
        boolean curseForge = source == ProjectSource.CURSEFORGE;
        results.getItems().clear();
        if (curseForge) {
            showStatus(
                    LanguageManager.text(
                            catalog.hasCurseForgeApiKey()
                                    ? "download.status.curseForgeReady"
                                    : "download.status.curseForgeKeyRequired"
                    ),
                    catalog.hasCurseForgeApiKey()
                            ? "status-muted"
                            : "status-warning"
            );
        }
    }

    private void showTab(VBox selectedPane, ToggleButton selectedButton) {
        for (VBox pane : List.of(gamePane, modsPane, packsPane)) {
            boolean selected = pane == selectedPane;
            pane.setVisible(selected);
            pane.setManaged(selected);
        }
        for (ToggleButton button : List.of(gameTabBtn, modsTabBtn, packsTabBtn)) {
            button.setSelected(button == selectedButton);
        }
    }

    private void loadGameCatalog() {
        setBusy(true);
        Task<List<GameVersion>> task = new Task<>() {
            @Override
            protected List<GameVersion> call() throws Exception {
                return catalog.fetchGameVersions();
            }
        };
        task.setOnSucceeded(event -> {
            List<GameVersion> versions = task.getValue();
            gameVersionCombo.getItems().setAll(versions);
            modGameVersionCombo.getItems().setAll(versions);
            packGameVersionCombo.getItems().setAll(versions);
            GameVersion firstRelease = versions.stream()
                    .filter(version -> "release".equals(version.type()))
                    .findFirst()
                    .orElse(versions.isEmpty() ? null : versions.getFirst());
            gameVersionCombo.getSelectionModel().select(firstRelease);
            modGameVersionCombo.getSelectionModel().select(firstRelease);
            packGameVersionCombo.getSelectionModel().select(firstRelease);
            setBusy(false);
            showStatus(LanguageManager.text(
                    "download.status.catalogReady",
                    versions.size()
            ), "status-success");
            refreshInstallerCatalog();
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showStatus(LanguageManager.text(
                    "download.status.catalogFailed",
                    friendlyMessage(task.getException())
            ), "status-error");
        });
        start(task, "tcl-download-catalog");
    }

    private void refreshInstallerCatalog() {
        GameVersion game = gameVersionCombo.getValue();
        LoaderType loader = loaderCombo.getValue();
        if (game == null || loader == null) {
            updateInstallButton();
            return;
        }

        int requestId = catalogRequest.incrementAndGet();
        loaderVersionCombo.getItems().clear();
        optiFineVersionCombo.getItems().clear();
        optiFineCheck.setSelected(false);
        optiFineCheck.setDisable(true);
        optiFineVersionCombo.setDisable(true);
        installBtn.setDisable(true);
        showStatus(LanguageManager.text("download.status.loadingCompatibility"), "status-active");

        Task<InstallerCatalog> task = new Task<>() {
            @Override
            protected InstallerCatalog call() throws Exception {
                List<LoaderVersion> loaderVersions =
                        catalog.fetchLoaderVersions(loader, game.id());
                List<OptiFineVersion> optiFineVersions =
                        loader.supportsOptiFine()
                                ? catalog.fetchOptiFineVersions(game.id())
                                : List.of();
                return new InstallerCatalog(loaderVersions, optiFineVersions);
            }
        };
        task.setOnSucceeded(event -> {
            if (catalogRequest.get() != requestId) {
                return;
            }
            InstallerCatalog value = task.getValue();
            loaderVersionCombo.getItems().setAll(value.loaderVersions());
            loaderVersionCombo.setDisable(loader == LoaderType.VANILLA);
            if (loader != LoaderType.VANILLA) {
                loaderVersionCombo.getSelectionModel().selectFirst();
            }

            optiFineVersionCombo.getItems().setAll(value.optiFineVersions());
            boolean hasOptiFine = !value.optiFineVersions().isEmpty()
                    && loader.supportsOptiFine();
            optiFineCheck.setDisable(!hasOptiFine);
            if (hasOptiFine) {
                optiFineVersionCombo.getSelectionModel().selectFirst();
            }
            updateInstallButton();
            updateCompatibility();
            showStatus(LanguageManager.text("download.status.selectionReady"), "status-muted");
        });
        task.setOnFailed(event -> {
            if (catalogRequest.get() != requestId) {
                return;
            }
            updateInstallButton();
            updateCompatibility();
            showStatus(LanguageManager.text(
                    "download.status.compatibilityFailed",
                    friendlyMessage(task.getException())
            ), "status-error");
        });
        start(task, "tcl-loader-catalog");
    }

    private void updateCompatibility() {
        LoaderType loader = loaderCombo.getValue();
        if (loader == null) {
            return;
        }
        compatibilityText.getStyleClass().removeAll(
                "status-muted", "status-success", "status-warning", "status-error"
        );

        if (!loader.supportsOptiFine()) {
            compatibilityText.setText(LanguageManager.text(
                    "download.compatibility.optifineBlocked",
                    loader.displayName()
            ));
            compatibilityText.getStyleClass().add("status-warning");
            return;
        }
        if (!optiFineCheck.isSelected()) {
            compatibilityText.setText(LanguageManager.text(
                    loader == LoaderType.FORGE
                            ? "download.compatibility.forge"
                            : "download.compatibility.vanilla"
            ));
            compatibilityText.getStyleClass().add("status-muted");
            return;
        }

        if (loader == LoaderType.FORGE) {
            LoaderVersion forge = loaderVersionCombo.getValue();
            OptiFineVersion optiFine = optiFineVersionCombo.getValue();
            String recommended = optiFine == null
                    ? ""
                    : optiFine.recommendedForge().replace("Forge ", "").trim();
            if (forge != null && !recommended.isBlank()
                    && !forge.version().equals(recommended)) {
                compatibilityText.setText(LanguageManager.text(
                        "download.compatibility.forgeMismatch",
                        optiFine.displayName(),
                        recommended
                ));
                compatibilityText.getStyleClass().add("status-warning");
                return;
            }
        }
        compatibilityText.setText(LanguageManager.text(
                "download.compatibility.supported"
        ));
        compatibilityText.getStyleClass().add("status-success");
    }

    private void updateInstallButton() {
        LoaderType loader = loaderCombo.getValue();
        boolean missingLoaderVersion = loader != null
                && loader != LoaderType.VANILLA
                && loaderVersionCombo.getValue() == null;
        installBtn.setDisable(
                busy
                        || gameVersionCombo.getValue() == null
                        || loader == null
                        || missingLoaderVersion
        );
    }

    private void installSelectedGame() {
        GameVersion game = gameVersionCombo.getValue();
        LoaderType loader = loaderCombo.getValue();
        LoaderVersion loaderVersion = loaderVersionCombo.getValue();
        OptiFineVersion optiFine = optiFineCheck.isSelected()
                ? optiFineVersionCombo.getValue()
                : null;
        if (game == null || loader == null
                || loader != LoaderType.VANILLA && loaderVersion == null) {
            showStatus(LanguageManager.text("download.status.incompleteSelection"), "status-warning");
            return;
        }

        setBusy(true);
        Task<LoaderInstallService.InstallResult> task = new Task<>() {
            @Override
            protected LoaderInstallService.InstallResult call() throws Exception {
                return installer.install(
                        game.id(),
                        loader,
                        loaderVersion == null ? "" : loaderVersion.version(),
                        optiFine,
                        key -> updateMessage(LanguageManager.text(key))
                );
            }
        };
        downloadStatusText.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            downloadStatusText.textProperty().unbind();
            setBusy(false);
            reloadInstalledTargets();
            LoaderInstallService.InstallResult result = task.getValue();
            if (result.externalInstallerOpened()) {
                String key = result.optiFineFile() == null
                        ? "download.status.installerOpened"
                        : "download.status.installerOpenedWithOptiFine";
                showStatus(LanguageManager.text(
                        key,
                        TCLPaths.MINECRAFT_DIR.toAbsolutePath().normalize(),
                        result.optiFineFile() == null
                                ? ""
                                : result.optiFineFile().toAbsolutePath().normalize()
                ), "status-warning");
            } else {
                showStatus(LanguageManager.text(
                        "download.status.installed",
                        result.versionId()
                ), "status-success");
            }
        });
        task.setOnFailed(event -> {
            downloadStatusText.textProperty().unbind();
            setBusy(false);
            showStatus(LanguageManager.text(
                    "download.status.installFailed",
                    friendlyMessage(task.getException())
            ), "status-error");
        });
        start(task, "tcl-version-installer");
    }

    private void searchMods() {
        searchProjects(
                modSourceCombo.getValue(),
                modQueryField.getText(),
                "mod",
                modGameVersionCombo.getValue(),
                modLoaderCombo.getValue(),
                modResultsList,
                "tcl-mod-search"
        );
    }

    private void searchPacks() {
        searchProjects(
                packSourceCombo.getValue(),
                packQueryField.getText(),
                "modpack",
                packGameVersionCombo.getValue(),
                packLoaderCombo.getValue(),
                packResultsList,
                "tcl-pack-search"
        );
    }

    private void searchProjects(
            ProjectSource source,
            String query,
            String projectType,
            GameVersion game,
            LoaderType loader,
            ListView<ProjectResult> results,
            String threadName
    ) {
        if (game == null) {
            showStatus(LanguageManager.text("download.status.chooseGameVersion"), "status-warning");
            return;
        }
        if (source == null) {
            showStatus(LanguageManager.text("download.status.chooseSource"), "status-warning");
            return;
        }
        if (source == ProjectSource.CURSEFORGE
                && !catalog.hasCurseForgeApiKey()) {
            showStatus(
                    LanguageManager.text("download.status.curseForgeKeyRequired"),
                    "status-warning"
            );
            return;
        }
        setBusy(true);
        showStatus(LanguageManager.text(
                "download.status.searchingSource",
                source.displayName()
        ), "status-active");
        Task<List<ProjectResult>> task = new Task<>() {
            @Override
            protected List<ProjectResult> call() throws Exception {
                return catalog.searchProjects(
                        source,
                        query,
                        projectType,
                        game.id(),
                        loader
                );
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            results.getItems().setAll(task.getValue());
            if (!task.getValue().isEmpty()) {
                results.getSelectionModel().selectFirst();
            }
            showStatus(LanguageManager.text(
                    "download.status.sourceResults",
                    source.displayName(),
                    task.getValue().size()
            ), "status-success");
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showStatus(LanguageManager.text(
                    "download.status.searchFailed",
                    friendlyMessage(task.getException())
            ), "status-error");
        });
        start(task, threadName);
    }

    private void downloadSelectedMod() {
        ProjectResult project = modResultsList.getSelectionModel()
                .getSelectedItem();
        GameVersion game = modGameVersionCombo.getValue();
        LoaderType loader = modLoaderCombo.getValue();
        String target = modTargetCombo.getValue();
        if (project == null || game == null || loader == null || target == null) {
            showStatus(LanguageManager.text("download.status.chooseModTarget"), "status-warning");
            return;
        }
        Path destination = TCLPaths.VERSIONS_DIR.resolve(target)
                .resolve("game")
                .resolve("mods");
        downloadProject(
                project,
                "mod",
                game,
                loader,
                destination,
                "tcl-mod-download"
        );
    }

    private void downloadSelectedPack() {
        ProjectResult project = packResultsList.getSelectionModel()
                .getSelectedItem();
        GameVersion game = packGameVersionCombo.getValue();
        if (project == null || game == null) {
            showStatus(LanguageManager.text("download.status.choosePack"), "status-warning");
            return;
        }
        downloadProject(
                project,
                "modpack",
                game,
                packLoaderCombo.getValue(),
                TCLPaths.DOWNLOADS_DIR.resolve("modpacks"),
                "tcl-pack-download"
        );
    }

    private void downloadProject(
            ProjectResult project,
            String projectType,
            GameVersion game,
            LoaderType loader,
            Path destinationDirectory,
            String threadName
    ) {
        if (project.source() == ProjectSource.CURSEFORGE
                && !catalog.hasCurseForgeApiKey()) {
            showStatus(
                    LanguageManager.text("download.status.curseForgeKeyRequired"),
                    "status-warning"
            );
            return;
        }
        setBusy(true);
        showStatus(LanguageManager.text(
                "download.status.resolvingFile",
                project.title()
        ), "status-active");
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                ProjectFile file = catalog.findLatestProjectFile(
                        project.source(),
                        project.id(),
                        projectType,
                        game.id(),
                        loader
                );
                updateMessage(LanguageManager.text(
                        "download.status.downloadingProject",
                        file.filename()
                ));
                Files.createDirectories(destinationDirectory);
                Path destination = destinationDirectory.resolve(file.filename());
                catalog.downloadProjectFile(
                        project.source(),
                        file,
                        destination
                );
                return destination;
            }
        };
        downloadStatusText.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            downloadStatusText.textProperty().unbind();
            setBusy(false);
            showStatus(LanguageManager.text(
                    "download.status.projectDownloaded",
                    task.getValue().toAbsolutePath().normalize()
            ), "status-success");
        });
        task.setOnFailed(event -> {
            downloadStatusText.textProperty().unbind();
            setBusy(false);
            showStatus(LanguageManager.text(
                    "download.status.projectFailed",
                    friendlyMessage(task.getException())
            ), "status-error");
        });
        start(task, threadName);
    }

    private void reloadInstalledTargets() {
        GameVersion selectedGame = modGameVersionCombo.getValue();
        LoaderType selectedLoader = modLoaderCombo.getValue();
        try (Stream<Path> directories = Files.list(TCLPaths.VERSIONS_DIR)) {
            List<String> versions = directories
                    .filter(Files::isDirectory)
                    .filter(path -> Files.exists(
                            path.resolve(path.getFileName() + ".json")
                    ))
                    .filter(path -> selectedGame == null || selectedLoader == null
                            || isCompatibleInstance(
                            path,
                            selectedGame.id(),
                            selectedLoader
                    ))
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .toList();
            modTargetCombo.getItems().setAll(versions);
            if (!versions.isEmpty()) {
                modTargetCombo.getSelectionModel().selectFirst();
            }
        } catch (IOException error) {
            modTargetCombo.getItems().clear();
        }
    }

    private boolean isCompatibleInstance(
            Path versionDirectory,
            String minecraftVersion,
            LoaderType loader
    ) {
        String id = versionDirectory.getFileName().toString();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(
                    versionDirectory.resolve(id + ".json")
            )).getAsJsonObject();
            String baseVersion = root.has("inheritsFrom")
                    ? root.get("inheritsFrom").getAsString()
                    : id;
            if (!minecraftVersion.equals(baseVersion)
                    && !id.contains(minecraftVersion)) {
                return false;
            }
            String mainClass = root.has("mainClass")
                    ? root.get("mainClass").getAsString()
                    .toLowerCase(Locale.ROOT)
                    : "";
            String normalizedId = id.toLowerCase(Locale.ROOT);
            return switch (loader) {
                case FABRIC -> normalizedId.contains("fabric")
                        || mainClass.contains("fabricmc");
                case QUILT -> normalizedId.contains("quilt")
                        || mainClass.contains("quiltmc");
                case NEOFORGE -> normalizedId.contains("neoforge")
                        || mainClass.contains("neoforged");
                case FORGE -> !normalizedId.contains("neoforge")
                        && !mainClass.contains("neoforged")
                        && (normalizedId.contains("forge")
                        || mainClass.contains("minecraftforge"));
                case VANILLA -> false;
            };
        } catch (Exception error) {
            return false;
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        downloadProgress.setManaged(value);
        downloadProgress.setVisible(value);
        gameTabBtn.setDisable(value);
        modsTabBtn.setDisable(value);
        packsTabBtn.setDisable(value);
        installBtn.setDisable(value);
        modSearchBtn.setDisable(value);
        packSearchBtn.setDisable(value);
        modSourceCombo.setDisable(value);
        packSourceCombo.setDisable(value);
        modDownloadBtn.setDisable(
                value || modResultsList.getSelectionModel().getSelectedItem() == null
                        || modTargetCombo.getValue() == null
        );
        packDownloadBtn.setDisable(
                value || packResultsList.getSelectionModel().getSelectedItem() == null
        );
        if (!value) {
            updateInstallButton();
        }
    }

    private void showStatus(String message, String styleClass) {
        downloadStatusText.textProperty().unbind();
        downloadStatusText.setText(message);
        downloadStatusText.getStyleClass().removeAll(
                "status-muted",
                "status-active",
                "status-success",
                "status-warning",
                "status-error"
        );
        downloadStatusText.getStyleClass().add(styleClass);
    }

    private void start(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private String friendlyMessage(Throwable error) {
        Throwable current = error;
        while (current != null && (current.getMessage() == null
                || current.getMessage().isBlank()) && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null || current.getMessage() == null
                || current.getMessage().isBlank()) {
            return LanguageManager.text("common.unknownError");
        }
        return current.getMessage();
    }

    private record InstallerCatalog(
            List<LoaderVersion> loaderVersions,
            List<OptiFineVersion> optiFineVersions
    ) {}

    private static final class ProjectCell extends ListCell<ProjectResult> {
        private final Label title = new Label();
        private final Label description = new Label();
        private final Label metadata = new Label();
        private final VBox content = new VBox(4, title, description, metadata);

        private ProjectCell() {
            content.setAlignment(Pos.CENTER_LEFT);
            content.getStyleClass().add("project-cell-content");
            title.getStyleClass().add("project-title");
            description.getStyleClass().add("project-description");
            metadata.getStyleClass().add("project-meta");
            description.setWrapText(true);
            setGraphic(content);
        }

        @Override
        protected void updateItem(ProjectResult project, boolean empty) {
            super.updateItem(project, empty);
            if (empty || project == null) {
                setGraphic(null);
                return;
            }
            title.setText(project.title());
            description.setText(project.description());
            metadata.setText(LanguageManager.text(
                    "download.project.meta",
                    project.source().displayName(),
                    project.author(),
                    String.format(Locale.ROOT, "%,d", project.downloads())
            ));
            setGraphic(content);
        }
    }
}
