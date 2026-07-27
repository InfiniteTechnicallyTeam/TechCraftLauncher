package itt.tcl.version;

import com.google.gson.*;
import itt.tcl.config.TCLPaths;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class VersionManifest {
    private final JsonObject root;
    private final Path versionDir;

    public VersionManifest(Path versionJsonFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(versionJsonFile)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        this.versionDir = versionJsonFile.getParent();
    }

    public String getMainClass() {
        return root.get("mainClass").getAsString();
    }

    public List<String> getGameArguments() {
        if (root.has("arguments")) {
            JsonArray gameArgs = root.getAsJsonObject("arguments").getAsJsonArray("game");
            List<String> args = new ArrayList<>();
            for (JsonElement elem : gameArgs) {
                if (elem.isJsonPrimitive()) args.add(elem.getAsString());
            }
            return args;
        } else {
            return Arrays.asList(root.get("minecraftArguments").getAsString().split(" "));
        }
    }

    public List<String> getLibrariesClasspath() {
        JsonArray libs = root.getAsJsonArray("libraries");
        List<String> cp = new ArrayList<>();
        for (JsonElement elem : libs) {
            JsonObject lib = elem.getAsJsonObject();
            String name = lib.get("name").getAsString();
            String[] parts = name.split(":");
            String group = parts[0].replace('.', '/');
            String artifact = parts[1];
            String version = parts[2];
            Path jarPath = TCLPaths.LIBRARIES_DIR.resolve(group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar");
            cp.add(jarPath.toAbsolutePath().normalize().toString());
        }
        Path clientJar = versionDir.resolve(versionDir.getFileName() + ".jar");
        if (Files.exists(clientJar)) cp.add(clientJar.toAbsolutePath().normalize().toString());
        return cp;
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
}
