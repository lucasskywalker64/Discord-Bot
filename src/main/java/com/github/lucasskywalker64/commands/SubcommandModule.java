package com.github.lucasskywalker64.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

public interface SubcommandModule {
    String getRootName();
    String getSubcommandName();
    default String getGroupName() { return null; }

    String getDescription();
    SubcommandData definition();

    void handle(SlashCommandInteractionEvent event);
}
