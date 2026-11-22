package com.github.lucasskywalker64.installation;

import com.github.lucasskywalker64.DiscordBotApplication;
import com.github.lucasskywalker64.discord.DiscordGatewayFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.session.GenericSessionEvent;
import net.dv8tion.jda.api.events.session.SessionState;
import net.dv8tion.jda.api.hooks.EventListener;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.github.lucasskywalker64.installation.InstallationConfiguration.DiscordUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.SecretUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.State;
import com.github.lucasskywalker64.installation.InstallationConfiguration.TwitchUpdate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
class RuntimeConfigurationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private final LinkedBlockingQueue<Connection> connections = new LinkedBlockingQueue<>();
    private volatile boolean providerUnavailable;
    private volatile CountDownLatch loginStarted;
    private volatile CountDownLatch loginReleased;
    private volatile CountDownLatch loginCancelled;
    private volatile URI tokenEndpoint;
    private volatile CountDownLatch tokenRequestFinished;

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
    void replacesDiscordAfterCommitAndIgnoresOldCallbacks() throws Exception {
        try (var application = startApplication()) {
            var settings = application.getBean(InstallationConfigurationService.class);
            assertTrue(connections.isEmpty());
            updateToken(settings, "first-token");
            var first = nextConnection();
            first.event(SessionState.READY);
            awaitState(settings, State.CONNECTED);

            var transaction = new TransactionTemplate(application.getBean(PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                updateToken(settings, "rolled-back-token");
                assertTrue(connections.isEmpty());
                status.setRollbackOnly();
            });
            assertEquals(State.CONNECTED, settings.read().discord().status().state());

            settings.updatePublicUrl("https://bot.example");
            settings.updateTwitch(new TwitchUpdate(true, "client", SecretUpdate.replace("twitch-secret"),
                    "https://bot.example/callback"));
            assertTrue(settings.updateDiscord(new DiscordUpdate(true, SecretUpdate.keep(), true, "123",
                    SecretUpdate.replace("oauth-secret"),
                    "https://bot.example/dashboard/oauth/discord/callback")).errors().isEmpty());
            updateToken(settings, "second-token");
            var second = nextConnection();
            verify(first.discord()).shutdown();
            first.event(SessionState.SHUTDOWN);
            second.event(SessionState.READY);
            awaitState(settings, State.CONNECTED);
            assertNull(settings.read().discord().status().lastFailure());
            assertTrue(connections.isEmpty());
            settings.updateDiscord(new DiscordUpdate(false, SecretUpdate.keep(), false, "123",
                    SecretUpdate.keep(), "https://bot.example/dashboard/oauth/discord/callback"));
            awaitState(settings, State.DISABLED);
            verify(second.discord(), timeout(5_000)).shutdown();
        }
    }

    @Test
    void keepsHttpReadyDuringOutageAndRetainsFailureAcrossRestart() throws Exception {
        providerUnavailable = true;
        try (var application = startApplication()) {
            var settings = application.getBean(InstallationConfigurationService.class);
            updateToken(settings, "invalid-token");
            awaitState(settings, State.UNAVAILABLE);
            assertNotNull(settings.read().discord().status().lastFailure());
            assertFalse(settings.read().toString().contains("invalid-token"));
            assertReady(application);
        }
        try (var application = startApplication()) {
            var settings = application.getBean(InstallationConfigurationService.class);
            awaitState(settings, State.UNAVAILABLE);
            assertNotNull(settings.read().discord().status().lastFailure());
            assertReady(application);
            providerUnavailable = false;
            settings.retryDiscord();
            nextConnection().event(SessionState.READY);
            awaitState(settings, State.CONNECTED);
            assertNotNull(settings.read().discord().status().lastFailure());
        }
    }

    @Test
    void queuesStatusPersistenceWithoutBlockingGatewayCallbacks() throws Exception {
        try (var application = startApplication()) {
            var settings = application.getBean(InstallationConfigurationService.class);
            updateToken(settings, "test-token");
            var gateway = nextConnection();
            gateway.event(SessionState.READY);
            awaitState(settings, State.CONNECTED);
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.executeQuery("SELECT id FROM installation_configuration WHERE id = 1 FOR UPDATE").close();
                }
                var callback = CompletableFuture.runAsync(() -> gateway.event(SessionState.DISCONNECTED));
                try {
                    callback.get(1, TimeUnit.SECONDS);
                } finally {
                    connection.rollback();
                }
            }
            awaitState(settings, State.UNAVAILABLE);
        }
    }

    @Test
    void servesHttpAndCancelsSlowLoginAfterRestart() throws Exception {
        try (var application = startApplication()) {
            var settings = application.getBean(InstallationConfigurationService.class);
            updateToken(settings, "test-token");
            nextConnection().event(SessionState.READY);
            awaitState(settings, State.CONNECTED);
        }
        loginStarted = new CountDownLatch(1);
        loginReleased = new CountDownLatch(1);
        loginCancelled = new CountDownLatch(1);
        var startup = CompletableFuture.supplyAsync(this::startApplication);
        ConfigurableApplicationContext application = null;
        try {
            assertTrue(loginStarted.await(10, TimeUnit.SECONDS));
            application = startup.get(2, TimeUnit.SECONDS);
            assertReady(application);
            var stop = CompletableFuture.runAsync(application::stop);
            stop.get(2, TimeUnit.SECONDS);
            assertTrue(loginCancelled.await(2, TimeUnit.SECONDS));
        } finally {
            loginReleased.countDown();
            if (application == null) {
                application = startup.get(10, TimeUnit.SECONDS);
            }
            application.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelsRealTokenVerificationBeforeTheProviderResponds(boolean stopApplication) throws Exception {
        var requestStarted = new CountDownLatch(1);
        var releaseResponse = new CompletableFuture<Void>();
        tokenRequestFinished = new CountDownLatch(1);
        var provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/", exchange -> {
            requestStarted.countDown();
            releaseResponse.join();
            try {
                exchange.sendResponseHeaders(401, -1);
            } finally {
                exchange.close();
            }
        });
        provider.start();
        tokenEndpoint = URI.create("http://127.0.0.1:" + provider.getAddress().getPort() + "/");
        try (var application = startApplication()) {
            var settings = application.getBean(InstallationConfigurationService.class);
            updateToken(settings, "slow-token");
            assertTrue(requestStarted.await(10, TimeUnit.SECONDS), "JDA did not reach token verification");
            assertReady(application);
            if (stopApplication) {
                application.stop();
            } else {
                updateToken(settings, "replacement-token");
            }
            try {
                assertTrue(tokenRequestFinished.await(2, TimeUnit.SECONDS),
                        "Cancellation did not release JDA's token verification request");
                if (!stopApplication) {
                    nextConnection().event(SessionState.READY);
                    awaitState(settings, State.CONNECTED);
                }
            } finally {
                releaseResponse.complete(null);
            }
        } finally {
            releaseResponse.complete(null);
            provider.stop(0);
        }
    }

    @Test
    void ignoresOlderCommitNotificationsWhileLatestLoginIsPending() throws Exception {
        try (var application = startApplication()) {
            var settings = application.getBean(InstallationConfigurationService.class);
            updateToken(settings, "initial-token");
            nextConnection().event(SessionState.READY);
            awaitState(settings, State.CONNECTED);
            var firstCommitted = new CountDownLatch(1);
            var releaseFirstNotification = new CountDownLatch(1);
            var transaction = new TransactionTemplate(application.getBean(PlatformTransactionManager.class));
            var olderUpdate = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(_ -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public int getOrder() {
                        return Ordered.HIGHEST_PRECEDENCE;
                    }

                    @Override
                    public void afterCompletion(int status) {
                        firstCommitted.countDown();
                        try {
                            releaseFirstNotification.await();
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(failure);
                        }
                    }
                });
                updateToken(settings, "older-token");
            }));
            try {
                assertTrue(firstCommitted.await(10, TimeUnit.SECONDS));
                loginStarted = new CountDownLatch(1);
                loginReleased = new CountDownLatch(1);
                loginCancelled = new CountDownLatch(1);
                updateToken(settings, "latest-token");
                assertTrue(loginStarted.await(10, TimeUnit.SECONDS));
                releaseFirstNotification.countDown();
                olderUpdate.get(5, TimeUnit.SECONDS);
                loginReleased.countDown();
                nextConnection().event(SessionState.READY);
                awaitState(settings, State.CONNECTED);
                assertEquals(1, loginCancelled.getCount());
                assertTrue(connections.isEmpty());
            } finally {
                releaseFirstNotification.countDown();
                if (loginReleased != null) {
                    loginReleased.countDown();
                }
                olderUpdate.get(10, TimeUnit.SECONDS);
            }
        }
    }

    private ConfigurableApplicationContext startApplication() {
        var application = new SpringApplication(DiscordBotApplication.class);
        application.addInitializers(context -> ((GenericApplicationContext) context).registerBean(
                "testGatewayFactory", DiscordGatewayFactory.class, () -> (token, httpClient) -> {
                    if (tokenEndpoint != null && token.equals("slow-token")) {
                        var transport = httpClient.newBuilder().addInterceptor(chain -> {
                            try {
                                return chain.proceed(chain.request().newBuilder().url(tokenEndpoint.toString()).build());
                            } finally {
                                tokenRequestFinished.countDown();
                            }
                        }).build();
                        return JDABuilder.createLight(token).setHttpClient(transport);
                    }
                    if (loginStarted != null) {
                        loginStarted.countDown();
                        try {
                            loginReleased.await();
                        } catch (InterruptedException _) {
                            loginCancelled.countDown();
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Login interrupted");
                        }
                    }
                    if (providerUnavailable) {
                        throw new IllegalArgumentException("Provider rejected " + token);
                    }
                    var discord = mock(JDA.class);
                    var builder = mock(JDABuilder.class, RETURNS_SELF);
                    try {
                        when(discord.awaitShutdown(Duration.ofSeconds(10))).thenReturn(true);
                    } catch (InterruptedException failure) {
                        throw new AssertionError(failure);
                    }
                    doAnswer(call -> {
                        connections.add(new Connection(discord, call.getArgument(0)));
                        return builder;
                    }).when(builder).addEventListeners(any(Object.class));
                    when(builder.build()).thenReturn(discord);
                    return builder;
                }));
        return application.run("--server.port=0", "--installation.admin.port=0",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.main.banner-mode=off", "--logging.level.root=OFF");
    }

    private Connection nextConnection() throws InterruptedException {
        var connection = connections.poll(10, TimeUnit.SECONDS);
        assertNotNull(connection, "Discord did not start");
        return connection;
    }

    private static void updateToken(InstallationConfigurationService settings, String token) {
        var result = settings.updateDiscord(new DiscordUpdate(true, SecretUpdate.replace(token), false,
                null, SecretUpdate.keep(), null));
        assertTrue(result.errors().isEmpty());
    }

    private static void awaitState(InstallationConfigurationService settings, State state) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (settings.read().discord().status().state() == state) {
                return;
            }
        }
        fail("Expected " + state + ", got " + settings.read().discord().status());
    }

    private static void assertReady(ConfigurableApplicationContext application) throws Exception {
        int port = ((WebServerApplicationContext) application).getWebServer().getPort();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
    }

    private record Connection(JDA discord, EventListener listener) {
        void event(SessionState state) {
            var event = mock(GenericSessionEvent.class);
            when(event.getState()).thenReturn(state);
            listener.onEvent(event);
        }
    }
}
