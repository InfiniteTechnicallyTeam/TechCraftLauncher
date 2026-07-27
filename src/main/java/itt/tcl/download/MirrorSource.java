package itt.tcl.download;

public class MirrorSource {
    public static final String BMCLAPI_BASE = "https://bmclapi2.bangbang93.com";
    public static final String OFFICIAL_BASE = "https://piston-meta.mojang.com";
    public static final String MANIFEST_URL_BMCLAPI = BMCLAPI_BASE + "/mc/game/version_manifest_v2.json";
    public static final String MANIFEST_URL_OFFICIAL = OFFICIAL_BASE + "/mc/game/version_manifest_v2.json";
    public static final String LIBRARIES_BMCLAPI = BMCLAPI_BASE + "/maven";
    public static final String LIBRARIES_OFFICIAL = "https://libraries.minecraft.net";
    public static final String ASSETS_BMCLAPI = BMCLAPI_BASE + "/assets";
    public static final String ASSETS_OFFICIAL = "https://resources.download.minecraft.net";

    public static String getFallbackUrl(String url) {
        if (url.startsWith(BMCLAPI_BASE)) {
            return url.replace(BMCLAPI_BASE, OFFICIAL_BASE);
        }
        if (url.startsWith(LIBRARIES_OFFICIAL)) {
            return url.replace(LIBRARIES_OFFICIAL, LIBRARIES_BMCLAPI);
        }
        if (url.startsWith(ASSETS_OFFICIAL)) {
            return url.replace(ASSETS_OFFICIAL, ASSETS_BMCLAPI);
        }
        if (url.startsWith(OFFICIAL_BASE)) {
            return url.replace(OFFICIAL_BASE, BMCLAPI_BASE);
        }
        return null;
    }

    public static String resolveLibraryUrl(String name) {
        String[] parts = name.split(":");
        return LIBRARIES_BMCLAPI + "/" + parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";
    }
}
