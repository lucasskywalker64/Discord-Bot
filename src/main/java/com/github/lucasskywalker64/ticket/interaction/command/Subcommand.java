package com.github.lucasskywalker64.ticket.interaction.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public interface Subcommand {
    String name();
    String description();
    void define(SlashCommandData root);
    void handle(SlashCommandInteractionEvent event);
}
