package itt.tcl.auth;

import com.google.gson.*;
import itt.tcl.util.HttpHelper;
import java.io.*;
import java.net.URI;
import java.net.http.*;

public class MicrosoftAuth {
    private static final String CLIENT_ID = "f1812aae-969e-48a0-80c4-8afbeb9703f7";
    private static final String SCOPE = "XboxLive.signin offline_access";

    public record DeviceCodeResult(String deviceCode, String userCode, String verificationUri, int expiresIn, int interval) {}

    public static DeviceCodeResult requestDeviceCode() throws IOException, InterruptedException {
        String body = "client_id=" + CLIENT_ID + "&scope=" + SCOPE;
        HttpResponse<String> resp = HttpHelper.postForm(
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", body);
        if (resp.statusCode() != 200) {
            throw new IOException("Device code request failed: " + resp.statusCode() + " " + resp.body());
        }
        JsonObject json = HttpHelper.parseJson(resp.body());
        return new DeviceCodeResult(
                json.get("device_code").getAsString(),
                json.get("user_code").getAsString(),
                json.get("verification_uri").getAsString(),
                json.get("expires_in").getAsInt(),
                json.has("interval") ? json.get("interval").getAsInt() : 5
        );
    }

    public static void showDeviceCode(DeviceCodeResult result) {
        System.out.println("\n========================================");
        System.out.println("  Open: " + result.verificationUri());
        System.out.println("  Code: " + result.userCode());
        System.out.println("========================================");

        try {
            java.awt.Desktop.getDesktop().browse(new URI(result.verificationUri()));
            System.out.println("Browser opened.");
        } catch (Exception e) {
            System.out.println("Failed to open browser, please open manually.");
        }

        try {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(result.userCode());
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            System.out.println("Code copied to clipboard!");
        } catch (Exception e) {
            System.out.println("Failed to copy code.");
        }
    }

    public static String pollToken(String deviceCode, int expiresIn, int interval) throws Exception {
        String tokenBody = "client_id=" + CLIENT_ID
                + "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
                + "&device_code=" + deviceCode;
        long start = System.currentTimeMillis();
        long timeout = expiresIn * 1000L;

        while (System.currentTimeMillis() - start < timeout) {
            Thread.sleep(interval * 1000L);
            HttpResponse<String> resp = HttpHelper.postForm(
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token", tokenBody);
            JsonObject json = HttpHelper.parseJson(resp.body());

            if (json.has("access_token")) {
                System.out.println("Microsoft login successful.");
                return json.get("access_token").getAsString();
            }
            String error = json.has("error") ? json.get("error").getAsString() : "";
            if ("authorization_pending".equals(error)) continue;
            if ("slow_down".equals(error)) { interval += 5; continue; }
            throw new IOException("Login failed: " + json);
        }
        throw new IOException("Login timed out.");
    }

    public static String getClientId() {
        return CLIENT_ID;
    }
}
