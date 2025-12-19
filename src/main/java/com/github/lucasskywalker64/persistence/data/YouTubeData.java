package com.github.lucasskywalker64.persistence.data;

import java.util.List;

public record YouTubeData(
        String channelId,
        String name,
        String guildId,
        String discordChannelId,
        String message,
        String roleId,
        String secret,
        Long expirationTime,
        List<String> videoIds) implements Data<YouTubeData> {
    @Override
    public YouTubeData self() {
        return this;
    }

    @Override
    public YouTubeData withMessage(String message) {
        return new YouTubeData(channelId, name, guildId, discordChannelId, message, roleId, secret, expirationTime, videoIds);
    }

    @Override
    public YouTubeData withRoleId(String roleId) {
        return new YouTubeData(channelId, name, guildId, discordChannelId, message, roleId, secret, expirationTime, videoIds);
    }

    public YouTubeData withExpirationTime(Long expirationTime) {
        return new YouTubeData(channelId, name, guildId, discordChannelId, message, roleId, secret, expirationTime, videoIds);
    }
}
