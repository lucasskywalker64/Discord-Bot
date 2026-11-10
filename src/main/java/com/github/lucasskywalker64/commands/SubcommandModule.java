package com.github.lucasskywalker64.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface SubcommandModule {

    CommandDefinition definition();

    CompletionStage<?> handle(CommandContext context, SlashCommandInteractionEvent event);

    default CompletionStage<?> handleAutoComplete(CommandContext context, CommandAutoCompleteInteractionEvent event) {
        return event.replyChoices(List.of()).submit();
    }
}
