package com.github.lucasskywalker64.persistence.data;

public record YouTubeData(
        String channel,
        String message,
        String name,
        String playlistId,
        String role,
        String videoId,
        String streamId) implements Data<YouTubeData> {
    @Override
    public YouTubeData self() {
        return this;
    }

    public YouTubeData withVideoId(String videoId) {
        return new YouTubeData(channel, message, name, playlistId, role, videoId, streamId);
    }
    public YouTubeData withStreamId(String streamId) {
        return new YouTubeData(channel, message, name, playlistId, role, videoId, streamId);
    }
    @Override
    public YouTubeData withMessage(String message) {
        return new YouTubeData(channel, message, name, playlistId, role, videoId, streamId);
    }
    @Override
    public YouTubeData withRole(String role) {
        return new YouTubeData(channel, message, name, playlistId, role, videoId, streamId);
    }
}
