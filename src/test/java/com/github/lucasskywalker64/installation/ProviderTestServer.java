package com.github.lucasskywalker64.installation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.context.support.GenericApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProviderTestServer implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final Map<String, Received> requests = new ConcurrentHashMap<>();
    private volatile int headBucketStatus = 200;
    private volatile int listingStatus = 200;
    private volatile String listingBody = "<ListBucketResult><Name>attachments</Name><IsTruncated>false</IsTruncated></ListBucketResult>";

    public ProviderTestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/discord/users/@me", request -> respond(request, "{\"bot\":true}"));
        server.createContext("/discord/oauth2/token", request -> respond(request, "{\"access_token\":\"discord-test\"}"));
        server.createContext("/twitch/oauth2/token", request -> respond(request, "{\"access_token\":\"twitch-test\"}"));
        server.createContext("/attachments", request -> {
            if (request.getRequestMethod().equals("HEAD")) {
                request.getResponseHeaders().set("x-amz-bucket-region", "us-east-1");
                respond(request, "", statuses.getOrDefault("/attachments", headBucketStatus));
            } else {
                request.getResponseHeaders().set("Content-Type", "application/xml");
                respond(request, listingBody, statuses.getOrDefault("/attachments", listingStatus));
            }
        });
        server.start();
    }

    public void register(GenericApplicationContext context) {
        context.registerBean("testProviderChecks", ProviderConfigurationChecks.class,
                this::checks, bean -> bean.setPrimary(true));
    }

    ProviderConfigurationChecks checks() {
        return new ProviderConfigurationChecks(endpoint() + "/discord", endpoint() + "/twitch");
    }

    String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void status(String path, int status) {
        statuses.put(path, status);
    }

    void headBucketStatus(int status) {
        headBucketStatus = status;
    }

    void bucketListingError(int status, String code) {
        listingBody = "<Error><Code>" + code + "</Code><Message>Raw provider detail</Message></Error>";
        listingStatus = status;
    }

    Received received(String path) {
        return requests.get(path);
    }

    private void respond(HttpExchange request, String json) throws IOException {
        respond(request, json, statuses.getOrDefault(request.getRequestURI().getPath(), 200));
    }

    private void respond(HttpExchange request, String bodyText, int status) throws IOException {
        try (request) {
            String path = request.getRequestURI().getPath();
            requests.put(path, new Received(request.getRequestMethod(), request.getRequestHeaders().getFirst("Authorization"),
                    new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);
            request.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            request.getResponseBody().write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    record Received(String method, String authorization, String body) {
    }
}
