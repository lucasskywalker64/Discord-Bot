package com.github.lucasskywalker64.installation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.context.support.GenericApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ProviderTestServer implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final Map<String, Received> requests = new ConcurrentHashMap<>();
    private final List<Received> storageRequests = new CopyOnWriteArrayList<>();
    private final Map<String, String> storageObjects = new ConcurrentHashMap<>();
    private final Map<String, StorageError> storageErrors = new ConcurrentHashMap<>();
    private volatile String versionId;
    private volatile String downloadOverride;

    public ProviderTestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/discord/users/@me", request -> respond(request, "{\"bot\":true}"));
        server.createContext("/discord/oauth2/token", request -> respond(request, "{\"access_token\":\"discord-test\"}"));
        server.createContext("/twitch/oauth2/token", request -> respond(request, "{\"access_token\":\"twitch-test\"}"));
        server.createContext("/attachments/", this::storage);
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

    public void storageError(String method, int status, String code) {
        storageErrors.put(method, new StorageError(status, code));
    }

    void versionUploads(String version) {
        versionId = version;
    }

    void overrideDownload(String body) {
        downloadOverride = body;
    }

    List<Received> storageRequests() {
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
            send(request, json, statuses.getOrDefault(received.path(), 200));
        }
    }

    private void storage(HttpExchange request) throws IOException {
        try (request) {
            var received = receive(request);
            storageRequests.add(received);
            var error = storageErrors.get(received.method());
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

    record Received(String method, String authorization, String body, String path, String query) {
    }

    private record StorageError(int status, String code) {
    }
}
