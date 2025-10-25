package com.github.lucasskywalker64.persistence.data;

public record YouTubeData(
        String channelId,
        String name,
        String guildId,
        String discordChannelId,
        String message,
        String roleId,
        String secret,
        Long expirationTime,
        String videoId,
        String streamId) implements Data<YouTubeData> {
    @Override
    public YouTubeData self() {
        return this;
    }

    @Override
    public YouTubeData withMessage(String message) {
        return new YouTubeData(channelId, name, guildId, discordChannelId, message, roleId, secret, expirationTime, videoId, streamId);
    }

    @Override
    public YouTubeData withRoleId(String roleId) {
        return new YouTubeData(channelId, name, guildId, discordChannelId, message, roleId, secret, expirationTime, videoId, streamId);
    }

    public YouTubeData withExpirationTime(Long expirationTime) {
        return new YouTubeData(channelId, name, guildId, discordChannelId, message, roleId, secret, expirationTime, videoId, streamId);
    }

    public YouTubeData withVideoId(String videoId) {
        return new YouTubeData(channelId, name, guildId, discordChannelId, message, roleId, secret, expirationTime, videoId, streamId);
    }

    public YouTubeData withStreamId(String streamId) {
        return new YouTubeData(channelId, name, guildId, discordChannelId, message, roleId, secret, expirationTime, videoId, streamId);
    }
}
