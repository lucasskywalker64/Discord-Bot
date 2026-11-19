package com.github.lucasskywalker64.discord;

import net.dv8tion.jda.api.JDA;
import com.github.lucasskywalker64.installation.InstallationConfigurationService;
import com.github.lucasskywalker64.installation.InstallationConfiguration.State;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscordLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DiscordLifecycle.class);

    private final DiscordGatewayFactory factory;
    private final DiscordListener listener;
    private final GuildCommandRegistration registrations;
    private final InstallationConfigurationService settings;
    private final ExecutorService executor;
    private JDA discord;
    private volatile boolean running;
    private volatile long generation;
    private long revision = -1;
    private long requestedRevision = -1;
    private Thread connectingThread;
    private ConnectionHttp connectingHttp;
    private ConnectionHttp discordHttp;

    public DiscordLifecycle(DiscordGatewayFactory factory, DiscordListener listener, GuildCommandRegistration registrations,
                            InstallationConfigurationService settings, ExecutorService executor) {
        this.factory = factory;
        this.listener = listener;
        this.registrations = registrations;
        this.settings = settings;
        this.executor = executor;
    }

    @Override
    public synchronized void start() {
        if (!running) {
            running = true;
            enqueue(this::reconcile);
        }
    }

    @TransactionalEventListener
    public synchronized void configurationChanged(InstallationConfigurationService.Changed event) {
        requestedRevision = Math.max(requestedRevision, event.discordBotRevision());
        if (event.discordBotRevision() > revision && connectingThread != null) {
            generation++;
            connectingHttp.close();
            connectingThread.interrupt();
        }
        enqueue(this::reconcile);
    }

    private void reconcile() {
        if (!running) {
            return;
        }
        var configuration = settings.discordConnectionSettings();
        JDA previous;
        ConnectionHttp previousHttp;
        long session;
        synchronized (this) {
            if (!running || revision == configuration.revision() || configuration.revision() < requestedRevision) {
                return;
            }
            session = ++generation;
            registrations.stop();
            previous = discord;
            previousHttp = discordHttp;
            discord = null;
            discordHttp = null;
            revision = configuration.revision();
        }
        stopConnection(previous, previousHttp);
        if (!configuration.enabled()) {
            return;
        }
        long configuredRevision = configuration.revision();
        settings.reportDiscordState(configuredRevision, State.CONNECTING);
        long registrationSession;
        ConnectionHttp http;
        synchronized (this) {
            if (!running || generation != session || configuration.revision() < requestedRevision) {
                return;
            }
            registrationSession = registrations.start();
            http = new ConnectionHttp();
            connectingHttp = http;
            connectingThread = Thread.currentThread();
        }
        var sessionListener = listener.forSession(registrationSession, () -> running && generation == session,
                state -> enqueue(() -> {
                    if (running && generation == session) {
                        settings.reportDiscordState(configuredRevision, state);
                    }
                }));
        boolean published = false;
        try {
            var connected = factory.create(configuration.token(), http.client).setEnableShutdownHook(false)
                    .addEventListeners(sessionListener).build();
            synchronized (this) {
                connectingThread = null;
                connectingHttp = null;
                if (running && generation == session) {
                    discord = connected;
                    discordHttp = http;
                    published = true;
                    return;
                }
            }
            stopConnection(connected, http);
        } catch (RuntimeException _) {
            synchronized (this) {
                connectingThread = null;
                connectingHttp = null;
                if (!running || generation != session) {
                    return;
                }
                generation++;
                registrations.stop();
            }
            settings.reportDiscordState(configuredRevision, State.UNAVAILABLE);
            log.warn("Discord could not start; check installation configuration and provider availability");
        } finally {
            if (!published) {
                http.close();
            }
        }
    }

    @Override
    public void stop() {
        JDA previous;
        ConnectionHttp previousHttp;
        synchronized (this) {
            running = false;
            generation++;
            revision = -1;
            registrations.stop();
            if (connectingThread != null) {
                connectingHttp.close();
                connectingThread.interrupt();
            }
            previous = discord;
            previousHttp = discordHttp;
            discord = null;
            discordHttp = null;
        }
        stopConnection(previous, previousHttp);
    }

    private void stopConnection(JDA connection, ConnectionHttp http) {
        try {
            drainConnection(connection);
        } finally {
            if (http != null) {
                http.close();
            }
        }
    }

    private void drainConnection(JDA connection) {
        if (connection == null) {
            return;
        }
        connection.shutdown();
        try {
            if (!connection.awaitShutdown(Duration.ofSeconds(10))) {
                log.warn("Discord shutdown exceeded 10 seconds; cancelling pending requests");
                connection.shutdownNow();
                if (!connection.awaitShutdown(Duration.ofSeconds(5))) {
                    log.warn("Discord did not finish shutting down after cancellation");
                }
            }
        } catch (InterruptedException _) {
            log.warn("Discord shutdown interrupted; cancelling pending requests");
            connection.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void enqueue(Runnable task) {
        if (!running) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException _) {
                    log.warn("Discord configuration/status could not be reconciled; retry from the private console");
                }
            });
        } catch (RejectedExecutionException _) {
            log.debug("Discord lifecycle executor is stopping");
        }
    }

    private static final class ConnectionHttp implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final OkHttpClient client;

        private ConnectionHttp() {
            var dispatcher = new Dispatcher();
            dispatcher.setMaxRequestsPerHost(25);
            client = new OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .connectionPool(new ConnectionPool(5, 10, TimeUnit.SECONDS))
                    .addInterceptor(chain -> {
                        if (closed.get()) {
                            chain.call().cancel();
                        }
                        return chain.proceed(chain.request());
                    }).build();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                client.dispatcher().cancelAll();
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }
}
