package itt.tcl.version;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import itt.tcl.config.TCLPaths;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class VersionManifest {
    private final JsonObject root;
    private final Path versionDir;
    private final String clientJarVersion;

    public VersionManifest(Path versionJsonFile) throws IOException {
        LoadedManifest loaded = load(versionJsonFile, new HashSet<>());
        root = loaded.root();
        clientJarVersion = loaded.clientJarVersion();
        versionDir = versionJsonFile.getParent();
    }

    public String getMainClass() {
        return root.get("mainClass").getAsString();
    }

    public List<String> getGameArguments() {
        if (root.has("arguments")
                && root.getAsJsonObject("arguments").has("game")) {
            return parseArguments(
                    root.getAsJsonObject("arguments").getAsJsonArray("game")
            );
        }
        if (root.has("minecraftArguments")) {
            return Arrays.asList(
                    root.get("minecraftArguments").getAsString().split(" ")
            );
        }
        return List.of();
    }

    public List<String> getJvmArguments() {
        if (root.has("arguments")
                && root.getAsJsonObject("arguments").has("jvm")) {
            return parseArguments(
                    root.getAsJsonObject("arguments").getAsJsonArray("jvm")
            );
        }
        return List.of();
    }

    public List<String> getLibrariesClasspath() {
        JsonArray libraries = root.getAsJsonArray("libraries");
        List<String> classpath = new ArrayList<>();
        if (libraries != null) {
            for (JsonElement element : libraries) {
                JsonObject library = element.getAsJsonObject();
                if (!isAllowed(library)) {
                    continue;
                }
                Path jarPath = artifactPath(library);
                if (jarPath != null && Files.exists(jarPath)) {
                    classpath.add(jarPath.toAbsolutePath().normalize().toString());
                }
            }
        }

        Path clientJar = TCLPaths.VERSIONS_DIR
                .resolve(clientJarVersion)
                .resolve(clientJarVersion + ".jar");
        if (Files.exists(clientJar)) {
            classpath.add(clientJar.toAbsolutePath().normalize().toString());
        }
        return classpath;
    }

    public String getAssetIndex() {
        return root.getAsJsonObject("assetIndex").get("id").getAsString();
    }

    public String getAssetIndexUrl() {
        return root.getAsJsonObject("assetIndex").get("url").getAsString();
    }

    public JsonObject getRoot() {
        return root;
    }

    public Path getVersionDir() {
        return versionDir;
    }

    private static LoadedManifest load(Path file, Set<Path> seen)
            throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        if (!seen.add(normalized)) {
            throw new IOException("Circular version inheritance: " + file);
        }

        JsonObject child;
        try (Reader reader = Files.newBufferedReader(file)) {
            child = JsonParser.parseReader(reader).getAsJsonObject();
        }

        String fallbackId = stripJsonExtension(file.getFileName().toString());
        if (!child.has("inheritsFrom")) {
            String jar = child.has("jar")
                    ? child.get("jar").getAsString()
                    : child.has("id")
                    ? child.get("id").getAsString()
                    : fallbackId;
            return new LoadedManifest(child, jar);
        }

        String parentId = child.get("inheritsFrom").getAsString();
        Path parentFile = TCLPaths.VERSIONS_DIR.resolve(parentId)
                .resolve(parentId + ".json");
        if (!Files.exists(parentFile)) {
            throw new IOException(
                    "Missing parent version " + parentId + " for " + fallbackId
            );
        }
        LoadedManifest parent = load(parentFile, seen);
        JsonObject merged = merge(parent.root(), child);
        String clientJar = child.has("jar")
                ? child.get("jar").getAsString()
                : parent.clientJarVersion();
        return new LoadedManifest(merged, clientJar);
    }

    private static JsonObject merge(JsonObject parent, JsonObject child) {
        JsonObject merged = parent.deepCopy();
        for (var entry : child.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if ("libraries".equals(key) && value.isJsonArray()) {
                merged.add(key, mergeLibraries(
                        parent.getAsJsonArray("libraries"),
                        value.getAsJsonArray()
                ));
            } else if ("arguments".equals(key) && value.isJsonObject()) {
                merged.add(key, mergeArguments(
                        parent.has("arguments")
                                ? parent.getAsJsonObject("arguments")
                                : null,
                        value.getAsJsonObject()
                ));
            } else if ("minecraftArguments".equals(key)
                    && parent.has("minecraftArguments")) {
                merged.addProperty(
                        key,
                        parent.get(key).getAsString() + " "
                                + value.getAsString()
                );
            } else {
                merged.add(key, value.deepCopy());
            }
        }
        return merged;
    }

    private static JsonArray mergeLibraries(
            JsonArray parent,
            JsonArray child
    ) {
        LinkedHashMap<String, JsonElement> libraries = new LinkedHashMap<>();
        if (parent != null) {
            for (JsonElement element : parent) {
                JsonObject library = element.getAsJsonObject();
                libraries.put(libraryKey(library), element.deepCopy());
            }
        }
        for (JsonElement element : child) {
            JsonObject library = element.getAsJsonObject();
            libraries.put(libraryKey(library), element.deepCopy());
        }
        JsonArray result = new JsonArray();
        libraries.values().forEach(result::add);
        return result;
    }

    private static JsonObject mergeArguments(
            JsonObject parent,
            JsonObject child
    ) {
        JsonObject result = parent == null
                ? new JsonObject()
                : parent.deepCopy();
        for (String type : List.of("game", "jvm")) {
            JsonArray combined = new JsonArray();
            if (parent != null && parent.has(type)) {
                parent.getAsJsonArray(type)
                        .forEach(value -> combined.add(value.deepCopy()));
            }
            if (child.has(type)) {
                child.getAsJsonArray(type)
                        .forEach(value -> combined.add(value.deepCopy()));
            }
            if (!combined.isEmpty()) {
                result.add(type, combined);
            }
        }
        return result;
    }

    private static List<String> parseArguments(JsonArray rawArguments) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : rawArguments) {
            if (element.isJsonPrimitive()) {
                result.add(element.getAsString());
                continue;
            }
            JsonObject conditional = element.getAsJsonObject();
            if (!rulesAllow(conditional.getAsJsonArray("rules"))) {
                continue;
            }
            JsonElement value = conditional.get("value");
            if (value.isJsonArray()) {
                value.getAsJsonArray().forEach(
                        item -> result.add(item.getAsString())
                );
            } else {
                result.add(value.getAsString());
            }
        }
        return result;
    }

    private static Path artifactPath(JsonObject library) {
        if (library.has("downloads")) {
            JsonObject downloads = library.getAsJsonObject("downloads");
            if (downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                if (artifact.has("path")) {
                    return TCLPaths.LIBRARIES_DIR.resolve(
                            artifact.get("path").getAsString()
                    );
                }
            }
        }

        String[] extensionParts = library.get("name")
                .getAsString()
                .split("@", 2);
        String extension = extensionParts.length == 2
                ? extensionParts[1]
                : "jar";
        String[] parts = extensionParts[0].split(":");
        if (parts.length < 3) {
            return null;
        }
        String classifier = parts.length >= 4 ? "-" + parts[3] : "";
        String filename = parts[1] + "-" + parts[2] + classifier
                + "." + extension;
        return TCLPaths.LIBRARIES_DIR
                .resolve(parts[0].replace('.', '/'))
                .resolve(parts[1])
                .resolve(parts[2])
                .resolve(filename);
    }

    private static boolean isAllowed(JsonObject library) {
        return !library.has("rules")
                || rulesAllow(library.getAsJsonArray("rules"));
    }

    private static boolean rulesAllow(JsonArray rules) {
        if (rules == null) {
            return true;
        }
        boolean allowed = false;
        for (JsonElement element : rules) {
            JsonObject rule = element.getAsJsonObject();
            if (!ruleMatches(rule)) {
                continue;
            }
            allowed = "allow".equals(rule.get("action").getAsString());
        }
        return allowed;
    }

    private static boolean ruleMatches(JsonObject rule) {
        if (rule.has("features")) {
            return false;
        }
        if (!rule.has("os")) {
            return true;
        }
        JsonObject osRule = rule.getAsJsonObject("os");
        String os = currentOs();
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

    private static String libraryKey(JsonObject library) {
        String coordinate = library.get("name").getAsString();
        String[] extensionParts = coordinate.split("@", 2);
        String[] parts = extensionParts[0].split(":");
        if (parts.length < 2) {
            return coordinate;
        }

        // A modern version manifest can contain all of these at once:
        //   org.lwjgl:lwjgl-glfw:3.4.1
        //   org.lwjgl:lwjgl-glfw:3.4.1:natives-windows
        //   org.lwjgl:lwjgl-glfw:3.4.1:natives-windows-arm64
        // They are different artifacts and must not overwrite one another.
        // The version is intentionally omitted so a child profile can still
        // replace the parent's version of the same artifact/classifier.
        String classifier = parts.length >= 4 ? parts[3] : "";
        String extension = extensionParts.length == 2
                ? extensionParts[1]
                : "jar";
        return parts[0] + ":" + parts[1] + ":" + classifier + "@" + extension;
    }

    private static String stripJsonExtension(String filename) {
        return filename.endsWith(".json")
                ? filename.substring(0, filename.length() - 5)
                : filename;
    }

    private record LoadedManifest(JsonObject root, String clientJarVersion) {}
}
