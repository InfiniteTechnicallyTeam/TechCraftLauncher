package itt.tcl.download;

import com.google.gson.JsonObject;
import itt.tcl.config.TCLPaths;
import itt.tcl.version.VersionInstaller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class LoaderInstallService {
    private static final long INSTALLER_TIMEOUT_MINUTES = 12;

    private final DownloadCatalogService catalog;

    public LoaderInstallService(DownloadCatalogService catalog) {
        this.catalog = catalog;
    }

    public record InstallResult(
            String versionId,
            boolean externalInstallerOpened,
            Path optiFineFile
    ) {}

    public InstallResult install(
            String minecraftVersion,
            LoaderType loader,
            String loaderVersion,
            DownloadCatalogService.OptiFineVersion optiFine,
            Consumer<String> status
    ) throws Exception {
        if (optiFine != null && !loader.supportsOptiFine()) {
            throw new IllegalArgumentException(
                    "OptiFine is not compatible with " + loader.displayName()
            );
        }

        status.accept("download.status.installingVanilla");
        VersionInstaller.installVersion(minecraftVersion);

        String installedId = minecraftVersion;
        boolean installerOpened = false;
        if (loader == LoaderType.FABRIC || loader == LoaderType.QUILT) {
            status.accept("download.status.installingLoader");
            installedId = installProfile(loader, minecraftVersion, loaderVersion);
        } else if (loader == LoaderType.FORGE || loader == LoaderType.NEOFORGE) {
            status.accept("download.status.downloadingInstaller");
            InstallerResult result = installWithExternalInstaller(
                    loader,
                    minecraftVersion,
                    loaderVersion
            );
            installedId = result.versionId() == null
                    ? minecraftVersion
                    : result.versionId();
            installerOpened = result.openedGui();
        }

        Path optiFineFile = null;
        if (optiFine != null) {
            status.accept("download.status.downloadingOptiFine");
            optiFineFile = downloadOptiFine(optiFine);
            if (loader == LoaderType.FORGE && !installerOpened
                    && !minecraftVersion.equals(installedId)) {
                Path modsDir = TCLPaths.VERSIONS_DIR.resolve(installedId)
                        .resolve("game")
                        .resolve("mods");
                Files.createDirectories(modsDir);
                Files.copy(
                        optiFineFile,
                        modsDir.resolve(optiFine.filename()),
                        StandardCopyOption.REPLACE_EXISTING
                );
            } else if (loader == LoaderType.VANILLA) {
                openJar(optiFineFile);
                installerOpened = true;
            }
        }
        return new InstallResult(installedId, installerOpened, optiFineFile);
    }

    private String installProfile(
            LoaderType loader,
            String minecraftVersion,
            String loaderVersion
    ) throws Exception {
        JsonObject profile = catalog.fetchLoaderProfile(
                loader,
                minecraftVersion,
                loaderVersion
        );
        String id = profile.get("id").getAsString();
        Path versionDir = TCLPaths.VERSIONS_DIR.resolve(id);
        Files.createDirectories(versionDir);
        Files.writeString(
                versionDir.resolve(id + ".json"),
                profile.toString(),
                StandardCharsets.UTF_8
        );
        VersionInstaller.ensureVersion(id);
        return id;
    }

    private InstallerResult installWithExternalInstaller(
            LoaderType loader,
            String minecraftVersion,
            String loaderVersion
    ) throws Exception {
        Path installers = TCLPaths.DOWNLOADS_DIR.resolve("installers");
        Files.createDirectories(installers);
        String filename;
        String primaryUrl;
        String fallbackUrl = null;
        if (loader == LoaderType.FORGE) {
            filename = "forge-" + minecraftVersion + "-" + loaderVersion
                    + "-installer.jar";
            primaryUrl = catalog.forgeInstallerUrl(minecraftVersion, loaderVersion);
            fallbackUrl = catalog.forgeInstallerOfficialUrl(
                    minecraftVersion,
                    loaderVersion
            );
        } else {
            filename = "neoforge-" + loaderVersion + "-installer.jar";
            primaryUrl = catalog.neoForgeInstallerUrl(loaderVersion);
        }

        Path installer = installers.resolve(filename);
        if (!Files.exists(installer)) {
            try {
                DownloadManager.downloadFileSilent(primaryUrl, installer);
            } catch (IOException primaryError) {
                if (fallbackUrl == null) {
                    throw primaryError;
                }
                DownloadManager.downloadFileSilent(fallbackUrl, installer);
            }
        }

        Set<String> before = versionDirectories();
        Process process = new ProcessBuilder(
                javaCommand(),
                "-jar",
                installer.toAbsolutePath().toString(),
                "--installClient",
                TCLPaths.MINECRAFT_DIR.toAbsolutePath().toString()
        )
                .directory(TCLPaths.MINECRAFT_DIR.toFile())
                .redirectErrorStream(true)
                .start();
        Thread outputDrain = new Thread(() -> {
            try {
                process.getInputStream().transferTo(
                        java.io.OutputStream.nullOutputStream()
                );
            } catch (IOException ignored) {
                // The process may close its stream while exiting.
            }
        }, "tcl-loader-installer-output");
        outputDrain.setDaemon(true);
        outputDrain.start();
        boolean completed = process.waitFor(
                INSTALLER_TIMEOUT_MINUTES,
                TimeUnit.MINUTES
        );
        if (!completed) {
            process.destroyForcibly();
        }

        String installed = findNewLoaderVersion(before, loader, minecraftVersion);
        if (installed == null && completed && process.exitValue() == 0) {
            installed = findInstalledLoaderVersion(
                    loader,
                    minecraftVersion,
                    loaderVersion
            );
        }
        if (completed && process.exitValue() == 0 && installed != null) {
            return new InstallerResult(installed, false);
        }

        openJar(installer);
        return new InstallerResult(null, true);
    }

    private Path downloadOptiFine(
            DownloadCatalogService.OptiFineVersion version
    ) throws Exception {
        Path directory = TCLPaths.DOWNLOADS_DIR.resolve("optifine");
        Files.createDirectories(directory);
        Path destination = directory.resolve(version.filename());
        if (!Files.exists(destination)) {
            DownloadManager.downloadFileSilent(
                    catalog.optiFineDownloadUrl(version),
                    destination
            );
        }
        return destination;
    }

    private void openJar(Path jar) throws IOException {
        new ProcessBuilder(
                javaCommand(),
                "-jar",
                jar.toAbsolutePath().toString()
        )
                .directory(TCLPaths.MINECRAFT_DIR.toFile())
                .start();
    }

    private String findNewLoaderVersion(
            Set<String> before,
            LoaderType loader,
            String minecraftVersion
    ) throws IOException {
        String marker = loader == LoaderType.FORGE ? "forge" : "neoforge";
        try (Stream<Path> stream = Files.list(TCLPaths.VERSIONS_DIR)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(id -> !before.contains(id))
                    .filter(id -> id.toLowerCase().contains(marker))
                    .filter(id -> id.contains(minecraftVersion)
                            || loader == LoaderType.NEOFORGE)
                    .findFirst()
                    .orElse(null);
        }
    }

    private String findInstalledLoaderVersion(
            LoaderType loader,
            String minecraftVersion,
            String loaderVersion
    ) throws IOException {
        String marker = loader == LoaderType.FORGE ? "forge" : "neoforge";
        try (Stream<Path> stream = Files.list(TCLPaths.VERSIONS_DIR)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(id -> id.toLowerCase().contains(marker))
                    .filter(id -> id.contains(loaderVersion))
                    .filter(id -> id.contains(minecraftVersion)
                            || loader == LoaderType.NEOFORGE)
                    .findFirst()
                    .orElse(null);
        }
    }

    private Set<String> versionDirectories() throws IOException {
        Set<String> result = new HashSet<>();
        try (Stream<Path> stream = Files.list(TCLPaths.VERSIONS_DIR)) {
            stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .forEach(result::add);
        }
        return result;
    }

    private String javaCommand() {
        return ProcessHandle.current().info().command().orElse("java");
    }

    private record InstallerResult(String versionId, boolean openedGui) {}
}
