package com.github.lucasskywalker64.discord;

import com.github.lucasskywalker64.commands.CommandRegistry;
import com.github.lucasskywalker64.guild.ConfiguredGuilds;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GuildCommandRegistration {

    private static final Logger log = LoggerFactory.getLogger(GuildCommandRegistration.class);
    private static final int MAX_ATTEMPTS = 3;

    private final ConfiguredGuilds configuredGuilds;
    private final CommandRegistry commands;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Registration> pending = new HashMap<>();
    private boolean running;

    public GuildCommandRegistration(ConfiguredGuilds configuredGuilds, CommandRegistry commands,
                                    ScheduledExecutorService scheduler) {
        this.configuredGuilds = configuredGuilds;
        this.commands = commands;
        this.scheduler = scheduler;
    }

    public synchronized void start() {
        running = true;
    }

    public synchronized void stop() {
        running = false;
        pending.values().forEach(registration -> {
            if (registration.retry != null) {
                registration.retry.cancel(false);
            }
        });
        pending.clear();
    }

    public synchronized void register(Guild guild) {
        if (!running || !configuredGuilds.contains(guild.getId()) || pending.containsKey(guild.getId())) {
            return;
        }
        var registration = new Registration(guild);
        pending.put(guild.getId(), registration);
        attempt(registration);
    }

    private synchronized void attempt(Registration registration) {
        if (!running || pending.get(registration.guild.getId()) != registration) {
            return;
        }
        if (!configuredGuilds.contains(registration.guild.getId())) {
            pending.remove(registration.guild.getId());
            return;
        }
        registration.retry = null;
        registration.attempt++;
        try {
            registration.guild.updateCommands().addCommands(commands.definitions().toArray(CommandData[]::new))
                    .queue(ignored -> complete(registration), failure -> failed(registration, failure));
        } catch (RuntimeException failure) {
            failed(registration, failure);
        }
    }

    private synchronized void complete(Registration registration) {
        pending.remove(registration.guild.getId(), registration);
    }

    private synchronized void failed(Registration registration, Throwable failure) {
        String guildId = registration.guild.getId();
        if (!running || pending.get(guildId) != registration) {
            return;
        }
        if (registration.attempt < MAX_ATTEMPTS && isTransient(failure)) {
            long delay = 1L << (registration.attempt - 1);
            log.warn("Command registration failed for guild {}; retrying in {} seconds", guildId, delay, failure);
            try {
                registration.retry = scheduler.schedule(() -> attempt(registration), delay, TimeUnit.SECONDS);
                return;
            } catch (RuntimeException schedulingFailure) {
                failure.addSuppressed(schedulingFailure);
            }
        }
        pending.remove(guildId);
        log.error("Command registration failed for guild {} after {} attempts", guildId, registration.attempt, failure);
    }

    private static boolean isTransient(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ErrorResponseException response) {
                int status = response.getResponse().code;
                if (status > 0) {
                    return status >= 500 && status < 600;
                }
            }
            if (cause instanceof IOException || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static class Registration {
        private final Guild guild;
        private int attempt;
        private ScheduledFuture<?> retry;

        private Registration(Guild guild) {
            this.guild = guild;
        }
    }
}
