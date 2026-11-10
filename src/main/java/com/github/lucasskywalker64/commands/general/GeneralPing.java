package com.github.lucasskywalker64.commands.general;

import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.commands.CommandContext;
import com.github.lucasskywalker64.commands.CommandDefinition;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.concurrent.CompletionStage;

public class GeneralPing implements SubcommandModule {

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition(
                new CommandDefinition.Root("general", "General utilities", DefaultMemberPermissions.DISABLED),
                null, new SubcommandData("ping", "Check gateway and REST latency"));
    }

    @Override
    public CompletionStage<?> handle(CommandContext context, SlashCommandInteractionEvent event) {
        return event.deferReply(true).submit().thenCompose(hook -> event.getJDA().getRestPing().submit()
                .thenCompose(rest -> hook.editOriginal("Rest ping: %d ms\nGateway ping: %d ms"
                        .formatted(rest, event.getJDA().getGatewayPing())).submit()));
    }
}
