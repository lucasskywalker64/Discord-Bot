package com.github.lucasskywalker64.discord;

import net.dv8tion.jda.api.JDABuilder;

@FunctionalInterface
public interface DiscordGatewayFactory {
    JDABuilder create(String token);
}
