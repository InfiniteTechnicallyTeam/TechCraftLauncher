package itt.tcl.util;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.Map;

public class HttpHelper {
    public interface TransferProgressListener {
        void onStart(long totalBytes);

        void onProgress(long downloadedBytes);
    }

    private static final TransferProgressListener NO_PROGRESS =
            new TransferProgressListener() {
                @Override
                public void onStart(long totalBytes) {}

                @Override
                public void onProgress(long downloadedBytes) {}
            };

    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static HttpClient getClient() {
        return client;
    }

    public static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<String> postJson(String url, JsonObject body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<String> postXboxJson(String url, JsonObject body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("x-xbl-contract-version", "1")
                .header("User-Agent", "TechCraftLauncher/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<String> postForm(String url, String formBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<InputStream> getStream(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    public static void downloadFile(String url, Path destination) throws IOException, InterruptedException {
        downloadFile(url, destination, Map.of(), NO_PROGRESS);
    }

    public static void downloadFile(
            String url,
            Path destination,
            Map<String, String> headers,
            TransferProgressListener progress
    ) throws IOException, InterruptedException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(
                URI.create(url)
        ).GET();
        headers.forEach(requestBuilder::header);
        HttpResponse<InputStream> response = client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofInputStream()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            Files.deleteIfExists(destination);
            throw new IOException("Download failed, status " + response.statusCode());
        }
        long totalBytes = response.headers()
                .firstValueAsLong("Content-Length")
                .orElse(-1L);
        progress.onStart(totalBytes);
        try (var in = response.body(); var out = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            long downloaded = 0L;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                out.write(buffer, 0, read);
                downloaded += read;
                progress.onProgress(downloaded);
            }
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(destination);
            throw error;
        }
    }

    public static JsonObject parseJson(String body) {
        return JsonParser.parseString(body).getAsJsonObject();
    }
}
