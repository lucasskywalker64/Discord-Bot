package com.github.lucasskywalker64.installation;

import com.github.lucasskywalker64.installation.ProviderConfigurationChecks.Inputs;
import com.github.lucasskywalker64.installation.ProviderConfigurationChecks.OAuth;
import com.github.lucasskywalker64.installation.ProviderConfigurationChecks.Storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck;
import static com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck.SUCCEEDED;
import static com.github.lucasskywalker64.installation.InstallationConfiguration.SecretUpdate.replace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderConfigurationChecksTest {
    @Test
    void verifiesBothOAuthCredentialsTheDiscordBotTokenAndStorageRoundTrip() throws Exception {
        try (var server = new ProviderTestServer()) {
            var checks = server.checks().verify(inputs(server.endpoint(), "auto")).checks();
            assertEquals(Map.of("discord.botToken", SUCCEEDED, "discord.clientSecret", SUCCEEDED,
                    "twitch.clientSecret", SUCCEEDED, "storage.endpoint", SUCCEEDED), checks);
            assertEquals("Bot test-bot", server.received("/discord/users/@me").authorization());
            var discord = server.received("/discord/oauth2/token");
            assertEquals("Basic ZGlzY29yZDpkLXNlY3JldA==", discord.authorization());
            assertEquals("grant_type=client_credentials&scope=identify", discord.body());
            var twitch = server.received("/twitch/oauth2/token");
            assertEquals("grant_type=client_credentials&client_id=twitch&client_secret=t%2Bsecret", twitch.body());
            var storage = server.storageRequests();
            assertEquals(List.of("PUT", "GET", "DELETE"), storage.stream().map(ProviderTestServer.Received::method).toList());
            String key = storage.getFirst().path();
            assertTrue(key.startsWith("/attachments/discord-bot/validation/"));
            assertTrue(storage.stream().allMatch(request -> request.path().equals(key)
                    && request.authorization().startsWith("AWS4-HMAC-SHA256 Credential=storage-key/")
                    && request.authorization().contains("/auto/s3/aws4_request")));
            assertEquals(0, server.storedObjects());
        }
    }

    @ParameterizedTest
    @CsvSource({"401,FAILED", "403,FAILED", "404,FAILED", "429,UNAVAILABLE", "503,UNAVAILABLE"})
    void distinguishesCredentialRejectionFromTemporaryProviderFailure(int status, ProviderCheck outcome) throws Exception {
        try (var server = new ProviderTestServer()) {
            server.status("/discord/users/@me", status);
            server.status("/discord/oauth2/token", status);
            server.status("/twitch/oauth2/token", status);
            server.status("/attachments", status);
            assertEquals(Map.of("discord.botToken", outcome, "discord.clientSecret", outcome,
                    "twitch.clientSecret", outcome, "storage.endpoint", outcome),
                    server.checks().verify(inputs(server.endpoint(), "us-east-1")).checks());
        }
    }

    @Test
    void rejectsAStorageRegionWhenTheProviderRejectsRequestsSignedForThatRegion() throws Exception {
        try (var server = new ProviderTestServer()) {
            server.storageError("PUT", 400, "AuthorizationHeaderMalformed");
            var verification = server.checks().verify(inputs(server.endpoint(), "us-west-2"));
            assertEquals(ProviderCheck.FAILED, verification.checks().get("storage.endpoint"));
            assertEquals("Storage test upload failed: the endpoint or signing region was rejected.",
                    verification.errors().get("storage.endpoint"));
            assertEquals(1, server.storageRequests().size());
        }
    }

    @Test
    void verifiesAndDeletesTheExactUploadedVersion() throws Exception {
        try (var server = new ProviderTestServer()) {
            server.versionUploads("test-version");
            var verification = server.checks().verify(inputs(server.endpoint(), "auto"));
            assertEquals(SUCCEEDED, verification.checks().get("storage.endpoint"));
            var requests = server.storageRequests();
            assertEquals("versionId=test-version", requests.get(1).query());
            assertEquals("versionId=test-version", requests.get(2).query());
        }
    }

    @Test
    void cleansUpAfterARejectedDownload() throws Exception {
        try (var server = new ProviderTestServer()) {
            server.storageError("GET", 403, "AccessDenied");
            var verification = server.checks().verify(inputs(server.endpoint(), "auto"));
            assertEquals(ProviderCheck.FAILED, verification.checks().get("storage.endpoint"));
            assertEquals("Storage test download failed: access denied for this operation.",
                    verification.errors().get("storage.endpoint"));
            assertEquals(0, server.storedObjects());
        }
    }

    @Test
    void rejectsMismatchedBytesAndCleansUp() throws Exception {
        try (var server = new ProviderTestServer()) {
            server.overrideDownload("wrong bytes");
            var result = server.checks().verify(inputs(server.endpoint(), "auto"));
            assertEquals(ProviderCheck.FAILED, result.checks().get("storage.endpoint"));
            assertEquals("Storage test download returned different contents from the upload.",
                    result.errors().get("storage.endpoint"));
            assertEquals(0, server.storedObjects());
        }
    }

    @Test
    void triesCleanupWhenTheProviderDoesNotConfirmTheUpload() throws Exception {
        try (var server = new ProviderTestServer()) {
            server.storageError("PUT", 503, "ServiceUnavailable");
            var result = server.checks().verify(inputs(server.endpoint(), "auto"));
            assertEquals(ProviderCheck.UNAVAILABLE, result.checks().get("storage.endpoint"));
            assertEquals("Storage test upload could not finish. Try again.", result.errors().get("storage.endpoint"));
            assertEquals(List.of("PUT", "DELETE"), server.storageRequests().stream().map(ProviderTestServer.Received::method).toList());
        }
    }

    @Test
    void reportsAnActionableStorageRejectionWithoutExposingTheProviderResponse() throws Exception {
        try (var server = new ProviderTestServer()) {
            server.storageError("PUT", 403, "SignatureDoesNotMatch");
            var verification = server.checks().verify(inputs(server.endpoint(), "auto"));
            assertEquals(ProviderCheck.FAILED, verification.checks().get("storage.endpoint"));
            assertEquals("Storage test upload failed: the request signature was rejected. Check the secret key, endpoint and region.",
                    verification.errors().get("storage.endpoint"));
        }
    }

    private static Inputs inputs(String endpoint, String region) {
        return new Inputs(replace("test-bot"), new OAuth("discord", replace("d-secret")),
                new OAuth("twitch", replace("t+secret")),
                new Storage(endpoint, "attachments", region, replace("storage-key"), replace("storage-secret")));
    }
}
