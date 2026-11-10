package com.github.lucasskywalker64.commands.general;

import com.github.lucasskywalker64.commands.SubcommandModule;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeneralPing implements SubcommandModule {

    private static final Logger log = LoggerFactory.getLogger(GeneralPing.class);

    @Override
    public String getRootName() {
        return "general";
    }

    @Override
    public String getSubcommandName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "Check gateway and REST latency";
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).submit().thenCompose(hook -> event.getJDA().getRestPing().submit()
                .thenCompose(rest -> hook.editOriginal("Rest ping: %d ms\nGateway ping: %d ms"
                        .formatted(rest, event.getJDA().getGatewayPing())).submit()))
                .exceptionally(failure -> {
                    log.warn("Failed to answer ping in guild {}", event.getGuild().getId(), failure);
                    return null;
                });
    }
}
