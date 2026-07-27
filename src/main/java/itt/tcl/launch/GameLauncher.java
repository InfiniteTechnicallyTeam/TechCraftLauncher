package itt.tcl.launch;

import itt.tcl.config.TCLPaths;
import itt.tcl.version.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class GameLauncher {

    public static void launch(String versionId) throws IOException {
        try {
            VersionInstaller.ensureVersion(versionId);
        } catch (Exception e) {
            throw new IOException("Failed to ensure version files: " + e.getMessage(), e);
        }

        Path versionJson = TCLPaths.VERSIONS_DIR.resolve(versionId + "/" + versionId + ".json");
        if (!Files.exists(versionJson)) {
            System.out.println("Version not found, installing...");
            try { VersionInstaller.installVersion(versionId); }
            catch (Exception e) { throw new IOException(e); }
        }

        VersionManifest manifest = new VersionManifest(versionJson);

        // Extract natives
        Path nativesDir = TCLPaths.VERSIONS_DIR.resolve(versionId + "/natives");
        NativeExtractor.extract(versionJson, nativesDir);

        System.out.println("Native libraries in " + nativesDir + ":");
        try (Stream<Path> list = Files.list(nativesDir)) {
            list.forEach(p -> System.out.println("  " + p.getFileName()));
        } catch (IOException e) {
            System.err.println("Could not list natives dir: " + e.getMessage());
        }

        // Build args
        var launchArgs = ArgumentBuilder.build(versionId, manifest, nativesDir);
        System.out.println("Args file: " + launchArgs.argsFile().toAbsolutePath());

        // Launch process
        List<String> command = new ArrayList<>();
        command.add(ProcessHandle.current().info().command().orElse("java"));
        command.add("@" + launchArgs.argsFile().toAbsolutePath());

        System.out.println("Command: " + String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(TCLPaths.MINECRAFT_DIR.toFile());
        pb.inheritIO();
        Process process = pb.start();
        try { process.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Cleanup
        try { Files.deleteIfExists(launchArgs.argsFile()); } catch (Exception ignored) {}
    }
}
