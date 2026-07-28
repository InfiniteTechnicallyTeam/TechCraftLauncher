package itt.tcl.download;

public final class CurseForgeConfig {
    /*
     * 在下面两个双引号之间填写你的 CurseForge API Key。
     * 示例：public static final String API_KEY = "your-api-key";
     */
    public static final String API_KEY = "$2a$10$SQiyFI60pZ5rKFYpTVi7a.ZyThwTrv7lR1o8Mo89VrKuiNJDnwIaa";

    private CurseForgeConfig() {}

    public static String apiKey() {
        return API_KEY;
    }
}
