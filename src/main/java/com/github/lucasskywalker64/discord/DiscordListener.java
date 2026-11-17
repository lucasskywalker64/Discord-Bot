package com.github.lucasskywalker64.discord;

import com.github.lucasskywalker64.commands.CommandRegistry;
import com.github.lucasskywalker64.guild.ConfiguredGuilds;
import net.dv8tion.jda.api.events.guild.GuildAvailableEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.GenericSessionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import com.github.lucasskywalker64.operator.OperatorConfiguration.State;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class DiscordListener extends ListenerAdapter {

    private final ConfiguredGuilds configuredGuilds;
    private final CommandRegistry commands;
    private final GuildCommandRegistration registrations;

    public DiscordListener(ConfiguredGuilds configuredGuilds, CommandRegistry commands, GuildCommandRegistration registrations) {
        this.configuredGuilds = configuredGuilds;
        this.commands = commands;
        this.registrations = registrations;
    }

    EventListener forSession(long registrationSession, BooleanSupplier active, Consumer<State> status) {
        return event -> {
            if (!active.getAsBoolean()) {
                return;
            }
            if (event instanceof GenericSessionEvent session) {
                switch (session.getState()) {
                    case READY, RESUMED, RECREATED -> status.accept(State.CONNECTED);
                    case DISCONNECTED, INVALIDATED, SHUTDOWN -> status.accept(State.UNAVAILABLE);
                }
            }
            switch (event) {
                case GuildReadyEvent ready -> registrations.register(ready.getGuild(), registrationSession);
                case GuildJoinEvent joined -> registrations.register(joined.getGuild(), registrationSession);
                case GuildAvailableEvent available -> registrations.register(available.getGuild(), registrationSession);
                default -> onEvent(event);
            }
        };
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
