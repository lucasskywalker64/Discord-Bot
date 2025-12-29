package com.github.lucasskywalker64.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.lucasskywalker64.BotMain;
import com.github.twitch4j.helix.domain.ChannelVip;
import org.tinylog.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class TwitchVipsCacheService {

    private final Cache<String, List<ChannelVip>> vipsCache;

    public TwitchVipsCacheService() {
        vipsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(1000)
                .build();
    }

    public List<ChannelVip> getOrFetch(String broadcasterId) {
        return vipsCache.get(broadcasterId, id -> {
            try {
                return BotMain.getContext().twitch().fetchVips();
            } catch (Exception e) {
                Logger.error(e, "Failed to fetch VIPs for broadcaster {}.", id);
                return Collections.emptyList();
            }
        });
    }
}
