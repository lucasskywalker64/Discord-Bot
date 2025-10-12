package com.github.lucasskywalker64.ticket.interaction.commands;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.exceptions.RateLimitException;
import com.github.lucasskywalker64.ticket.TicketModule;
import com.github.lucasskywalker64.ticket.model.Ticket;
import com.github.lucasskywalker64.ticket.service.TicketService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.awt.*;

import static com.github.lucasskywalker64.BotConstants.RATE_LIMIT_TICKETS;

public class TicketCreate implements SubcommandModule {

    private final TicketService ticketService;
    private final TicketModule module;

    public TicketCreate() {
        this.module = BotMain.getContext().ticketModule();
        this.ticketService = module.getService();
    }

    @Override public String getRootName() { return "ticket"; }
    @Override public String getSubcommandName() { return "create"; }
    @Override public String getDescription() { return "Create a new ticket"; }

    @Override public SubcommandData definition() { return new SubcommandData(getSubcommandName(), getDescription()); }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        if (!module.isSetup()) {
            event.reply("The ticket system is not set up.").setEphemeral(true).queue();
            return;
        }

        Ticket ticket = null;
        try {
            ticket = ticketService.createTicket(event.getMember().getIdLong());
        } catch (RateLimitException ex) {
            Logger.error(ex);
            event.reply(RATE_LIMIT_TICKETS).setEphemeral(true).queue();
        } catch (Exception e) {
            Logger.error(e);
            event.reply("Something went wrong. Please contact the developer").setEphemeral(true).queue();
        }
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Ticket Created");
        embed.setDescription(String.format("Opened a new ticket: %s",
                event.getGuild().getTextChannelById(ticket.channelId()).getAsMention()));
        embed.setColor(Color.GREEN);
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
