package com.github.lucasskywalker64.ticket.interaction.buttons;

import com.github.lucasskywalker64.buttons.ButtonModule;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class TicketCloseReasonButton implements ButtonModule {

    @Override
    public String getId() { return "ticketCloseReason"; }

    @Override
    public void handle(ButtonInteractionEvent event) {
        TextInput reason = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Reason for closing the ticket, e.g. \"Resolved\"")
                .build();
        Modal modal = Modal.create("ticketClose", "Close")
                .addComponents(ActionRow.of(reason))
                .build();
        event.replyModal(modal).queue();
    }
}
