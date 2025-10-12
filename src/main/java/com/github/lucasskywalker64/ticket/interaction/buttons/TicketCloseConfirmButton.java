package com.github.lucasskywalker64.ticket.interaction.buttons;

import com.github.lucasskywalker64.buttons.ButtonModule;
import com.github.lucasskywalker64.ticket.service.TicketService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.tinylog.Logger;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class TicketCloseConfirmButton implements ButtonModule {

    private final TicketService service;

    public TicketCloseConfirmButton(TicketService service) {
        this.service = service;
    }

    @Override
    public String getId() { return "ticketCloseConfirm"; }

    @Override
    public void handle(ButtonInteractionEvent event) {
        event.deferReply(true).queue();
        try {
            service.closeTicket(event.getChannel().asTextChannel(), event.getUser().getIdLong(), null)
                    .whenComplete((success, error) -> {
                if (error != null) {
                    Logger.error(error);
                    event.getHook().sendMessage(INTERNAL_ERROR).queue();
                }
            });
        } catch (Exception e) {
            Logger.error(e);
            event.getHook().sendMessage(INTERNAL_ERROR).queue();
        }
    }
}
