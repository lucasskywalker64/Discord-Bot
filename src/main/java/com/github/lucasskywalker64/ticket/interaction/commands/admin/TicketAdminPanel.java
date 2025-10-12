package com.github.lucasskywalker64.ticket.interaction.commands.admin;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.ticket.TicketModule;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.tinylog.Logger;

public class TicketAdminPanel implements SubcommandModule {

    private final TicketModule module;

    public TicketAdminPanel() {
        module = BotMain.getContext().ticketModule();
    }

    @Override public String getRootName() { return "ticket-admin"; }
    @Override public String getSubcommandName() { return "panel"; }
    @Override public String getDescription() { return "Post the Create Ticket panel"; }

    @Override public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription());
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        if (!module.isSetup()) {
            event.reply("Please set up the ticket system first.").setEphemeral(true).queue();
            return;
        }

        GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Open a ticket!")
                .setDescription("Click the button to open a new ticket.")
                .setColor(0x5865F2);
        ActionRow row = ActionRow.of(Button.primary("createTicket", "Create Ticket"));
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
