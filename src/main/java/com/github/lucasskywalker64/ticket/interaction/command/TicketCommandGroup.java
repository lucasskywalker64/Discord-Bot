package com.github.lucasskywalker64.ticket.interaction.command;

import com.github.lucasskywalker64.ticket.interaction.command.subcommands.TicketSetupCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

public class TicketCommandGroup {

    private final List<Subcommand> subs;

    public TicketCommandGroup() {
        subs = List.of(
                new TicketSetupCommand()
        );
    }

    public SlashCommandData data() {
        SlashCommandData root = Commands.slash("ticket", "Ticket system commands");
        for (Subcommand sub : subs) {
            sub.define(root);
        }
        return root;
    }

    public void handle(SlashCommandInteractionEvent event) {
        String subName = event.getSubcommandName();
        for (Subcommand sub : subs) {
            if (sub.name().equals(subName)) {
                sub.handle(event);
                return;
            }
        }
        event.replyFormat("Unknown subcommand: %s", subName).setEphemeral(true).queue();
    }
}
