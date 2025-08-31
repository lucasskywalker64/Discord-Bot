package com.github.lucasskywalker64.persistence.data;

public record TwitchData(
        String channel,
        String message,
        String username,
        String role,
        String announcementId,
        Long timestamp
) implements Data<TwitchData> {
    @Override
    public TwitchData self() {
        return this;
    }

    @Override
    public TwitchData withMessage(String message) {
        return new TwitchData(channel, message, username, role, announcementId, timestamp);
    }
    @Override
    public TwitchData withRole(String role) {
        return new TwitchData(channel, message, username, role, announcementId, timestamp);
    }
    public TwitchData withAnnouncementId(String announcementId) {
        return new TwitchData(channel, message, username, role, announcementId, timestamp);
    }
    public TwitchData withTimestamp(Long timestamp) {
        return new TwitchData(channel, message, username, role, announcementId, timestamp);
    }
}
