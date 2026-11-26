package com.github.lucasskywalker64.installation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.context.support.GenericApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ProviderTestServer implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final Map<String, Received> requests = new ConcurrentHashMap<>();
    private final Map<String, String> discordChallenges = new ConcurrentHashMap<>();
    private final List<Received> storageRequests = new CopyOnWriteArrayList<>();
    private final Map<String, String> storageObjects = new ConcurrentHashMap<>();
    private final Map<String, StorageError> storageErrors = new ConcurrentHashMap<>();
    private volatile String versionId;
    private volatile boolean rejectVersionedRequests;
    private volatile String downloadOverride;

    public ProviderTestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/discord/users/@me", this::discordUser);
        server.createContext("/discord/oauth2/token", this::discordToken);
        server.createContext("/twitch/oauth2/token", request -> respond(request, "{\"access_token\":\"twitch-test\"}"));
        registerStorageBucket("attachments");
        server.start();
    }

    public void register(GenericApplicationContext context) {
        context.registerBean("testProviderChecks", ProviderConfigurationChecks.class,
                this::checks, bean -> bean.setPrimary(true));
    }

    ProviderConfigurationChecks checks() {
        return new ProviderConfigurationChecks(endpoint() + "/discord", endpoint() + "/twitch");
    }

    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void status(String path, int status) {
        statuses.put(path, status);
    }

    public void expectDiscordChallenge(String code, String challenge) {
        discordChallenges.put(code, challenge);
    }

    public void storageError(String method, int status, String code) {
        storageErrors.put(method, new StorageError(status, code));
    }

    public void registerStorageBucket(String bucket) {
        server.createContext("/" + bucket + "/", this::storage);
    }

    void versionUploads(String version) {
        versionId = version;
    }

    void rejectVersionedRequests() {
        rejectVersionedRequests = true;
    }

    void overrideDownload(String body) {
        downloadOverride = body;
    }

    public List<Received> storageRequests() {
        return List.copyOf(storageRequests);
    }

    int storedObjects() {
        return storageObjects.size();
    }

    Received received(String path) {
        return requests.get(path);
    }

    private void respond(HttpExchange request, String json) throws IOException {
        try (request) {
            var received = receive(request);
            requests.put(received.path(), received);
            request.getResponseHeaders().set("Content-Type", "application/json");
            send(request, json, statuses.getOrDefault(received.path(), 200));
        }
    }

    private void discordToken(HttpExchange request) throws IOException {
        try (request) {
            var received = receive(request);
            requests.put(received.path(), received);
            String code = formField(received.body(), "code");
            String expectedChallenge = discordChallenges.get(code);
            boolean rejected = code.equals("rejected") || (expectedChallenge != null
                    && !expectedChallenge.equals(challenge(formField(received.body(), "code_verifier"))));
            int status = statuses.getOrDefault(received.path(), rejected ? 400 : 200);
            String token = code.matches("[0-9]+") ? "dashboard-user-" + code : "discord-test";
            request.getResponseHeaders().set("Content-Type", "application/json");
            send(request, "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\"}", status);
        }
    }

    private static String formField(String body, String name) {
        return Arrays.stream(body.split("&")).filter(field -> field.startsWith(name + "=")).findFirst()
                .map(field -> URLDecoder.decode(field.substring(name.length() + 1), StandardCharsets.UTF_8))
                .orElse("");
    }

    private static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private void discordUser(HttpExchange request) throws IOException {
        String authorization = request.getRequestHeaders().getFirst("Authorization");
        String prefix = "Bearer dashboard-user-";
        if (authorization != null && authorization.startsWith(prefix)) {
            String id = authorization.substring(prefix.length());
            if (id.matches("[0-9]+")) {
                respond(request, "{\"id\":\"" + id + "\",\"username\":\"person-" + id + "\"}");
                return;
            }
        }
        respond(request, "{\"bot\":true}");
    }

    private void storage(HttpExchange request) throws IOException {
        try (request) {
            var received = receive(request);
            storageRequests.add(received);
            var error = storageErrors.get(received.method());
            if (error == null && rejectVersionedRequests && received.query() != null
                    && received.query().startsWith("versionId=")) {
                error = new StorageError(501, "NotImplemented");
            }
            int status = statuses.getOrDefault("/attachments", 200);
            if (error != null) {
                request.getResponseHeaders().set("Content-Type", "application/xml");
                send(request, "<Error><Code>" + error.code() + "</Code><Message>Raw provider detail</Message></Error>",
                        error.status());
                return;
            }
            if (status != 200) {
                send(request, "", status);
                return;
            }
            switch (received.method()) {
                case "PUT" -> {
                    storageObjects.put(received.path(), received.body());
                    if (versionId != null) request.getResponseHeaders().set("x-amz-version-id", versionId);
                    send(request, "", 200);
                }
                case "GET" -> {
                    String body = storageObjects.get(received.path());
                    if (body == null) {
                        send(request, "", 404);
                    } else {
                        send(request, downloadOverride == null ? body : downloadOverride, 200);
                    }
                }
                case "DELETE" -> {
                    storageObjects.remove(received.path());
                    send(request, "", 204);
                }
                default -> send(request, "", 405);
            }
        }
    }

    private static Received receive(HttpExchange request) throws IOException {
        return new Received(request.getRequestMethod(), request.getRequestHeaders().getFirst("Authorization"),
                new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                request.getRequestURI().getPath(), request.getRequestURI().getQuery());
    }

    private static void send(HttpExchange request, String bodyText, int status) throws IOException {
        byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);
        request.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        request.getResponseBody().write(body);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public record Received(String method, String authorization, String body, String path, String query) {
    }

    private record StorageError(int status, String code) {
    }
}
