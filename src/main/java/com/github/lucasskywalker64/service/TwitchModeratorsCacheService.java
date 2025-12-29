package com.github.lucasskywalker64.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.lucasskywalker64.BotMain;
import com.github.twitch4j.helix.domain.Moderator;
import org.tinylog.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class TwitchModeratorsCacheService {

    private final Cache<String, List<Moderator>> moderatorsCache;

    public TwitchModeratorsCacheService() {
        moderatorsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(1000)
                .build();
    }

    public List<Moderator> getOrFetch(String broadcasterId) {
        return moderatorsCache.get(broadcasterId, id -> {
            try {
                return BotMain.getContext().twitch().fetchModerators();
            } catch (Exception e) {
                Logger.error(e, "Failed to fetch moderators for broadcaster {}.", id);
                return Collections.emptyList();
            }
        });
    }
}
