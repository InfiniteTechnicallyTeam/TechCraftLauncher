package itt.tcl.download;

import itt.tcl.util.HttpHelper;
import java.io.*;
import java.nio.file.*;
import java.util.Map;

public class DownloadManager {
    private static final DownloadProgressTracker PROGRESS =
            DownloadProgressTracker.getInstance();

    public static void downloadFile(String url, Path destination) throws IOException, InterruptedException {
        System.out.print("  - " + destination.getFileName() + " ... ");
        downloadTracked(url, destination, Map.of());
        System.out.println("Done");
    }

    public static void downloadFileSilent(String url, Path destination) throws IOException, InterruptedException {
        downloadTracked(url, destination, Map.of());
    }

    public static void downloadFileSilent(
            String url,
            Path destination,
            Map<String, String> headers
    ) throws IOException, InterruptedException {
        downloadTracked(url, destination, headers);
    }

    public static void downloadFileWithFallback(String url, Path destination) throws IOException, InterruptedException {
        try {
            downloadFile(url, destination);
        } catch (IOException e) {
            String fallbackUrl = MirrorSource.getFallbackUrl(url);
            if (fallbackUrl != null) {
                downloadFile(fallbackUrl, destination);
            } else {
                throw e;
            }
        }
    }

    public static void downloadFileSilentWithFallback(String url, Path destination) throws IOException, InterruptedException {
        try {
            downloadFileSilent(url, destination);
        } catch (IOException e) {
            String fallbackUrl = MirrorSource.getFallbackUrl(url);
            if (fallbackUrl != null) {
                downloadFileSilent(fallbackUrl, destination);
            } else {
                throw e;
            }
        }
    }

    private static void downloadTracked(
            String url,
            Path destination,
            Map<String, String> headers
    ) throws IOException, InterruptedException {
        long[] transferId = {-1L};
        try {
            HttpHelper.downloadFile(
                    url,
                    destination,
                    headers,
                    new HttpHelper.TransferProgressListener() {
                        @Override
                        public void onStart(long totalBytes) {
                            transferId[0] = PROGRESS.beginTransfer(
                                    destination.getFileName().toString(),
                                    totalBytes
                            );
                        }

                        @Override
                        public void onProgress(long downloadedBytes) {
                            PROGRESS.updateTransfer(
                                    transferId[0],
                                    downloadedBytes
                            );
                        }
                    }
            );
            PROGRESS.finishTransfer(transferId[0]);
        } catch (IOException | InterruptedException | RuntimeException error) {
            PROGRESS.discardTransfer(transferId[0]);
            throw error;
        }
    }
}
