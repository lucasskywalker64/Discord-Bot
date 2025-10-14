package com.github.lucasskywalker64.api.twitch.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lucasskywalker64.BotContext;
import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.persistence.data.TokenData;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import io.github.cdimascio.dotenv.Dotenv;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class TwitchOAuthService {

    public record TokenBundle(String accessToken, String refreshToken, Instant expiresAt) {}
    public record TwitchTokenInfo(String clientId, String userId, String[] scopes, long expiresIn, String login) {}

    private final BotContext context = BotMain.getContext();
    private final TwitchRepository repository = TwitchRepository.getInstance();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String CLIENT_ID;
    private final String CLIENT_SECRET;
    private final String LOGIN_URI;
    private final String ENCODED_REDIRECT_URI;

    public TwitchOAuthService() {
        Dotenv config = context.config();
        this.CLIENT_ID = config.get("TWITCH_CLIENT_ID");
        this.CLIENT_SECRET = config.get("TWITCH_CLIENT_SECRET");
        String serverBaseUrl = config.get("SERVER_BASE_URL");
        String loginPath = config.get("TWITCH_LOGIN_PATH");
        String redirectPath = config.get("TWITCH_REDIRECT_PATH");
        String port = config.get("SERVER_PORT");
        String redirectUri;
        if ("prod".equals(System.getProperty("app.env", "prod"))) {
            redirectUri = serverBaseUrl + redirectPath;
            LOGIN_URI = serverBaseUrl + loginPath;
        } else {
            redirectUri = serverBaseUrl + ":" + port + redirectPath;
            LOGIN_URI = serverBaseUrl + ":" + port + loginPath;
        }
        ENCODED_REDIRECT_URI = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
    }

    public String createAuthorizationLink() throws IOException {
        return LOGIN_URI;
    }

    public void revokeToken(String token) throws Exception {
        String form = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                    + "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://id.twitch.tv/oauth2/revoke"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        http.send(request, BodyHandlers.ofString());
        repository.deleteToken();
        context.twitch().shutdown();
        context.setTwitch(null);
    }

    public void onOAuthCallback(String code) throws Exception {
        TokenBundle tb = exchangeCodeForTokens(code);
        TwitchTokenInfo info = validate(tb.accessToken);

        repository.saveToken(new TokenData(tb, info.userId(), info.login()));
    }

    public TokenBundle refreshToken(String refreshToken) throws Exception {
        String tokenUrl = "https://id.twitch.tv/oauth2/token";

        String body = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8) +
                "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8) +
                "&grant_type=refresh_token";
        HttpRequest req = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("Token refresh failed: " + resp.body());

        return getTokenBundle(resp);
    }

    private TwitchOAuthService.TokenBundle exchangeCodeForTokens(String code) throws Exception {
        String tokenUrl = "https://id.twitch.tv/oauth2/token";

        String body = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8) +
                "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                "&grant_type=authorization_code" +
                "&redirect_uri=" + ENCODED_REDIRECT_URI;

        HttpRequest req = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("Token exchange failed: " + resp.body());

        return getTokenBundle(resp);
    }

    private TwitchOAuthService.TwitchTokenInfo validate(String accessToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://id.twitch.tv/oauth2/validate"))
                .header("Authorization", "OAuth " + accessToken)
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("Validate failed: " + resp.body());
        var json = new ObjectMapper().readTree(resp.body());
        String clientId = json.get("client_id").asText();
        String userId = json.get("user_id").asText();
        String login = json.get("login").asText();
        long expiresIn = json.get("expires_in").asLong();
        List<String> scopes = new ArrayList<>();
        json.get("scopes").forEach(n -> scopes.add(n.asText()));
        return new TwitchTokenInfo(clientId, userId, scopes.toArray(new String[0]), expiresIn, login);
    }

    @NotNull
    private TokenBundle getTokenBundle(HttpResponse<String> resp) throws JsonProcessingException {
        var json = new ObjectMapper().readTree(resp.body());
        String access = json.get("access_token").asText();
        String refresh = json.get("refresh_token").asText();
        int expiresIn = json.get("expires_in").asInt();

        return new TokenBundle(access, refresh, Instant.now().plusSeconds(expiresIn));
    }
}
