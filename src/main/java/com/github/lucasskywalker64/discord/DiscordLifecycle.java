package com.github.lucasskywalker64.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;

public class DiscordLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DiscordLifecycle.class);

    private final JDABuilder builder;
    private final DiscordListener listener;
    private volatile JDA discord;

    public DiscordLifecycle(JDABuilder builder, DiscordListener listener) {
        this.builder = builder;
        this.listener = listener;
    }

    @Override
    public void start() {
        if (!isRunning()) {
            discord = builder.addEventListeners(listener).build();
        }
    }

    @Override
    public void stop() {
        if (!isRunning()) {
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
        } catch (InterruptedException exception) {
            log.warn("Discord shutdown interrupted; cancelling pending requests");
            discord.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            discord = null;
        }
    }

    @Override
    public boolean isRunning() {
        return discord != null;
    }
}
