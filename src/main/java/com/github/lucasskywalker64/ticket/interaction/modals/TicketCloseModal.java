package com.github.lucasskywalker64.ticket.interaction.modals;

import com.github.lucasskywalker64.modals.ModalModule;
import com.github.lucasskywalker64.ticket.service.TicketService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.tinylog.Logger;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class TicketCloseModal implements ModalModule {

    private final TicketService service;

    public TicketCloseModal(TicketService service) {
        this.service = service;
    }

    @Override
    public String getId() { return "ticketClose"; }

    @Override
    public void handle(ModalInteractionEvent event) {
        event.deferReply(true).queue();
        String reason = event.getValue("reason").getAsString();
        try {
            service.closeTicket(event.getChannel().asTextChannel(), event.getUser().getIdLong(), reason)
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
