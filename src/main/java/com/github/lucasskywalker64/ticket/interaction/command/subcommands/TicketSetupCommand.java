package com.github.lucasskywalker64.ticket.interaction.command.subcommands;

import com.github.lucasskywalker64.ticket.interaction.command.Subcommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.tinylog.Logger;

public class TicketSetupCommand implements Subcommand {

    @Override
    public String name() {
        return "setup";
    }

    @Override
    public String description() {
        return "Post the Create Ticket panel";
    }

    @Override
    public void define(SlashCommandData root) {
        root.addSubcommands(new SubcommandData(name(), description()))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Open a ticket!")
                .setDescription("Click the button to open a new ticket.")
                .setColor(0x5865F2);
        ActionRow row = ActionRow.of(Button.primary("create_ticket", "Create Ticket"));
        channel.sendMessageEmbeds(embed.build()).setComponents(row).queue(
                ok -> event.reply("Panel posted.").setEphemeral(true).queue(),
                err -> {
                    Logger.error(err.getMessage());
                    event.reply("Failed to post panel. Please contact the developer.")
                            .setEphemeral(true).queue();
                }
        );
    }
}
