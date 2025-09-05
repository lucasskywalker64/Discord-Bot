package com.github.lucasskywalker64.persistence.data;

public record TwitchData(
        String channel,
        String message,
        String username,
        String roleId,
        String announcementId,
        Long timestamp,
        String gameName,
        String boxArtUrl
) implements Data<TwitchData> {
    @Override
    public TwitchData self() {
        return this;
    }

    @Override
    public TwitchData withMessage(String message) {
        return new TwitchData(channel, message, username, roleId, announcementId, timestamp, gameName, boxArtUrl);
    }
    @Override
    public TwitchData withRoleId(String roleId) {
        return new TwitchData(channel, message, username, roleId, announcementId, timestamp, gameName, boxArtUrl);
    }
    public TwitchData withAnnouncementId(String announcementId) {
        return new TwitchData(channel, message, username, roleId, announcementId, timestamp, gameName, boxArtUrl);
    }
    public TwitchData withTimestamp(Long timestamp) {
        return new TwitchData(channel, message, username, roleId, announcementId, timestamp, gameName, boxArtUrl);
    }
    public TwitchData withLastPlayed(String gameName, String boxArtUrl) {
        return new TwitchData(channel, message, username, roleId, announcementId, timestamp, gameName, boxArtUrl);
    }
}
