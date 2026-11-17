package com.github.lucasskywalker64.discord;

import net.dv8tion.jda.api.JDABuilder;
import okhttp3.OkHttpClient;

@FunctionalInterface
public interface DiscordGatewayFactory {
    JDABuilder create(String token, OkHttpClient httpClient);
}
