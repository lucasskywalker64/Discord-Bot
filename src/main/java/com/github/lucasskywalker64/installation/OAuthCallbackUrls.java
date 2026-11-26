package com.github.lucasskywalker64.installation;

import org.jspecify.annotations.Nullable;

import java.net.URI;

public final class OAuthCallbackUrls {
    public static final String DISCORD_PATH = "/dashboard/oauth/discord/callback";
    public static final String TWITCH_PATH = "/dashboard/oauth/twitch/callback";

    private OAuthCallbackUrls() {
    }

    static @Nullable String origin(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            var uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() > 65535
                    || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))) {
                return null;
            }
            boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
            return "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback)
                    ? uri.getScheme() + "://" + uri.getRawAuthority() : null;
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    static @Nullable String callback(@Nullable String origin, String path) {
        return origin == null ? null : origin + path;
    }
}
