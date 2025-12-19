package com.github.lucasskywalker64.persistence.data;

public record TwitchData(
        String channel,
        String message,
        String username,
        String roleId,
        String announcementId,
        Long timestamp,
        String gameName,
        String boxArtUrl,
        String streamId
) implements Data<TwitchData> {
    @Override
    public TwitchData self() {
        return this;
    }

    @Override
    public TwitchData withMessage(String message) {
        return new TwitchData(channel, message, username, roleId, announcementId, timestamp, gameName, boxArtUrl, streamId);
    }
    @Override
    public TwitchData withRoleId(String roleId) {
        return new TwitchData(channel, message, username, roleId, announcementId, timestamp, gameName, boxArtUrl, streamId);
    }
    public TwitchData withAnnouncementId(String announcementId) {
        return new TwitchData(channel, message, username, roleId, announcementId, timestamp, gameName, boxArtUrl, streamId);
    }
    public TwitchData withTimestamp(Long timestamp) {
        return new TwitchData(channel, message, username, roleId, announcementId, timestamp, gameName, boxArtUrl, streamId);
    }
    public TwitchData withLastPlayed(String gameName, String boxArtUrl) {
        return new TwitchData(channel, message, username, roleId, announcementId, timestamp, gameName, boxArtUrl, streamId);
    }
    public TwitchData withStreamId(String streamId) {
        return new TwitchData(channel, message, username, roleId, announcementId, timestamp, gameName, boxArtUrl, streamId);
    }
}
