package com.github.lucasskywalker64.discord;

import com.github.lucasskywalker64.commands.CommandRegistry;
import com.github.lucasskywalker64.guild.GuildConfigurationRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;

public class DiscordListener extends ListenerAdapter {

    private final GuildConfigurationRepository configurations;
    private final CommandRegistry commands;

    public DiscordListener(GuildConfigurationRepository configurations, CommandRegistry commands) {
        this.configurations = configurations;
        this.commands = commands;
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
        var guild = event.getGuild();
        if (guild == null || !configurations.existsById(guild.getId())) {
            event.reply("Use this command in a configured guild.").setEphemeral(true).queue();
            return;
        }
        commands.dispatch(event);
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null || !configurations.existsById(guild.getId())) {
            event.replyChoices(List.of()).queue();
            return;
        }
        commands.dispatchAutoComplete(event);
    }

    private void registerCommands(Guild guild) {
        if (configurations.existsById(guild.getId())) {
            guild.updateCommands().addCommands(commands.definitions().toArray(CommandData[]::new)).queue();
        }
    }
}
