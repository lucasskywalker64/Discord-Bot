package com.github.lucasskywalker64.installation;

import com.github.lucasskywalker64.installation.ProviderConfigurationChecks.Inputs;
import com.github.lucasskywalker64.installation.ProviderConfigurationChecks.OAuth;
import com.github.lucasskywalker64.installation.ProviderConfigurationChecks.Storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck;
import static com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck.SUCCEEDED;
import static com.github.lucasskywalker64.installation.InstallationConfiguration.SecretUpdate.replace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderConfigurationChecksTest {
    @Test
    void verifiesBothOAuthCredentialsTheDiscordBotTokenAndSignedBucketAccess() throws Exception {
        try (var server = new ProviderTestServer()) {
            var checks = server.checks().verify(inputs(server.endpoint(), "us-east-1"));
            assertEquals(Map.of("discord.botToken", SUCCEEDED, "discord.clientSecret", SUCCEEDED,
                    "twitch.clientSecret", SUCCEEDED, "storage.endpoint", SUCCEEDED), checks);
            assertEquals("Bot test-bot", server.received("/discord/users/@me").authorization());
            var discord = server.received("/discord/oauth2/token");
            assertEquals("Basic ZGlzY29yZDpkLXNlY3JldA==", discord.authorization());
            assertEquals("grant_type=client_credentials&scope=identify", discord.body());
            var twitch = server.received("/twitch/oauth2/token");
            assertEquals("grant_type=client_credentials&client_id=twitch&client_secret=t%2Bsecret", twitch.body());
            var storage = server.received("/attachments");
            assertEquals("HEAD", storage.method());
            assertTrue(storage.authorization().startsWith("AWS4-HMAC-SHA256 Credential=storage-key/"));
            assertTrue(storage.authorization().contains("/us-east-1/s3/aws4_request"));
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
                    server.checks().verify(inputs(server.endpoint(), "us-east-1")));
        }
    }

    @Test
    void rejectsAStorageRegionDifferentFromTheRegionReturnedByTheProvider() throws Exception {
        try (var server = new ProviderTestServer()) {
            assertEquals(ProviderCheck.FAILED, server.checks().verify(inputs(server.endpoint(), "us-west-2"))
                    .get("storage.endpoint"));
        }
    }

    private static Inputs inputs(String endpoint, String region) {
        return new Inputs(replace("test-bot"), new OAuth("discord", replace("d-secret")),
                new OAuth("twitch", replace("t+secret")),
                new Storage(endpoint, "attachments", region, replace("storage-key"), replace("storage-secret")));
    }
}
