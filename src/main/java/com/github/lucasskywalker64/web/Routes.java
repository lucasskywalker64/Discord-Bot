package com.github.lucasskywalker64.web;

public class Routes {
    private Routes() {}

    public static final String API_BASE = "/api";

    public static final String AUTH_BASE = API_BASE + "/auth";
    public static final String AUTH_DISCORD = AUTH_BASE + "/discord";
    public static final String AUTH_DISCORD_CALLBACK = AUTH_DISCORD + "/callback";
    public static final String AUTH_TWITCH = AUTH_BASE + "/twitch";
    public static final String AUTH_TWITCH_CALLBACK = AUTH_TWITCH + "/callback";

    public static final String WEBHOOKS_BASE = API_BASE + "/webhooks";
    public static final String WEBHOOK_TWITCH = WEBHOOKS_BASE + "/twitch";
    public static final String WEBHOOK_YOUTUBE = WEBHOOKS_BASE + "/youtube";

    public static final String TICKETS_BASE = API_BASE + "/tickets";
    public static final String TICKETS_TRANSCRIPTS = TICKETS_BASE + "/transcripts/{id}";
    public static final String TICKETS_ATTACHMENTS = TICKETS_TRANSCRIPTS + "/attachments";
    public static String buildTranscriptUrl(String baseUrl, long ticketId) {
        return baseUrl + TICKETS_TRANSCRIPTS.replace("{id}", String.valueOf(ticketId));
    }

    public static final String TWITCH_BASE = API_BASE + "/twitch";
    public static final String TWITCH_REWARDS = TWITCH_BASE + "/rewards";
}
