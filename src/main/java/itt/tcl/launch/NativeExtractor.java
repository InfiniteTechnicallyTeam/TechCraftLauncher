package itt.tcl.launch;

import com.google.gson.*;
import itt.tcl.config.TCLPaths;
import itt.tcl.download.*;
import itt.tcl.version.VersionManifest;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Stream;
import java.util.Comparator;

public class NativeExtractor {

    public static void extract(Path versionJson, Path nativesDir) throws IOException {
        extract(new VersionManifest(versionJson), nativesDir);
    }

    public static void extract(VersionManifest manifest, Path nativesDir) throws IOException {
        if (Files.exists(nativesDir)) {
            try (Stream<Path> walk = Files.walk(nativesDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException e) { /* ignore */ }
                        });
            } catch (IOException e) {
                System.err.println("Failed to clean natives dir: " + e.getMessage());
            }
        }
        Files.createDirectories(nativesDir);

        {
            JsonObject root = manifest.getRoot();
            JsonArray libraries = root.getAsJsonArray("libraries");
            String nativeKey = System.getProperty("os.name").toLowerCase().contains("win") ? "windows" : "linux";
            for (JsonElement elem : libraries) {
                JsonObject lib = elem.getAsJsonObject();
                String name = lib.get("name").getAsString();
                String[] parts = name.split(":");
                if (parts.length < 3) continue;

                String classifier = null;
                if (parts.length >= 4 && parts[3].equals("natives-" + nativeKey)) {
                    classifier = parts[3];
                }
                if (classifier == null && lib.has("natives")) {
                    JsonObject nativesObj = lib.getAsJsonObject("natives");
                    if (nativesObj.has(nativeKey)) {
                        classifier = nativesObj.get(nativeKey).getAsString();
                    }
                }
                if (classifier == null) continue;
                classifier = classifier.replace(
                        "${arch}",
                        System.getProperty("os.arch", "").contains("64")
                                ? "64"
                                : "32"
                );

                if (lib.has("rules")) {
                    boolean allowed = false;
                    for (JsonElement ruleElem : lib.getAsJsonArray("rules")) {
                        JsonObject rule = ruleElem.getAsJsonObject();
                        String action = rule.get("action").getAsString();
                        if (rule.has("os")) {
                            String osName = rule.getAsJsonObject("os").get("name").getAsString();
                            if (action.equals("allow") && osName.equals(nativeKey)) allowed = true;
                            if (action.equals("disallow") && osName.equals(nativeKey)) allowed = false;
                        } else if (action.equals("allow")) {
                            allowed = true;
                        }
                    }
                    if (!allowed) continue;
                }

                String group = parts[0].replace('.', '/');
                String artifact = parts[1];
                String version = parts[2];
                String nativeJarName = artifact + "-" + version + "-" + classifier + ".jar";
                Path nativeJar = TCLPaths.LIBRARIES_DIR.resolve(group).resolve(artifact).resolve(version).resolve(nativeJarName);

                if (!Files.exists(nativeJar)) {
                    System.out.println("Missing native JAR: " + nativeJarName + ", downloading...");
                    String urlBMCL = MirrorSource.LIBRARIES_BMCLAPI + "/" + group + "/" + artifact + "/" + version + "/" + nativeJarName;
                    String urlOfficial = MirrorSource.LIBRARIES_OFFICIAL + "/" + group + "/" + artifact + "/" + version + "/" + nativeJarName;
                    boolean downloaded = false;
                    try {
                        DownloadManager.downloadFile(urlBMCL, nativeJar);
                        downloaded = true;
                    } catch (Exception e) {
                        try {
                            DownloadManager.downloadFile(urlOfficial, nativeJar);
                            downloaded = true;
                        } catch (Exception ex) {
                            System.err.println("Failed to download " + nativeJarName + ": " + ex.getMessage());
                        }
                    }
                    if (!downloaded) {
                        System.err.println("Warning: Could not download " + nativeJarName + ", skipping.");
                        continue;
                    }
                }

                try (JarFile jar = new JarFile(nativeJar.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.isDirectory()) continue;
                        String entryName = entry.getName();
                        if (entryName.endsWith(".dll") || entryName.endsWith(".so") || entryName.endsWith(".dylib")) {
                            String fileName = Path.of(entryName).getFileName().toString();
                            Path target = nativesDir.resolve(fileName);
                            try (InputStream is = jar.getInputStream(entry)) {
                                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                            System.out.println("  Extracted: " + fileName);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Failed to extract native from " + nativeJarName + ": " + e.getMessage());
                }
            }
        }
    }
}
