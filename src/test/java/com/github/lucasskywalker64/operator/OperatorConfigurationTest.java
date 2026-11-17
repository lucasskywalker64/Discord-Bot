package com.github.lucasskywalker64.operator;

import com.github.lucasskywalker64.DiscordBotApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.sql.DriverManager;

import com.github.lucasskywalker64.operator.OperatorConfiguration.DiscordUpdate;
import com.github.lucasskywalker64.operator.OperatorConfiguration.SecretUpdate;
import com.github.lucasskywalker64.operator.OperatorConfiguration.SetupStep;
import com.github.lucasskywalker64.operator.OperatorConfiguration.State;
import com.github.lucasskywalker64.operator.OperatorConfiguration.StorageUpdate;
import com.github.lucasskywalker64.operator.OperatorConfiguration.TwitchUpdate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class OperatorConfigurationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @BeforeEach
    void resetDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void retainsSetupAndCredentialsAcrossRestartWithoutReturningSecrets() {
        try (var application = startApplication()) {
            var settings = application.getBean(OperatorConfigurationService.class);
            assertEquals(SetupStep.PROVIDERS, settings.read().setupStep());
            assertFalse(settings.read().discord().botEnabled());
            assertFalse(settings.read().discord().botTokenConfigured());

            var result = settings.updateDiscord(new DiscordUpdate(false, SecretUpdate.replace("bot-secret"),
                    true, "123456789", SecretUpdate.replace("oauth-secret"), "https://bot.example/discord/callback"));
            assertTrue(result.errors().isEmpty());
            assertTrue(result.configuration().discord().botTokenConfigured());
            assertTrue(result.configuration().discord().clientSecretConfigured());
            assertEquals(1, result.configuration().discord().oauthRevision());
            assertFalse(new JsonMapper().writeValueAsString(result).contains("oauth-secret"));
            assertFalse(result.toString().contains("bot-secret"));
            assertFalse(SecretUpdate.replace("secret").toString().contains("secret"));
            assertTrue(settings.updatePublicUrl("https://bot.example").errors().isEmpty());
            assertTrue(settings.updateTwitch(new TwitchUpdate(true, "twitch-client", SecretUpdate.replace("twitch-secret"),
                    "https://bot.example/twitch/callback")).errors().isEmpty());
            assertTrue(settings.updateStorage(new StorageUpdate(true, "https://s3.example", "attachments", "region",
                    SecretUpdate.replace("storage-access"), SecretUpdate.replace("storage-secret"))).errors().isEmpty());
            assertTrue(settings.updateSetupStep(SetupStep.STORAGE).errors().isEmpty());
        }

        try (var application = startApplication()) {
            var settings = application.getBean(OperatorConfigurationService.class);
            var saved = settings.read();
            assertEquals(SetupStep.STORAGE, saved.setupStep());
            assertEquals("https://bot.example", saved.publicUrl());
            assertEquals("123456789", saved.discord().clientId());
            assertTrue(saved.discord().botTokenConfigured());
            assertTrue(saved.discord().clientSecretConfigured());
            assertEquals(1, saved.discord().oauthRevision());
            assertTrue(saved.twitch().enabled());
            assertEquals("twitch-client", saved.twitch().clientId());
            assertTrue(saved.twitch().clientSecretConfigured());
            assertEquals(1, saved.twitch().oauthRevision());
            assertEquals(State.CONFIGURED, saved.twitch().status().state());
            assertEquals("https://s3.example", saved.storage().endpoint());
            assertEquals("attachments", saved.storage().bucket());
            assertEquals("region", saved.storage().region());
            assertTrue(saved.storage().accessKeyConfigured());
            assertTrue(saved.storage().secretKeyConfigured());
            String response = new JsonMapper().writeValueAsString(saved);
            for (String secret : java.util.List.of("bot-secret", "oauth-secret", "twitch-secret", "storage-access", "storage-secret")) {
                assertFalse(response.contains(secret));
            }
            assertTrue(settings.updateTwitch(new TwitchUpdate(true, "twitch-client", SecretUpdate.keep(),
                    "https://bot.example/new-callback")).errors().isEmpty());
            assertEquals(1, settings.read().twitch().oauthRevision());
            assertTrue(settings.updateTwitch(new TwitchUpdate(true, "other-client", SecretUpdate.replace("new-secret"),
                    "https://bot.example/new-callback")).errors().isEmpty());
            assertEquals(2, settings.read().twitch().oauthRevision());
            assertEquals(1, settings.read().discord().oauthRevision());
        }
    }

    @Test
    void rejectsInvalidCapabilitiesWithoutChangingWorkingSettings() {
        try (var application = startApplication()) {
            var settings = application.getBean(OperatorConfigurationService.class);
            var invalid = settings.updateDiscord(new DiscordUpdate(true, SecretUpdate.keep(), true,
                    "", SecretUpdate.keep(), "javascript:callback"));
            assertEquals(java.util.Set.of("discord.botToken", "discord.clientId", "discord.clientSecret",
                    "discord.callbackUrl"), invalid.errors().keySet());
            assertFalse(settings.read().discord().botEnabled());
            assertTrue(settings.updateTwitch(new TwitchUpdate(true, "client", SecretUpdate.replace("twitch-secret"),
                    "https://bot.example/twitch/callback")).errors().isEmpty());
            assertEquals(1, settings.read().twitch().oauthRevision());
            assertFalse(settings.updateTwitch(new TwitchUpdate(true, "client", SecretUpdate.clear(),
                    "https://bot.example/twitch/callback")).errors().isEmpty());
            assertTrue(settings.read().twitch().clientSecretConfigured());
            assertEquals(1, settings.read().twitch().oauthRevision());
            assertFalse(settings.updatePublicUrl("https://password@bot.example").errors().isEmpty());

            var storage = settings.updateStorage(new StorageUpdate(true, "file:///tmp/files", "", "",
                    SecretUpdate.keep(), SecretUpdate.keep()));
            assertEquals(java.util.Set.of("storage.endpoint", "storage.bucket", "storage.region",
                    "storage.accessKey", "storage.secretKey"), storage.errors().keySet());
            assertFalse(settings.read().storage().enabled());
        }
    }

    private static ConfigurableApplicationContext startApplication() {
        return SpringApplication.run(DiscordBotApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.main.banner-mode=off", "--logging.level.root=OFF");
    }
}
