package com.github.lucasskywalker64;

import com.github.lucasskywalker64.guild.GuildConfiguration;
import com.github.lucasskywalker64.guild.GuildConfigurationRepository;
import org.flywaydb.core.api.FlywayException;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
class DiscordBotApplicationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @BeforeEach
    void resetDatabase() throws SQLException {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void reportsReadyAfterStartup() throws IOException, InterruptedException {
        List<Map<String, Object>> migrations;
        try (var application = startApplication()) {
            assertReady(application);
            var configurations = application.getBean(GuildConfigurationRepository.class);
            configurations.saveAllAndFlush(List.of(
                    new GuildConfiguration("100000000000000001", "200000000000000001"),
                    new GuildConfiguration("100000000000000002", "200000000000000002"),
                    new GuildConfiguration("18446744073709551615", null)));
            var jdbc = application.getBean(JdbcTemplate.class);
            assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                    "INSERT INTO guild_configuration (guild_id) VALUES (?)", "100000000000000001"));
            migrations = appliedMigrations(jdbc);
            assertEquals(1, migrations.size());
        }

        try (var application = startApplication()) {
            assertReady(application);
            var configurations = application.getBean(GuildConfigurationRepository.class);
            assertEquals(3, configurations.count());
            assertEquals("200000000000000001", configurations.findById("100000000000000001")
                    .orElseThrow().getMemberCountVoiceChannelId());
            assertEquals("200000000000000002", configurations.findById("100000000000000002")
                    .orElseThrow().getMemberCountVoiceChannelId());
            assertNull(configurations.findById("18446744073709551615")
                    .orElseThrow().getMemberCountVoiceChannelId());
            assertEquals(migrations, appliedMigrations(application.getBean(JdbcTemplate.class)));
        }
    }

    @Test
    void rejectsBrokenMigrationAtStartup() {
        var failure = assertThrows(BeanCreationException.class, () -> {
            try (var _ = startApplication(
                    "--spring.flyway.locations=classpath:db/migration,classpath:db/broken")) {
                // Startup must fail before returning an application context.
            }
        });
        assertHasCause(failure, FlywayException.class);
    }

    @Test
    void rejectsSchemaMismatchInsteadOfRepairingIt() {
        try (var application = startApplication()) {
            application.getBean(JdbcTemplate.class).execute(
                    "ALTER TABLE guild_configuration DROP COLUMN member_count_voice_channel_id");
        }

        var failure = assertThrows(BeanCreationException.class, () -> {
            try (var _ = startApplication()) {
                // Hibernate must validate, rather than change, the migrated schema.
            }
        });
        assertHasCause(failure, SchemaManagementException.class);
    }

    private static ConfigurableApplicationContext startApplication(String... additionalArguments) {
        var arguments = new ArrayList<>(List.of(
                "--server.port=0",
                "--discord.enabled=false",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.main.banner-mode=off",
                "--logging.level.root=OFF"));
        arguments.addAll(List.of(additionalArguments));
        return SpringApplication.run(DiscordBotApplication.class, arguments.toArray(String[]::new));
    }

    private static List<Map<String, Object>> appliedMigrations(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
    }

    private static void assertHasCause(Throwable failure, Class<? extends Throwable> expectedType) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (expectedType.isInstance(cause)) {
                return;
            }
        }
        fail("Expected cause " + expectedType.getName() + " in " + failure, failure);
    }

    private static void assertReady(ConfigurableApplicationContext application)
            throws IOException, InterruptedException {
        int port = ((WebServerApplicationContext) application).getWebServer().getPort();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness"))
                .timeout(Duration.ofSeconds(5))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), response::body);
            assertEquals("UP", new JsonMapper().readTree(response.body()).path("status").asText());
        }
    }
}
