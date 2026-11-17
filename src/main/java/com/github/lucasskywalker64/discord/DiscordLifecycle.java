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
            reconcile();
        }
    }

    @TransactionalEventListener
    public void configurationChanged(OperatorConfigurationService.Changed event) {
        enqueue(this::reconcile);
    }

    private synchronized void reconcile() {
        if (!running) {
            return;
        }
        var configuration = settings.discordConnectionSettings();
        if (revision == configuration.revision()) {
            return;
        }
        generation++;
        stopConnection();
        revision = configuration.revision();
        if (!configuration.enabled()) {
            return;
        }
        long session = generation;
        long configuredRevision = revision;
        settings.reportDiscordState(configuredRevision, State.CONNECTING);
        long registrationSession = registrations.start();
        var sessionListener = listener.forSession(registrationSession, () -> running && generation == session,
                state -> enqueue(() -> {
                    if (running && generation == session) {
                        settings.reportDiscordState(configuredRevision, state);
                    }
                }));
        try {
            discord = factory.create(configuration.token()).setEnableShutdownHook(false)
                    .addEventListeners(sessionListener).build();
        } catch (RuntimeException _) {
            generation++;
            registrations.stop();
            settings.reportDiscordState(configuredRevision, State.UNAVAILABLE);
            log.warn("Discord could not start; check operator configuration and provider availability");
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        generation++;
        revision = -1;
        stopConnection();
    }

    private void stopConnection() {
        registrations.stop();
        if (discord == null) {
            return;
        }
        discord.shutdown();
        try {
            if (!discord.awaitShutdown(Duration.ofSeconds(10))) {
                log.warn("Discord shutdown exceeded 10 seconds; cancelling pending requests");
                discord.shutdownNow();
                if (!discord.awaitShutdown(Duration.ofSeconds(5))) {
                    log.warn("Discord did not finish shutting down after cancellation");
                }
            }
        } catch (InterruptedException _) {
            log.warn("Discord shutdown interrupted; cancelling pending requests");
            discord.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            discord = null;
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
