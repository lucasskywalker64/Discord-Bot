package com.github.lucasskywalker64.discord;

import com.github.lucasskywalker64.guild.GuildConfigurationRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscordListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordListener.class);

    private final GuildConfigurationRepository configurations;

    public DiscordListener(GuildConfigurationRepository configurations) {
        this.configurations = configurations;
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        registerCommands(event.getGuild());
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        registerCommands(event.getGuild());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"general".equals(event.getName()) || !"ping".equals(event.getSubcommandName())
                || event.getSubcommandGroup() != null) {
            return;
        }
        var guild = event.getGuild();
        if (guild == null || !configurations.existsById(guild.getId())) {
            event.reply("Use this command in a configured guild.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).submit().thenCompose(hook -> event.getJDA().getRestPing().submit()
                .thenCompose(rest -> hook.editOriginal("Rest ping: %d ms\nGateway ping: %d ms"
                        .formatted(rest, event.getJDA().getGatewayPing())).submit()))
                .exceptionally(failure -> {
                    log.warn("Failed to answer ping in guild {}", guild.getId(), failure);
                    return null;
                });
    }

    private void registerCommands(Guild guild) {
        if (configurations.existsById(guild.getId())) {
            guild.updateCommands().addCommands(Commands.slash("general", "General utilities")
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                    .addSubcommands(new SubcommandData("ping", "Check gateway and REST latency"))).queue();
        }
    }
}
