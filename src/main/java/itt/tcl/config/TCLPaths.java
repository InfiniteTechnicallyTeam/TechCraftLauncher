package itt.tcl.config;

import java.nio.file.Files;
import java.nio.file.Path;

public class TCLPaths {
    private static final Path ROOT_DIR = Path.of(System.getProperty("tcl.root", "."));

    public static final Path MINECRAFT_DIR = ROOT_DIR.resolve(".minecraft");
    public static final Path VERSIONS_DIR = MINECRAFT_DIR.resolve("versions");
    public static final Path LIBRARIES_DIR = MINECRAFT_DIR.resolve("libraries");
    public static final Path ASSETS_DIR = MINECRAFT_DIR.resolve("assets");

    public static final Path TCL_DIR = ROOT_DIR.resolve("TCL");
    public static final Path TEMP_DIR = TCL_DIR.resolve("temp");
    public static final Path DOWNLOADS_DIR = TCL_DIR.resolve("downloads");
    public static final Path PLUGINS_DIR = TCL_DIR.resolve("plugins");
    public static final Path LAUNCHER_PROFILES = TCL_DIR.resolve("launcher_profiles.json");
    public static final Path VERSION_MANIFEST = TCL_DIR.resolve("version_manifest_v2.json");
    public static final Path CONFIG = TCL_DIR.resolve("config.json");

    static {
        try {
            Files.createDirectories(TCL_DIR);
            Files.createDirectories(TEMP_DIR);
            Files.createDirectories(DOWNLOADS_DIR);
            Files.createDirectories(PLUGINS_DIR);
            Files.createDirectories(VERSIONS_DIR);
            Files.createDirectories(LIBRARIES_DIR);
            Files.createDirectories(ASSETS_DIR);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
