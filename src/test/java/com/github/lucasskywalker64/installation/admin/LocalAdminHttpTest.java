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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final TestClock clock = new TestClock();

    @Test
    void savedRecoveryCodeChangesTheSameAccountWithoutAnAdministrator() throws Exception {
        try (var application = startApplication(); var admin = new Browser(adminPort(webServer(application)));
             var owner = new Browser(webServer(application).getPort()); var claimant = new Browser(owner.port);
             var unrelated = new Browser(owner.port)) {
            saveDiscordOAuth(admin, owner.port);
            var original = owner.signInAs("101");
            var accountId = UUID.fromString(original.path("id").asString());
            seedAccountLinks(accountId);
            String code = owner.recoveryCode();
            var authorization = claimant.beginRecovery(code);
            assertEquals("consent", parameter(authorization, "prompt"));
            var callback = discordCallback(authorization, "202");
            assertEquals("/dashboard/?oauth=expired", location(unrelated.getPath(callback)));
            assertEquals("/dashboard/?oauth=recovery_confirm", location(claimant.getPath(callback)));
            var proof = claimant.recoveryProof();
            assertEquals("101", proof.path("current").path("id").asString());
            assertEquals("202", proof.path("proposed").path("id").asString());
            assertEquals("101", owner.dashboardAccount().path("discordId").asString());
            assertEquals(204, claimant.confirmRecovery(proof).statusCode());
            assertEquals(410, claimant.confirmRecovery(proof).statusCode());
            assertEquals(401, owner.getPath("/dashboard/api/account").statusCode());
            assertEquals(401, claimant.getPath("/dashboard/api/account").statusCode());
            assertEquals("/dashboard/?oauth=recovery_invalid", location(unrelated.recoveryRequest(code)));
            assertEquals(original.path("id"), claimant.signInAs("202").path("id"));
            assertAccountLinksPreserved(accountId);
        }
    }

    @Test
    void recoveryCodeRotationInvalidatesStagedProofAndOnlyOneConcurrentResetCommits() throws Exception {
        try (var application = startApplication(); var admin = new Browser(adminPort(webServer(application)));
             var owner = new Browser(webServer(application).getPort()); var first = new Browser(owner.port);
             var second = new Browser(owner.port); var signedOut = new Browser(owner.port)) {
            saveDiscordOAuth(admin, owner.port);
            var original = owner.signInAs("101");
            var oldCode = owner.recoveryCode();
            var oldProof = first.proveRecovery(oldCode, "303");
            var code = owner.recoveryCode();
            assertEquals(410, first.confirmRecovery(oldProof).statusCode());
            assertEquals(401, signedOut.dashboardPost("recovery-code", signedOut.dashboardCsrf()).statusCode());
            clock.advance(Duration.ofMinutes(11));
            assertEquals(428, owner.dashboardPost("recovery-code", owner.dashboardCsrf()).statusCode());

            var firstProof = first.proveRecovery(code, "303").path("confirmation").asString();
            var secondProof = second.proveRecovery(code, "404").path("confirmation").asString();
            var firstConfirm = first.sendDashboard("confirm-recovery", firstProof, first.dashboardCsrf());
            var secondConfirm = second.sendDashboard("confirm-recovery", secondProof, second.dashboardCsrf());
            int firstStatus = firstConfirm.get(10, TimeUnit.SECONDS).statusCode();
            int secondStatus = secondConfirm.get(10, TimeUnit.SECONDS).statusCode();
            assertEquals(List.of(204, 410), List.of(firstStatus, secondStatus).stream().sorted().toList());
            assertEquals(original.path("id"), signedOut.signInAs(firstStatus == 204 ? "303" : "404").path("id"));
        }
    }

    @Test
    void privateAdministratorCanIssueAndRevokeAnAuditedRecoveryLink() throws Exception {
        try (var application = startApplication(); var admin = new Browser(adminPort(webServer(application)));
             var owner = new Browser(webServer(application).getPort()); var claimant = new Browser(owner.port);
             var otherAccount = new Browser(owner.port)) {
            saveDiscordOAuth(admin, owner.port);
            var original = owner.signInAs("101");
            var accountId = original.path("id").asString();
            var savedCode = owner.recoveryCode();
            otherAccount.signInAs("202");
            assertEquals(404, owner.getPath("/admin/api/accounts").statusCode());
            var list = admin.get("accounts?query=PERSON-101");
            assertEquals(200, list.statusCode(), list::body);
            var items = JSON.readTree(list.body()).path("accounts");
            assertEquals(1, items.size());
            assertEquals(accountId, items.get(0).path("id").asString());
            assertFalse(list.body().contains(savedCode));

            String path = "accounts/" + accountId + "/";
            long version = items.get(0).path("loginVersion").asLong();
            assertEquals(400, admin.post(path + "recovery-link",
                    Map.of("password", PASSWORD, "reason", "", "loginVersion", version)).statusCode());
            assertEquals(401, admin.post(path + "recovery-link",
                    Map.of("password", "incorrect test passphrase", "reason", "Reviewed prior ownership",
                            "loginVersion", version)).statusCode());

            var revoked = admin.recoveryLink(path, version);
            var revokedProof = claimant.proveRecovery(revoked, "303");
            assertEquals(204, admin.post(path + "revoke-recovery-link", Map.of()).statusCode());
            assertEquals(410, claimant.confirmRecovery(revokedProof).statusCode());
            assertEquals("101", owner.dashboardAccount().path("discordId").asString());

            var expired = admin.recoveryLink(path, version);
            clock.advance(Duration.ofMinutes(31));
            assertEquals("/dashboard/?oauth=recovery_invalid", location(claimant.recoveryRequest(expired)));
            var replacedLink = admin.recoveryLink(path, version);
            var activeLink = admin.recoveryLink(path, version);
            assertEquals("/dashboard/?oauth=recovery_invalid", location(claimant.recoveryRequest(replacedLink)));
            assertEquals(409, claimant.confirmRecovery(claimant.proveRecovery(activeLink, "202")).statusCode());
            assertEquals("101", owner.dashboardAccount().path("discordId").asString());
            assertEquals("202", otherAccount.dashboardAccount().path("discordId").asString());
            assertEquals(204, claimant.confirmRecovery(claimant.proveRecovery(activeLink, "303")).statusCode());
            assertEquals(401, owner.getPath("/dashboard/api/account").statusCode());
            assertEquals("/dashboard/?oauth=recovery_invalid", location(claimant.recoveryRequest(savedCode)));
            assertEquals(original.path("id"), claimant.signInAs("303").path("id"));
            assertEquals(412, admin.post(path + "recovery-link", Map.of("password", PASSWORD,
                    "reason", "Previously verified account", "loginVersion", version)).statusCode());
            assertRecoveryAudit(UUID.fromString(accountId));
        }
    }

    private static void assertRecoveryAudit(UUID accountId) throws Exception {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var select = connection.prepareStatement("""
                     SELECT action, actor, reason, previous_discord_id, new_discord_id
                     FROM dashboard_recovery_audit WHERE account_id = ? ORDER BY id
                     """)) {
            select.setObject(1, accountId);
            try (var events = select.executeQuery()) {
                assertTrue(events.next());
                assertEquals("LINK_ISSUED", events.getString("action"));
                assertEquals("local-admin", events.getString("actor"));
                assertEquals("Reviewed prior ownership", events.getString("reason"));
                assertTrue(events.next());
                assertEquals("LINK_REVOKED", events.getString("action"));
                int revocations = 1;
                while (events.next()) {
                    String action = events.getString("action");
                    if ("LINK_REVOKED".equals(action)) {
                        revocations++;
                    }
                    if ("ISSUED_LINK_USED".equals(action)) {
                        assertEquals(2, revocations);
                        assertEquals("101", events.getString("previous_discord_id"));
                        assertEquals("303", events.getString("new_discord_id"));
                        assertFalse(events.next());
                        return;
                    }
                }
                throw new AssertionError("No completed recovery event");
            }
        }
    }

    @Test
    void administratorTestsTheSavedCallbackWithoutCreatingDashboardSessions() throws Exception {
        try (var application = startApplication(); var admin = new Browser(adminPort(webServer(application)));
             var otherAdmin = new Browser(admin.port); var publicBrowser = new Browser(webServer(application).getPort());
             var otherBrowser = new Browser(publicBrowser.port)) {
            saveDiscordOAuth(admin, publicBrowser.port);
            otherAdmin.login(CREDENTIALS);
            assertEquals(404, publicBrowser.getPath("/admin/api/discord-authorization-test").statusCode());
            assertEquals(403, admin.send("discord-authorization-test", Map.of(), null).get().statusCode());

            var launch = admin.beginAuthorizationTest();
            assertEquals("PENDING", admin.authorizationTestStatus());
            assertEquals("IDLE", otherAdmin.authorizationTestStatus());
            var authorization = location(publicBrowser.getPath(launch));
            assertEquals("consent", parameter(authorization, "prompt"));
            assertEquals(uri(publicBrowser.port, "/dashboard/oauth/discord/callback").toString(),
                    parameter(authorization, "redirect_uri"));
            providers.expectDiscordChallenge("test-success", parameter(authorization, "code_challenge"));
            var callback = discordCallback(authorization, "test-success");
            assertEquals(410, otherBrowser.getPath(callback).statusCode());
            assertEquals("PENDING", admin.authorizationTestStatus());
            assertEquals(200, publicBrowser.getPath(callback).statusCode());
            assertEquals("SUCCEEDED", admin.authorizationTestStatus());
            assertEquals(410, publicBrowser.getPath(callback).statusCode());
            assertEquals(401, publicBrowser.getPath("/dashboard/api/account").statusCode());

            var signIn = publicBrowser.beginDiscord("sign-in");
            providers.expectDiscordChallenge("101", parameter(signIn, "code_challenge"));
            var rejected = location(publicBrowser.getPath(admin.beginAuthorizationTest()));
            assertEquals(200, publicBrowser.getPath(discordCallback(rejected, "101")).statusCode());
            assertEquals("REJECTED", admin.authorizationTestStatus());
            assertEquals(401, publicBrowser.getPath("/dashboard/api/account").statusCode());
            assertEquals("/dashboard/", location(publicBrowser.getPath(discordCallback(signIn, "101"))));
            var account = publicBrowser.dashboardAccount();

            var providerRejected = location(publicBrowser.getPath(admin.beginAuthorizationTest()));
            assertEquals(200, publicBrowser.getPath(discordCallback(providerRejected, "rejected")).statusCode());
            assertEquals("REJECTED", admin.authorizationTestStatus());

            var expired = location(publicBrowser.getPath(admin.beginAuthorizationTest()));
            clock.advance(Duration.ofMinutes(6));
            assertEquals(410, publicBrowser.getPath(discordCallback(expired, "test-success")).statusCode());
            assertEquals("EXPIRED", admin.authorizationTestStatus());

            assertEquals(200, admin.post("setup/deployment", Map.of("publicUrl", "")).statusCode());
            assertEquals(200, admin.post("setup/storage", storageDisabled()).statusCode());
            var beforeSettingsChange = location(publicBrowser.getPath(admin.beginAuthorizationTest()));
            assertEquals(200, admin.post("configuration/discord", discordOAuth(publicBrowser.port,
                    "replacement-secret")).statusCode());
            assertEquals(410, publicBrowser.getPath(discordCallback(beforeSettingsChange, "test-success")).statusCode());
            assertEquals("EXPIRED", admin.authorizationTestStatus());

            var invalidated = location(publicBrowser.getPath(admin.beginAuthorizationTest()));
            assertEquals(204, admin.send("logout", Map.of(), admin.csrf()).get().statusCode());
            assertEquals(410, publicBrowser.getPath(discordCallback(invalidated, "test-success")).statusCode());
            assertEquals("IDLE", otherAdmin.authorizationTestStatus());
            assertEquals(account, publicBrowser.dashboardAccount());
        }
    }

    @Test
    void discordReplacementRequiresRecentProofAndPreservesTheAccountAcrossConflictsAndSuccess() throws Exception {
        try (var application = startApplication(); var admin = new Browser(adminPort(webServer(application)));
             var first = new Browser(webServer(application).getPort()); var oldSession = new Browser(first.port);
             var other = new Browser(first.port); var newSession = new Browser(first.port)) {
            saveDiscordOAuth(admin, first.port);
            var original = first.signInAs("101");
            var accountId = UUID.fromString(original.path("id").asString());
            seedAccountLinks(accountId);
            oldSession.signInAs("101");
            other.signInAs("202");
            clock.advance(Duration.ofMinutes(11));
            assertEquals("/dashboard/?oauth=reauthenticate",
                    location(first.dashboardPost("replace-discord", first.dashboardCsrf())));
            var wrongIdentity = first.beginDiscord("reauthenticate");
            assertEquals("/dashboard/?oauth=rejected", location(first.getPath(discordCallback(wrongIdentity, "303"))));
            assertEquals("101", first.dashboardAccount().path("discordId").asString());
            var reauthentication = first.beginDiscord("reauthenticate");
            assertEquals("consent", parameter(reauthentication, "prompt"));
            assertEquals("/dashboard/", location(first.getPath(discordCallback(reauthentication, "101"))));

            var conflict = first.proveReplacement("202");
            assertEquals(409, first.confirmReplacement(conflict).statusCode());
            assertEquals("101", first.dashboardAccount().path("discordId").asString());
            assertEquals("202", other.dashboardAccount().path("discordId").asString());
            assertEquals(410, first.confirmReplacement(conflict).statusCode());

            var pending = first.proveReplacement("303");
            var oldReauthentication = oldSession.beginDiscord("reauthenticate");
            assertEquals("/dashboard/", location(oldSession.getPath(discordCallback(oldReauthentication, "101"))));
            var stale = oldSession.proveReplacement("404");
            var reviewed = JSON.readTree(first.getPath("/dashboard/api/replacement").body());
            assertEquals("101", reviewed.path("current").path("id").asString());
            assertEquals("303", reviewed.path("proposed").path("id").asString());
            assertEquals(410, other.confirmReplacement(pending).statusCode());
            assertEquals(204, first.confirmReplacement(pending).statusCode());
            assertEquals("303", first.dashboardAccount().path("discordId").asString());
            assertEquals(original.path("id"), newSession.signInAs("303").path("id"));
            assertAccountLinksPreserved(accountId);
            assertEquals(401, oldSession.getPath("/dashboard/api/account").statusCode());
            assertEquals(401, oldSession.confirmReplacement(stale).statusCode());
        }
    }

    @Test
    void competingSignupAndReplacementRespectTheUniqueDiscordBinding() throws Exception {
        try (var application = startApplication(); var admin = new Browser(adminPort(webServer(application)));
             var first = new Browser(webServer(application).getPort()); var second = new Browser(first.port);
             var third = new Browser(first.port); var fourth = new Browser(first.port)) {
            saveDiscordOAuth(admin, first.port);
            first.signInAs("101");
            second.signInAs("202");
            var firstProof = first.proveReplacement("303");
            var secondProof = second.proveReplacement("303");
            var firstConfirm = first.sendDashboard("confirm-replacement", firstProof, first.dashboardCsrf());
            var secondConfirm = second.sendDashboard("confirm-replacement", secondProof, second.dashboardCsrf());
            var statuses = List.of(firstConfirm.get(10, TimeUnit.SECONDS).statusCode(),
                    secondConfirm.get(10, TimeUnit.SECONDS).statusCode()).stream().sorted().toList();
            assertEquals(List.of(204, 409), statuses);
            assertEquals(1, List.of(first.dashboardAccount(), second.dashboardAccount()).stream()
                    .filter(account -> account.path("discordId").asString().equals("303")).count());

            var thirdAuthorization = third.beginDiscord("sign-in");
            var fourthAuthorization = fourth.beginDiscord("sign-in");
            var thirdCallback = third.getPathAsync(discordCallback(thirdAuthorization, "404"));
            var fourthCallback = fourth.getPathAsync(discordCallback(fourthAuthorization, "404"));
            assertEquals("/dashboard/", location(thirdCallback.get(10, TimeUnit.SECONDS)));
            assertEquals("/dashboard/", location(fourthCallback.get(10, TimeUnit.SECONDS)));
            assertEquals(third.dashboardAccount().path("id"), fourth.dashboardAccount().path("id"));
        }
    }

    @Test
    void expiringOrSameIdentityProofKeepsTheWorkingDiscordLogin() throws Exception {
        try (var application = startApplication(); var admin = new Browser(adminPort(webServer(application)));
             var first = new Browser(webServer(application).getPort()); var otherSession = new Browser(first.port)) {
            saveDiscordOAuth(admin, first.port);
            var original = first.signInAs("101");
            otherSession.signInAs("101");
            var noChange = first.proveReplacement("101");
            assertEquals(204, first.confirmReplacement(noChange).statusCode());
            assertEquals(original.path("id"), otherSession.dashboardAccount().path("id"));
            var pending = first.proveReplacement("202");
            clock.advance(Duration.ofMinutes(6));
            assertEquals(410, first.confirmReplacement(pending).statusCode());
            assertEquals("101", first.dashboardAccount().path("discordId").asString());
        }
    }

    @Test
    void discordSignInResolvesOneAccountAndLogoutRevokesTheSession() throws Exception {
        try (var application = startApplication(); var admin = new Browser(adminPort(webServer(application)));
             var first = new Browser(webServer(application).getPort()); var second = new Browser(first.port)) {
            saveDiscordOAuth(admin, first.port);
            assertEquals(200, first.getPath("/dashboard/").statusCode());
            assertEquals(404, admin.getPath("/dashboard/").statusCode());
            assertEquals(401, first.getPath("/dashboard/api/account").statusCode());

            var authorization = first.beginDiscord("sign-in");
            assertEquals("none", parameter(authorization, "prompt"));
            assertEquals("identify", parameter(authorization, "scope"));
            assertEquals("S256", parameter(authorization, "code_challenge_method"));
            providers.expectDiscordChallenge("101", parameter(authorization, "code_challenge"));
            assertEquals(uri(first.port, "/dashboard/oauth/discord/callback").toString(),
                    parameter(authorization, "redirect_uri"));
            var callback = discordCallback(authorization, "101");
            assertEquals("/dashboard/?oauth=expired", location(second.getPath(callback)));
            assertEquals("/dashboard/", location(first.getPath(callback)));
            var account = first.dashboardAccount();
            assertEquals("101", account.path("discordId").asString());
            assertEquals("/dashboard/?oauth=expired", location(first.getPath(callback)));
            var secondAuthorization = second.beginDiscord("sign-in");
            assertEquals("none", parameter(secondAuthorization, "prompt"));
            providers.expectDiscordChallenge("101", parameter(secondAuthorization, "code_challenge"));
            assertEquals("/dashboard/", location(second.getPath(discordCallback(secondAuthorization, "101"))));
            assertEquals(account.path("id"), second.dashboardAccount().path("id"));

            assertEquals(403, first.dashboardPost("logout", null).statusCode());
            var oldCookie = first.cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals("DISCORD_BOT_DASHBOARD")).findFirst().orElseThrow();
            assertEquals(204, first.dashboardPost("logout", first.dashboardCsrf()).statusCode());
            first.cookies.getCookieStore().add(uri(first.port, "/dashboard/"), oldCookie);
            assertEquals(401, first.getPath("/dashboard/api/account").statusCode());
            assertEquals(account.path("id"), second.dashboardAccount().path("id"));
        }
    }

    @Test
    void discordSignInRetriesWithConsentOnlyOnceWhenInteractionIsRequired() throws Exception {
        try (var application = startApplication(); var admin = new Browser(adminPort(webServer(application)));
             var first = new Browser(webServer(application).getPort()); var second = new Browser(first.port)) {
            saveDiscordOAuth(admin, first.port);

            var silent = first.beginDiscord("sign-in");
            var interactionRequired = discordError(silent, "interaction_required");
            assertEquals("/dashboard/?oauth=expired", location(second.getPath(interactionRequired)));
            var interactive = location(first.getPath(interactionRequired));
            assertEquals("consent", parameter(interactive, "prompt"));
            assertNotEquals(parameter(silent, "state"), parameter(interactive, "state"));
            assertNotEquals(parameter(silent, "code_challenge"), parameter(interactive, "code_challenge"));
            assertEquals("/dashboard/?oauth=expired", location(first.getPath(interactionRequired)));
            assertEquals("/dashboard/?oauth=rejected", location(first.getPath(discordError(interactive, "access_denied"))));
            assertEquals(401, first.getPath("/dashboard/api/account").statusCode());

            var firstTimeConsent = location(second.getPath(discordError(second.beginDiscord("sign-in"), "access_denied")));
            assertEquals("consent", parameter(firstTimeConsent, "prompt"));
            providers.expectDiscordChallenge("101", parameter(firstTimeConsent, "code_challenge"));
            assertEquals("/dashboard/", location(second.getPath(discordCallback(firstTimeConsent, "101"))));
            assertEquals("101", second.dashboardAccount().path("discordId").asString());
        }
    }

    @Test
    void showsTheSuggestedDiscordCallbackAndRejectsInvalidUrlsOnSave() throws Exception {
        try (var application = startApplication(); var admin = new Browser(adminPort(webServer(application)))) {
            admin.claimAndLogin();
            var initial = JSON.readTree(admin.get("configuration").body()).path("discord");
            assertEquals("https://<your-public-host>/dashboard/oauth/discord/callback",
                    initial.path("suggestedCallbackUrl").asString());

            int publicPort = webServer(application).getPort();
            var discord = new HashMap<>(discordOAuth(publicPort, "test-discord-secret"));
            var twitch = Map.of("enabled", false, "clientSecret", keep());
            var callback = (String) discord.get("callbackUrl");
            discord.put("callbackUrl", uri(publicPort, "/api/auth/discord/callback").toString());
            var rejectedSetup = admin.post("setup/providers", Map.of("discord", discord, "twitch", twitch));
            assertEquals(400, rejectedSetup.statusCode(), rejectedSetup::body);
            assertTrue(JSON.readTree(rejectedSetup.body()).path("errors").path("discord.callbackUrl").asString()
                    .contains("/dashboard/oauth/discord/callback"));

            discord.put("callbackUrl", callback);
            var saved = admin.post("setup/providers", Map.of("discord", discord, "twitch", twitch));
            assertEquals(200, saved.statusCode(), saved::body);
            assertEquals(callback, JSON.readTree(saved.body()).path("discord").path("suggestedCallbackUrl").asString());
            var deployment = admin.post("setup/deployment", Map.of("publicUrl", "https://bot.example:8443/dashboard/"));
            assertEquals(200, deployment.statusCode(), deployment::body);
            assertEquals("https://bot.example:8443/dashboard/oauth/discord/callback",
                    JSON.readTree(deployment.body()).path("discord").path("suggestedCallbackUrl").asString());
            assertEquals(200, admin.post("setup/storage", storageDisabled()).statusCode());

            for (String invalid : List.of(callback + "?extra=true", callback + "#fragment",
                    "http://bot.example/dashboard/oauth/discord/callback")) {
                discord.put("callbackUrl", invalid);
                var rejected = admin.post("configuration/discord", discord);
                assertEquals(400, rejected.statusCode(), rejected::body);
                assertTrue(JSON.readTree(rejected.body()).path("errors").has("discord.callbackUrl"));
            }
            var persisted = JSON.readTree(admin.get("configuration").body()).path("discord");
            assertEquals(callback, persisted.path("callbackUrl").asString());
            assertEquals(1, persisted.path("oauthRevision").asInt());
        }
    }

    @Test
    void dashboardAccountLoadKeepsTheCsrfTokenForReplacement() throws Exception {
        try (var application = startApplication(); var admin = new Browser(adminPort(webServer(application)));
             var browser = new Browser(webServer(application).getPort())) {
            saveDiscordOAuth(admin, browser.port);
            String beforeSignIn = browser.dashboardCsrf();
            var signIn = location(browser.submitDiscord("sign-in", beforeSignIn));
            assertEquals("/dashboard/", location(browser.getPath(discordCallback(signIn, "101"))));
            assertEquals(403, browser.submitDiscord("replace-discord", beforeSignIn).statusCode());

            String csrf = browser.dashboardCsrf();
            assertEquals("101", browser.dashboardAccount().path("discordId").asString());
            var replacement = browser.submitDiscord("replace-discord", csrf);
            assertEquals(303, replacement.statusCode());
            assertEquals("discord.com", URI.create(location(replacement)).getHost());
        }
    }

    private static void saveDiscordOAuth(Browser admin, int publicPort) throws Exception {
        admin.claimAndLogin();
        var saved = admin.post("setup/providers", Map.of("discord", discordOAuth(publicPort, "test-discord-secret"),
                "twitch", Map.of("enabled", false, "clientSecret", keep())));
        assertEquals(200, saved.statusCode(), saved::body);
    }

    private static Map<String, Object> discordOAuth(int publicPort, String secret) {
        return Map.of("botEnabled", true, "botToken", Map.of("action", "REPLACE", "value", BOT_TOKEN),
                "oauthEnabled", true, "clientId", "test-client", "clientSecret",
                Map.of("action", "REPLACE", "value", secret), "callbackUrl",
                uri(publicPort, "/dashboard/oauth/discord/callback").toString());
    }

    private static void seedAccountLinks(UUID accountId) throws Exception {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var update = connection.prepareStatement("""
                     UPDATE dashboard_account SET primary_twitch_id = '720', managed_guild_id = '820' WHERE id = ?
                     """)) {
            update.setObject(1, accountId);
            assertEquals(1, update.executeUpdate());
        }
    }

    private static void assertAccountLinksPreserved(UUID accountId) throws Exception {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var select = connection.prepareStatement("""
                     SELECT primary_twitch_id, managed_guild_id FROM dashboard_account WHERE id = ?
                     """)) {
            select.setObject(1, accountId);
            try (var links = select.executeQuery()) {
                assertTrue(links.next());
                assertEquals("720", links.getString("primary_twitch_id"));
                assertEquals("820", links.getString("managed_guild_id"));
            }
        }
    }

    private static String location(HttpResponse<String> response) {
        assertTrue(response.statusCode() == 302 || response.statusCode() == 303,
                () -> response.statusCode() + " " + response.body());
        return response.headers().firstValue("Location").orElseThrow();
    }

    private static String parameter(String target, String name) {
        return Arrays.stream(URI.create(target).getRawQuery().split("&"))
                .filter(part -> part.startsWith(name + "=")).findFirst()
                .map(part -> URLDecoder.decode(part.substring(name.length() + 1), StandardCharsets.UTF_8))
                .orElseThrow();
    }

    private static String discordCallback(String authorization, String code) {
        return "/dashboard/oauth/discord/callback?state=" + parameter(authorization, "state") + "&code=" + code;
    }

    private static String discordError(String authorization, String error) {
        return "/dashboard/oauth/discord/callback?state=" + parameter(authorization, "state") + "&error=" + error;
    }

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

        Instant storageVerifiedAt;
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
            var storageResult = JSON.readTree(storageSaved.body());
            assertEquals("SUCCEEDED", storageResult.path("providerChecks").path("storage.endpoint").asString());
            storageVerifiedAt = Instant.parse(storageResult.path("storage").path("verifiedAt").asString());
            assertFalse(storageSaved.body().contains("test-storage-secret-key"));

            providers.registerStorageBucket("new-attachments");
            providers.storageError("DELETE", 403, "AccessDenied");
            var invalidStorage = browser.post("configuration/storage", Map.of("enabled", true, "endpoint", providers.endpoint(),
                    "bucket", "new-attachments", "region", "us-east-1",
                    "accessKey", Map.of("action", "REPLACE", "value", "new-storage-access-key"),
                    "secretKey", Map.of("action", "REPLACE", "value", "new-storage-secret-key")));
            assertEquals(422, invalidStorage.statusCode(), invalidStorage::body);
            var requests = providers.storageRequests();
            var attemptedReplacement = requests.subList(3, requests.size());
            assertEquals(List.of("PUT", "GET", "DELETE"), attemptedReplacement.stream()
                    .map(ProviderTestServer.Received::method).toList());
            assertTrue(attemptedReplacement.stream().allMatch(request -> request.path().startsWith("/new-attachments/")
                    && request.authorization().contains("Credential=new-storage-access-key/")));
            var cleanupError = JSON.readTree(invalidStorage.body()).path("errors").path("storage.endpoint").asString();
            assertTrue(cleanupError.startsWith("Storage test cleanup failed: access denied for this operation."));
            assertTrue(cleanupError.contains("Test object: s3://new-attachments/discord-bot/validation/"), cleanupError);
            assertFalse(invalidStorage.body().contains("new-storage-secret-key"));
            var persistedStorage = JSON.readTree(browser.get("configuration").body()).path("storage");
            assertEquals("attachments", persistedStorage.path("bucket").asString());
            assertEquals("auto", persistedStorage.path("region").asString());
            assertEquals(storageVerifiedAt, Instant.parse(persistedStorage.path("verifiedAt").asString()));
        }

        try (var application = startApplication(); var browser = new Browser(adminPort(webServer(application)))) {
            browser.login(CREDENTIALS);
            var storage = JSON.readTree(browser.get("configuration").body()).path("storage");
            assertEquals(storageVerifiedAt, Instant.parse(storage.path("verifiedAt").asString()));

            var disabled = browser.post("configuration/storage", storageDisabled());
            assertEquals(200, disabled.statusCode(), disabled::body);
            var disabledStorage = JSON.readTree(disabled.body()).path("storage");
            assertEquals("DISABLED", disabledStorage.path("status").path("state").asString());
            assertTrue(disabledStorage.path("verifiedAt").isNull());
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
                "testClock", Clock.class, () -> clock, bean -> bean.setPrimary(true)));
        application.addInitializers(context -> ((GenericApplicationContext) context).registerBean(
                "testGatewayFactory", DiscordGatewayFactory.class, () -> (token, _) -> {
                    throw new IllegalArgumentException("Provider unavailable: " + token);
                }));
        return application.run(databaseArguments());
    }

    private String[] databaseArguments() {
        return new String[]{"--server.port=0", "--installation.admin.port=0",
                "--dashboard.discord.api-base=" + providers.endpoint() + "/discord",
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
            return getPath("/admin/api/" + endpoint);
        }

        HttpResponse<String> getPath(String path) throws Exception {
            return LocalAdminHttpTest.get(http, port, path);
        }

        CompletableFuture<HttpResponse<String>> getPathAsync(String path) {
            return http.sendAsync(HttpRequest.newBuilder(uri(port, path)).timeout(Duration.ofSeconds(8))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        JsonNode signInAs(String id) throws Exception {
            assertEquals("/dashboard/", location(getPath(discordCallback(beginDiscord("sign-in"), id))));
            return dashboardAccount();
        }

        String proveReplacement(String id) throws Exception {
            var authorization = beginDiscord("replace-discord");
            assertEquals("consent", parameter(authorization, "prompt"));
            assertEquals("/dashboard/?oauth=confirm", location(getPath(discordCallback(authorization, id))));
            return JSON.readTree(getPath("/dashboard/api/replacement").body()).path("confirmation").asString();
        }

        String recoveryCode() throws Exception {
            var response = dashboardPost("recovery-code", dashboardCsrf());
            assertEquals(200, response.statusCode(), response::body);
            return JSON.readTree(response.body()).path("code").asString();
        }

        HttpResponse<String> recoveryRequest(String credential) throws Exception {
            String body = "credential=" + URLEncoder.encode(credential, StandardCharsets.UTF_8)
                    + "&_csrf=" + URLEncoder.encode(dashboardCsrf(), StandardCharsets.UTF_8);
            var request = HttpRequest.newBuilder(uri(port, "/dashboard/api/recover"))
                    .header("Content-Type", "application/x-www-form-urlencoded").timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        String beginRecovery(String credential) throws Exception {
            return location(recoveryRequest(credential));
        }

        String recoveryLink(String accountPath, long loginVersion) throws Exception {
            var response = post(accountPath + "recovery-link",
                    Map.of("password", PASSWORD, "reason", "Reviewed prior ownership", "loginVersion", loginVersion));
            assertEquals(200, response.statusCode(), response::body);
            var link = URI.create(JSON.readTree(response.body()).path("url").asString());
            assertEquals("/dashboard/", link.getPath());
            assertTrue(link.getFragment().startsWith("recovery="));
            return link.getFragment().substring("recovery=".length());
        }

        JsonNode proveRecovery(String credential, String id) throws Exception {
            var authorization = beginRecovery(credential);
            assertEquals("/dashboard/?oauth=recovery_confirm", location(getPath(discordCallback(authorization, id))));
            return recoveryProof();
        }

        JsonNode recoveryProof() throws Exception {
            var proof = getPath("/dashboard/api/recovery");
            assertEquals(200, proof.statusCode(), proof::body);
            return JSON.readTree(proof.body());
        }

        HttpResponse<String> confirmRecovery(JsonNode proof) throws Exception {
            return sendDashboard("confirm-recovery", proof.path("confirmation").asString(), dashboardCsrf())
                    .get(10, TimeUnit.SECONDS);
        }

        HttpResponse<String> confirmReplacement(String proof) throws Exception {
            return sendDashboard("confirm-replacement", proof, dashboardCsrf()).get(10, TimeUnit.SECONDS);
        }

        String dashboardCsrf() throws Exception {
            return JSON.readTree(getPath("/dashboard/api/session").body()).path("csrfToken").asString();
        }

        String beginDiscord(String operation) throws Exception {
            return location(submitDiscord(operation, dashboardCsrf()));
        }

        HttpResponse<String> submitDiscord(String operation, String csrf) throws Exception {
            String body = "_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8);
            var request = HttpRequest.newBuilder(uri(port, "/dashboard/api/" + operation))
                    .header("Content-Type", "application/x-www-form-urlencoded").timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        JsonNode dashboardAccount() throws Exception {
            var account = getPath("/dashboard/api/account");
            assertEquals(200, account.statusCode(), account::body);
            return JSON.readTree(account.body());
        }

        HttpResponse<String> dashboardPost(String endpoint, String csrf) throws Exception {
            return sendDashboard(endpoint, null, csrf).get(10, TimeUnit.SECONDS);
        }

        CompletableFuture<HttpResponse<String>> sendDashboard(String endpoint, String proof, String csrf) {
            var request = HttpRequest.newBuilder(uri(port, "/dashboard/api/" + endpoint))
                    .timeout(Duration.ofSeconds(8)).POST(proof == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString("confirmation=" + proof));
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
            if (proof != null) request.header("Content-Type", "application/x-www-form-urlencoded");
            return http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
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

        String beginAuthorizationTest() throws Exception {
            var response = post("discord-authorization-test", Map.of());
            assertEquals(200, response.statusCode(), response::body);
            var url = URI.create(JSON.readTree(response.body()).path("launchUrl").asString());
            return url.getRawPath() + "?" + url.getRawQuery();
        }

        String authorizationTestStatus() throws Exception {
            return JSON.readTree(get("discord-authorization-test").body()).path("status").asString();
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

    private static final class TestClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-21T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
