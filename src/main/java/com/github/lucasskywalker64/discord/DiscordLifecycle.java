package com.github.lucasskywalker64.discord;

import net.dv8tion.jda.api.JDA;
import com.github.lucasskywalker64.operator.OperatorConfigurationService;
import com.github.lucasskywalker64.operator.OperatorConfiguration.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public class DiscordLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DiscordLifecycle.class);

    private final DiscordGatewayFactory factory;
    private final DiscordListener listener;
    private final GuildCommandRegistration registrations;
    private final OperatorConfigurationService settings;
    private final ExecutorService executor;
    private JDA discord;
    private volatile boolean running;
    private volatile long generation;
    private long revision = -1;
    private long requestedRevision = -1;
    private Thread connectingThread;

    public DiscordLifecycle(DiscordGatewayFactory factory, DiscordListener listener, GuildCommandRegistration registrations,
                            OperatorConfigurationService settings, ExecutorService executor) {
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
    public synchronized void configurationChanged(OperatorConfigurationService.Changed event) {
        requestedRevision = Math.max(requestedRevision, event.discordBotRevision());
        if (event.discordBotRevision() > revision && connectingThread != null) {
            generation++;
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
        long session;
        synchronized (this) {
            if (!running || revision == configuration.revision() || configuration.revision() < requestedRevision) {
                return;
            }
            session = ++generation;
            registrations.stop();
            previous = discord;
            discord = null;
            revision = configuration.revision();
        }
        stopConnection(previous);
        if (!configuration.enabled()) {
            return;
        }
        long configuredRevision = configuration.revision();
        settings.reportDiscordState(configuredRevision, State.CONNECTING);
        long registrationSession;
        synchronized (this) {
            if (!running || generation != session || configuration.revision() < requestedRevision) {
                return;
            }
            registrationSession = registrations.start();
            connectingThread = Thread.currentThread();
        }
        var sessionListener = listener.forSession(registrationSession, () -> running && generation == session,
                state -> enqueue(() -> {
                    if (running && generation == session) {
                        settings.reportDiscordState(configuredRevision, state);
                    }
                }));
        try {
            var connected = factory.create(configuration.token()).setEnableShutdownHook(false)
                    .addEventListeners(sessionListener).build();
            synchronized (this) {
                connectingThread = null;
                if (running && generation == session) {
                    discord = connected;
                    return;
                }
            }
            stopConnection(connected);
        } catch (RuntimeException _) {
            synchronized (this) {
                connectingThread = null;
                if (!running || generation != session) {
                    return;
                }
                generation++;
                registrations.stop();
            }
            settings.reportDiscordState(configuredRevision, State.UNAVAILABLE);
            log.warn("Discord could not start; check operator configuration and provider availability");
        }
    }

    @Override
    public void stop() {
        JDA previous;
        synchronized (this) {
            running = false;
            generation++;
            revision = -1;
            registrations.stop();
            if (connectingThread != null) {
                connectingThread.interrupt();
            }
            previous = discord;
            discord = null;
        }
        stopConnection(previous);
    }

    private void stopConnection(JDA connection) {
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
}
