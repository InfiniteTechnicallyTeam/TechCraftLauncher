package itt.tcl.download;

import itt.tcl.util.HttpHelper;
import java.io.*;
import java.nio.file.*;

public class DownloadManager {

    public static void downloadFile(String url, Path destination) throws IOException, InterruptedException {
        System.out.print("  - " + destination.getFileName() + " ... ");
        HttpHelper.downloadFile(url, destination);
        System.out.println("Done");
    }

    public static void downloadFileSilent(String url, Path destination) throws IOException, InterruptedException {
        HttpHelper.downloadFile(url, destination);
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
}
