package itt.tcl.auth;

import com.google.gson.*;
import itt.tcl.config.TCLPaths;
import java.io.*;
import java.nio.file.*;

public class AuthManager {
    public record AuthResult(String accessToken, String uuid, String username) {}

    private static final Path AUTH_FILE = TCLPaths.TCL_DIR.resolve("auth.json");

    public static AuthResult login() throws Exception {
        // Device Code Flow
        System.out.println("\nWaiting for login...");
        MicrosoftAuth.DeviceCodeResult dc = MicrosoftAuth.requestDeviceCode();
        MicrosoftAuth.showDeviceCode(dc);
        System.out.println("\nWaiting for login...");

        String msAccessToken = MicrosoftAuth.pollToken(dc.deviceCode(), dc.expiresIn(), dc.interval());

        // Xbox + MC auth
        XboxAuth.XboxResult xboxResult = XboxAuth.authenticate(msAccessToken);

        // Save
        saveAuth(xboxResult.mcAccessToken(), xboxResult.uuid(), xboxResult.username(), msAccessToken);
        System.out.println("Logged in as " + xboxResult.username() + " (" + xboxResult.uuid() + ")");

        return new AuthResult(xboxResult.mcAccessToken(), xboxResult.uuid(), xboxResult.username());
    }

    public static AuthResult loadAuth() throws IOException {
        if (!Files.exists(AUTH_FILE)) return null;
        JsonObject json = JsonParser.parseString(Files.readString(AUTH_FILE)).getAsJsonObject();
        String accessToken = json.get("access_token").getAsString();
        String uuid = json.get("uuid").getAsString();
        String username = json.get("username").getAsString();
        return new AuthResult(accessToken, uuid, username);
    }

    public static void logout() throws IOException {
        Files.deleteIfExists(AUTH_FILE);
        System.out.println("Logged out.");
    }

    public static boolean isLoggedIn() {
        return Files.exists(AUTH_FILE);
    }

    public static String getUsername() {
        try {
            AuthResult auth = loadAuth();
            return auth != null ? auth.username() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveAuth(String accessToken, String uuid, String username, String msAccessToken) throws IOException {
        JsonObject auth = new JsonObject();
        auth.addProperty("access_token", accessToken);
        auth.addProperty("uuid", uuid);
        auth.addProperty("username", username);
        auth.addProperty("client_id", MicrosoftAuth.getClientId());
        Files.writeString(AUTH_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(auth));
    }
}
