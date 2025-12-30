package com.github.lucasskywalker64.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.lucasskywalker64.BotMain;
import com.github.twitch4j.helix.domain.CustomReward;
import org.tinylog.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class TwitchRedeemCacheService {

    private final Cache<String, List<CustomReward>> redeemCache;

    public TwitchRedeemCacheService() {
        this.redeemCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(1000)
                .build();
    }

    public List<CustomReward> getOrFetch(String broadcasterId) {
        return redeemCache.get(broadcasterId, id -> {
            try {
                return BotMain.getContext().twitch().fetchRewards();
            } catch (Exception e) {
                Logger.error(e, "Failed to fetch rewards for broadcaster {}.", id);
                return Collections.emptyList();
            }
        });
    }

    public void invalidate(String broadcasterId) {
        redeemCache.invalidate(broadcasterId);
    }
}
