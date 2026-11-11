package com.github.lucasskywalker64.guild;

import java.util.Set;
import java.util.stream.Collectors;

public class ConfiguredGuilds {

    private final GuildConfigurationRepository repository;
    private volatile Set<String> guildIds = Set.of();

    public ConfiguredGuilds(GuildConfigurationRepository repository) {
        this.repository = repository;
        refresh();
    }

    public boolean contains(String guildId) {
        return guildIds.contains(guildId);
    }

    // Blocking: call outside gateway callbacks, after configuration transactions commit.
    public synchronized void refresh() {
        try {
            guildIds = repository.findAll().stream().map(GuildConfiguration::getGuildId)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException failure) {
            guildIds = Set.of();
            throw failure;
        }
    }
}
