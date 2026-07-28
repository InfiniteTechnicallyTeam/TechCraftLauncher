package itt.tcl.version;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import itt.tcl.config.TCLPaths;
import itt.tcl.download.AssetDownloader;
import itt.tcl.download.DownloadManager;
import itt.tcl.download.MirrorSource;
import itt.tcl.util.HttpHelper;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class VersionInstaller {
    private VersionInstaller() {}

    public static void installVersion(String versionId) throws Exception {
        ensureVersion(versionId);
    }

    public static void ensureVersion(String versionId) throws Exception {
        Path versionDir = TCLPaths.VERSIONS_DIR.resolve(versionId);
        Path versionJson = versionDir.resolve(versionId + ".json");
        Path clientJar = versionDir.resolve(versionId + ".jar");
        Files.createDirectories(versionDir);

        if (Files.exists(versionJson)) {
            JsonObject localRoot = readJson(versionJson);
            if (localRoot.has("inheritsFrom")) {
                String parent = localRoot.get("inheritsFrom").getAsString();
                ensureVersion(parent);
                downloadLibraries(localRoot);
                System.out.println(
                        "All loader files for version " + versionId + " are ready."
                );
                return;
            }
        }

        if (!Files.exists(versionJson) || !Files.exists(clientJar)) {
            downloadVanillaVersion(versionId, versionJson, clientJar);
        }

        JsonObject root = readJson(versionJson);
        downloadLibraries(root);
        ensureAssets(root);
        System.out.println("All files for version " + versionId + " are ready.");
    }

    public static JsonObject fetchVersionManifest()
            throws IOException, InterruptedException {
        Path cacheFile = TCLPaths.VERSION_MANIFEST;
        if (Files.exists(cacheFile)
                && System.currentTimeMillis()
                - Files.getLastModifiedTime(cacheFile).toMillis() < 86_400_000) {
            return HttpHelper.parseJson(Files.readString(cacheFile));
        }

        String body = null;
        for (String url : new String[]{
                MirrorSource.MANIFEST_URL_BMCLAPI,
                MirrorSource.MANIFEST_URL_OFFICIAL
        }) {
            try {
                var response = HttpHelper.get(url);
                if (response.statusCode() == 200) {
                    body = response.body();
                    break;
                }
            } catch (IOException ignored) {
                // Try the next source.
            }
        }
        if (body == null || body.isBlank()) {
            throw new IOException("Failed to fetch Minecraft version manifest");
        }
        Files.writeString(cacheFile, body);
        return HttpHelper.parseJson(body);
    }

    private static void downloadVanillaVersion(
            String versionId,
            Path versionJson,
            Path clientJar
    ) throws Exception {
        boolean bmclapiOk = true;
        try {
            if (!Files.exists(versionJson)) {
                DownloadManager.downloadFileSilent(
                        MirrorSource.BMCLAPI_BASE
                                + "/version/" + versionId + "/json",
                        versionJson
                );
            }
            if (!Files.exists(clientJar)) {
                DownloadManager.downloadFileSilent(
                        MirrorSource.BMCLAPI_BASE
                                + "/version/" + versionId + "/client",
                        clientJar
                );
            }
        } catch (IOException error) {
            bmclapiOk = false;
            Files.deleteIfExists(versionJson);
            Files.deleteIfExists(clientJar);
        }

        if (bmclapiOk) {
            return;
        }

        System.out.println(
                "BMCLAPI fast endpoint failed, falling back to official manifest."
        );
        JsonObject manifest = fetchVersionManifest();
        String versionUrl = null;
        for (JsonElement element : manifest.getAsJsonArray("versions")) {
            JsonObject version = element.getAsJsonObject();
            if (versionId.equals(version.get("id").getAsString())) {
                versionUrl = version.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            throw new IllegalArgumentException("Unknown version " + versionId);
        }

        DownloadManager.downloadFileWithFallback(versionUrl, versionJson);
        JsonObject root = readJson(versionJson);
        JsonObject client = root.getAsJsonObject("downloads")
                .getAsJsonObject("client");
        DownloadManager.downloadFileWithFallback(
                client.get("url").getAsString(),
                clientJar
        );
    }

    private static void downloadLibraries(JsonObject root) throws Exception {
        JsonArray libraries = root.getAsJsonArray("libraries");
        if (libraries == null) {
            return;
        }

        String os = currentOs();
        List<LibraryDownload> downloads = new ArrayList<>();
        for (JsonElement libraryElement : libraries) {
            JsonObject library = libraryElement.getAsJsonObject();
            if (!isAllowed(library, os)) {
                continue;
            }
            String name = library.get("name").getAsString();
            MavenArtifact artifact = MavenArtifact.parse(name);

            Path mainDestination;
            String officialUrl;
            String relativePath;
            if (library.has("downloads")
                    && library.getAsJsonObject("downloads").has("artifact")) {
                JsonObject download = library.getAsJsonObject("downloads")
                        .getAsJsonObject("artifact");
                relativePath = download.has("path")
                        ? download.get("path").getAsString()
                        : artifact.relativePath();
                mainDestination = TCLPaths.LIBRARIES_DIR.resolve(relativePath);
                officialUrl = download.has("url")
                        ? download.get("url").getAsString()
                        : libraryBaseUrl(library) + relativePath;
            } else {
                relativePath = artifact.relativePath();
                mainDestination = TCLPaths.LIBRARIES_DIR.resolve(relativePath);
                officialUrl = libraryBaseUrl(library) + relativePath;
            }
            addIfMissing(
                    downloads,
                    mainDestination,
                    mirrorUrl(relativePath),
                    officialUrl,
                    mainDestination.getFileName().toString()
            );

            String classifier = nativeClassifier(library, os);
            if (classifier == null) {
                continue;
            }
            classifier = classifier
                    .replace("${arch}", System.getProperty("os.arch").contains("64")
                            ? "64" : "32");

            JsonObject nativeDownload = null;
            if (library.has("downloads")
                    && library.getAsJsonObject("downloads").has("classifiers")) {
                JsonObject classifiers = library.getAsJsonObject("downloads")
                        .getAsJsonObject("classifiers");
                if (classifiers.has(classifier)) {
                    nativeDownload = classifiers.getAsJsonObject(classifier);
                }
            }
            MavenArtifact nativeArtifact = artifact.withClassifier(classifier);
            String nativeRelative = nativeDownload != null
                    && nativeDownload.has("path")
                    ? nativeDownload.get("path").getAsString()
                    : nativeArtifact.relativePath();
            String nativeOfficial = nativeDownload != null
                    && nativeDownload.has("url")
                    ? nativeDownload.get("url").getAsString()
                    : libraryBaseUrl(library) + nativeRelative;
            Path nativeDestination = TCLPaths.LIBRARIES_DIR.resolve(nativeRelative);
            addIfMissing(
                    downloads,
                    nativeDestination,
                    mirrorUrl(nativeRelative),
                    nativeOfficial,
                    nativeDestination.getFileName().toString()
            );
        }

        if (downloads.isEmpty()) {
            System.out.println("Libraries: all cached.");
            return;
        }

        System.out.println(
                "Libraries: downloading " + downloads.size() + " files..."
        );
        AtomicInteger completed = new AtomicInteger();
        ConcurrentLinkedQueue<Exception> failures = new ConcurrentLinkedQueue<>();
        int threadCount = Math.min(
                16,
                Math.max(2, Runtime.getRuntime().availableProcessors() * 2)
        );
        var executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();
        for (LibraryDownload download : downloads) {
            futures.add(executor.submit(() -> {
                try {
                    downloadWithFallback(download);
                    int current = completed.incrementAndGet();
                    System.out.println(
                            "  Libraries: " + current + "/" + downloads.size()
                    );
                } catch (Exception error) {
                    failures.add(new IOException(
                            "Failed to download " + download.label()
                                    + ": " + error.getMessage(),
                            error
                    ));
                }
            }));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception error) {
                failures.add(error);
            }
        }
        executor.shutdown();
        if (!failures.isEmpty()) {
            Exception first = failures.peek();
            throw new IOException(
                    failures.size() + " libraries failed; "
                            + (first == null ? "unknown error" : first.getMessage()),
                    first
            );
        }
    }

    private static void ensureAssets(JsonObject root) throws Exception {
        JsonObject assetIndex = root.getAsJsonObject("assetIndex");
        if (assetIndex == null) {
            return;
        }
        Path indexesDir = TCLPaths.ASSETS_DIR.resolve("indexes");
        Files.createDirectories(indexesDir);
        Path indexFile = indexesDir.resolve(
                assetIndex.get("id").getAsString() + ".json"
        );
        if (!Files.exists(indexFile)) {
            DownloadManager.downloadFileWithFallback(
                    assetIndex.get("url").getAsString(),
                    indexFile
            );
        }
        AssetDownloader.downloadAssets(indexFile);
    }

    private static void downloadWithFallback(LibraryDownload download)
            throws Exception {
        try {
            DownloadManager.downloadFileSilent(
                    download.primaryUrl(),
                    download.destination()
            );
        } catch (Exception primaryError) {
            Files.deleteIfExists(download.destination());
            if (download.fallbackUrl() == null
                    || download.fallbackUrl().equals(download.primaryUrl())) {
                throw primaryError;
            }
            DownloadManager.downloadFileSilent(
                    download.fallbackUrl(),
                    download.destination()
            );
        }
    }

    private static void addIfMissing(
            List<LibraryDownload> downloads,
            Path destination,
            String primary,
            String fallback,
            String label
    ) {
        if (!Files.exists(destination)) {
            downloads.add(new LibraryDownload(
                    primary,
                    fallback,
                    destination,
                    label
            ));
        }
    }

    private static String mirrorUrl(String relativePath) {
        return MirrorSource.LIBRARIES_BMCLAPI + "/" + relativePath;
    }

    private static String libraryBaseUrl(JsonObject library) {
        if (library.has("url")) {
            String url = library.get("url").getAsString();
            return url.endsWith("/") ? url : url + "/";
        }
        return MirrorSource.LIBRARIES_OFFICIAL + "/";
    }

    private static String nativeClassifier(JsonObject library, String os) {
        if (!library.has("natives")) {
            return null;
        }
        JsonObject natives = library.getAsJsonObject("natives");
        return natives.has(os) ? natives.get(os).getAsString() : null;
    }

    private static boolean isAllowed(JsonObject library, String os) {
        if (!library.has("rules")) {
            return true;
        }
        boolean allowed = false;
        for (JsonElement element : library.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            if (!ruleMatches(rule, os)) {
                continue;
            }
            allowed = "allow".equals(rule.get("action").getAsString());
        }
        return allowed;
    }

    private static boolean ruleMatches(JsonObject rule, String os) {
        if (rule.has("features")) {
            return false;
        }
        if (!rule.has("os")) {
            return true;
        }
        JsonObject osRule = rule.getAsJsonObject("os");
        if (osRule.has("name")
                && !os.equals(osRule.get("name").getAsString())) {
            return false;
        }
        if (osRule.has("arch")) {
            String arch = System.getProperty("os.arch", "");
            if (!arch.matches(osRule.get("arch").getAsString())) {
                return false;
            }
        }
        return true;
    }

    private static String currentOs() {
        String name = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return "windows";
        }
        if (name.contains("mac")) {
            return "osx";
        }
        return "linux";
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private record LibraryDownload(
            String primaryUrl,
            String fallbackUrl,
            Path destination,
            String label
    ) {}

    private record MavenArtifact(
            String group,
            String artifact,
            String version,
            String classifier,
            String extension
    ) {
        private static MavenArtifact parse(String coordinate) {
            String[] extensionParts = coordinate.split("@", 2);
            String extension = extensionParts.length == 2
                    ? extensionParts[1]
                    : "jar";
            String[] parts = extensionParts[0].split(":");
            if (parts.length < 3) {
                throw new IllegalArgumentException(
                        "Invalid Maven coordinate: " + coordinate
                );
            }
            return new MavenArtifact(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts.length >= 4 ? parts[3] : null,
                    extension
            );
        }

        private MavenArtifact withClassifier(String value) {
            return new MavenArtifact(
                    group,
                    artifact,
                    version,
                    value,
                    extension
            );
        }

        private String relativePath() {
            String filename = artifact + "-" + version
                    + (classifier == null || classifier.isBlank()
                    ? ""
                    : "-" + classifier)
                    + "." + extension;
            return group.replace('.', '/') + "/" + artifact + "/"
                    + version + "/" + filename;
        }
    }
}
