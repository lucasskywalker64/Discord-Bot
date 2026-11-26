package com.github.lucasskywalker64.installation;

import com.github.lucasskywalker64.DiscordBotApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.sql.DriverManager;

import com.github.lucasskywalker64.installation.InstallationConfiguration.DiscordUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.SecretUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.SetupStep;
import com.github.lucasskywalker64.installation.InstallationConfiguration.State;
import com.github.lucasskywalker64.installation.InstallationConfiguration.StorageUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.TwitchUpdate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class InstallationConfigurationTest {

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
            var settings = application.getBean(InstallationConfigurationService.class);
            assertEquals(SetupStep.SERVER, settings.read().setupStep());
            assertFalse(settings.read().discord().botEnabled());
            assertFalse(settings.read().discord().botTokenConfigured());

            assertTrue(settings.updatePublicUrl("https://bot.example").errors().isEmpty());
            var result = settings.updateDiscord(new DiscordUpdate(false, SecretUpdate.replace("bot-secret"),
                    true, "123456789", SecretUpdate.replace("oauth-secret")));
            assertTrue(result.errors().isEmpty());
            assertTrue(result.configuration().discord().botTokenConfigured());
            assertTrue(result.configuration().discord().clientSecretConfigured());
            assertEquals(2, result.configuration().discord().oauthRevision());
            assertFalse(new JsonMapper().writeValueAsString(result).contains("oauth-secret"));
            assertFalse(result.toString().contains("bot-secret"));
            assertFalse(SecretUpdate.replace("secret").toString().contains("secret"));
            assertTrue(settings.updateTwitch(new TwitchUpdate(true, "twitch-client", SecretUpdate.replace("twitch-secret")))
                    .errors().isEmpty());
            assertTrue(settings.updateStorage(new StorageUpdate(true, "https://s3.example", "attachments", "region",
                    SecretUpdate.replace("storage-access"), SecretUpdate.replace("storage-secret")), null).errors().isEmpty());
            assertTrue(settings.updateSetupStep(SetupStep.STORAGE).errors().isEmpty());
        }

        try (var application = startApplication()) {
            var settings = application.getBean(InstallationConfigurationService.class);
            var saved = settings.read();
            assertEquals(SetupStep.STORAGE, saved.setupStep());
            assertEquals("https://bot.example", saved.publicUrl());
            assertEquals("123456789", saved.discord().clientId());
            assertTrue(saved.discord().botTokenConfigured());
            assertTrue(saved.discord().clientSecretConfigured());
            assertEquals(2, saved.discord().oauthRevision());
            assertTrue(saved.twitch().enabled());
            assertEquals("twitch-client", saved.twitch().clientId());
            assertTrue(saved.twitch().clientSecretConfigured());
            assertEquals(2, saved.twitch().oauthRevision());
            assertEquals(State.CONFIGURED, saved.twitch().status().state());
            assertEquals("https://s3.example", saved.storage().endpoint());
            assertEquals("attachments", saved.storage().bucket());
            assertEquals("region", saved.storage().region());
            assertTrue(saved.storage().accessKeyConfigured());
            assertTrue(saved.storage().secretKeyConfigured());
            assertEquals(State.CONFIGURED, saved.storage().status().state());
            assertNull(saved.storage().verifiedAt());
            String response = new JsonMapper().writeValueAsString(saved);
            for (String secret : java.util.List.of("bot-secret", "oauth-secret", "twitch-secret", "storage-access", "storage-secret")) {
                assertFalse(response.contains(secret));
            }
            assertTrue(settings.updateTwitch(new TwitchUpdate(true, "twitch-client", SecretUpdate.keep())).errors().isEmpty());
            assertEquals(2, settings.read().twitch().oauthRevision());
            assertTrue(settings.updateTwitch(new TwitchUpdate(true, "other-client", SecretUpdate.replace("new-secret")))
                    .errors().isEmpty());
            assertEquals(3, settings.read().twitch().oauthRevision());
            assertEquals(2, settings.read().discord().oauthRevision());
        }
    }

    @Test
    void rejectsInvalidCapabilitiesWithoutChangingWorkingSettings() {
        try (var application = startApplication()) {
            var settings = application.getBean(InstallationConfigurationService.class);
            var invalid = settings.updateDiscord(new DiscordUpdate(true, SecretUpdate.keep(), true,
                    "", SecretUpdate.keep()));
            assertEquals(java.util.Set.of("discord.botToken", "discord.clientId", "discord.clientSecret",
                    "discord.callbackUrl"), invalid.errors().keySet());
            assertFalse(settings.read().discord().botEnabled());
            assertTrue(settings.updatePublicUrl("https://bot.example").errors().isEmpty());
            assertTrue(settings.updateTwitch(new TwitchUpdate(true, "client", SecretUpdate.replace("twitch-secret")))
                    .errors().isEmpty());
            assertEquals(2, settings.read().twitch().oauthRevision());
            assertFalse(settings.updateTwitch(new TwitchUpdate(true, "client", SecretUpdate.clear())).errors().isEmpty());
            assertTrue(settings.read().twitch().clientSecretConfigured());
            assertEquals(2, settings.read().twitch().oauthRevision());
            assertFalse(settings.updatePublicUrl("https://password@bot.example").errors().isEmpty());

            var storage = settings.updateStorage(new StorageUpdate(true, "file:///tmp/files", "", "",
                    SecretUpdate.keep(), SecretUpdate.keep()), null);
            assertEquals(java.util.Set.of("storage.endpoint", "storage.bucket", "storage.region",
                    "storage.accessKey", "storage.secretKey"), storage.errors().keySet());
            assertFalse(settings.read().storage().enabled());
        }
    }

    @Test
    void migratesAnActiveDiscordCallbackWithoutChangingItsRegisteredUrl() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("6").load().migrate();
        String callback = "https://registered.example:8443/dashboard/oauth/discord/callback";
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var update = connection.prepareStatement("""
                     UPDATE installation_configuration
                     SET setup_step = 'DEPLOYMENT', public_url = 'https://listed.example/dashboard/',
                         discord_oauth_enabled = true, discord_callback_url = ?, discord_oauth_revision = 5
                     """)) {
            update.setString(1, callback);
            assertEquals(1, update.executeUpdate());
        }

        try (var application = startApplication()) {
            var settings = application.getBean(InstallationConfigurationService.class);
            var saved = settings.read();
            assertEquals(SetupStep.STORAGE, saved.setupStep());
            assertEquals("https://registered.example:8443", saved.publicUrl());
            assertEquals(callback, saved.discord().callbackUrl());
            assertEquals(callback, settings.discordOAuthSettings().callbackUrl());
            assertEquals(5, saved.discord().oauthRevision());
            assertEquals("https://registered.example:8443/dashboard/oauth/twitch/callback", saved.twitch().callbackUrl());
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
