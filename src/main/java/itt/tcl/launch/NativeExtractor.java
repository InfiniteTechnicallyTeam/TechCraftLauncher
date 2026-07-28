package itt.tcl.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import itt.tcl.config.TCLPaths;
import itt.tcl.download.DownloadManager;
import itt.tcl.download.MirrorSource;
import itt.tcl.version.VersionManifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class NativeExtractor {
    private NativeExtractor() {}

    public static void extract(Path versionJson, Path nativesDir)
            throws IOException {
        extract(new VersionManifest(versionJson), nativesDir);
    }

    public static void extract(VersionManifest manifest, Path nativesDir)
            throws IOException {
        cleanDirectory(nativesDir);
        Files.createDirectories(nativesDir);

        JsonArray libraries = manifest.getRoot().getAsJsonArray("libraries");
        if (libraries == null) {
            return;
        }

        for (JsonElement element : libraries) {
            JsonObject library = element.getAsJsonObject();
            if (!isAllowed(library)) {
                continue;
            }

            String classifier = nativeClassifier(library);
            if (classifier == null) {
                continue;
            }

            NativeArtifact artifact = resolveNativeArtifact(
                    library,
                    classifier
            );
            if (!Files.exists(artifact.path())) {
                downloadNative(artifact);
            }
            if (Files.exists(artifact.path())) {
                extractNativeJar(artifact.path(), nativesDir);
            }
        }
    }

    private static void cleanDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A later extraction will report any unusable directory.
                }
            });
        }
    }

    private static String nativeClassifier(JsonObject library) {
        String coordinate = library.get("name").getAsString();
        String[] extensionParts = coordinate.split("@", 2);
        String[] parts = extensionParts[0].split(":");

        if (parts.length >= 4 && parts[3].startsWith("natives-")) {
            return classifierMatchesCurrentPlatform(parts[3])
                    ? parts[3]
                    : null;
        }

        if (!library.has("natives")) {
            return null;
        }
        JsonObject natives = library.getAsJsonObject("natives");
        String os = currentOs();
        if (!natives.has(os)) {
            return null;
        }
        return natives.get(os).getAsString().replace(
                "${arch}",
                currentArch() == Architecture.X86 ? "32" : "64"
        );
    }

    private static boolean classifierMatchesCurrentPlatform(
            String classifier
    ) {
        String expected = switch (currentOs()) {
            case "windows" -> switch (currentArch()) {
                case ARM64 -> "natives-windows-arm64";
                case X86 -> "natives-windows-x86";
                case X64 -> "natives-windows";
            };
            case "osx" -> currentArch() == Architecture.ARM64
                    ? "natives-macos-arm64"
                    : "natives-macos";
            default -> "natives-linux";
        };
        return classifier.equals(expected);
    }

    private static NativeArtifact resolveNativeArtifact(
            JsonObject library,
            String classifier
    ) {
        String coordinate = library.get("name").getAsString();
        String[] extensionParts = coordinate.split("@", 2);
        String extension = extensionParts.length == 2
                ? extensionParts[1]
                : "jar";
        String[] parts = extensionParts[0].split(":");

        JsonObject metadata = null;
        if (library.has("downloads")) {
            JsonObject downloads = library.getAsJsonObject("downloads");
            if (parts.length >= 4 && downloads.has("artifact")) {
                metadata = downloads.getAsJsonObject("artifact");
            } else if (downloads.has("classifiers")) {
                JsonObject classifiers = downloads.getAsJsonObject("classifiers");
                if (classifiers.has(classifier)) {
                    metadata = classifiers.getAsJsonObject(classifier);
                }
            }
        }

        String relativePath;
        if (metadata != null && metadata.has("path")) {
            relativePath = metadata.get("path").getAsString();
        } else {
            String group = parts[0].replace('.', '/');
            String artifact = parts[1];
            String version = parts[2];
            relativePath = group + "/" + artifact + "/" + version + "/"
                    + artifact + "-" + version + "-" + classifier
                    + "." + extension;
        }

        String fallbackUrl;
        if (metadata != null && metadata.has("url")) {
            fallbackUrl = metadata.get("url").getAsString();
        } else {
            String base = library.has("url")
                    ? library.get("url").getAsString()
                    : MirrorSource.LIBRARIES_OFFICIAL + "/";
            fallbackUrl = (base.endsWith("/") ? base : base + "/")
                    + relativePath;
        }
        return new NativeArtifact(
                TCLPaths.LIBRARIES_DIR.resolve(relativePath),
                MirrorSource.LIBRARIES_BMCLAPI + "/" + relativePath,
                fallbackUrl
        );
    }

    private static void downloadNative(NativeArtifact artifact) {
        System.out.println(
                "Missing native JAR: " + artifact.path().getFileName()
                        + ", downloading..."
        );
        try {
            DownloadManager.downloadFile(
                    artifact.mirrorUrl(),
                    artifact.path()
            );
            return;
        } catch (Exception mirrorError) {
            try {
                Files.deleteIfExists(artifact.path());
                DownloadManager.downloadFile(
                        artifact.fallbackUrl(),
                        artifact.path()
                );
                return;
            } catch (Exception officialError) {
                System.err.println(
                        "Failed to download " + artifact.path().getFileName()
                                + ": " + officialError.getMessage()
                );
            }
        }
        try {
            Files.deleteIfExists(artifact.path());
        } catch (IOException ignored) {
            // Keep the original error as the useful diagnostic.
        }
    }

    private static void extractNativeJar(Path nativeJar, Path nativesDir) {
        try (JarFile jar = new JarFile(nativeJar.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !isNativeFile(entry.getName())) {
                    continue;
                }
                String filename = Path.of(entry.getName())
                        .getFileName()
                        .toString();
                Path target = nativesDir.resolve(filename);
                try (InputStream input = jar.getInputStream(entry)) {
                    Files.copy(
                            input,
                            target,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
                System.out.println("  Extracted: " + filename);
            }
        } catch (IOException error) {
            System.err.println(
                    "Failed to extract native from " + nativeJar.getFileName()
                            + ": " + error.getMessage()
            );
        }
    }

    private static boolean isNativeFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".dll")
                || lower.endsWith(".so")
                || lower.endsWith(".dylib");
    }

    private static boolean isAllowed(JsonObject library) {
        if (!library.has("rules")) {
            return true;
        }
        boolean allowed = false;
        for (JsonElement element : library.getAsJsonArray("rules")) {
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
        JsonObject os = rule.getAsJsonObject("os");
        if (os.has("name")
                && !currentOs().equals(os.get("name").getAsString())) {
            return false;
        }
        if (os.has("arch")) {
            String arch = System.getProperty("os.arch", "");
            return arch.matches(os.get("arch").getAsString());
        }
        return true;
    }

    private static String currentOs() {
        String os = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac")) {
            return "osx";
        }
        return "linux";
    }

    private static Architecture currentArch() {
        String arch = System.getProperty("os.arch", "")
                .toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return Architecture.ARM64;
        }
        if (arch.equals("x86") || arch.matches("i[3-6]86")) {
            return Architecture.X86;
        }
        return Architecture.X64;
    }

    private enum Architecture {
        X64,
        X86,
        ARM64
    }

    private record NativeArtifact(
            Path path,
            String mirrorUrl,
            String fallbackUrl
    ) {}
}
