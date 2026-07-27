package itt.tcl.download;

import com.google.gson.*;
import itt.tcl.config.TCLPaths;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AssetDownloader {

    public static void downloadAssets(Path assetIndexFile) throws Exception {
        Path objectsDir = TCLPaths.ASSETS_DIR.resolve("objects");
        Files.createDirectories(objectsDir);

        JsonObject indexRoot;
        try (Reader r = Files.newBufferedReader(assetIndexFile)) {
            indexRoot = JsonParser.parseReader(r).getAsJsonObject();
        }
        JsonObject objects = indexRoot.getAsJsonObject("objects");

        List<String[]> toDownload = new ArrayList<>();
        int skipped = 0;
        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            Path objectFile = objectsDir.resolve(prefix).resolve(hash);
            if (!Files.exists(objectFile)) {
                toDownload.add(new String[]{hash, prefix});
            } else {
                skipped++;
            }
        }

        int total = objects.size();
        if (toDownload.isEmpty()) {
            System.out.println("Assets: 0 downloaded, " + skipped + " cached, " + total + " total.");
            return;
        }

        System.out.println("Assets: downloading " + toDownload.size() + " files (" + skipped + " cached, " + total + " total)...");
        AtomicInteger done = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        int threads = Math.min(16, Runtime.getRuntime().availableProcessors() * 2);
        var executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (String[] item : toDownload) {
            String hash = item[0];
            String prefix = item[1];
            futures.add(executor.submit(() -> {
                Path objectFile = objectsDir.resolve(prefix).resolve(hash);
                try {
                    String bmclUrl = MirrorSource.ASSETS_BMCLAPI + "/" + prefix + "/" + hash;
                    String officialUrl = MirrorSource.ASSETS_OFFICIAL + "/" + prefix + "/" + hash;
                    try {
                        DownloadManager.downloadFileSilent(bmclUrl, objectFile);
                    } catch (Exception e) {
                        DownloadManager.downloadFileSilent(officialUrl, objectFile);
                    }
                    int cur = done.incrementAndGet();
                    if (cur % 20 == 0 || cur == toDownload.size()) {
                        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                        double speed = elapsed > 0 ? (double) cur / elapsed : 0;
                        System.out.println("  Progress: " + cur + "/" + toDownload.size() + " (" + (int) speed + " files/s)");
                    }
                } catch (Exception e) {
                    System.err.println("  Failed: " + hash);
                }
            }));
        }
        for (var f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        executor.shutdown();
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("Assets: " + done.get() + " downloaded, " + skipped + " cached, " + total + " total. (" + elapsed + "s)");
    }
}
