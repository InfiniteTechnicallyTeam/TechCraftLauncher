package itt.tcl.version;

import com.google.gson.*;
import itt.tcl.config.TCLPaths;
import itt.tcl.download.*;
import itt.tcl.util.HttpHelper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class VersionInstaller {

    public static void installVersion(String versionId) throws Exception {
        ensureVersion(versionId);
    }

    public static void ensureVersion(String versionId) throws Exception {
        Path versionDir = TCLPaths.VERSIONS_DIR.resolve(versionId);
        Path versionJson = versionDir.resolve(versionId + ".json");
        Path clientJar = versionDir.resolve(versionId + ".jar");

        // 1. 尝试 BMCLAPI 快速下载 JSON 和 JAR
        boolean bmclapiOk = true;
        try {
            DownloadManager.downloadFileSilent(MirrorSource.BMCLAPI_BASE + "/version/" + versionId + "/json", versionJson);
            DownloadManager.downloadFileSilent(MirrorSource.BMCLAPI_BASE + "/version/" + versionId + "/client", clientJar);
        } catch (IOException e) {
            bmclapiOk = false;
            Files.deleteIfExists(versionJson);
            Files.deleteIfExists(clientJar);
        }

        // 如果 BMCLAPI 失败，走官方 manifest 流程
        if (!bmclapiOk) {
            System.out.println("BMCLAPI fast endpoint failed, falling back to official manifest.");
            JsonObject manifest = fetchVersionManifest();
            JsonArray versions = manifest.getAsJsonArray("versions");
            String versionUrl = null;
            for (JsonElement e : versions) {
                JsonObject v = e.getAsJsonObject();
                if (v.get("id").getAsString().equals(versionId)) {
                    versionUrl = v.get("url").getAsString();
                    break;
                }
            }
            if (versionUrl == null) throw new IllegalArgumentException("Unknown version " + versionId);
            DownloadManager.downloadFileWithFallback(versionUrl, versionJson);
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(versionJson)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonObject clientDownload = root.getAsJsonObject("downloads").getAsJsonObject("client");
            DownloadManager.downloadFileWithFallback(clientDownload.get("url").getAsString(), clientJar);
        }

        // 3. 读取版本 JSON
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(versionJson)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        // 4. 收集并并发下载所有库 JAR
        JsonArray libraries = root.getAsJsonArray("libraries");
        String osKey = System.getProperty("os.name").toLowerCase().contains("win") ? "windows" : "linux";

        record LibDownload(String url, Path dest, String label) {}
        List<LibDownload> libDownloads = new ArrayList<>();

        for (JsonElement libElem : libraries) {
            JsonObject lib = libElem.getAsJsonObject();
            String name = lib.get("name").getAsString();
            String[] parts = name.split(":");

            Path mainJar = getLibraryPath(name);
            if (!Files.exists(mainJar)) {
                String mainUrl = MirrorSource.resolveLibraryUrl(name);
                libDownloads.add(new LibDownload(mainUrl, mainJar, parts[parts.length - 1] + ".jar"));
            }

            String classifier = null;
            if (parts.length >= 4 && parts[3].equals("natives-" + osKey)) {
                classifier = parts[3];
            } else if (lib.has("natives")) {
                JsonObject nativesObj = lib.getAsJsonObject("natives");
                if (nativesObj.has(osKey)) {
                    classifier = nativesObj.get(osKey).getAsString();
                }
            }
            if (classifier != null && parts.length >= 3) {
                String group = parts[0].replace('.', '/');
                String nativeJarName = parts[1] + "-" + parts[2] + "-" + classifier + ".jar";
                Path nativeJar = TCLPaths.LIBRARIES_DIR.resolve(group)
                        .resolve(parts[1]).resolve(parts[2]).resolve(nativeJarName);
                if (!Files.exists(nativeJar)) {
                    String nativeUrl = MirrorSource.LIBRARIES_BMCLAPI + "/" + group + "/" + parts[1] + "/" + parts[2] + "/" + nativeJarName;
                    libDownloads.add(new LibDownload(nativeUrl, nativeJar, nativeJarName));
                }
            }
        }

        if (!libDownloads.isEmpty()) {
            System.out.println("Libraries: downloading " + libDownloads.size() + " files...");
            AtomicInteger libDone = new AtomicInteger(0);
            long libStart = System.currentTimeMillis();
            int libThreads = Math.min(16, Runtime.getRuntime().availableProcessors() * 2);
            var libExecutor = Executors.newFixedThreadPool(libThreads);
            List<Future<?>> libFutures = new ArrayList<>();
            for (var ld : libDownloads) {
                libFutures.add(libExecutor.submit(() -> {
                    try {
                        DownloadManager.downloadFileSilentWithFallback(ld.url(), ld.dest());
                        int cur = libDone.incrementAndGet();
                        System.out.println("  Libraries: " + cur + "/" + libDownloads.size());
                    } catch (Exception e) {
                        System.err.println("  Failed: " + ld.label() + " - " + e.getMessage());
                    }
                }));
            }
            for (var f : libFutures) {
                try { f.get(); } catch (Exception ignored) {}
            }
            libExecutor.shutdown();
            long libElapsed = (System.currentTimeMillis() - libStart) / 1000;
            System.out.println("Libraries: " + libDone.get() + " downloaded. (" + libElapsed + "s)");
        } else {
            System.out.println("Libraries: all cached.");
        }

        // 5. 确保资源索引文件
        JsonObject assetIndex = root.getAsJsonObject("assetIndex");
        Path indexesDir = TCLPaths.ASSETS_DIR.resolve("indexes");
        Files.createDirectories(indexesDir);
        Path assetIndexFile = indexesDir.resolve(assetIndex.get("id").getAsString() + ".json");
        if (!Files.exists(assetIndexFile)) {
            DownloadManager.downloadFileWithFallback(assetIndex.get("url").getAsString(), assetIndexFile);
        }

        // 6. 下载资源文件
        AssetDownloader.downloadAssets(assetIndexFile);

        System.out.println("All files for version " + versionId + " are ready.");
    }

    public static JsonObject fetchVersionManifest() throws IOException, InterruptedException {
        Path cacheFile = TCLPaths.VERSION_MANIFEST;
        if (Files.exists(cacheFile) && System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis() < 86_400_000) {
            return HttpHelper.parseJson(Files.readString(cacheFile));
        }
        String body = null;
        for (String url : new String[]{MirrorSource.MANIFEST_URL_BMCLAPI, MirrorSource.MANIFEST_URL_OFFICIAL}) {
            try {
                var resp = HttpHelper.get(url);
                if (resp.statusCode() == 200) { body = resp.body(); break; }
            } catch (IOException ignored) {}
        }
        if (body == null || body.isBlank()) throw new IOException("Failed to fetch manifest");
        Files.writeString(cacheFile, body);
        return HttpHelper.parseJson(body);
    }

    private static Path getLibraryPath(String name) {
        String[] parts = name.split(":");
        return TCLPaths.LIBRARIES_DIR.resolve(parts[0].replace('.', '/'))
                .resolve(parts[1]).resolve(parts[2])
                .resolve(parts[1] + "-" + parts[2] + ".jar");
    }
}
