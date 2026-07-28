package itt.tcl.launch;

import itt.tcl.auth.AuthManager;
import itt.tcl.config.TCLPaths;
import itt.tcl.version.VersionManifest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ArgumentBuilder {

    public static record LaunchArgs(Path argsFile, List<String> argsList) {}

    public static LaunchArgs build(String versionId, VersionManifest manifest, Path nativesDir) throws IOException {
        List<String> cp = manifest.getLibrariesClasspath();
        String classpathStr = String.join(File.pathSeparator, cp);

        List<String> argsList = new ArrayList<>();

        // Auth placeholders
        AuthManager.AuthResult auth = null;
        try { auth = AuthManager.loadAuth(); } catch (Exception ignored) {}

        Map<String, String> placeholders = new HashMap<>();
        if (auth != null) {
            placeholders.put("auth_player_name", auth.username());
            placeholders.put("auth_uuid", formatUuid(auth.uuid()));
            placeholders.put("auth_access_token", auth.accessToken());
            placeholders.put("user_type", "msa");
            placeholders.put("clientid", "f1812aae-969e-48a0-80c4-8afbeb9703f7");
        } else {
            placeholders.put("auth_player_name", "TCLPlayer");
            placeholders.put("auth_uuid", "00000000-0000-0000-0000-000000000000");
            placeholders.put("auth_access_token", "0");
            placeholders.put("user_type", "legacy");
            placeholders.put("clientid", "");
        }

        // Version isolation
        Path versionGameDir = TCLPaths.VERSIONS_DIR.resolve(versionId).resolve("game");
        Files.createDirectories(versionGameDir);
        placeholders.put("version_name", versionId);
        placeholders.put("game_directory", versionGameDir.toAbsolutePath().normalize().toString());
        placeholders.put("assets_root", TCLPaths.ASSETS_DIR.toAbsolutePath().normalize().toString());
        placeholders.put("assets_index_name", manifest.getAssetIndex());
        placeholders.put("version_type", "release");
        placeholders.put("auth_xuid", "");
        placeholders.put("natives_directory", nativesDir.toAbsolutePath().normalize().toString());
        placeholders.put("library_directory", TCLPaths.LIBRARIES_DIR.toAbsolutePath().normalize().toString());
        placeholders.put("classpath_separator", File.pathSeparator);
        placeholders.put("classpath", classpathStr);
        placeholders.put("launcher_name", "TechCraftLauncher");
        placeholders.put("launcher_version", "1.0");

        for (String rawArg : manifest.getJvmArguments()) {
            String arg = replacePlaceholders(rawArg, placeholders);
            if (!arg.isEmpty()) argsList.add(arg);
        }

        argsList.add("-Djava.library.path=" + nativesDir.toAbsolutePath().normalize());
        argsList.add("-cp");
        argsList.add(classpathStr);
        argsList.add(manifest.getMainClass());

        for (String rawArg : manifest.getGameArguments()) {
            String arg = replacePlaceholders(rawArg, placeholders);
            if (!arg.isEmpty()) argsList.add(arg);
        }

        // Write args file
        Path argsFile = TCLPaths.TEMP_DIR.resolve("args_" + versionId + ".txt");
        try (BufferedWriter writer = Files.newBufferedWriter(argsFile, StandardCharsets.UTF_8)) {
            for (String arg : argsList) {
                writer.write(arg);
                writer.newLine();
            }
        }

        return new LaunchArgs(argsFile, argsList);
    }

    private static String replacePlaceholders(String arg, Map<String, String> map) {
        if (arg.startsWith("--")) return arg;
        for (var e : map.entrySet()) arg = arg.replace("${" + e.getKey() + "}", e.getValue());
        return arg;
    }

    private static String formatUuid(String raw) {
        if (raw == null) return "00000000-0000-0000-0000-000000000000";
        String clean = raw.replace("-", "");
        if (clean.length() != 32) return raw;
        return clean.substring(0, 8) + "-" + clean.substring(8, 12) + "-" + clean.substring(12, 16)
                + "-" + clean.substring(16, 20) + "-" + clean.substring(20, 32);
    }
}
