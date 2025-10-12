package com.github.lucasskywalker64.ticket.interaction.buttons;

import com.github.lucasskywalker64.buttons.ButtonModule;
import com.github.lucasskywalker64.ticket.service.TicketService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.tinylog.Logger;

import java.awt.*;
import java.util.List;

import static com.github.lucasskywalker64.BotConstants.GREEN;
import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class TicketClaimButton implements ButtonModule {

    private final TicketService ticketService;

    public TicketClaimButton(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public String getId() { return "ticketClaim"; }

    @Override
    public void handle(ButtonInteractionEvent event) {
        event.deferEdit().queue();
        try {
            if (!ticketService.claimTicket(event.getGuildChannel(), event.getMember())) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Error")
                        .setColor(Color.RED)
                        .setDescription("Only staff members can claim tickets");
                event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
                return;
            }
        } catch (Exception e) {
            Logger.error(e);
            event.getHook().sendMessage(INTERNAL_ERROR).setEphemeral(true).queue();
            return;
        }
        Message message = event.getMessage();
        List<Button> newButtons = message.getButtons().stream()
                .filter(button -> !button.getId().equals(event.getComponentId()))
                .toList();
        event.getHook().editMessageComponentsById(message.getId(), ActionRow.of(newButtons)).queue();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Ticket Claimed")
                .setColor(GREEN)
                .setDescription(String.format("Your ticket will be handled by %s",
                        event.getMember().getUser().getAsMention()));
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}
