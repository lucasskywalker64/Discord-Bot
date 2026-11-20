package com.github.lucasskywalker64.installation.admin;

import com.github.lucasskywalker64.DiscordBotApplication;
import com.github.lucasskywalker64.discord.DiscordGatewayFactory;
import com.github.lucasskywalker64.installation.ProviderTestServer;
import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class LocalAdminHttpTest {
    private static final JsonMapper JSON = new JsonMapper();
    private static final String ADMIN_COOKIE = "DISCORD_BOT_LOCAL_ADMIN";
    private static final String PASSWORD = "isolated test passphrase";
    private static final String BOT_TOKEN = "test-bot-token";
    private static final Map<String, String> CREDENTIALS = Map.of("username", "local-admin", "password", PASSWORD);
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    private ProviderTestServer providers;

    @BeforeEach
    void resetDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
        providers = new ProviderTestServer();
    }

    @AfterEach
    void closeProviderServer() {
        if (providers != null) providers.close();
    }

    @Test
    void onlyThePrivateListenerServesTheConsole() throws Exception {
        try (var application = startApplication(); var http = HttpClient.newHttpClient()) {
            var server = webServer(application);
            assertEquals(404, get(http, server.getPort(), "/admin/").statusCode());
            assertEquals(200, get(http, adminPort(server), "/admin/").statusCode());
            assertEquals(404, get(http, adminPort(server), "/actuator/health/readiness").statusCode());
            assertEquals(200, get(http, server.getPort(), "/actuator/health/readiness").statusCode());
        }
    }

    @Test
    void oneBrowserClaimsTheAdministratorAndOnlyLocalSessionsAuthorizeSettings() throws Exception {
        try (var application = startApplication();
             var first = new Browser(adminPort(webServer(application)));
             var second = new Browser(first.port)) {
            assertEquals(401, first.get("configuration").statusCode());
            assertEquals(403, first.send("claim", CREDENTIALS, null).get().statusCode());
            var firstClaim = first.send("claim", CREDENTIALS, first.csrf());
            var secondClaim = second.send("claim",
                    Map.of("username", "other-admin", "password", PASSWORD), second.csrf());
            var statuses = List.of(firstClaim.get(10, TimeUnit.SECONDS).statusCode(),
                    secondClaim.get(10, TimeUnit.SECONDS).statusCode()).stream().sorted().toList();
            assertEquals(List.of(204, 409), statuses);
            assertFalse(JSON.readTree(first.get("session").body()).get("claimAvailable").asBoolean());

            if (firstClaim.get().statusCode() == 204) {
                assertLocalLogin(first, CREDENTIALS, webServer(application).getPort());
            } else {
                assertLocalLogin(second, Map.of("username", "other-admin", "password", PASSWORD),
                        webServer(application).getPort());
            }
        }
    }

    @Test
    void discordOnlySetupResumesAfterRestartAndCanFinishDuringAProviderOutage() throws Exception {
        try (var application = startApplication(); var browser = new Browser(adminPort(webServer(application)))) {
            browser.claimAndLogin();
            assertEquals(409, browser.post("setup/storage", storageDisabled()).statusCode());
            var discord = Map.of("botEnabled", true, "botToken", Map.of("action", "REPLACE", "value", BOT_TOKEN),
                    "oauthEnabled", false, "clientSecret", keep());
            var invalid = browser.post("setup/providers", Map.of("discord", discord,
                    "twitch", Map.of("enabled", true, "clientSecret", keep())));
            assertEquals(400, invalid.statusCode());
            assertTrue(invalid.body().contains("twitch.clientId"));
            var unchanged = JSON.readTree(browser.get("configuration").body());
            assertEquals("PROVIDERS", unchanged.get("setupStep").asString());
            assertFalse(unchanged.get("discord").get("botTokenConfigured").asBoolean());
            var saved = browser.post("setup/providers", Map.of("discord", discord,
                    "twitch", Map.of("enabled", false, "clientSecret", keep())));
            assertEquals(200, saved.statusCode(), saved::body);
            var result = JSON.readTree(saved.body());
            assertEquals("DEPLOYMENT", result.get("setupStep").asString());
            assertEquals("SUCCEEDED", result.path("providerChecks").path("discord.botToken").asString());
            assertEquals(1, result.path("providerChecks").size());
            assertFalse(saved.body().contains(BOT_TOKEN));
        }

        try (var application = startApplication(); var browser = new Browser(adminPort(webServer(application)))) {
            assertFalse(JSON.readTree(browser.get("session").body()).get("claimAvailable").asBoolean());
            browser.login(CREDENTIALS);
            var persisted = JSON.readTree(browser.get("configuration").body());
            assertEquals("DEPLOYMENT", persisted.get("setupStep").asString());
            assertTrue(persisted.get("discord").get("botTokenConfigured").asBoolean());
            var deployment = browser.post("setup/deployment", Map.of("publicUrl", ""));
            assertEquals(200, deployment.statusCode(), deployment::body);
            var complete = browser.post("setup/storage", storageDisabled());
            assertEquals(200, complete.statusCode(), complete::body);
            assertEquals("COMPLETE", JSON.readTree(complete.body()).get("setupStep").asString());
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            HttpResponse<String> configuration;
            JsonNode settings;
            do {
                configuration = browser.get("configuration");
                settings = JSON.readTree(configuration.body());
            } while (System.nanoTime() < deadline
                    && !settings.path("discord").path("status").path("state").asString().equals("UNAVAILABLE"));
            assertEquals("UNAVAILABLE", settings.path("discord").path("status").path("state").asString());
            assertFalse(configuration.body().contains(BOT_TOKEN), configuration::body);
            var enabled = browser.post("configuration/twitch", Map.of("enabled", true, "clientId", "twitch-client",
                    "callbackUrl", "https://bot.example/twitch/callback",
                    "clientSecret", Map.of("action", "REPLACE", "value", "test-twitch-secret")));
            assertEquals(200, enabled.statusCode(), enabled::body);
            var saved = JSON.readTree(enabled.body());
            assertEquals("CONFIGURED", saved.path("twitch").path("status").path("state").asString());
            assertEquals("SUCCEEDED", saved.path("providerChecks").path("twitch.clientSecret").asString());
            assertFalse(enabled.body().contains("test-twitch-secret"), enabled::body);

            var changedTwitch = Map.of("enabled", true, "clientId", "changed-twitch-client",
                    "callbackUrl", "https://bot.example/twitch/callback", "clientSecret", keep());
            providers.status("/twitch/oauth2/token", 401);
            var rejected = browser.post("configuration/twitch", changedTwitch);
            assertEquals(422, rejected.statusCode(), rejected::body);
            assertEquals("FAILED", JSON.readTree(rejected.body()).path("providerChecks").path("twitch.clientSecret").asString());

            providers.status("/twitch/oauth2/token", 503);
            var unavailable = browser.post("configuration/twitch", changedTwitch);
            assertEquals(503, unavailable.statusCode(), unavailable::body);
            assertEquals("UNAVAILABLE", JSON.readTree(unavailable.body()).path("providerChecks")
                    .path("twitch.clientSecret").asString());
            var stillSaved = JSON.readTree(browser.get("configuration").body());
            assertEquals("twitch-client", stillSaved.path("twitch").path("clientId").asString());
            assertEquals(saved.path("twitch").path("oauthRevision"), stillSaved.path("twitch").path("oauthRevision"));

            var storage = Map.of("enabled", true, "endpoint", providers.endpoint(), "bucket", "attachments", "region", "auto",
                    "accessKey", Map.of("action", "REPLACE", "value", "test-storage-access-key"),
                    "secretKey", Map.of("action", "REPLACE", "value", "test-storage-secret-key"));
            var storageSaved = browser.post("configuration/storage", storage);
            assertEquals(200, storageSaved.statusCode(), storageSaved::body);
            assertEquals("SUCCEEDED", JSON.readTree(storageSaved.body()).path("providerChecks").path("storage.endpoint").asString());
            assertFalse(storageSaved.body().contains("test-storage-secret-key"));

            providers.storageError("DELETE", 403, "AccessDenied");
            var invalidStorage = browser.post("configuration/storage", Map.of("enabled", true, "endpoint", providers.endpoint(),
                    "bucket", "attachments", "region", "us-east-1", "accessKey", keep(), "secretKey", keep()));
            assertEquals(422, invalidStorage.statusCode(), invalidStorage::body);
            var cleanupError = JSON.readTree(invalidStorage.body()).path("errors").path("storage.endpoint").asString();
            assertTrue(cleanupError.startsWith("Storage test cleanup failed: access denied for this operation."));
            assertTrue(cleanupError.contains("Test object key: discord-bot/validation/"));
            assertEquals("auto", JSON.readTree(browser.get("configuration").body()).path("storage").path("region").asString());
        }
    }

    @Test
    void passwordChangeAndHostResetInvalidateOtherBrowserSessions() throws Exception {
        try (var application = startApplication(); var first = new Browser(adminPort(webServer(application)));
             var second = new Browser(first.port)) {
            first.claimAndLogin();
            second.login(CREDENTIALS);
            var providerSave = first.post("setup/providers", Map.of("discord", Map.of("botEnabled", true,
                            "botToken", Map.of("action", "REPLACE", "value", BOT_TOKEN),
                            "oauthEnabled", false, "clientSecret", keep()),
                    "twitch", Map.of("enabled", false, "clientSecret", keep())));
            assertEquals(200, providerSave.statusCode(), providerSave::body);
            var change = Map.of("currentPassword", PASSWORD, "newPassword", "changed test passphrase");
            assertEquals(403, first.send("password", change, null).get().statusCode());
            assertEquals(204, first.send("password", change, first.csrf()).get().statusCode());
            assertEquals(401, first.get("configuration").statusCode());
            assertEquals(401, second.get("configuration").statusCode());
            assertEquals(401, first.send("login", CREDENTIALS, first.csrf()).get().statusCode());
            first.login(Map.of("username", "local-admin", "password", change.get("newPassword")));

            assertEquals(404, first.send("reset", Map.of(), first.csrf()).get().statusCode());
            LocalAdminRecovery.reset("recovered test passphrase", databaseArguments());
            assertEquals(401, first.get("configuration").statusCode());
            assertEquals(401, first.send("login", Map.of("username", "local-admin",
                    "password", change.get("newPassword")), first.csrf()).get().statusCode());
            first.login(Map.of("username", "local-admin", "password", "recovered test passphrase"));
            var saved = JSON.readTree(first.get("configuration").body());
            assertEquals("DEPLOYMENT", saved.get("setupStep").asString());
            assertTrue(saved.get("discord").get("botTokenConfigured").asBoolean());
        }
    }

    @Test
    void loginAttemptsAreBoundedAcrossUsernames() throws Exception {
        try (var application = startApplication(); var browser = new Browser(adminPort(webServer(application)))) {
            assertEquals(204, browser.send("claim", CREDENTIALS, browser.csrf()).get().statusCode());
            for (int attempt = 0; attempt < 8; attempt++) {
                assertEquals(401, browser.send("login", Map.of("username", "other-" + attempt, "password", PASSWORD),
                        browser.csrf()).get().statusCode());
            }
            assertEquals(429, browser.send("login", CREDENTIALS, browser.csrf()).get().statusCode());
        }
    }

    private static Map<String, Object> storageDisabled() {
        return Map.of("enabled", false, "accessKey", keep(), "secretKey", keep());
    }

    private static Map<String, String> keep() {
        return Map.of("action", "KEEP");
    }

    private static void assertLocalLogin(Browser browser, Map<String, String> credentials, int publicPort)
            throws Exception {
        String csrf = browser.csrf();
        String anonymousSession = browser.sessionId();
        assertEquals(204, browser.send("login", credentials, csrf).get().statusCode());
        assertNotEquals(anonymousSession, browser.sessionId());
        assertTrue(JSON.readTree(browser.get("session").body()).get("authenticated").asBoolean());
        String activeSession = browser.sessionId();
        try (var direct = HttpClient.newHttpClient()) {
            for (String path : List.of("/admin/", "/admin/index.html", "/admin/api/configuration",
                    "/admin/api/session", "/admin/api/login")) {
                var request = HttpRequest.newBuilder(uri(publicPort, path))
                        .header("Cookie", ADMIN_COOKIE + "=" + activeSession)
                        .header("X-Forwarded-Port", String.valueOf(browser.port))
                        .header("X-Forwarded-Host", "127.0.0.1:" + browser.port)
                        .GET().build();
                assertEquals(404, direct.send(request, HttpResponse.BodyHandlers.ofString()).statusCode(), path);
            }
            for (String purpose : List.of("DISCORD_BOT_DASHBOARD", "DISCORD_BOT_TRANSCRIPT")) {
                var request = HttpRequest.newBuilder(uri(browser.port, "/admin/api/configuration"))
                        .header("Cookie", purpose + "=" + activeSession).GET().build();
                assertEquals(401, direct.send(request, HttpResponse.BodyHandlers.ofString()).statusCode(), purpose);
            }
        }
        assertEquals(200, browser.get("configuration").statusCode());
        assertEquals(403, browser.send("logout", Map.of(), null).get().statusCode());
        assertEquals(204, browser.send("logout", Map.of(), browser.csrf()).get().statusCode());
        assertEquals(401, browser.get("configuration").statusCode());
    }

    private ConfigurableApplicationContext startApplication() {
        var application = new SpringApplication(DiscordBotApplication.class);
        application.addInitializers(context -> providers.register((GenericApplicationContext) context));
        application.addInitializers(context -> ((GenericApplicationContext) context).registerBean(
                "testGatewayFactory", DiscordGatewayFactory.class, () -> (token, _) -> {
                    throw new IllegalArgumentException("Provider unavailable: " + token);
                }));
        return application.run(databaseArguments());
    }

    private static String[] databaseArguments() {
        return new String[]{"--server.port=0", "--installation.admin.port=0",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.main.banner-mode=off", "--logging.level.root=OFF"};
    }

    private static TomcatWebServer webServer(ConfigurableApplicationContext application) {
        return (TomcatWebServer) ((WebServerApplicationContext) application).getWebServer();
    }

    private static int adminPort(TomcatWebServer server) {
        return Arrays.stream(server.getTomcat().getService().findConnectors())
                .mapToInt(Connector::getLocalPort).filter(port -> port != server.getPort())
                .findFirst().orElseThrow();
    }

    private static HttpResponse<String> get(HttpClient http, int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(port, path))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(int port, String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static class Browser implements AutoCloseable {
        private final int port;
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

        Browser(int port) {
            this.port = port;
        }

        HttpResponse<String> get(String endpoint) throws Exception {
            return LocalAdminHttpTest.get(http, port, "/admin/api/" + endpoint);
        }

        String csrf() throws Exception {
            return JSON.readTree(get("session").body()).get("csrfToken").asString();
        }

        String sessionId() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals(ADMIN_COOKIE)).findFirst().orElseThrow().getValue();
        }

        CompletableFuture<HttpResponse<String>> send(
                String endpoint, Map<String, String> form, String csrf) {
            String encoded = form.entrySet().stream().map(entry -> entry.getKey() + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
            return send(endpoint, encoded, "application/x-www-form-urlencoded", csrf);
        }

        HttpResponse<String> post(String endpoint, Object body) throws Exception {
            return send(endpoint, JSON.writeValueAsString(body), "application/json", csrf()).get();
        }

        void claimAndLogin() throws Exception {
            assertEquals(204, send("claim", CREDENTIALS, csrf()).get().statusCode());
            login(CREDENTIALS);
        }

        void login(Map<String, String> credentials) throws Exception {
            assertEquals(204, send("login", credentials, csrf()).get().statusCode());
        }

        private CompletableFuture<HttpResponse<String>> send(String endpoint, String encoded, String type, String csrf) {
            var request = HttpRequest.newBuilder(uri(port, "/admin/api/" + endpoint))
                    .header("Content-Type", type)
                    .timeout(Duration.ofSeconds(8)).POST(HttpRequest.BodyPublishers.ofString(encoded));
            if (csrf != null) {
                request.header("X-CSRF-TOKEN", csrf);
            }
            return http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        @Override
        public void close() {
            http.close();
        }
    }
}
