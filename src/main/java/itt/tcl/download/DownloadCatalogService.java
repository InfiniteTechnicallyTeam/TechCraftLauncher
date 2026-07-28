package itt.tcl.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import itt.tcl.util.HttpHelper;
import itt.tcl.version.VersionInstaller;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class DownloadCatalogService {
    public static final String FABRIC_META_BMCLAPI =
            MirrorSource.BMCLAPI_BASE + "/fabric-meta";
    public static final String FABRIC_META_OFFICIAL = "https://meta.fabricmc.net";
    public static final String QUILT_META_OFFICIAL = "https://meta.quiltmc.org";
    public static final String NEOFORGE_MAVEN =
            "https://maven.neoforged.net/releases";
    public static final String MODRINTH_API = "https://api.modrinth.com/v2";
    public static final String CURSEFORGE_API = "https://api.curseforge.com";

    private static final int CURSEFORGE_MINECRAFT_GAME_ID = 432;
    private static final int CURSEFORGE_MOD_CLASS_ID = 6;
    private static final int CURSEFORGE_MODPACK_CLASS_ID = 4471;

    private static final String USER_AGENT =
            "InfiniteTechnicallyTeam/TechCraftLauncher/1.0 (github.com/InfiniteTechnicallyTeam/TechCraftLauncher)";

    public record GameVersion(String id, String type, String releaseTime) {
        @Override
        public String toString() {
            return id;
        }
    }

    public record LoaderVersion(String version, boolean stable) {
        @Override
        public String toString() {
            return stable ? version : version + " · beta";
        }
    }

    public record OptiFineVersion(
            String minecraftVersion,
            String type,
            String patch,
            String filename,
            String recommendedForge
    ) {
        public String displayName() {
            return type + " " + patch;
        }

        @Override
        public String toString() {
            return displayName();
        }
    }

    public record ProjectResult(
            ProjectSource source,
            String id,
            String slug,
            String title,
            String description,
            String author,
            long downloads,
            String projectType
    ) {
        @Override
        public String toString() {
            return title;
        }
    }

    public record ProjectFile(String filename, String url, String versionName) {}

    public List<GameVersion> fetchGameVersions() throws Exception {
        JsonObject manifest = VersionInstaller.fetchVersionManifest();
        List<GameVersion> result = new ArrayList<>();
        for (JsonElement element : manifest.getAsJsonArray("versions")) {
            JsonObject version = element.getAsJsonObject();
            String type = text(version, "type");
            if (!"release".equals(type) && !"snapshot".equals(type)) {
                continue;
            }
            result.add(new GameVersion(
                    text(version, "id"),
                    type,
                    text(version, "releaseTime")
            ));
        }
        return result;
    }

    public List<LoaderVersion> fetchLoaderVersions(
            LoaderType loader,
            String minecraftVersion
    ) throws Exception {
        return switch (loader) {
            case VANILLA -> List.of();
            case FORGE -> fetchForgeVersions(minecraftVersion);
            case NEOFORGE -> fetchNeoForgeVersions(minecraftVersion);
            case FABRIC -> fetchFabricVersions(minecraftVersion);
            case QUILT -> fetchQuiltVersions(minecraftVersion);
        };
    }

    public List<OptiFineVersion> fetchOptiFineVersions(String minecraftVersion)
            throws Exception {
        JsonArray array = getJson(
                MirrorSource.BMCLAPI_BASE + "/optifine/" + encodePath(minecraftVersion)
        ).getAsJsonArray();
        List<OptiFineVersion> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            result.add(new OptiFineVersion(
                    text(item, "mcversion"),
                    text(item, "type"),
                    text(item, "patch"),
                    text(item, "filename"),
                    text(item, "forge")
            ));
        }
        result.sort(Comparator
                .comparing((OptiFineVersion value) ->
                        value.filename().toLowerCase(Locale.ROOT).startsWith("preview_"))
                .thenComparing(OptiFineVersion::filename, Comparator.reverseOrder()));
        return result;
    }

    public JsonObject fetchLoaderProfile(
            LoaderType loader,
            String minecraftVersion,
            String loaderVersion
    ) throws Exception {
        String mc = encodePath(minecraftVersion);
        String version = encodePath(loaderVersion);
        return switch (loader) {
            case FABRIC -> getJsonWithFallback(
                    FABRIC_META_BMCLAPI + "/v2/versions/loader/" + mc + "/" + version + "/profile/json",
                    FABRIC_META_OFFICIAL + "/v2/versions/loader/" + mc + "/" + version + "/profile/json"
            ).getAsJsonObject();
            case QUILT -> getJson(
                    QUILT_META_OFFICIAL + "/v3/versions/loader/" + mc + "/" + version + "/profile/json"
            ).getAsJsonObject();
            default -> throw new IllegalArgumentException(
                    "Loader profile is not available for " + loader
            );
        };
    }

    public List<ProjectResult> searchProjects(
            ProjectSource source,
            String query,
            String projectType,
            String minecraftVersion,
            LoaderType loader,
            String curseForgeApiKey
    ) throws Exception {
        return switch (source) {
            case MODRINTH -> searchModrinthProjects(
                    query,
                    projectType,
                    minecraftVersion,
                    loader
            );
            case CURSEFORGE -> searchCurseForgeProjects(
                    query,
                    projectType,
                    minecraftVersion,
                    loader,
                    curseForgeApiKey
            );
        };
    }

    public ProjectFile findLatestProjectFile(
            ProjectSource source,
            String projectId,
            String projectType,
            String minecraftVersion,
            LoaderType loader,
            String curseForgeApiKey
    ) throws Exception {
        return switch (source) {
            case MODRINTH -> findLatestModrinthFile(
                    projectId,
                    projectType,
                    minecraftVersion,
                    loader
            );
            case CURSEFORGE -> findLatestCurseForgeFile(
                    projectId,
                    projectType,
                    minecraftVersion,
                    loader,
                    curseForgeApiKey
            );
        };
    }

    public void downloadProjectFile(
            ProjectSource source,
            ProjectFile file,
            Path destination,
            String curseForgeApiKey
    ) throws Exception {
        if (source == ProjectSource.MODRINTH) {
            DownloadManager.downloadFileSilent(file.url(), destination);
            return;
        }
        downloadCurseForgeFile(
                file.url(),
                destination,
                resolveCurseForgeApiKey(curseForgeApiKey)
        );
    }

    public boolean hasCurseForgeApiKey(String providedKey) {
        return !configuredCurseForgeApiKey(providedKey).isBlank();
    }

    private List<ProjectResult> searchModrinthProjects(
            String query,
            String projectType,
            String minecraftVersion,
            LoaderType loader
    ) throws Exception {
        JsonArray facets = new JsonArray();
        addFacet(facets, "project_type:" + projectType);
        if (minecraftVersion != null && !minecraftVersion.isBlank()) {
            addFacet(facets, "versions:" + minecraftVersion);
        }
        if (loader != null && loader != LoaderType.VANILLA) {
            addFacet(facets, "categories:" + loader.id());
        }

        String url = MODRINTH_API + "/search?limit=30&index=downloads"
                + "&query=" + encodeQuery(query == null ? "" : query.trim())
                + "&facets=" + encodeQuery(facets.toString());
        JsonObject response = getJson(url).getAsJsonObject();
        List<ProjectResult> results = new ArrayList<>();
        for (JsonElement element : response.getAsJsonArray("hits")) {
            JsonObject item = element.getAsJsonObject();
            results.add(new ProjectResult(
                    ProjectSource.MODRINTH,
                    text(item, "project_id"),
                    text(item, "slug"),
                    text(item, "title"),
                    text(item, "description"),
                    text(item, "author"),
                    longValue(item, "downloads"),
                    text(item, "project_type")
            ));
        }
        return results;
    }

    private ProjectFile findLatestModrinthFile(
            String projectId,
            String projectType,
            String minecraftVersion,
            LoaderType loader
    ) throws Exception {
        JsonArray gameVersions = new JsonArray();
        gameVersions.add(minecraftVersion);
        StringBuilder url = new StringBuilder(MODRINTH_API)
                .append("/project/")
                .append(encodePath(projectId))
                .append("/version?game_versions=")
                .append(encodeQuery(gameVersions.toString()));

        if (loader != null && loader != LoaderType.VANILLA) {
            JsonArray loaders = new JsonArray();
            loaders.add(loader.id());
            url.append("&loaders=").append(encodeQuery(loaders.toString()));
        }

        JsonArray versions = getJson(url.toString()).getAsJsonArray();
        Predicate<String> extension = "modpack".equals(projectType)
                ? name -> name.endsWith(".mrpack")
                : name -> name.endsWith(".jar");

        for (JsonElement versionElement : versions) {
            JsonObject version = versionElement.getAsJsonObject();
            JsonArray files = version.getAsJsonArray("files");
            JsonObject selected = selectFile(files, extension);
            if (selected != null) {
                return new ProjectFile(
                        text(selected, "filename"),
                        text(selected, "url"),
                        text(version, "name")
                );
            }
        }
        throw new IllegalStateException("No compatible downloadable file was found");
    }

    private List<ProjectResult> searchCurseForgeProjects(
            String query,
            String projectType,
            String minecraftVersion,
            LoaderType loader,
            String providedApiKey
    ) throws Exception {
        String apiKey = resolveCurseForgeApiKey(providedApiKey);
        int classId = "modpack".equals(projectType)
                ? CURSEFORGE_MODPACK_CLASS_ID
                : CURSEFORGE_MOD_CLASS_ID;
        StringBuilder url = new StringBuilder(CURSEFORGE_API)
                .append("/v1/mods/search?gameId=")
                .append(CURSEFORGE_MINECRAFT_GAME_ID)
                .append("&classId=")
                .append(classId)
                .append("&sortField=2&sortOrder=desc&pageSize=30");
        appendQuery(url, "gameVersion", minecraftVersion);
        if (query != null && !query.isBlank()) {
            appendQuery(url, "searchFilter", query.trim());
        }
        int loaderId = curseForgeLoaderId(loader);
        if (loaderId != 0) {
            appendQuery(url, "modLoaderType", Integer.toString(loaderId));
        }

        JsonObject response = getCurseForgeJson(url.toString(), apiKey)
                .getAsJsonObject();
        JsonArray data = response.has("data") && response.get("data").isJsonArray()
                ? response.getAsJsonArray("data")
                : new JsonArray();
        List<ProjectResult> results = new ArrayList<>();
        for (JsonElement element : data) {
            JsonObject item = element.getAsJsonObject();
            if (item.has("isAvailable") && !booleanValue(item, "isAvailable")) {
                continue;
            }
            results.add(new ProjectResult(
                    ProjectSource.CURSEFORGE,
                    Long.toString(longValue(item, "id")),
                    text(item, "slug"),
                    text(item, "name"),
                    text(item, "summary"),
                    curseForgeAuthors(item),
                    longValue(item, "downloadCount"),
                    projectType
            ));
        }
        return results;
    }

    private ProjectFile findLatestCurseForgeFile(
            String projectId,
            String projectType,
            String minecraftVersion,
            LoaderType loader,
            String providedApiKey
    ) throws Exception {
        String apiKey = resolveCurseForgeApiKey(providedApiKey);
        StringBuilder url = new StringBuilder(CURSEFORGE_API)
                .append("/v1/mods/")
                .append(encodePath(projectId))
                .append("/files?pageSize=50");
        appendQuery(url, "gameVersion", minecraftVersion);
        int loaderId = curseForgeLoaderId(loader);
        if (loaderId != 0) {
            appendQuery(url, "modLoaderType", Integer.toString(loaderId));
        }

        JsonObject response = getCurseForgeJson(url.toString(), apiKey)
                .getAsJsonObject();
        JsonArray files = response.has("data") && response.get("data").isJsonArray()
                ? response.getAsJsonArray("data")
                : new JsonArray();
        String wantedExtension = "modpack".equals(projectType) ? ".zip" : ".jar";

        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            if (file.has("isAvailable") && !booleanValue(file, "isAvailable")) {
                continue;
            }
            String filename = safeFilename(text(file, "fileName"));
            if (!filename.toLowerCase(Locale.ROOT).endsWith(wantedExtension)) {
                continue;
            }
            String downloadUrl = text(file, "downloadUrl");
            if (downloadUrl.isBlank()) {
                long fileId = longValue(file, "id");
                if (fileId == 0L) {
                    continue;
                }
                try {
                    JsonObject downloadResponse = getCurseForgeJson(
                            CURSEFORGE_API + "/v1/mods/" + encodePath(projectId)
                                    + "/files/" + fileId + "/download-url",
                            apiKey
                    ).getAsJsonObject();
                    JsonElement data = downloadResponse.get("data");
                    downloadUrl = data == null || data.isJsonNull()
                            ? ""
                            : data.getAsString();
                } catch (Exception unavailable) {
                    continue;
                }
            }
            if (!downloadUrl.isBlank()) {
                String displayName = text(file, "displayName");
                return new ProjectFile(
                        filename,
                        downloadUrl,
                        displayName.isBlank() ? filename : displayName
                );
            }
        }
        throw new IllegalStateException(
                "CurseForge did not return a compatible downloadable file"
        );
    }

    private void downloadCurseForgeFile(
            String url,
            Path destination,
            String apiKey
    ) throws Exception {
        Files.createDirectories(destination.getParent());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("Accept", "application/octet-stream, */*")
                .header("User-Agent", USER_AGENT)
                .header("x-api-key", apiKey)
                .GET()
                .build();
        HttpResponse<InputStream> response = HttpHelper.getClient().send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            Files.deleteIfExists(destination);
            throw new IOException(
                    "CurseForge download failed (" + response.statusCode() + ")"
            );
        }
        try (InputStream input = response.body()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            Files.deleteIfExists(destination);
            throw error;
        }
    }

    public String optiFineDownloadUrl(OptiFineVersion version) {
        return MirrorSource.BMCLAPI_BASE
                + "/optifine/" + encodePath(version.minecraftVersion())
                + "/" + encodePath(version.type())
                + "/" + encodePath(version.patch());
    }

    public String forgeInstallerUrl(String minecraftVersion, String forgeVersion) {
        String artifactVersion = minecraftVersion + "-" + forgeVersion;
        return MirrorSource.LIBRARIES_BMCLAPI
                + "/net/minecraftforge/forge/" + artifactVersion
                + "/forge-" + artifactVersion + "-installer.jar";
    }

    public String forgeInstallerOfficialUrl(String minecraftVersion, String forgeVersion) {
        String artifactVersion = minecraftVersion + "-" + forgeVersion;
        return "https://maven.minecraftforge.net/net/minecraftforge/forge/"
                + artifactVersion + "/forge-" + artifactVersion + "-installer.jar";
    }

    public String neoForgeInstallerUrl(String neoForgeVersion) {
        return NEOFORGE_MAVEN + "/net/neoforged/neoforge/" + neoForgeVersion
                + "/neoforge-" + neoForgeVersion + "-installer.jar";
    }

    private List<LoaderVersion> fetchForgeVersions(String minecraftVersion)
            throws Exception {
        JsonArray array = getJson(
                MirrorSource.BMCLAPI_BASE + "/forge/minecraft/"
                        + encodePath(minecraftVersion)
        ).getAsJsonArray();
        List<LoaderVersion> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            if (!hasInstaller(item)) {
                continue;
            }
            result.add(new LoaderVersion(text(item, "version"), true));
        }
        result.sort((left, right) -> compareVersions(right.version(), left.version()));
        return result;
    }

    private List<LoaderVersion> fetchFabricVersions(String minecraftVersion)
            throws Exception {
        JsonArray array = getJsonWithFallback(
                FABRIC_META_BMCLAPI + "/v2/versions/loader/" + encodePath(minecraftVersion),
                FABRIC_META_OFFICIAL + "/v2/versions/loader/" + encodePath(minecraftVersion)
        ).getAsJsonArray();
        List<LoaderVersion> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject loader = element.getAsJsonObject().getAsJsonObject("loader");
            result.add(new LoaderVersion(
                    text(loader, "version"),
                    booleanValue(loader, "stable")
            ));
        }
        return result;
    }

    private List<LoaderVersion> fetchQuiltVersions(String minecraftVersion)
            throws Exception {
        JsonArray array = getJson(
                QUILT_META_OFFICIAL + "/v3/versions/loader/" + encodePath(minecraftVersion)
        ).getAsJsonArray();
        List<LoaderVersion> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject loader = element.getAsJsonObject().getAsJsonObject("loader");
            String version = text(loader, "version");
            boolean stable = !version.toLowerCase(Locale.ROOT).contains("beta")
                    && !version.toLowerCase(Locale.ROOT).contains("pre")
                    && !version.toLowerCase(Locale.ROOT).contains("rc");
            result.add(new LoaderVersion(version, stable));
        }
        return result;
    }

    private List<LoaderVersion> fetchNeoForgeVersions(String minecraftVersion)
            throws Exception {
        String prefix = neoForgePrefix(minecraftVersion);
        if (prefix == null) {
            return List.of();
        }
        String xml = getText(
                NEOFORGE_MAVEN + "/net/neoforged/neoforge/maven-metadata.xml"
        );
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var document = factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
        );
        var nodes = document.getElementsByTagName("version");
        List<LoaderVersion> result = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            String version = nodes.item(index).getTextContent().trim();
            if (!version.startsWith(prefix)) {
                continue;
            }
            boolean stable = !version.toLowerCase(Locale.ROOT).contains("beta");
            result.add(new LoaderVersion(version, stable));
        }
        result.sort((left, right) -> compareVersions(right.version(), left.version()));
        return result;
    }

    private JsonElement getJsonWithFallback(String primary, String fallback)
            throws Exception {
        try {
            return getJson(primary);
        } catch (Exception primaryError) {
            return getJson(fallback);
        }
    }

    private JsonElement getJson(String url) throws Exception {
        return JsonParser.parseString(getText(url));
    }

    private JsonElement getCurseForgeJson(String url, String apiKey)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("x-api-key", apiKey)
                .GET()
                .build();
        HttpResponse<String> response = HttpHelper.getClient().send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "CurseForge request failed (" + response.statusCode()
                            + "). Check the API Key and try again."
            );
        }
        return JsonParser.parseString(response.body());
    }

    private String getText(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/xml, text/xml, */*")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = HttpHelper.getClient().send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Request failed (" + response.statusCode() + "): " + url
            );
        }
        return response.body();
    }

    private static String configuredCurseForgeApiKey(String providedKey) {
        if (providedKey != null && !providedKey.isBlank()) {
            return providedKey.trim();
        }
        String property = System.getProperty("tcl.curseforge.apiKey", "").trim();
        if (!property.isBlank()) {
            return property;
        }
        String environment = System.getenv("CURSEFORGE_API_KEY");
        return environment == null ? "" : environment.trim();
    }

    private static String resolveCurseForgeApiKey(String providedKey) {
        String apiKey = configuredCurseForgeApiKey(providedKey);
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "CurseForge API Key is required. Enter it in the download page "
                            + "or set -Dtcl.curseforge.apiKey / CURSEFORGE_API_KEY."
            );
        }
        return apiKey;
    }

    private static int curseForgeLoaderId(LoaderType loader) {
        if (loader == null || loader == LoaderType.VANILLA) {
            return 0;
        }
        return switch (loader) {
            case FORGE -> 1;
            case FABRIC -> 4;
            case QUILT -> 5;
            case NEOFORGE -> 6;
            case VANILLA -> 0;
        };
    }

    private static String curseForgeAuthors(JsonObject project) {
        if (!project.has("authors") || !project.get("authors").isJsonArray()) {
            return "";
        }
        List<String> authors = new ArrayList<>();
        for (JsonElement element : project.getAsJsonArray("authors")) {
            if (!element.isJsonObject()) {
                continue;
            }
            String name = text(element.getAsJsonObject(), "name");
            if (!name.isBlank()) {
                authors.add(name);
            }
        }
        return String.join(", ", authors);
    }

    private static void appendQuery(
            StringBuilder url,
            String name,
            String value
    ) {
        if (value == null || value.isBlank()) {
            return;
        }
        url.append('&')
                .append(encodeQuery(name))
                .append('=')
                .append(encodeQuery(value));
    }

    private static String safeFilename(String filename) {
        String normalized = filename == null
                ? ""
                : filename.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private static JsonObject selectFile(
            JsonArray files,
            Predicate<String> extension
    ) {
        JsonObject firstMatch = null;
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            String filename = text(file, "filename").toLowerCase(Locale.ROOT);
            if (!extension.test(filename)) {
                continue;
            }
            if (booleanValue(file, "primary")) {
                return file;
            }
            if (firstMatch == null) {
                firstMatch = file;
            }
        }
        return firstMatch;
    }

    private static boolean hasInstaller(JsonObject forge) {
        if (!forge.has("files") || !forge.get("files").isJsonArray()) {
            return true;
        }
        for (JsonElement element : forge.getAsJsonArray("files")) {
            JsonObject file = element.getAsJsonObject();
            if ("installer".equals(text(file, "category"))
                    && "jar".equals(text(file, "format"))) {
                return true;
            }
        }
        return false;
    }

    private static void addFacet(JsonArray facets, String value) {
        JsonArray group = new JsonArray();
        group.add(value);
        facets.add(group);
    }

    private static String neoForgePrefix(String minecraftVersion) {
        String[] parts = minecraftVersion.split("\\.");
        if (parts.length < 3 || !"1".equals(parts[0])) {
            return null;
        }
        try {
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2].replaceAll("[^0-9].*$", ""));
            if (minor < 20 || minor == 20 && patch < 2) {
                return null;
            }
            return minor + "." + patch + ".";
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("[.\\-+]");
        String[] b = right.split("[.\\-+]");
        int count = Math.max(a.length, b.length);
        for (int index = 0; index < count; index++) {
            String av = index < a.length ? a[index] : "0";
            String bv = index < b.length ? b[index] : "0";
            int result;
            try {
                result = Integer.compare(Integer.parseInt(av), Integer.parseInt(bv));
            } catch (NumberFormatException ignored) {
                result = av.compareToIgnoreCase(bv);
            }
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString()
                : "";
    }

    private static long longValue(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsLong()
                : 0L;
    }

    private static boolean booleanValue(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                && object.get(key).getAsBoolean();
    }
}
