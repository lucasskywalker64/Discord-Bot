package com.github.lucasskywalker64.guild;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@DependsOnDatabaseInitialization
public class GuildProvisioning {

    public GuildProvisioning(JdbcTemplate jdbc, @Value("${discord.guild-ids:}") List<String> guildIds) {
        for (String guildId : guildIds) {
            if (!guildId.matches("[1-9][0-9]{0,19}") || Long.parseUnsignedLong(guildId) == 0) {
                throw new IllegalArgumentException("Invalid Discord guild ID: " + guildId);
            }
        }
        for (String guildId : guildIds) {
            jdbc.update("INSERT INTO guild_configuration (guild_id) VALUES (?) ON CONFLICT (guild_id) DO NOTHING", guildId);
        }
    }
}
