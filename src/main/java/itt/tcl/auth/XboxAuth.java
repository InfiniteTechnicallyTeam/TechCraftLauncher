package itt.tcl.auth;

import com.google.gson.*;
import itt.tcl.util.HttpHelper;
import java.io.*;
import java.net.http.*;

public class XboxAuth {

    public record XboxResult(String mcAccessToken, String uuid, String username) {}

    public static XboxResult authenticate(String msAccessToken) throws Exception {
        // 1. Xbox Live Token
        JsonObject xboxProps = new JsonObject();
        xboxProps.addProperty("AuthMethod", "RPS");
        xboxProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xboxProps.addProperty("RpsTicket", "d=" + msAccessToken);
        JsonObject xboxBody = new JsonObject();
        xboxBody.add("Properties", xboxProps);
        xboxBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xboxBody.addProperty("TokenType", "JWT");

        HttpResponse<String> xboxResp = HttpHelper.postXboxJson(
                "https://user.auth.xboxlive.com/user/authenticate", xboxBody);
        System.out.println("Xbox Live response: status=" + xboxResp.statusCode());
        if (xboxResp.statusCode() != 200) {
            throw new IOException("Xbox Live auth failed (status " + xboxResp.statusCode() + "): " + xboxResp.body());
        }
        JsonObject xboxJson = HttpHelper.parseJson(xboxResp.body());
        String xblToken = xboxJson.get("Token").getAsString();
        String userHash = xboxJson.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0)
                .getAsJsonObject().get("uhs").getAsString();

        // 2. XSTS Token
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        xstsProps.add("UserTokens", userTokens);
        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProps);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");

        HttpResponse<String> xstsResp = HttpHelper.postXboxJson(
                "https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody);
        System.out.println("XSTS response: status=" + xstsResp.statusCode());
        if (xstsResp.statusCode() != 200) {
            throw new IOException("XSTS authorization failed (status " + xstsResp.statusCode() + "): " + xstsResp.body());
        }
        JsonObject xstsJson = HttpHelper.parseJson(xstsResp.body());
        String xstsToken = xstsJson.get("Token").getAsString();

        // 3. Minecraft Token
        String mcTokenBody = "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}";
        HttpRequest mcReq = HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mcTokenBody))
                .build();
        HttpResponse<String> mcResp = HttpHelper.getClient().send(mcReq, HttpResponse.BodyHandlers.ofString());
        if (mcResp.statusCode() != 200) {
            throw new IOException("Minecraft token request failed: " + mcResp.body());
        }
        String mcAccessToken = HttpHelper.parseJson(mcResp.body()).get("access_token").getAsString();

        // 4. 获取 Profile
        HttpRequest profileReq = HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + mcAccessToken)
                .GET().build();
        HttpResponse<String> profileResp = HttpHelper.getClient().send(profileReq, HttpResponse.BodyHandlers.ofString());
        if (profileResp.statusCode() != 200) {
            throw new IOException("Failed to get Minecraft profile: " + profileResp.body());
        }
        JsonObject profile = HttpHelper.parseJson(profileResp.body());
        String uuid = profile.get("id").getAsString();
        String username = profile.get("name").getAsString();

        return new XboxResult(mcAccessToken, uuid, username);
    }
}
