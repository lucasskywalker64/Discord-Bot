package com.github.lucasskywalker64.ticket.interaction.buttons;

import com.github.lucasskywalker64.buttons.ButtonModule;
import com.github.lucasskywalker64.exceptions.RateLimitException;
import com.github.lucasskywalker64.ticket.model.Ticket;
import com.github.lucasskywalker64.ticket.service.TicketService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.tinylog.Logger;

import java.awt.*;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;
import static com.github.lucasskywalker64.BotConstants.RATE_LIMIT_TICKETS;

public class TicketCreateButton implements ButtonModule {

    private final TicketService ticketService;

    public TicketCreateButton(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override public String getId() { return "createTicket"; }

    @Override
    public void handle(ButtonInteractionEvent event) {
        Ticket ticket;
        try {
            ticket = ticketService.createTicket(event.getMember().getIdLong());
        } catch (RateLimitException ex) {
            Logger.error(ex);
            event.reply(RATE_LIMIT_TICKETS)
                    .setEphemeral(true).queue();
            return;
        } catch (Exception e) {
            Logger.error(e);
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
            return;
        }
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Ticket Created");
        embed.setDescription(String.format("Opened a new ticket: %s",
                event.getGuild().getTextChannelById(ticket.channelId()).getAsMention()));
        embed.setColor(Color.GREEN);
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
