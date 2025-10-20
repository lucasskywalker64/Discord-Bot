package com.github.lucasskywalker64.persistence.data;

public record YouTubeData(
        String channelId,
        String name,
        String guildId,
        String channel,
        String message,
        String roleId,
        String secret,
        Long expirationTime) implements Data<YouTubeData> {
    @Override
    public YouTubeData self() {
        return this;
    }

    @Override
    public YouTubeData withMessage(String message) {
        return new YouTubeData(channelId, name, guildId, channel, message, roleId, secret, expirationTime);
    }

    @Override
    public YouTubeData withRoleId(String roleId) {
        return new YouTubeData(channelId, name, guildId, channel, message, roleId, secret, expirationTime);
    }

    public YouTubeData withExpirationTime(Long expirationTime) {
        return new YouTubeData(channelId, name, guildId, channel, message, roleId, secret, expirationTime);
    }
}
