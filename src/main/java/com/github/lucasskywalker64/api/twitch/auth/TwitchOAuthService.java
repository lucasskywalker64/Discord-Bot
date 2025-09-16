package com.github.lucasskywalker64.api.twitch.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lucasskywalker64.BotContext;
import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.persistence.data.TokenData;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TwitchOAuthService {

    public record TokenBundle(String accessToken, String refreshToken, Instant expiresAt) {}
    public record TwitchTokenInfo(String clientId, String userId, String[] scopes, long expiresIn, String login) {}
    public record AuthLink(String state, String url) {}

    public static class AuthSession {
        public String state;
        public long discordUserId;
        public Instant createdAt = Instant.now();
    }

    private final BotContext context = BotMain.getContext();
    private final TwitchRepository repository = TwitchRepository.getInstance();
    private final JDA jda;
    private final Map<String, AuthSession> pending = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String clientId;
    private final String clientSecret;
    private final URI redirectUri;
    private final String scopes;
    private final int port;
    private HttpServer server;

    public TwitchOAuthService() {
        Dotenv config = context.config();
        this.jda = context.jda();
        this.clientId = config.get("TWITCH_CLIENT_ID");
        this.clientSecret = config.get("TWITCH_CLIENT_SECRET");
        this.redirectUri = URI.create(config.get("TWITCH_REDIRECT_URI"));
        this.port = Integer.parseInt(config.get("TWITCH_CALLBACK_PORT"));
        this.scopes = config.get("TWITCH_SCOPE");
    }

    public AuthLink createAuthorizationLink(long discordUserId) throws IOException {
        String state = UUID.randomUUID().toString();
        AuthSession s = new AuthSession();
        s.state = state; s.discordUserId = discordUserId;
        pending.put(state, s);

        String url = "https://id.twitch.tv/oauth2/authorize" +
                "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&redirect_uri=" + redirectUri +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(scopes, StandardCharsets.UTF_8) +
                "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);

        startLocalServer();
        return new AuthLink(state, url);
    }

    public void revokeToken(String token) throws Exception {
        String form = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
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

    public void onOAuthCallback(String code, String state) throws Exception {
        AuthSession s = pending.remove(state);
        if (s == null || s.createdAt.isBefore(Instant.now().minus(Duration.ofMinutes(10)))) {
            throw new IllegalStateException("State not found or expired.");
        }

        TokenBundle tb = exchangeCodeForTokens(code);
        TwitchTokenInfo info = validate(tb.accessToken);

        repository.saveToken(new TokenData(tb, info.userId(), info.login()));

        jda.retrieveUserById(s.discordUserId).queue(user -> {
            user.openPrivateChannel().queue(pc -> {
                pc.sendMessage("Twitch authorization complete for @" + info.login() + ".").queue();
            }, __ -> {});
        }, __ -> {});
    }

    public TokenBundle refreshToken(String refreshToken) throws Exception {
        String tokenUrl = "https://id.twitch.tv/oauth2/token";

        String body = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
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

        String body = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                "&grant_type=authorization_code" +
                "&redirect_uri=" + redirectUri;

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

    private void startLocalServer() throws IOException {
        if (server != null) return;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), 0);
        server.createContext(redirectUri.getPath(), new CallbackHandler());
        server.start();
        Logger.info("OAuth callback server started waiting for request...");
    }

    @NotNull
    private TokenBundle getTokenBundle(HttpResponse<String> resp) throws JsonProcessingException {
        var json = new ObjectMapper().readTree(resp.body());
        String access = json.get("access_token").asText();
        String refresh = json.get("refresh_token").asText();
        int expiresIn = json.get("expires_in").asInt();

        return new TokenBundle(access, refresh, Instant.now().plusSeconds(expiresIn));
    }

    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Logger.info("Received OAuth callback");
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                String code = query.get("code");
                String state = query.get("state");
                if (code == null || state == null) {
                    writeHtml(exchange, 400, "<h1>Missing code/state</h1>");
                    return;
                }
                onOAuthCallback(code, state);
                writeHtml(exchange, 200, "<h1>Authorization complete</h1>");
                server.stop(3);
                server = null;
                Logger.info("OAuth callback server stopped");
                context.setTwitch(new TwitchImpl(context.jda()));
            } catch (Exception e) {
                Logger.error(e);
                writeHtml(exchange, 500, "<h1>Token exchange failed</h1>");
            }
        }

        private Map<String, String> parseQuery(String q) {
            Map<String, String> map = new HashMap<>();
            if (q == null || q.isEmpty()) return map;
            for (String pair : q.split("&")) {
                String[] kv = pair.split("=", 2);
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                map.put(k, v);
            }
            return map;
        }

        private void writeHtml(HttpExchange ex, int code, String html) throws IOException {
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }
}
