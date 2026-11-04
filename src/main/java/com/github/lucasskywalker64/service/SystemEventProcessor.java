package com.github.lucasskywalker64.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.persistence.data.SystemEvent;
import com.github.lucasskywalker64.persistence.repository.SystemEventRepository;
import com.github.twitch4j.common.util.TypeConvert;
import com.github.twitch4j.eventsub.events.CustomRewardRedemptionAddEvent;
import com.github.twitch4j.eventsub.events.CustomRewardRedemptionUpdateEvent;
import com.github.twitch4j.eventsub.events.EventSubEvent;
import org.tinylog.Logger;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SystemEventProcessor {

    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private final SystemEventRepository repo = SystemEventRepository.getInstance();

    private final Map<String, Class<? extends EventSubEvent>> eventRegistry = Map.of(
            "channel.channel_points_custom_reward_redemption.add", CustomRewardRedemptionAddEvent.class,
            "channel.channel_points_custom_reward_redemption.update", CustomRewardRedemptionUpdateEvent.class
    );

    public void start() {
        ticker.scheduleAtFixedRate(this::processQueue, 1, 1, TimeUnit.SECONDS);
        Logger.info("System Event Processor started.");
    }

    private void processQueue() {
        try {
            List<SystemEvent> pendingEvents = repo.getPendingEvents();
            if (pendingEvents.isEmpty()) return;

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (SystemEvent event : pendingEvents) {
                    executor.submit(() -> processSingleEvent(event));
                }
            }
        } catch (Exception e) {
            Logger.error(e, "Critical error reading from SystemEventRepository");
        }
    }

    private void processSingleEvent(SystemEvent event) {
        try {
            switch (event.domain()) {
                case "twitch" -> processTwitchEvent(event);
                case "youtube" -> processYouTubeEvent(event);
                case "discord" -> processDiscordEvent(event);
                default -> throw new IllegalArgumentException("Unknown domain: " + event.domain());
            }

            repo.updateStatus(event.id(), event.domain(), "PROCESSED");
        } catch (Exception e) {
            Logger.error(e, "POISON PILL DETECTED! Failed to process {} event: {}", event.domain(), event.id());
            try {
                repo.updateStatus(event.id(), event.domain(), "FAILED");
            } catch (SQLException sqlEx) {
                Logger.error(sqlEx, "Failed to mark event as FAILED in database!");
            }
        }
    }

    private void processTwitchEvent(SystemEvent event) throws Exception {
        Class<? extends EventSubEvent> eventClass = eventRegistry.get(event.eventType());
        if (eventClass == null) {
            Logger.warn("Unhandled Twitch event type in queue: {}", event.eventType());
            return;
        }

        JsonNode rootNode = TypeConvert.getObjectMapper().readTree(event.payload());
        EventSubEvent twitchEvent = TypeConvert.getObjectMapper().treeToValue(rootNode.get("event"), eventClass);

        BotMain.getContext().twitch().publishEvent(twitchEvent);
    }

    private void processYouTubeEvent(SystemEvent event) {
        // Not implemented yet
    }

    private void processDiscordEvent(SystemEvent event) {
        // Not implemented yet
    }
}
