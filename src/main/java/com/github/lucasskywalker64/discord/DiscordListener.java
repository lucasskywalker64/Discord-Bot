package com.github.lucasskywalker64.discord;

import com.github.lucasskywalker64.commands.CommandRegistry;
import com.github.lucasskywalker64.guild.ConfiguredGuilds;
import net.dv8tion.jda.api.events.guild.GuildAvailableEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

public class DiscordListener extends ListenerAdapter {

    private final ConfiguredGuilds configuredGuilds;
    private final CommandRegistry commands;
    private final GuildCommandRegistration registrations;

    public DiscordListener(ConfiguredGuilds configuredGuilds, CommandRegistry commands, GuildCommandRegistration registrations) {
        this.configuredGuilds = configuredGuilds;
        this.commands = commands;
        this.registrations = registrations;
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        registrations.register(event.getGuild());
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        registrations.register(event.getGuild());
    }

    @Override
    public void onGuildAvailable(GuildAvailableEvent event) {
        registrations.register(event.getGuild());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null || !configuredGuilds.contains(guild.getId())) {
            event.reply("Use this command in a configured guild.").setEphemeral(true).queue();
            return;
        }
        commands.dispatch(event);
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null || !configuredGuilds.contains(guild.getId())) {
            event.replyChoices(List.of()).queue();
            return;
        }
        commands.dispatchAutoComplete(event);
    }

}
