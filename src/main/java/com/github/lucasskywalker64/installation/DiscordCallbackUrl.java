package com.github.lucasskywalker64.installation;

import org.jspecify.annotations.Nullable;

import java.net.URI;

public final class DiscordCallbackUrl {
    public static final String PATH = "/dashboard/oauth/discord/callback";

    private DiscordCallbackUrl() {
    }

    public static boolean isValid(@Nullable String value) {
        var uri = withAllowedOrigin(value);
        return uri != null && PATH.equals(uri.getRawPath()) && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
    }

    static String suggested(@Nullable String publicUrl, @Nullable String savedCallbackUrl) {
        var uri = withAllowedOrigin(publicUrl);
        if (uri == null) {
            uri = withAllowedOrigin(savedCallbackUrl);
        }
        return uri == null ? "https://<your-public-host>" + PATH : uri.resolve(PATH).toString();
    }

    private static @Nullable URI withAllowedOrigin(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            var uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || uri.getRawUserInfo() != null) {
                return null;
            }
            boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
            return "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback) ? uri : null;
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
